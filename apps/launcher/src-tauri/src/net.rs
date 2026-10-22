//! Networking: an allowlisted HTTP client for the UI and a verified,
//! resumable-by-retry batch downloader with progress events.

use crate::error::{AppError, Result};
use crate::state::AppState;
use futures_util::StreamExt;
use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use sha2::Sha512;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tauri::{AppHandle, Emitter, State};
use tokio::io::AsyncWriteExt;

/// Hosts MOTION talks to. Anything else is refused, including on redirects.
const ALLOWED_HOSTS: &[&str] = &[
    // Mojang / Minecraft
    "piston-meta.mojang.com",
    "piston-data.mojang.com",
    "launchermeta.mojang.com",
    "launcher.mojang.com",
    "launchercontent.mojang.com",
    "libraries.minecraft.net",
    "resources.download.minecraft.net",
    // Microsoft / Xbox authentication
    "login.microsoftonline.com",
    "user.auth.xboxlive.com",
    "xsts.auth.xboxlive.com",
    "api.minecraftservices.com",
    // Server-join handshake used to prove account ownership to MOTION's API
    // (the access token itself never leaves for MOTION's servers).
    "sessionserver.mojang.com",
    // Mod loaders
    "meta.fabricmc.net",
    "maven.fabricmc.net",
    "meta.quiltmc.org",
    "maven.quiltmc.org",
    "maven.neoforged.net",
    "maven.minecraftforge.net",
    "files.minecraftforge.net",
    "repo1.maven.org",
    "repo.maven.apache.org",
    // Modrinth and hosts allowed by the .mrpack specification
    "api.modrinth.com",
    "cdn.modrinth.com",
    "github.com",
    "raw.githubusercontent.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
    "gitlab.com",
];

pub fn host_allowed(url: &url::Url) -> bool {
    let Some(host) = url.host_str() else { return false };
    if url.scheme() != "https" {
        // Debug builds may talk to a local MOTION API over plain http (npm run dev in apps/api).
        return cfg!(debug_assertions)
            && url.scheme() == "http"
            && matches!(host, "localhost" | "127.0.0.1")
            && option_env!("BLOOM_API_HOST").is_some_and(|h| h == host);
    }
    if ALLOWED_HOSTS.contains(&host) {
        return true;
    }
    // Optional MOTION web API host, baked in at build time (BLOOM_API_HOST=api.example.com).
    if let Some(api) = option_env!("BLOOM_API_HOST") {
        if !api.is_empty() && host == api {
            return true;
        }
    }
    false
}

pub fn build_client() -> reqwest::Client {
    // Modrinth asks clients to identify themselves with a contact (docs/MODRINTH.md).
    let user_agent = format!(
        "bloom-launcher/{} ({})",
        env!("CARGO_PKG_VERSION"),
        option_env!("BLOOM_CONTACT").unwrap_or("MOTION Minecraft launcher")
    );
    reqwest::Client::builder()
        .user_agent(user_agent)
        .connect_timeout(Duration::from_secs(15))
        .redirect(reqwest::redirect::Policy::custom(|attempt| {
            if attempt.previous().len() >= 10 || !host_allowed(attempt.url()) {
                attempt.stop()
            } else {
                attempt.follow()
            }
        }))
        .build()
        .expect("failed to build HTTP client")
}

fn parse_allowed(raw: &str) -> Result<url::Url> {
    let url = url::Url::parse(raw).map_err(|e| AppError::new("bad_url", e.to_string()))?;
    if !host_allowed(&url) {
        return Err(AppError::new(
            "host_not_allowed",
            format!("MOTION does not connect to {}", url.host_str().unwrap_or("this host")),
        ));
    }
    Ok(url)
}

// ---------------------------------------------------------------------------
// Generic request (used for JSON APIs: Mojang meta, Modrinth, auth)
// ---------------------------------------------------------------------------

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HttpRequest {
    method: String,
    url: String,
    #[serde(default)]
    headers: HashMap<String, String>,
    /// Raw string body (sent as-is).
    body: Option<String>,
    /// JSON body.
    json: Option<serde_json::Value>,
    /// `application/x-www-form-urlencoded` body.
    form: Option<HashMap<String, String>>,
    timeout_ms: Option<u64>,
}

#[derive(Serialize)]
pub struct HttpResponse {
    status: u16,
    headers: HashMap<String, String>,
    body: String,
}

#[tauri::command]
pub async fn http_request(state: State<'_, AppState>, req: HttpRequest) -> Result<HttpResponse> {
    let url = parse_allowed(&req.url)?;
    let method = reqwest::Method::from_bytes(req.method.to_uppercase().as_bytes())
        .map_err(|_| AppError::new("bad_request", "Invalid HTTP method"))?;
    log::debug!("http {} {}{}", method, url.host_str().unwrap_or(""), url.path());
    let mut rb = state
        .client
        .request(method, url)
        .timeout(Duration::from_millis(req.timeout_ms.unwrap_or(30_000)));
    for (k, v) in &req.headers {
        rb = rb.header(k.as_str(), v.as_str());
    }
    if let Some(json) = &req.json {
        rb = rb.json(json);
    } else if let Some(form) = &req.form {
        rb = rb.form(form);
    } else if let Some(body) = req.body {
        rb = rb.body(body);
    }
    let resp = rb.send().await?;
    let status = resp.status().as_u16();
    let mut headers = HashMap::new();
    for (k, v) in resp.headers() {
        if let Ok(v) = v.to_str() {
            headers.insert(k.as_str().to_lowercase(), v.to_string());
        }
    }
    let body = resp.text().await?;
    Ok(HttpResponse { status, headers, body })
}

// ---------------------------------------------------------------------------
// Batch downloads
// ---------------------------------------------------------------------------

#[derive(Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DownloadItem {
    url: String,
    path: String,
    sha1: Option<String>,
    sha512: Option<String>,
    size: Option<u64>,
    #[serde(default)]
    executable: bool,
}

#[derive(Deserialize, Clone, Copy, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum VerifyMode {
    /// Existing files are accepted if their size matches (fast; used before every launch).
    Size,
    /// Existing files are re-hashed (used by "Repair").
    Hash,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadBatch {
    task_id: String,
    items: Vec<DownloadItem>,
    concurrency: Option<usize>,
    verify: Option<VerifyMode>,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct ProgressEvent {
    task_id: String,
    done: usize,
    total: usize,
    bytes: u64,
    total_bytes: u64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadSummary {
    downloaded: usize,
    skipped: usize,
}

pub fn hash_file_sync(path: &Path, algo: &str) -> Result<String> {
    use std::io::Read;
    let mut f = std::fs::File::open(path)?;
    let mut buf = vec![0u8; 1 << 16];
    match algo {
        "sha1" => {
            let mut h = Sha1::new();
            loop {
                let n = f.read(&mut buf)?;
                if n == 0 {
                    break;
                }
                h.update(&buf[..n]);
            }
            Ok(hex::encode(h.finalize()))
        }
        "sha512" => {
            let mut h = Sha512::new();
            loop {
                let n = f.read(&mut buf)?;
                if n == 0 {
                    break;
                }
                h.update(&buf[..n]);
            }
            Ok(hex::encode(h.finalize()))
        }
        _ => Err(AppError::new("bad_request", "Unsupported hash algorithm")),
    }
}

async fn existing_ok(path: &Path, item: &DownloadItem, mode: VerifyMode) -> bool {
    let Ok(meta) = tokio::fs::metadata(path).await else {
        return false;
    };
    if !meta.is_file() {
        return false;
    }
    if let Some(size) = item.size {
        if meta.len() != size {
            return false;
        }
    }
    let wants_hash = mode == VerifyMode::Hash || item.size.is_none();
    if !wants_hash {
        return true;
    }
    let (algo, expected) = match (&item.sha1, &item.sha512) {
        (Some(s), _) => ("sha1", s.clone()),
        (None, Some(s)) => ("sha512", s.clone()),
        // Nothing to verify against: an existing file is accepted as-is.
        (None, None) => return true,
    };
    let p = path.to_path_buf();
    match tokio::task::spawn_blocking(move || hash_file_sync(&p, algo)).await {
        Ok(Ok(actual)) => actual.eq_ignore_ascii_case(&expected),
        _ => false,
    }
}

fn available_space(path: &Path) -> Option<u64> {
    let disks = sysinfo::Disks::new_with_refreshed_list();
    disks
        .list()
        .iter()
        .filter(|d| path.starts_with(d.mount_point()))
        .max_by_key(|d| d.mount_point().as_os_str().len())
        .map(|d| d.available_space())
}

struct Shared {
    client: reqwest::Client,
    cancel: Arc<AtomicBool>,
    done: AtomicUsize,
    bytes: AtomicU64,
}

async fn download_one(shared: &Shared, item: &DownloadItem, dest: &Path) -> Result<()> {
    let url = parse_allowed(&item.url)?;
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let tmp: PathBuf = {
        let mut s = dest.as_os_str().to_owned();
        s.push(format!(".part{}", std::process::id()));
        PathBuf::from(s)
    };
    let mut last_err: Option<AppError> = None;
    for attempt in 0..4u32 {
        if shared.cancel.load(Ordering::Relaxed) {
            return Err(AppError::new("cancelled", "Download cancelled"));
        }
        if attempt > 0 {
            tokio::time::sleep(Duration::from_millis(500 * 2u64.pow(attempt))).await;
        }
        match fetch_to(shared, &url, &tmp, item).await {
            Ok(()) => {
                tokio::fs::rename(&tmp, dest).await?;
                set_executable(dest, item.executable);
                return Ok(());
            }
            Err(e) => {
                let _ = tokio::fs::remove_file(&tmp).await;
                // Don't retry things that will never succeed.
                match e.code() {
                    "cancelled" | "disk_full" | "host_not_allowed" | "http_404" | "http_403" => return Err(e),
                    _ => last_err = Some(e),
                }
            }
        }
    }
    Err(last_err.unwrap_or_else(|| AppError::new("download_failed", "Download failed")))
}

#[cfg(unix)]
fn set_executable(path: &Path, executable: bool) {
    use std::os::unix::fs::PermissionsExt;
    if executable {
        let _ = std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755));
    }
}

#[cfg(not(unix))]
fn set_executable(_path: &Path, _executable: bool) {}

async fn fetch_to(shared: &Shared, url: &url::Url, tmp: &Path, item: &DownloadItem) -> Result<()> {
    let resp = shared.client.get(url.clone()).send().await?;
    let status = resp.status();
    if !status.is_success() {
        let code = match status.as_u16() {
            404 => "http_404",
            403 => "http_403",
            _ => "http_error",
        };
        return Err(AppError::new(
            code,
            format!("{} returned HTTP {}", url.host_str().unwrap_or(""), status.as_u16()),
        ));
    }
    let mut file = tokio::fs::File::create(tmp).await?;
    let mut stream = resp.bytes_stream();
    let mut sha1 = item.sha1.as_ref().map(|_| Sha1::new());
    let mut sha512 = if item.sha1.is_none() {
        item.sha512.as_ref().map(|_| Sha512::new())
    } else {
        None
    };
    let mut written: u64 = 0;
    loop {
        if shared.cancel.load(Ordering::Relaxed) {
            return Err(AppError::new("cancelled", "Download cancelled"));
        }
        let next = tokio::time::timeout(Duration::from_secs(30), stream.next())
            .await
            .map_err(|_| {
                AppError::new(
                    "timeout",
                    format!("{} stopped responding", url.host_str().unwrap_or("")),
                )
            })?;
        let Some(chunk) = next else { break };
        let chunk = chunk?;
        if let Some(h) = sha1.as_mut() {
            h.update(&chunk);
        }
        if let Some(h) = sha512.as_mut() {
            h.update(&chunk);
        }
        file.write_all(&chunk).await?;
        written += chunk.len() as u64;
        shared.bytes.fetch_add(chunk.len() as u64, Ordering::Relaxed);
    }
    file.flush().await?;
    drop(file);
    if let Some(size) = item.size {
        if size != written {
            return Err(AppError::new(
                "size_mismatch",
                format!("Expected {size} bytes, got {written}"),
            ));
        }
    }
    let check = |actual: String, expected: &String| -> Result<()> {
        if actual.eq_ignore_ascii_case(expected) {
            Ok(())
        } else {
            Err(AppError::new(
                "hash_mismatch",
                format!("Checksum mismatch for {}", url.path()),
            ))
        }
    };
    if let (Some(h), Some(expected)) = (sha1, &item.sha1) {
        check(hex::encode(h.finalize()), expected)?;
    }
    if let (Some(h), Some(expected)) = (sha512, &item.sha512) {
        check(hex::encode(h.finalize()), expected)?;
    }
    Ok(())
}

#[tauri::command]
pub async fn download_batch(
    app: AppHandle,
    state: State<'_, AppState>,
    batch: DownloadBatch,
) -> Result<DownloadSummary> {
    let mode = batch.verify.unwrap_or(VerifyMode::Size);
    let cancel = Arc::new(AtomicBool::new(false));
    state.register_task(&batch.task_id, cancel.clone());

    // Resolve every destination before touching the network.
    let mut jobs = Vec::with_capacity(batch.items.len());
    for item in batch.items {
        let dest = state.roots.resolve(&item.path)?;
        jobs.push((item, dest));
    }

    // Work out what is actually missing.
    let mut missing = Vec::new();
    let mut skipped = 0usize;
    for (item, dest) in jobs {
        if existing_ok(&dest, &item, mode).await {
            skipped += 1;
        } else {
            missing.push((item, dest));
        }
    }

    let total = missing.len();
    let total_bytes: u64 = missing.iter().filter_map(|(i, _)| i.size).sum();
    if let Some(free) = available_space(state.roots.data_dir()) {
        // Keep a 256 MiB safety margin.
        if total_bytes + 256 * 1024 * 1024 > free {
            state.finish_task(&batch.task_id);
            return Err(AppError::new(
                "disk_full",
                format!(
                    "Not enough disk space: {} MB needed, {} MB free",
                    total_bytes / 1_048_576 + 256,
                    free / 1_048_576
                ),
            ));
        }
    }

    let shared = Arc::new(Shared {
        client: state.client.clone(),
        cancel: cancel.clone(),
        done: AtomicUsize::new(0),
        bytes: AtomicU64::new(0),
    });

    // Progress reporter
    let reporter = {
        let shared = shared.clone();
        let app = app.clone();
        let task_id = batch.task_id.clone();
        tokio::spawn(async move {
            loop {
                let done = shared.done.load(Ordering::Relaxed);
                let _ = app.emit(
                    "download://progress",
                    ProgressEvent {
                        task_id: task_id.clone(),
                        done,
                        total,
                        bytes: shared.bytes.load(Ordering::Relaxed),
                        total_bytes,
                    },
                );
                if done >= total || shared.cancel.load(Ordering::Relaxed) {
                    break;
                }
                tokio::time::sleep(Duration::from_millis(150)).await;
            }
        })
    };

    let started = Instant::now();
    let concurrency = batch.concurrency.unwrap_or(16).clamp(1, 64);
    let results: Vec<Result<()>> = futures_util::stream::iter(missing.into_iter())
        .map(|(item, dest)| {
            let shared = shared.clone();
            async move {
                let r = download_one(&shared, &item, &dest).await;
                shared.done.fetch_add(1, Ordering::Relaxed);
                r.map_err(|e| {
                    log::warn!("download failed: {} ({})", item.url, e);
                    e
                })
            }
        })
        .buffer_unordered(concurrency)
        .collect()
        .await;

    // Make sure the reporter exits even if we failed early.
    shared.done.store(total, Ordering::Relaxed);
    let _ = reporter.await;
    state.finish_task(&batch.task_id);

    let failures: Vec<&AppError> = results.iter().filter_map(|r| r.as_ref().err()).collect();
    if cancel.load(Ordering::Relaxed) {
        return Err(AppError::new("cancelled", "Download cancelled"));
    }
    if let Some(first) = failures.first() {
        let code = match first.code() {
            "disk_full" => "disk_full",
            "offline" | "timeout" => "offline",
            "hash_mismatch" | "size_mismatch" => "hash_mismatch",
            _ => "download_failed",
        };
        return Err(AppError::new(
            code,
            format!(
                "{} of {} files failed to download. First error: {}",
                failures.len(),
                total,
                first
            ),
        ));
    }
    log::info!(
        "downloaded {} files ({} skipped) in {:?}",
        total,
        skipped,
        started.elapsed()
    );
    Ok(DownloadSummary {
        downloaded: total,
        skipped,
    })
}

#[tauri::command]
pub fn cancel_task(state: State<'_, AppState>, task_id: String) {
    state.cancel_task(&task_id);
}

#[cfg(test)]
mod tests {
    use super::host_allowed;

    fn ok(u: &str) -> bool {
        host_allowed(&url::Url::parse(u).unwrap())
    }

    #[test]
    fn allowlist() {
        assert!(ok("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));
        assert!(ok("https://cdn.modrinth.com/data/AANobbMI/versions/x/sodium.jar"));
        assert!(ok(
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
        ));
        assert!(!ok("http://piston-meta.mojang.com/"), "plain http is refused");
        assert!(!ok("https://evil.example/"), "unknown hosts are refused");
        assert!(
            !ok("https://cdn.modrinth.com.evil.example/"),
            "suffix tricks are refused"
        );
        assert!(!ok("file:///C:/Windows/System32"));
    }
}
