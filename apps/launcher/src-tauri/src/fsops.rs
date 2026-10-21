//! Filesystem, archive and shell-integration commands. All paths are confined
//! by `paths::Roots`; user content is moved to the Recycle Bin, never
//! hard-deleted, and nothing is overwritten unless the UI explicitly asks.

use crate::error::{AppError, Result};
use crate::state::AppState;
use serde::{Deserialize, Serialize};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;
use tauri::{AppHandle, Manager, State};
use tauri_plugin_dialog::DialogExt;
use tauri_plugin_opener::OpenerExt;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppPaths {
    data_dir: String,
    sep: String,
    platform: String,
    arch: String,
    version: String,
}

#[tauri::command]
pub fn app_paths(state: State<'_, AppState>) -> AppPaths {
    AppPaths {
        data_dir: state.roots.data_dir().to_string_lossy().to_string(),
        sep: std::path::MAIN_SEPARATOR.to_string(),
        platform: std::env::consts::OS.to_string(),
        arch: std::env::consts::ARCH.to_string(),
        version: env!("CARGO_PKG_VERSION").to_string(),
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    name: String,
    path: String,
    is_dir: bool,
    size: u64,
    modified_ms: u64,
}

fn entry_for(path: &Path, meta: &std::fs::Metadata) -> Entry {
    Entry {
        name: path
            .file_name()
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or_default(),
        path: path.to_string_lossy().to_string(),
        is_dir: meta.is_dir(),
        size: meta.len(),
        modified_ms: meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0),
    }
}

#[tauri::command]
pub async fn fs_read_text(state: State<'_, AppState>, path: String) -> Result<Option<String>> {
    let p = state.roots.resolve(&path)?;
    match tokio::fs::read_to_string(&p).await {
        Ok(s) => Ok(Some(s)),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e.into()),
    }
}

/// Read the last `max_bytes` (capped at 8 MiB) of a text file, starting at a
/// line boundary — used to follow logs the JVM is still writing.
#[tauri::command]
pub async fn fs_read_tail(state: State<'_, AppState>, path: String, max_bytes: u64) -> Result<Option<String>> {
    let p = state.roots.resolve(&path)?;
    tauri::async_runtime::spawn_blocking(move || -> Result<Option<String>> {
        use std::io::{Seek, SeekFrom};
        let mut f = match std::fs::File::open(&p) {
            Ok(f) => f,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            Err(e) => return Err(e.into()),
        };
        let len = f.metadata()?.len();
        let start = len.saturating_sub(max_bytes.min(8 * 1024 * 1024));
        f.seek(SeekFrom::Start(start))?;
        let mut buf = Vec::new();
        f.read_to_end(&mut buf)?;
        let text = String::from_utf8_lossy(&buf).to_string();
        if start > 0 {
            if let Some(i) = text.find('\n') {
                return Ok(Some(text[i + 1..].to_string()));
            }
        }
        Ok(Some(text))
    })
    .await
    .map_err(|e| AppError::new("internal", e.to_string()))?
}

/// Atomic write: write to a temporary sibling and rename over the target.
#[tauri::command]
pub async fn fs_write_text(state: State<'_, AppState>, path: String, contents: String) -> Result<()> {
    let p = state.roots.resolve(&path)?;
    if let Some(parent) = p.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let mut tmp = p.as_os_str().to_owned();
    tmp.push(".tmp");
    let tmp = PathBuf::from(tmp);
    tokio::fs::write(&tmp, contents.as_bytes()).await?;
    tokio::fs::rename(&tmp, &p).await?;
    Ok(())
}

#[tauri::command]
pub async fn fs_exists(state: State<'_, AppState>, path: String) -> Result<bool> {
    let p = state.roots.resolve(&path)?;
    Ok(tokio::fs::metadata(&p).await.is_ok())
}

#[tauri::command]
pub async fn fs_stat(state: State<'_, AppState>, path: String) -> Result<Option<Entry>> {
    let p = state.roots.resolve_source(&path)?;
    match std::fs::metadata(&p) {
        Ok(m) => Ok(Some(entry_for(&p, &m))),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e.into()),
    }
}

#[tauri::command]
pub async fn fs_list(state: State<'_, AppState>, path: String) -> Result<Vec<Entry>> {
    let p = state.roots.resolve(&path)?;
    let rd = match std::fs::read_dir(&p) {
        Ok(rd) => rd,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(vec![]),
        Err(e) => return Err(e.into()),
    };
    let mut out = Vec::new();
    for e in rd.flatten() {
        if let Ok(m) = e.metadata() {
            out.push(entry_for(&e.path(), &m));
        }
    }
    out.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    Ok(out)
}

#[tauri::command]
pub async fn fs_mkdir(state: State<'_, AppState>, path: String) -> Result<()> {
    let p = state.roots.resolve(&path)?;
    tokio::fs::create_dir_all(&p).await?;
    Ok(())
}

/// Move user content to the Recycle Bin so it can be recovered.
#[tauri::command]
pub async fn fs_trash(state: State<'_, AppState>, path: String) -> Result<()> {
    let p = state.roots.resolve(&path)?;
    if p == state.roots.data_dir() {
        return Err(AppError::new(
            "forbidden_path",
            "Refusing to remove BLOOD's data folder",
        ));
    }
    if !p.exists() {
        return Ok(());
    }
    tauri::async_runtime::spawn_blocking(move || trash::delete(&p))
        .await
        .map_err(|e| AppError::new("internal", e.to_string()))?
        .map_err(|e| AppError::new("trash_failed", format!("Could not move to the Recycle Bin: {e}")))
}

/// Permanently delete launcher-owned caches (versions, libraries, assets, natives...).
#[tauri::command]
pub async fn fs_remove_internal(state: State<'_, AppState>, path: String) -> Result<()> {
    let p = state.roots.resolve_internal(&path)?;
    match tokio::fs::metadata(&p).await {
        Ok(m) if m.is_dir() => tokio::fs::remove_dir_all(&p).await?,
        Ok(_) => tokio::fs::remove_file(&p).await?,
        Err(_) => {}
    }
    Ok(())
}

#[tauri::command]
pub async fn fs_rename(state: State<'_, AppState>, from: String, to: String) -> Result<()> {
    let a = state.roots.resolve(&from)?;
    let b = state.roots.resolve(&to)?;
    if b.exists() {
        return Err(AppError::new(
            "already_exists",
            format!("{} already exists", b.display()),
        ));
    }
    if let Some(parent) = b.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    tokio::fs::rename(&a, &b).await?;
    Ok(())
}

#[tauri::command]
pub async fn fs_copy(state: State<'_, AppState>, from: String, to: String, overwrite: Option<bool>) -> Result<()> {
    let a = state.roots.resolve_source(&from)?;
    let b = state.roots.resolve(&to)?;
    if b.exists() && !overwrite.unwrap_or(false) {
        return Err(AppError::new(
            "already_exists",
            format!("{} already exists", b.display()),
        ));
    }
    if let Some(parent) = b.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    tokio::fs::copy(&a, &b).await?;
    Ok(())
}

fn copy_dir_recursive(from: &Path, to: &Path, skip: &[String]) -> std::io::Result<()> {
    std::fs::create_dir_all(to)?;
    for entry in std::fs::read_dir(from)? {
        let entry = entry?;
        let name = entry.file_name().to_string_lossy().to_string();
        if skip.iter().any(|s| s == &name) {
            continue;
        }
        let ft = entry.file_type()?;
        let dest = to.join(&name);
        if ft.is_dir() {
            copy_dir_recursive(&entry.path(), &dest, &[])?;
        } else if ft.is_file() {
            std::fs::copy(entry.path(), &dest)?;
        }
    }
    Ok(())
}

/// Copy a directory tree into a new (non-existent) destination, e.g. to duplicate an installation.
#[tauri::command]
pub async fn fs_copy_dir(
    state: State<'_, AppState>,
    from: String,
    to: String,
    skip: Option<Vec<String>>,
) -> Result<()> {
    let a = state.roots.resolve(&from)?;
    let b = state.roots.resolve(&to)?;
    if b.exists() {
        return Err(AppError::new(
            "already_exists",
            format!("{} already exists", b.display()),
        ));
    }
    let skip = skip.unwrap_or_default();
    tauri::async_runtime::spawn_blocking(move || copy_dir_recursive(&a, &b, &skip))
        .await
        .map_err(|e| AppError::new("internal", e.to_string()))??;
    Ok(())
}

#[tauri::command]
pub async fn fs_hash(state: State<'_, AppState>, path: String, algo: String) -> Result<String> {
    let p = state.roots.resolve_source(&path)?;
    tauri::async_runtime::spawn_blocking(move || crate::net::hash_file_sync(&p, &algo))
        .await
        .map_err(|e| AppError::new("internal", e.to_string()))?
}

// ---------------------------------------------------------------------------
// Zip archives (mod jars, natives jars, .mrpack files)
// ---------------------------------------------------------------------------

fn open_zip(p: &Path) -> Result<zip::ZipArchive<std::fs::File>> {
    let f = std::fs::File::open(p)?;
    Ok(zip::ZipArchive::new(f)?)
}

fn read_entry_bytes(p: &Path, entry: &str, limit: u64) -> Result<Option<Vec<u8>>> {
    let mut z = open_zip(p)?;
    let result = match z.by_name(entry) {
        Ok(f) => {
            let mut buf = Vec::new();
            f.take(limit).read_to_end(&mut buf)?;
            Some(buf)
        }
        Err(zip::result::ZipError::FileNotFound) => None,
        Err(e) => return Err(e.into()),
    };
    Ok(result)
}

/// Read a text entry (e.g. `fabric.mod.json`, `modrinth.index.json`) from an archive. Capped at 4 MiB.
#[tauri::command]
pub async fn zip_read_text(state: State<'_, AppState>, path: String, entry: String) -> Result<Option<String>> {
    let p = state.roots.resolve_source(&path)?;
    tauri::async_runtime::spawn_blocking(move || -> Result<Option<String>> {
        Ok(read_entry_bytes(&p, &entry, 4 * 1024 * 1024)?.map(|b| String::from_utf8_lossy(&b).to_string()))
    })
    .await
    .map_err(|e| AppError::new("internal", e.to_string()))?
}

/// Read an image entry (mod icon) as a data: URL. Capped at 1 MiB.
#[tauri::command]
pub async fn zip_read_data_url(state: State<'_, AppState>, path: String, entry: String) -> Result<Option<String>> {
    let p = state.roots.resolve_source(&path)?;
    tauri::async_runtime::spawn_blocking(move || -> Result<Option<String>> {
        let mime = if entry.to_lowercase().ends_with(".jpg") || entry.to_lowercase().ends_with(".jpeg") {
            "image/jpeg"
        } else {
            "image/png"
        };
        Ok(read_entry_bytes(&p, &entry, 1024 * 1024)?.map(|b| format!("data:{mime};base64,{}", base64(&b))))
    })
    .await
    .map_err(|e| AppError::new("internal", e.to_string()))?
}

#[tauri::command]
pub async fn zip_list(state: State<'_, AppState>, path: String) -> Result<Vec<String>> {
    let p = state.roots.resolve_source(&path)?;
    tauri::async_runtime::spawn_blocking(move || -> Result<Vec<String>> {
        let z = open_zip(&p)?;
        Ok(z.file_names().map(|s| s.to_string()).collect())
    })
    .await
    .map_err(|e| AppError::new("internal", e.to_string()))?
}

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ExtractOptions {
    /// Only extract entries under this prefix, with the prefix stripped (e.g. "overrides/").
    strip_prefix: Option<String>,
    /// Skip entries starting with any of these prefixes (e.g. "META-INF/").
    #[serde(default)]
    exclude: Vec<String>,
    #[serde(default)]
    overwrite: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExtractResult {
    extracted: usize,
    skipped_existing: Vec<String>,
}

#[tauri::command]
pub async fn zip_extract(
    state: State<'_, AppState>,
    path: String,
    dest: String,
    options: Option<ExtractOptions>,
) -> Result<ExtractResult> {
    let src = state.roots.resolve_source(&path)?;
    let dest = state.roots.resolve(&dest)?;
    let opts = options.unwrap_or_default();
    tauri::async_runtime::spawn_blocking(move || -> Result<ExtractResult> {
        let mut z = open_zip(&src)?;
        let mut extracted = 0usize;
        let mut skipped_existing = Vec::new();
        for i in 0..z.len() {
            let mut file = z.by_index(i)?;
            let name = file.name().replace('\\', "/");
            if opts.exclude.iter().any(|e| name.starts_with(e.as_str())) {
                continue;
            }
            let rel_name = match &opts.strip_prefix {
                Some(prefix) => match name.strip_prefix(prefix.as_str()) {
                    Some(r) if !r.is_empty() => r.to_string(),
                    _ => continue,
                },
                None => name.clone(),
            };
            // Zip-slip protection: reject absolute paths and `..`.
            let rel = Path::new(&rel_name);
            if rel.is_absolute() || rel.components().any(|c| !matches!(c, std::path::Component::Normal(_))) {
                continue;
            }
            if file.enclosed_name().is_none() {
                continue;
            }
            let target = dest.join(rel);
            if file.is_dir() {
                std::fs::create_dir_all(&target)?;
                continue;
            }
            if target.exists() && !opts.overwrite {
                skipped_existing.push(rel_name);
                continue;
            }
            if let Some(parent) = target.parent() {
                std::fs::create_dir_all(parent)?;
            }
            let mut out = std::fs::File::create(&target)?;
            std::io::copy(&mut file, &mut out)?;
            extracted += 1;
        }
        Ok(ExtractResult {
            extracted,
            skipped_existing,
        })
    })
    .await
    .map_err(|e| AppError::new("internal", e.to_string()))?
}

// ---------------------------------------------------------------------------
// Shell integration and imports
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn open_path(app: AppHandle, state: State<'_, AppState>, path: String) -> Result<()> {
    let p = state.roots.resolve(&path)?;
    std::fs::create_dir_all(&p).ok();
    app.opener()
        .open_path(p.to_string_lossy().to_string(), None::<&str>)
        .map_err(|e| AppError::new("open_failed", e.to_string()))
}

#[tauri::command]
pub async fn open_url(app: AppHandle, url: String) -> Result<()> {
    let parsed = url::Url::parse(&url).map_err(|e| AppError::new("bad_url", e.to_string()))?;
    if parsed.scheme() != "https" {
        return Err(AppError::new("bad_url", "Only https links can be opened"));
    }
    app.opener()
        .open_url(parsed.to_string(), None::<&str>)
        .map_err(|e| AppError::new("open_failed", e.to_string()))
}

/// Open the folder with BLOOD's own log files (outside the data dir, so not via `open_path`).
#[tauri::command]
pub async fn open_log_dir(app: AppHandle) -> Result<()> {
    let dir = app
        .path()
        .app_log_dir()
        .map_err(|e| AppError::new("internal", e.to_string()))?;
    std::fs::create_dir_all(&dir).ok();
    app.opener()
        .open_path(dir.to_string_lossy().to_string(), None::<&str>)
        .map_err(|e| AppError::new("open_failed", e.to_string()))
}

/// Launch-at-login (Windows registry Run key, managed by the official autostart plugin).
#[tauri::command]
pub fn autostart_get(app: AppHandle) -> Result<bool> {
    use tauri_plugin_autostart::ManagerExt;
    app.autolaunch()
        .is_enabled()
        .map_err(|e| AppError::new("autostart_failed", e.to_string()))
}

#[tauri::command]
pub fn autostart_set(app: AppHandle, enabled: bool) -> Result<()> {
    use tauri_plugin_autostart::ManagerExt;
    let al = app.autolaunch();
    let r = if enabled { al.enable() } else { al.disable() };
    r.map_err(|e| AppError::new("autostart_failed", e.to_string()))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PickOptions {
    title: Option<String>,
    #[serde(default)]
    filters: Vec<(String, Vec<String>)>,
    #[serde(default)]
    multiple: bool,
    #[serde(default)]
    directory: bool,
}

/// Native file/folder picker. Picked paths become readable import sources for this session.
#[tauri::command]
pub async fn pick_files(app: AppHandle, state: State<'_, AppState>, options: PickOptions) -> Result<Vec<String>> {
    let app2 = app.clone();
    let picked: Vec<PathBuf> = tauri::async_runtime::spawn_blocking(move || -> Vec<PathBuf> {
        let mut b = app2.dialog().file();
        if let Some(t) = &options.title {
            b = b.set_title(t);
        }
        for (name, exts) in &options.filters {
            let refs: Vec<&str> = exts.iter().map(|s| s.as_str()).collect();
            b = b.add_filter(name, &refs);
        }
        if options.directory {
            b.blocking_pick_folder()
                .and_then(|f| f.into_path().ok())
                .into_iter()
                .collect()
        } else if options.multiple {
            b.blocking_pick_files()
                .unwrap_or_default()
                .into_iter()
                .filter_map(|f| f.into_path().ok())
                .collect()
        } else {
            b.blocking_pick_file()
                .and_then(|f| f.into_path().ok())
                .into_iter()
                .collect()
        }
    })
    .await
    .map_err(|e| AppError::new("internal", e.to_string()))?;
    state.roots.mark_importable(picked.iter().cloned());
    Ok(picked.into_iter().map(|p| p.to_string_lossy().to_string()).collect())
}

/// Let the user pick a custom game directory. The folder becomes an allowed root.
#[tauri::command]
pub async fn pick_game_dir(app: AppHandle, state: State<'_, AppState>) -> Result<Option<String>> {
    let app2 = app.clone();
    let picked = tauri::async_runtime::spawn_blocking(move || -> Option<PathBuf> {
        app2.dialog()
            .file()
            .set_title("Choose a game directory")
            .blocking_pick_folder()
            .and_then(|f| f.into_path().ok())
    })
    .await
    .map_err(|e| AppError::new("internal", e.to_string()))?;
    match picked {
        Some(dir) => {
            state.roots.add_root(dir.clone())?;
            Ok(Some(dir.to_string_lossy().to_string()))
        }
        None => Ok(None),
    }
}

/// List files in a folder the user picked or dropped (for "import mods from folder").
#[tauri::command]
pub async fn list_importable_folder(state: State<'_, AppState>, path: String) -> Result<Vec<Entry>> {
    let p = state.roots.resolve_source(&path)?;
    let mut out = Vec::new();
    let mut files = Vec::new();
    for e in std::fs::read_dir(&p)?.flatten() {
        if let Ok(m) = e.metadata() {
            if m.is_file() {
                files.push(e.path());
                out.push(entry_for(&e.path(), &m));
            }
        }
    }
    state.roots.mark_importable(files);
    Ok(out)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportResult {
    copied: Vec<String>,
    conflicts: Vec<String>,
}

/// Copy user-chosen files into a folder inside BLOOD (e.g. an installation's `mods`).
/// Never overwrites: name clashes are reported back so the UI can ask.
#[tauri::command]
pub async fn import_files(state: State<'_, AppState>, sources: Vec<String>, dest_dir: String) -> Result<ImportResult> {
    let dest = state.roots.resolve(&dest_dir)?;
    tokio::fs::create_dir_all(&dest).await?;
    let mut copied = Vec::new();
    let mut conflicts = Vec::new();
    for s in sources {
        let src = state.roots.resolve_source(&s)?;
        let Some(name) = src.file_name() else { continue };
        let target = dest.join(name);
        if target.exists() {
            conflicts.push(name.to_string_lossy().to_string());
            continue;
        }
        tokio::fs::copy(&src, &target).await?;
        copied.push(target.to_string_lossy().to_string());
    }
    Ok(ImportResult { copied, conflicts })
}

pub fn base64(data: &[u8]) -> String {
    const T: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(data.len().div_ceil(3) * 4);
    for chunk in data.chunks(3) {
        let b = [chunk[0], *chunk.get(1).unwrap_or(&0), *chunk.get(2).unwrap_or(&0)];
        let n = ((b[0] as u32) << 16) | ((b[1] as u32) << 8) | b[2] as u32;
        out.push(T[(n >> 18) as usize & 63] as char);
        out.push(T[(n >> 12) as usize & 63] as char);
        out.push(if chunk.len() > 1 {
            T[(n >> 6) as usize & 63] as char
        } else {
            '='
        });
        out.push(if chunk.len() > 2 {
            T[n as usize & 63] as char
        } else {
            '='
        });
    }
    out
}

#[cfg(test)]
mod tests {
    #[test]
    fn base64_matches_rfc4648() {
        assert_eq!(super::base64(b""), "");
        assert_eq!(super::base64(b"f"), "Zg==");
        assert_eq!(super::base64(b"fo"), "Zm8=");
        assert_eq!(super::base64(b"foo"), "Zm9v");
        assert_eq!(super::base64(b"foobar"), "Zm9vYmFy");
    }
}
