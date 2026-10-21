//! Secure storage for authentication tokens in the OS credential store
//! (Windows Credential Manager). Tokens never touch settings files or logs.
//!
//! Windows limits a credential blob to 2560 bytes, and Microsoft refresh tokens
//! can approach that, so values are split across numbered entries.

use crate::error::{AppError, Result};

const SERVICE: &str = "BLOOD Launcher";
const CHUNK: usize = 1000;

fn entry(key: &str) -> Result<keyring::Entry> {
    keyring::Entry::new(SERVICE, key).map_err(|e| AppError::new("secret_store", e.to_string()))
}

fn validate_key(key: &str) -> Result<()> {
    let ok = !key.is_empty()
        && key.len() <= 128
        && key
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | '.' | ':'));
    if ok {
        Ok(())
    } else {
        Err(AppError::new("bad_request", "Invalid secret key"))
    }
}

fn delete_all(key: &str) -> Result<()> {
    let head = entry(key)?;
    if let Ok(count) = head.get_password() {
        if let Ok(n) = count.parse::<usize>() {
            for i in 0..n {
                let _ = entry(&format!("{key}#{i}"))?.delete_credential();
            }
        }
    }
    match head.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(AppError::new("secret_store", e.to_string())),
    }
}

#[tauri::command]
pub fn secret_set(key: String, value: String) -> Result<()> {
    validate_key(&key)?;
    delete_all(&key)?;
    let chars: Vec<char> = value.chars().collect();
    let chunks: Vec<String> = chars.chunks(CHUNK).map(|c| c.iter().collect()).collect();
    for (i, chunk) in chunks.iter().enumerate() {
        entry(&format!("{key}#{i}"))?
            .set_password(chunk)
            .map_err(|e| AppError::new("secret_store", e.to_string()))?;
    }
    entry(&key)?
        .set_password(&chunks.len().to_string())
        .map_err(|e| AppError::new("secret_store", e.to_string()))
}

#[tauri::command]
pub fn secret_get(key: String) -> Result<Option<String>> {
    validate_key(&key)?;
    let count = match entry(&key)?.get_password() {
        Ok(c) => c,
        Err(keyring::Error::NoEntry) => return Ok(None),
        Err(e) => return Err(AppError::new("secret_store", e.to_string())),
    };
    let n: usize = count
        .parse()
        .map_err(|_| AppError::new("secret_store", "Corrupt secret"))?;
    let mut value = String::new();
    for i in 0..n {
        match entry(&format!("{key}#{i}"))?.get_password() {
            Ok(part) => value.push_str(&part),
            Err(_) => return Ok(None),
        }
    }
    Ok(Some(value))
}

#[tauri::command]
pub fn secret_delete(key: String) -> Result<()> {
    validate_key(&key)?;
    delete_all(&key)
}
