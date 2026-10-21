use crate::paths::Roots;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

pub struct GameHandle {
    pub pid: u32,
    pub kill: Option<tokio::sync::oneshot::Sender<()>>,
}

pub struct AppState {
    pub roots: Roots,
    pub client: reqwest::Client,
    tasks: Mutex<HashMap<String, Arc<AtomicBool>>>,
    pub games: Mutex<HashMap<String, GameHandle>>,
    pub sys: Mutex<sysinfo::System>,
}

impl AppState {
    pub fn new(data_dir: PathBuf) -> Self {
        AppState {
            roots: Roots::new(data_dir),
            client: crate::net::build_client(),
            tasks: Mutex::new(HashMap::new()),
            games: Mutex::new(HashMap::new()),
            sys: Mutex::new(sysinfo::System::new()),
        }
    }

    pub fn register_task(&self, id: &str, flag: Arc<AtomicBool>) {
        if let Ok(mut t) = self.tasks.lock() {
            t.insert(id.to_string(), flag);
        }
    }

    pub fn finish_task(&self, id: &str) {
        if let Ok(mut t) = self.tasks.lock() {
            t.remove(id);
        }
    }

    pub fn cancel_task(&self, id: &str) {
        if let Ok(t) = self.tasks.lock() {
            if let Some(flag) = t.get(id) {
                flag.store(true, Ordering::Relaxed);
            }
        }
    }
}
