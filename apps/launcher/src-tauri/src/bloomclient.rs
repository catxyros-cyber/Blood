//! MOTION Client (the in-game mod) shipped inside the launcher.
//!
//! CI builds the mod and places the jars plus `manifest.json` in
//! `resources/bloomclient/`, which Tauri bundles with the app. These commands
//! only ever read from that bundled folder and copy a jar into a mods folder
//! that the path confinement in `paths.rs` already allows. Nothing is
//! downloaded and existing files are never overwritten.

use crate::error::{AppError, Result};
use crate::state::AppState;
use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use std::path::{Path, PathBuf};
use tauri::{AppHandle, Manager, State};

#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct BundleFile {
    /// "fabric" (also used for Quilt) or "neoforge".
    pub loader: String,
    pub file: String,
    pub sha1: String,
    pub size: u64,
    /// Minecraft version this jar is for (manifests before 0.3 list one version for every file).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub minecraft: Option<String>,
}

#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct Bundle {
    pub version: String,
    /// Minecraft versions the bundled jars support, e.g. ["1.21.1"].
    pub minecraft: Vec<String>,
    pub files: Vec<BundleFile>,
}

fn bundle_dir(app: &AppHandle) -> Result<PathBuf> {
    let dir = app
        .path()
        .resource_dir()
        .map_err(|e| AppError::new("internal", e.to_string()))?;
    Ok(dir.join("bloomclient"))
}

fn read_bundle(dir: &Path) -> Result<Option<Bundle>> {
    let manifest = dir.join("manifest.json");
    let text = match std::fs::read_to_string(&manifest) {
        Ok(t) => t,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(e) => return Err(e.into()),
    };
    let bundle: Bundle = serde_json::from_str(&text)?;
    for f in &bundle.files {
        if !is_plain_jar_name(&f.file) {
            return Err(AppError::new("invalid_bundle", "MOTION Client bundle has an invalid file name"));
        }
    }
    Ok(Some(bundle))
}

/// File names in the manifest must be plain `*.jar` names (no separators, no `..`).
fn is_plain_jar_name(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 128
        && name.ends_with(".jar")
        && !name.starts_with('.')
        && name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | '.' | '+'))
}

/// The MOTION Client version bundled with this launcher, or null in builds without it.
#[tauri::command]
pub async fn bloom_client_bundle(app: AppHandle) -> Result<Option<Bundle>> {
    read_bundle(&bundle_dir(&app)?)
}

/// Copy a bundled jar into `mods_dir` (an installation's mods folder): the one named `file`
/// (it must be listed in the manifest), or the first one for `loader`.
/// Fails if a file with the same name already exists; returns the installed path.
#[tauri::command]
pub async fn bloom_client_install(
    app: AppHandle,
    state: State<'_, AppState>,
    loader: String,
    mods_dir: String,
    file: Option<String>,
) -> Result<String> {
    let dir = bundle_dir(&app)?;
    let bundle = read_bundle(&dir)?
        .ok_or_else(|| AppError::new("not_bundled", "This build of MOTION doesn't include MOTION Client"))?;
    let wanted = file;
    let file = bundle
        .files
        .iter()
        .find(|f| f.loader == loader && wanted.as_deref().map_or(true, |w| w == f.file))
        .ok_or_else(|| AppError::new("unsupported_loader", format!("MOTION Client isn't available for {loader}")))?;
    let dest_dir = state.roots.resolve(&mods_dir)?;
    if dest_dir.file_name().map(|n| n != "mods").unwrap_or(true) {
        return Err(AppError::new("forbidden_path", "MOTION Client can only be installed into a mods folder"));
    }
    let src = dir.join(&file.file);
    let bytes = tokio::fs::read(&src).await?;
    let digest = hex::encode(Sha1::digest(&bytes));
    if !digest.eq_ignore_ascii_case(&file.sha1) {
        return Err(AppError::new("corrupt_bundle", "The bundled MOTION Client file is damaged; reinstall MOTION"));
    }
    tokio::fs::create_dir_all(&dest_dir).await?;
    let target = dest_dir.join(&file.file);
    if target.exists() {
        return Err(AppError::new("exists", format!("{} is already in the mods folder", file.file)));
    }
    let mut tmp = target.as_os_str().to_owned();
    tmp.push(".part");
    let tmp = PathBuf::from(tmp);
    tokio::fs::write(&tmp, &bytes).await?;
    tokio::fs::rename(&tmp, &target).await?;
    Ok(target.to_string_lossy().to_string())
}

/// Read a local image (custom background, skybox preview), or Minecraft's servers.dat / level.dat, as a data: URL.
/// The path must be inside MOTION's roots or picked by the user this session.
#[tauri::command]
pub async fn fs_read_data_url(state: State<'_, AppState>, path: String, max_bytes: u64) -> Result<Option<String>> {
    let p = state.roots.resolve_source(&path)?;
    let lower = p.to_string_lossy().to_lowercase();
    let mime = if lower.ends_with(".png") {
        "image/png"
    } else if lower.ends_with(".jpg") || lower.ends_with(".jpeg") {
        "image/jpeg"
    } else if lower.ends_with(".webp") {
        "image/webp"
    } else if lower.ends_with("servers.dat") || lower.ends_with("level.dat") {
        // Minecraft's server list and world info (NBT), read for "Worlds & servers".
        "application/octet-stream"
    } else {
        return Err(AppError::new("unsupported", "Only PNG, JPEG and WebP images can be shown"));
    };
    let limit = max_bytes.min(25 * 1024 * 1024);
    let meta = match tokio::fs::metadata(&p).await {
        Ok(m) => m,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(e) => return Err(e.into()),
    };
    if meta.len() > limit {
        return Err(AppError::new("too_large", "The image is too large"));
    }
    let bytes = tokio::fs::read(&p).await?;
    Ok(Some(format!("data:{mime};base64,{}", crate::fsops::base64(&bytes))))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn jar_names_must_be_plain() {
        assert!(is_plain_jar_name("bloomclient-fabric-1.21.1-0.1.0.jar"));
        assert!(is_plain_jar_name("mod+extra_1.jar"));
        assert!(!is_plain_jar_name("../evil.jar"));
        assert!(!is_plain_jar_name("dir/evil.jar"));
        assert!(!is_plain_jar_name("dir\\evil.jar"));
        assert!(!is_plain_jar_name("evil.exe"));
        assert!(!is_plain_jar_name(".hidden.jar"));
        assert!(!is_plain_jar_name(""));
    }

    #[test]
    fn missing_manifest_means_no_bundle() {
        let dir = std::env::temp_dir().join(format!("bloom-bundle-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        assert!(read_bundle(&dir).unwrap().is_none());
        std::fs::write(
            dir.join("manifest.json"),
            r#"{"version":"0.1.0","minecraft":["1.21.1"],"files":[{"loader":"fabric","file":"../x.jar","sha1":"0","size":1}]}"#,
        )
        .unwrap();
        assert!(read_bundle(&dir).is_err());
        std::fs::write(
            dir.join("manifest.json"),
            r#"{"version":"0.1.0","minecraft":["1.21.1"],"files":[{"loader":"fabric","file":"bloomclient-fabric-1.21.1-0.1.0.jar","sha1":"0","size":1}]}"#,
        )
        .unwrap();
        let b = read_bundle(&dir).unwrap().unwrap();
        assert_eq!(b.files[0].loader, "fabric");
        assert!(b.files[0].minecraft.is_none());
        std::fs::write(
            dir.join("manifest.json"),
            r#"{"version":"0.3.0","minecraft":["1.21.1","1.21.11"],"files":[{"loader":"fabric","minecraft":"1.21.11","file":"bloomclient-fabric-1.21.11-0.3.0.jar","sha1":"0","size":1}]}"#,
        )
        .unwrap();
        let b = read_bundle(&dir).unwrap().unwrap();
        assert_eq!(b.files[0].minecraft.as_deref(), Some("1.21.11"));
    }
}
