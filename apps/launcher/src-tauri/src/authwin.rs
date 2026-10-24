//! In-app Microsoft sign-in window.
//!
//! Opens Microsoft's own sign-in page in a separate, unprivileged window (it has
//! no capabilities, so the page can't call any BLOOD command). When Microsoft
//! redirects to the native-client redirect URI, the navigation is stopped, the
//! window closes, and the redirect URL (containing a one-time code) is returned
//! to the UI, which exchanges it with PKCE. BLOOD never sees the password.

use crate::error::{AppError, Result};
use std::sync::{Arc, Mutex};
use tauri::{AppHandle, Manager, WebviewUrl, WebviewWindowBuilder, WindowEvent};
use tokio::sync::oneshot;

const LABEL: &str = "ms-login";
const AUTHORIZE_PREFIX: &str = "https://login.microsoftonline.com/";
const REDIRECT_URI: &str = "https://login.microsoftonline.com/common/oauth2/nativeclient";

/// True when this navigation is Microsoft redirecting back with the result.
fn is_redirect(url: &tauri::Url) -> bool {
    url.as_str().starts_with(REDIRECT_URI)
        && matches!(url.as_str().as_bytes().get(REDIRECT_URI.len()), None | Some(b'?') | Some(b'#'))
}

/// Opens the sign-in window and waits. Returns the redirect URL, or `None` if the
/// player closed the window.
#[tauri::command]
pub async fn ms_login_window(app: AppHandle, authorize_url: String) -> Result<Option<String>> {
    if !authorize_url.starts_with(AUTHORIZE_PREFIX) {
        return Err(AppError::new("invalid_request", "Not a Microsoft sign-in address"));
    }
    let url: tauri::Url = authorize_url
        .parse()
        .map_err(|_| AppError::new("invalid_request", "Invalid sign-in address"))?;

    // Only one sign-in window at a time.
    if let Some(old) = app.get_webview_window(LABEL) {
        let _ = old.destroy();
    }

    let (tx, rx) = oneshot::channel::<Option<String>>();
    let tx = Arc::new(Mutex::new(Some(tx)));
    let send = {
        let tx = tx.clone();
        move |v: Option<String>| {
            if let Some(t) = tx.lock().ok().and_then(|mut g| g.take()) {
                let _ = t.send(v);
            }
        }
    };

    let on_nav = {
        let send = send.clone();
        let app = app.clone();
        move |u: &tauri::Url| -> bool {
            if is_redirect(u) {
                send(Some(u.to_string()));
                if let Some(w) = app.get_webview_window(LABEL) {
                    let _ = w.close();
                }
                return false;
            }
            // Microsoft's flow can pass through several of its own domains (and federated
            // sign-in providers); only secure pages are allowed.
            matches!(u.scheme(), "https" | "about")
        }
    };

    let mut builder = WebviewWindowBuilder::new(&app, LABEL, WebviewUrl::External(url))
        .title("Sign in with Microsoft — MOTION")
        .inner_size(500.0, 680.0)
        .min_inner_size(400.0, 520.0)
        .resizable(true)
        .center()
        .focused(true)
        .on_navigation(on_nav);
    if let Some(main) = app.get_webview_window("main") {
        builder = builder.parent(&main).map_err(|e| AppError::new("internal", e.to_string()))?;
    }
    let window = builder.build().map_err(|e| AppError::new("internal", e.to_string()))?;

    {
        let send = send.clone();
        window.on_window_event(move |e| {
            if matches!(e, WindowEvent::CloseRequested { .. } | WindowEvent::Destroyed) {
                send(None);
            }
        });
    }

    let result = rx.await.unwrap_or(None);
    if let Some(w) = app.get_webview_window(LABEL) {
        let _ = w.destroy();
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recognises_only_the_exact_redirect() {
        let ok: tauri::Url = "https://login.microsoftonline.com/common/oauth2/nativeclient?code=x&state=y".parse().unwrap();
        assert!(is_redirect(&ok));
        let other: tauri::Url = "https://login.microsoftonline.com/common/oauth2/nativeclientX?code=x".parse().unwrap();
        assert!(!is_redirect(&other));
        let authorize: tauri::Url = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?x=1".parse().unwrap();
        assert!(!is_redirect(&authorize));
    }
}
