//! Discord Rich Presence for active Minecraft sessions.
//! Player names, server addresses, and world names are never shared.
use discord_rich_presence::{activity, DiscordIpc, DiscordIpcClient};
use std::{
    collections::HashMap,
    sync::{Arc, Mutex},
    thread,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tauri::{AppHandle, Manager};

#[derive(Clone)]
struct GamePresence {
    version: String,
    started_at: i64,
}

#[derive(Default)]
pub struct DiscordState {
    games: Arc<Mutex<HashMap<String, GamePresence>>>,
}

pub fn game_started(app: &AppHandle, id: &str, version: String) {
    if let Some(state) = app.try_state::<DiscordState>() {
        let started_at = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;
        if let Ok(mut games) = state.games.lock() {
            games.insert(id.to_string(), GamePresence { version, started_at });
        }
    }
}

pub fn game_stopped(app: &AppHandle, id: &str) {
    if let Some(state) = app.try_state::<DiscordState>() {
        if let Ok(mut games) = state.games.lock() {
            games.remove(id);
        }
    }
}

pub fn start(app: AppHandle) {
    let Some(client_id) = option_env!("MOTION_DISCORD_CLIENT_ID")
        .filter(|id| !id.trim().is_empty())
        .map(str::to_owned)
    else {
        log::info!(
            "Discord Rich Presence disabled: MOTION_DISCORD_CLIENT_ID is not configured at build time"
        );
        return;
    };

    let games = app.state::<DiscordState>().games.clone();
    let _ = thread::Builder::new()
        .name("discord-presence".into())
        .spawn(move || {
            let mut client: Option<DiscordIpcClient> = None;
            loop {
                let game = games.lock().ok().and_then(|items| items.values().next().cloned());
                match game {
                    Some(game) => {
                        if client.is_none() {
                            let mut rpc = DiscordIpcClient::new(&client_id);
                            match rpc.connect() {
                                Ok(()) => client = Some(rpc),
                                Err(error) => log::debug!("Discord RPC unavailable: {error}"),
                            }
                        }

                        let presence = activity::Activity::new()
                            .details("Playing Minecraft")
                            .state(format!("{} • MOTION Client", game.version))
                            .timestamps(
                                activity::Timestamps::new().start(game.started_at),
                            );
                        if let Some(rpc) = client.as_mut() {
                            if let Err(error) = rpc.set_activity(presence) {
                                log::debug!("Discord presence update failed: {error}");
                                let _ = rpc.close();
                                client = None;
                            }
                        }
                    }
                    None => {
                        if let Some(mut rpc) = client.take() {
                            let _ = rpc.clear_activity();
                            let _ = rpc.close();
                        }
                    }
                }
                thread::sleep(Duration::from_secs(10));
            }
        });
}
