//! A stable, anonymous id for this PC, used to bind a BLOOD Premium key to one computer.
//!
//! Windows: the `MachineGuid` that Windows creates at install time. Only a SHA-256 of it ever
//! leaves the PC, so the real id is never sent anywhere. Reinstalling Windows changes it (the
//! player then moves their key to "this PC", which is limited to once per 24 hours).

use crate::error::{AppError, Result};
use sha2::{Digest, Sha256};
use std::sync::OnceLock;

static MACHINE_ID: OnceLock<String> = OnceLock::new();

#[cfg(windows)]
async fn raw_machine_id() -> Result<String> {
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    let out = tokio::process::Command::new("reg")
        .args(["query", r"HKLM\SOFTWARE\Microsoft\Cryptography", "/v", "MachineGuid", "/reg:64"])
        .creation_flags(CREATE_NO_WINDOW)
        .output()
        .await
        .map_err(|e| AppError::new("machine_id", format!("Couldn't read this PC's id: {e}")))?;
    let text = String::from_utf8_lossy(&out.stdout);
    text.lines()
        .find(|l| l.trim_start().starts_with("MachineGuid"))
        .and_then(|l| l.split_whitespace().last())
        .map(|s| s.trim().to_lowercase())
        .filter(|s| s.len() >= 16)
        .ok_or_else(|| AppError::new("machine_id", "Couldn't read this PC's id from the registry."))
}

#[cfg(target_os = "linux")]
async fn raw_machine_id() -> Result<String> {
    for p in ["/etc/machine-id", "/var/lib/dbus/machine-id"] {
        if let Ok(s) = tokio::fs::read_to_string(p).await {
            let s = s.trim().to_lowercase();
            if s.len() >= 16 {
                return Ok(s);
            }
        }
    }
    Err(AppError::new("machine_id", "Couldn't read this PC's machine id."))
}

#[cfg(target_os = "macos")]
async fn raw_machine_id() -> Result<String> {
    let out = tokio::process::Command::new("ioreg")
        .args(["-rd1", "-c", "IOPlatformExpertDevice"])
        .output()
        .await
        .map_err(|e| AppError::new("machine_id", format!("Couldn't read this Mac's id: {e}")))?;
    let text = String::from_utf8_lossy(&out.stdout);
    text.lines()
        .find(|l| l.contains("IOPlatformUUID"))
        .and_then(|l| l.split('"').nth(3))
        .map(|s| s.to_lowercase())
        .ok_or_else(|| AppError::new("machine_id", "Couldn't read this Mac's id."))
}

/// SHA-256 (hex) of this PC's machine id.
#[tauri::command]
pub async fn machine_id() -> Result<String> {
    if let Some(id) = MACHINE_ID.get() {
        return Ok(id.clone());
    }
    let raw = raw_machine_id().await?;
    let hash = hex::encode(Sha256::digest(format!("blood-hwid-v1:{raw}").as_bytes()));
    Ok(MACHINE_ID.get_or_init(|| hash).clone())
}
