//! Starting Minecraft, streaming its output, and reading process/system stats.

use crate::error::{AppError, Result};
use crate::state::{AppState, GameHandle};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::io::{AsyncBufReadExt, AsyncRead, AsyncWriteExt, BufReader};
use tokio::sync::mpsc;

#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x0800_0000;

fn is_java_executable(p: &Path) -> bool {
    let name = p
        .file_name()
        .map(|n| n.to_string_lossy().to_lowercase())
        .unwrap_or_default();
    matches!(name.as_str(), "java" | "javaw" | "java.exe" | "javaw.exe") && p.is_file()
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchRequest {
    instance_id: String,
    java: String,
    args: Vec<String>,
    cwd: String,
    #[serde(default)]
    env: HashMap<String, String>,
    /// Where to write the raw game output (inside MOTION's data dir).
    log_file: String,
}

#[derive(Serialize, Clone)]
struct LogLine {
    stream: &'static str,
    line: String,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct LogBatch {
    instance_id: String,
    lines: Vec<LogLine>,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct ExitEvent {
    instance_id: String,
    code: Option<i32>,
    killed: bool,
}

/// Collect values that must never reach logs (the Minecraft access token).
fn secrets_from_args(args: &[String]) -> Vec<String> {
    let mut out = Vec::new();
    for w in args.windows(2) {
        if w[0] == "--accessToken" && w[1].len() > 8 {
            out.push(w[1].clone());
        }
    }
    out
}

fn mask(line: &str, secrets: &[String]) -> String {
    let mut s = line.to_string();
    for secret in secrets {
        if s.contains(secret.as_str()) {
            s = s.replace(secret.as_str(), "[redacted]");
        }
    }
    s
}

async fn pump<R: AsyncRead + Unpin>(reader: R, stream: &'static str, tx: mpsc::UnboundedSender<LogLine>) {
    let mut reader = BufReader::new(reader);
    let mut buf = Vec::with_capacity(512);
    loop {
        buf.clear();
        match reader.read_until(b'\n', &mut buf).await {
            Ok(0) | Err(_) => break,
            Ok(_) => {
                let line = String::from_utf8_lossy(&buf).trim_end_matches(['\r', '\n']).to_string();
                if tx.send(LogLine { stream, line }).is_err() {
                    break;
                }
            }
        }
    }
}

#[tauri::command]
pub async fn launch_game(app: AppHandle, state: State<'_, AppState>, req: LaunchRequest) -> Result<u32> {
    let java = PathBuf::from(&req.java);
    if !is_java_executable(&java) {
        return Err(AppError::new(
            "java_missing",
            format!("Java was not found at {}", java.display()),
        ));
    }
    let cwd = state.roots.resolve(&req.cwd)?;
    let log_path = state.roots.resolve(&req.log_file)?;
    std::fs::create_dir_all(&cwd)?;
    if let Some(parent) = log_path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    {
        let games = state
            .games
            .lock()
            .map_err(|_| AppError::new("internal", "lock poisoned"))?;
        if games.contains_key(&req.instance_id) {
            return Err(AppError::new("already_running", "This installation is already running"));
        }
    }

    let secrets = secrets_from_args(&req.args);
    let mut cmd = tokio::process::Command::new(&java);
    cmd.args(&req.args)
        .current_dir(&cwd)
        .envs(&req.env)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);

    let mut child = cmd
        .spawn()
        .map_err(|e| AppError::new("launch_failed", format!("Could not start Java: {e}")))?;
    let pid = child.id().unwrap_or(0);
    log::info!("launched instance {} (pid {pid})", req.instance_id);
    crate::discord::game_started(&app, &req.instance_id, game_version(&req.args));

    let (tx, mut rx) = mpsc::unbounded_channel::<LogLine>();
    if let Some(out) = child.stdout.take() {
        tokio::spawn(pump(out, "stdout", tx.clone()));
    }
    if let Some(err) = child.stderr.take() {
        tokio::spawn(pump(err, "stderr", tx.clone()));
    }
    drop(tx);

    // Batch log lines into events (and into the log file) so a chatty game
    // can't flood the UI with thousands of IPC messages per second.
    {
        let app = app.clone();
        let instance_id = req.instance_id.clone();
        let secrets = secrets.clone();
        tokio::spawn(async move {
            let mut file = tokio::fs::File::create(&log_path).await.ok();
            let mut batch: Vec<LogLine> = Vec::new();
            let mut tick = tokio::time::interval(Duration::from_millis(100));
            loop {
                tokio::select! {
                    msg = rx.recv() => match msg {
                        Some(mut l) => {
                            l.line = mask(&l.line, &secrets);
                            if let Some(f) = file.as_mut() {
                                let _ = f.write_all(l.line.as_bytes()).await;
                                let _ = f.write_all(b"\n").await;
                            }
                            batch.push(l);
                            if batch.len() >= 250 {
                                let _ = app.emit("game://log", LogBatch { instance_id: instance_id.clone(), lines: std::mem::take(&mut batch) });
                            }
                        }
                        None => break,
                    },
                    _ = tick.tick() => {
                        if !batch.is_empty() {
                            let _ = app.emit("game://log", LogBatch { instance_id: instance_id.clone(), lines: std::mem::take(&mut batch) });
                        }
                    }
                }
            }
            if !batch.is_empty() {
                let _ = app.emit(
                    "game://log",
                    LogBatch {
                        instance_id: instance_id.clone(),
                        lines: batch,
                    },
                );
            }
            if let Some(mut f) = file {
                let _ = f.flush().await;
            }
        });
    }

    let (kill_tx, kill_rx) = tokio::sync::oneshot::channel::<()>();
    {
        let mut games = state
            .games
            .lock()
            .map_err(|_| AppError::new("internal", "lock poisoned"))?;
        games.insert(
            req.instance_id.clone(),
            GameHandle {
                pid,
                kill: Some(kill_tx),
            },
        );
    }

    {
        let app = app.clone();
        let instance_id = req.instance_id.clone();
        tokio::spawn(async move {
            let mut killed = false;
            let status = tokio::select! {
                s = child.wait() => s.ok(),
                _ = kill_rx => {
                    killed = true;
                    let _ = child.kill().await;
                    child.wait().await.ok()
                }
            };
            if let Ok(mut games) = app.state::<AppState>().games.lock() {
                games.remove(&instance_id);
            }
            crate::discord::game_stopped(&app, &instance_id);
            let code = status.and_then(|s| s.code());
            log::info!("instance {instance_id} exited with {code:?}");
            let _ = app.emit(
                "game://exit",
                ExitEvent {
                    instance_id,
                    code,
                    killed,
                },
            );
        });
    }
    Ok(pid)
}

fn game_version(args: &[String]) -> String {
    args.windows(2)
        .find(|pair| pair[0] == "--version")
        .map(|pair| pair[1].clone())
        .unwrap_or_else(|| "Java Edition".to_string())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaToolRequest {
    java: String,
    classpath: Vec<String>,
    main_class: String,
    args: Vec<String>,
    cwd: String,
    task_id: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaToolResult {
    code: Option<i32>,
    output: String,
}

/// Run a short-lived Java program to completion — used for Forge/NeoForge
/// installer processors. The jars must live in MOTION's own library cache, the
/// process gets no console window, and it is killed on cancel or after 20 minutes.
#[tauri::command]
pub async fn run_java_tool(state: State<'_, AppState>, req: JavaToolRequest) -> Result<JavaToolResult> {
    use std::collections::VecDeque;
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::Arc;

    let java = PathBuf::from(&req.java);
    if !is_java_executable(&java) {
        return Err(AppError::new(
            "java_missing",
            format!("Java was not found at {}", java.display()),
        ));
    }
    let mut jars = Vec::with_capacity(req.classpath.len());
    for entry in &req.classpath {
        jars.push(state.roots.resolve_internal(entry)?.to_string_lossy().to_string());
    }
    let cwd = state.roots.resolve(&req.cwd)?;
    let sep = if cfg!(windows) { ";" } else { ":" };

    let cancel = Arc::new(AtomicBool::new(false));
    state.register_task(&req.task_id, cancel.clone());

    let mut cmd = tokio::process::Command::new(&java);
    cmd.arg("-cp")
        .arg(jars.join(sep))
        .arg(&req.main_class)
        .args(&req.args)
        .current_dir(&cwd)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true);
    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);
    log::info!("running installer tool {}", req.main_class);
    let mut child = match cmd.spawn() {
        Ok(c) => c,
        Err(e) => {
            state.finish_task(&req.task_id);
            return Err(AppError::new("launch_failed", format!("Could not start Java: {e}")));
        }
    };

    let (tx, mut rx) = mpsc::unbounded_channel::<LogLine>();
    if let Some(out) = child.stdout.take() {
        tokio::spawn(pump(out, "stdout", tx.clone()));
    }
    if let Some(err) = child.stderr.take() {
        tokio::spawn(pump(err, "stderr", tx.clone()));
    }
    drop(tx);
    let collector = tokio::spawn(async move {
        let mut tail: VecDeque<String> = VecDeque::new();
        while let Some(l) = rx.recv().await {
            log::debug!("[tool] {}", l.line);
            tail.push_back(l.line);
            if tail.len() > 60 {
                tail.pop_front();
            }
        }
        tail.into_iter().collect::<Vec<_>>().join("\n")
    });

    let started = std::time::Instant::now();
    let limit = Duration::from_secs(20 * 60);
    let status = loop {
        tokio::select! {
            s = child.wait() => break s.ok(),
            _ = tokio::time::sleep(Duration::from_millis(250)) => {
                let cancelled = cancel.load(Ordering::Relaxed);
                if cancelled || started.elapsed() > limit {
                    let _ = child.kill().await;
                    state.finish_task(&req.task_id);
                    return Err(if cancelled {
                        AppError::new("cancelled", "Cancelled")
                    } else {
                        AppError::new("timeout", "The installer step took too long and was stopped")
                    });
                }
            }
        }
    };
    state.finish_task(&req.task_id);
    let output = collector.await.unwrap_or_default();
    let code = status.and_then(|s| s.code());
    log::info!("installer tool {} exited with {code:?}", req.main_class);
    Ok(JavaToolResult { code, output })
}

#[tauri::command]
pub fn kill_game(state: State<'_, AppState>, instance_id: String) -> Result<()> {
    let mut games = state
        .games
        .lock()
        .map_err(|_| AppError::new("internal", "lock poisoned"))?;
    if let Some(h) = games.get_mut(&instance_id) {
        if let Some(tx) = h.kill.take() {
            let _ = tx.send(());
        }
    }
    Ok(())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RunningGame {
    instance_id: String,
    pid: u32,
}

#[tauri::command]
pub fn running_games(state: State<'_, AppState>) -> Vec<RunningGame> {
    state
        .games
        .lock()
        .map(|g| {
            g.iter()
                .map(|(k, v)| RunningGame {
                    instance_id: k.clone(),
                    pid: v.pid,
                })
                .collect()
        })
        .unwrap_or_default()
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProcessStats {
    memory_bytes: u64,
    cpu_percent: f32,
}

/// Resident memory and CPU usage of a running game process.
#[tauri::command]
pub fn process_stats(state: State<'_, AppState>, pid: u32) -> Option<ProcessStats> {
    let mut sys = state.sys.lock().ok()?;
    let pid = sysinfo::Pid::from_u32(pid);
    if !sys.refresh_process(pid) {
        return None;
    }
    sys.process(pid).map(|p| ProcessStats {
        memory_bytes: p.memory(),
        cpu_percent: p.cpu_usage(),
    })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemInfo {
    total_memory: u64,
    available_memory: u64,
    cpu_brand: String,
    logical_cores: usize,
    physical_cores: Option<usize>,
    os: String,
    gpus: Vec<String>,
}

#[cfg(windows)]
async fn detect_gpus() -> Vec<String> {
    let mut cmd = tokio::process::Command::new("powershell.exe");
    cmd.args([
        "-NoProfile",
        "-NonInteractive",
        "-Command",
        "(Get-CimInstance Win32_VideoController).Name",
    ])
    .stdin(Stdio::null())
    .stdout(Stdio::piped())
    .stderr(Stdio::null())
    .creation_flags(CREATE_NO_WINDOW);
    match tokio::time::timeout(Duration::from_secs(8), cmd.output()).await {
        Ok(Ok(out)) => String::from_utf8_lossy(&out.stdout)
            .lines()
            .map(|l| l.trim().to_string())
            .filter(|l| !l.is_empty())
            .collect(),
        _ => vec![],
    }
}

#[cfg(not(windows))]
async fn detect_gpus() -> Vec<String> {
    vec![]
}

#[tauri::command]
pub async fn system_info(state: State<'_, AppState>) -> Result<SystemInfo> {
    let (total_memory, available_memory, cpu_brand, logical_cores, physical_cores) = {
        let mut sys = state
            .sys
            .lock()
            .map_err(|_| AppError::new("internal", "lock poisoned"))?;
        sys.refresh_memory();
        sys.refresh_cpu();
        (
            sys.total_memory(),
            sys.available_memory(),
            sys.cpus()
                .first()
                .map(|c| c.brand().trim().to_string())
                .unwrap_or_default(),
            sys.cpus().len(),
            sys.physical_core_count(),
        )
    };
    Ok(SystemInfo {
        total_memory,
        available_memory,
        cpu_brand,
        logical_cores,
        physical_cores,
        os: sysinfo::System::long_os_version().unwrap_or_else(|| std::env::consts::OS.to_string()),
        gpus: detect_gpus().await,
    })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaInfo {
    path: String,
    version: String,
    major: u32,
    is_64bit: bool,
}

fn parse_java_version(output: &str) -> Option<(String, u32)> {
    // `openjdk version "21.0.3" 2024-04-16` / `java version "1.8.0_401"`
    let start = output.find('"')? + 1;
    let end = start + output[start..].find('"')?;
    let version = output[start..end].to_string();
    let mut parts = version.split(|c: char| c == '.' || c == '_' || c == '-' || c == '+');
    let first: u32 = parts.next()?.parse().ok()?;
    let major = if first == 1 { parts.next()?.parse().ok()? } else { first };
    Some((version, major))
}

/// Run `java -version` to validate a Java executable and read its version.
#[tauri::command]
pub async fn java_probe(path: String) -> Result<JavaInfo> {
    let p = PathBuf::from(&path);
    if !is_java_executable(&p) {
        return Err(AppError::new(
            "java_missing",
            format!("{} is not a Java executable", p.display()),
        ));
    }
    // javaw has no console; probe with the sibling java(.exe) when available.
    let probe = {
        let name = p
            .file_name()
            .map(|n| n.to_string_lossy().to_lowercase())
            .unwrap_or_default();
        let sibling = if name == "javaw.exe" {
            p.with_file_name("java.exe")
        } else if name == "javaw" {
            p.with_file_name("java")
        } else {
            p.clone()
        };
        if sibling.is_file() {
            sibling
        } else {
            p.clone()
        }
    };
    let mut cmd = tokio::process::Command::new(&probe);
    cmd.arg("-version")
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);
    let out = tokio::time::timeout(Duration::from_secs(15), cmd.output())
        .await
        .map_err(|_| AppError::new("java_broken", "Java did not respond"))?
        .map_err(|e| AppError::new("java_broken", format!("Could not run Java: {e}")))?;
    let text = format!(
        "{}{}",
        String::from_utf8_lossy(&out.stderr),
        String::from_utf8_lossy(&out.stdout)
    );
    let (version, major) =
        parse_java_version(&text).ok_or_else(|| AppError::new("java_broken", "Could not read the Java version"))?;
    Ok(JavaInfo {
        path,
        version,
        major,
        is_64bit: text.contains("64-Bit"),
    })
}

/// Look for Java installations in the usual places.
#[tauri::command]
pub fn find_javas(state: State<'_, AppState>) -> Vec<String> {
    let exe = if cfg!(windows) { "javaw.exe" } else { "java" };
    let mut roots: Vec<PathBuf> = vec![state.roots.data_dir().join("runtimes")];
    if let Ok(home) = std::env::var("JAVA_HOME") {
        roots.push(PathBuf::from(home).join(".."));
    }
    #[cfg(windows)]
    {
        for var in ["ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"] {
            if let Ok(pf) = std::env::var(var) {
                for vendor in [
                    "Java",
                    "Eclipse Adoptium",
                    "Eclipse Foundation",
                    "Microsoft",
                    "Zulu",
                    "BellSoft",
                    "Amazon Corretto",
                    "Semeru",
                ] {
                    roots.push(PathBuf::from(&pf).join(vendor));
                }
            }
        }
        if let Ok(local) = std::env::var("LOCALAPPDATA") {
            roots.push(PathBuf::from(&local).join("Programs").join("Eclipse Adoptium"));
        }
    }
    #[cfg(not(windows))]
    {
        roots.push(PathBuf::from("/usr/lib/jvm"));
    }
    let mut found = Vec::new();
    for root in roots {
        let Ok(rd) = std::fs::read_dir(&root) else { continue };
        for e in rd.flatten() {
            let candidate = e.path().join("bin").join(exe);
            if candidate.is_file() {
                found.push(candidate.to_string_lossy().to_string());
            }
        }
    }
    found.sort();
    found.dedup();
    found
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_java_versions() {
        assert_eq!(
            parse_java_version("openjdk version \"21.0.3\" 2024-04-16 LTS"),
            Some(("21.0.3".into(), 21))
        );
        assert_eq!(
            parse_java_version("java version \"1.8.0_401\""),
            Some(("1.8.0_401".into(), 8))
        );
        assert_eq!(
            parse_java_version("openjdk version \"17\" 2021-09-14"),
            Some(("17".into(), 17))
        );
    }

    #[test]
    fn masks_tokens() {
        let args = vec![
            "--username".into(),
            "Steve".into(),
            "--accessToken".into(),
            "eyJabcdefghijk".into(),
        ];
        let s = secrets_from_args(&args);
        assert_eq!(mask("token=eyJabcdefghijk", &s), "token=[redacted]");
    }
}
