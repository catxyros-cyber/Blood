//! MOTION's native layer.
//!
//! Deliberately small: the launcher logic (version resolution, launch
//! arguments, Modrinth, compatibility checks, presets) lives in TypeScript
//! under `src/core` where it is unit-tested. Rust provides the audited
//! primitives that logic needs — allowlisted HTTP, verified downloads,
//! confined filesystem access, archives, process control and secure token
//! storage.

mod authwin;
mod bloomclient;
mod discord;
mod error;
mod fsops;
mod launch;
mod machine;
mod net;
mod paths;
mod secrets;
mod state;

use serde::Serialize;
use tauri::{DragDropEvent, Emitter, Manager, WindowEvent};

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct DragPayload {
    kind: &'static str,
    paths: Vec<String>,
}

pub fn run() {
    tauri::Builder::default()
        // Must be registered first: focus the existing window instead of starting a second MOTION.
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(w) = app.get_webview_window("main") {
                let _ = w.unminimize();
                let _ = w.show();
                let _ = w.set_focus();
            }
        }))
        .plugin(
            tauri_plugin_log::Builder::new()
                .level(log::LevelFilter::Info)
                .max_file_size(5_000_000)
                .build(),
        )
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            Some(vec!["--minimized"]),
        ))
        .setup(|app| {
            let data_dir = app.path().app_data_dir()?;
            std::fs::create_dir_all(&data_dir)?;
            log::info!(
                "MOTION {} starting; data dir {}",
                env!("CARGO_PKG_VERSION"),
                data_dir.display()
            );
            app.manage(state::AppState::new(data_dir));
            app.manage(discord::DiscordState::default());
            discord::start(app.handle().clone());
            // Started by Windows at login: stay out of the way.
            if std::env::args().any(|a| a == "--minimized") {
                if let Some(w) = app.get_webview_window("main") {
                    let _ = w.minimize();
                }
            }
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::DragDrop(dd) = event {
                let payload = match dd {
                    DragDropEvent::Enter { paths, .. } => DragPayload {
                        kind: "enter",
                        paths: paths.iter().map(|p| p.to_string_lossy().to_string()).collect(),
                    },
                    DragDropEvent::Drop { paths, .. } => {
                        // Dropped files become readable import sources for this session.
                        if let Some(state) = window.app_handle().try_state::<state::AppState>() {
                            state.roots.mark_importable(paths.iter().cloned());
                        }
                        DragPayload {
                            kind: "drop",
                            paths: paths.iter().map(|p| p.to_string_lossy().to_string()).collect(),
                        }
                    }
                    DragDropEvent::Leave => DragPayload {
                        kind: "leave",
                        paths: vec![],
                    },
                    _ => return,
                };
                let _ = window.emit("bloom://drag", payload);
            }
        })
        .invoke_handler(tauri::generate_handler![
            net::http_request,
            net::download_batch,
            net::cancel_task,
            fsops::app_paths,
            fsops::fs_read_text,
            fsops::fs_read_tail,
            fsops::fs_write_text,
            fsops::fs_exists,
            fsops::fs_stat,
            fsops::fs_list,
            fsops::fs_mkdir,
            fsops::fs_trash,
            fsops::fs_remove_internal,
            fsops::fs_rename,
            fsops::fs_copy,
            fsops::fs_copy_dir,
            fsops::fs_hash,
            fsops::zip_read_text,
            fsops::zip_read_data_url,
            fsops::zip_list,
            fsops::zip_extract,
            fsops::open_path,
            fsops::open_url,
            fsops::open_log_dir,
            fsops::autostart_get,
            fsops::autostart_set,
            fsops::pick_files,
            fsops::pick_game_dir,
            fsops::list_importable_folder,
            fsops::import_files,
            launch::launch_game,
            launch::run_java_tool,
            launch::kill_game,
            launch::running_games,
            launch::process_stats,
            launch::system_info,
            launch::java_probe,
            launch::find_javas,
            machine::machine_id,
            secrets::secret_set,
            secrets::secret_get,
            secrets::secret_delete,
            bloomclient::bloom_client_bundle,
            bloomclient::bloom_client_install,
            bloomclient::fs_read_data_url,
            authwin::ms_login_window,
        ])
        .run(tauri::generate_context!())
        .expect("error while running MOTION");
}
