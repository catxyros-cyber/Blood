//! Structured errors returned to the UI as `{ code, message }`.
//!
//! Codes are stable strings the frontend maps to friendly messages
//! (e.g. `offline`, `disk_full`, `hash_mismatch`, `forbidden_path`).

use serde::{Serialize, Serializer};

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("{0}")]
    Io(#[from] std::io::Error),
    #[error("{0}")]
    Net(#[from] reqwest::Error),
    #[error("{0}")]
    Json(#[from] serde_json::Error),
    #[error("{0}")]
    Zip(#[from] zip::result::ZipError),
    #[error("{message}")]
    Coded { code: &'static str, message: String },
}

impl AppError {
    pub fn new(code: &'static str, message: impl Into<String>) -> Self {
        AppError::Coded {
            code,
            message: message.into(),
        }
    }

    pub fn code(&self) -> &'static str {
        match self {
            AppError::Io(e) => io_code(e),
            AppError::Net(e) => {
                if e.is_timeout() {
                    "timeout"
                } else if e.is_connect() {
                    "offline"
                } else {
                    "network"
                }
            }
            AppError::Json(_) => "invalid_json",
            AppError::Zip(_) => "corrupt_archive",
            AppError::Coded { code, .. } => code,
        }
    }
}

fn io_code(e: &std::io::Error) -> &'static str {
    // ERROR_HANDLE_DISK_FULL (39) and ERROR_DISK_FULL (112) on Windows, ENOSPC (28) elsewhere.
    match e.raw_os_error() {
        #[cfg(windows)]
        Some(39) | Some(112) => return "disk_full",
        #[cfg(not(windows))]
        Some(28) => return "disk_full",
        _ => {}
    }
    match e.kind() {
        std::io::ErrorKind::NotFound => "not_found",
        std::io::ErrorKind::PermissionDenied => "permission_denied",
        std::io::ErrorKind::AlreadyExists => "already_exists",
        _ => "io",
    }
}

impl Serialize for AppError {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        use serde::ser::SerializeStruct;
        let mut s = serializer.serialize_struct("AppError", 2)?;
        s.serialize_field("code", self.code())?;
        s.serialize_field("message", &self.to_string())?;
        s.end()
    }
}

pub type Result<T> = std::result::Result<T, AppError>;
