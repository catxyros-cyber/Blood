//! Path confinement.
//!
//! The webview never gets unrestricted filesystem access. Every path it sends
//! is resolved here and must fall inside:
//!   * MOTION's data directory, or
//!   * a folder the user explicitly picked in a native dialog (custom game
//!     directories), which is remembered in `roots.json`.
//! Files *outside* those roots can only be read as import sources, and only if
//! the user picked them in a native file dialog or dropped them on the window
//! during this session.

use crate::error::{AppError, Result};
use std::collections::HashSet;
use std::path::{Component, Path, PathBuf};
use std::sync::{Mutex, RwLock};

pub struct Roots {
    data: PathBuf,
    extra: RwLock<Vec<PathBuf>>,
    importable: Mutex<HashSet<PathBuf>>,
}

impl Roots {
    pub fn new(data: PathBuf) -> Self {
        let extra = std::fs::read_to_string(data.join("roots.json"))
            .ok()
            .and_then(|s| serde_json::from_str::<Vec<PathBuf>>(&s).ok())
            .unwrap_or_default();
        Roots {
            data,
            extra: RwLock::new(extra),
            importable: Mutex::new(HashSet::new()),
        }
    }

    pub fn data_dir(&self) -> &Path {
        &self.data
    }

    /// Lexically normalise a path and reject `..` so a path can't climb out of a root.
    fn normalize(p: &Path) -> Result<PathBuf> {
        let mut out = PathBuf::new();
        for c in p.components() {
            match c {
                Component::ParentDir => return Err(AppError::new("forbidden_path", "Paths may not contain '..'")),
                Component::CurDir => {}
                other => out.push(other.as_os_str()),
            }
        }
        Ok(out)
    }

    fn in_roots(&self, p: &Path) -> bool {
        if p.starts_with(&self.data) {
            return true;
        }
        self.extra
            .read()
            .map(|r| r.iter().any(|root| p.starts_with(root)))
            .unwrap_or(false)
    }

    /// Resolve a path from the UI. Relative paths are relative to the data dir.
    pub fn resolve(&self, raw: &str) -> Result<PathBuf> {
        if raw.is_empty() {
            return Err(AppError::new("forbidden_path", "Empty path"));
        }
        let p = Self::normalize(Path::new(raw))?;
        let full = if p.is_absolute() { p } else { self.data.join(p) };
        if self.in_roots(&full) {
            Ok(full)
        } else {
            Err(AppError::new(
                "forbidden_path",
                format!("MOTION is not allowed to access {}", full.display()),
            ))
        }
    }

    /// Like `resolve`, but only for MOTION's own caches, which may be hard-deleted.
    pub fn resolve_internal(&self, raw: &str) -> Result<PathBuf> {
        let p = self.resolve(raw)?;
        let rel = p
            .strip_prefix(&self.data)
            .map_err(|_| AppError::new("forbidden_path", "Not an internal path"))?;
        let first = rel
            .components()
            .next()
            .map(|c| c.as_os_str().to_string_lossy().to_string());
        match first.as_deref() {
            Some("versions") | Some("libraries") | Some("assets") | Some("natives") | Some("meta") | Some("tmp")
            | Some("runtimes") | Some("cache") => Ok(p),
            _ => Err(AppError::new(
                "forbidden_path",
                "Only launcher caches can be removed permanently",
            )),
        }
    }

    pub fn add_root(&self, dir: PathBuf) -> Result<()> {
        let dir = Self::normalize(&dir)?;
        {
            let mut w = self
                .extra
                .write()
                .map_err(|_| AppError::new("internal", "lock poisoned"))?;
            if !w.contains(&dir) {
                w.push(dir);
            }
            let json = serde_json::to_string_pretty(&*w)?;
            std::fs::write(self.data.join("roots.json"), json)?;
        }
        Ok(())
    }

    pub fn mark_importable<I: IntoIterator<Item = PathBuf>>(&self, paths: I) {
        if let Ok(mut set) = self.importable.lock() {
            for p in paths {
                set.insert(p);
            }
        }
    }

    pub fn is_importable(&self, p: &Path) -> bool {
        self.importable.lock().map(|s| s.contains(p)).unwrap_or(false)
    }

    /// Resolve a path that may be read as an import source: inside the roots,
    /// or explicitly chosen/dropped by the user.
    pub fn resolve_source(&self, raw: &str) -> Result<PathBuf> {
        let p = Self::normalize(Path::new(raw))?;
        if self.is_importable(&p) {
            return Ok(p);
        }
        self.resolve(raw)
    }
}

#[cfg(test)]
mod tests {
    use super::Roots;
    use std::path::PathBuf;

    fn roots() -> (Roots, PathBuf) {
        let dir = std::env::temp_dir().join(format!("bloom-roots-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        (Roots::new(dir.clone()), dir)
    }

    #[test]
    fn confines_paths_to_the_data_dir() {
        let (r, dir) = roots();
        assert!(r.resolve(dir.join("instances").join("a").to_str().unwrap()).is_ok());
        assert!(
            r.resolve("instances/a/mods").is_ok(),
            "relative paths are under the data dir"
        );
        assert!(
            r.resolve(dir.join("..").join("escape").to_str().unwrap()).is_err(),
            "'..' is rejected"
        );
        assert!(r.resolve("../escape").is_err());
        assert!(r.resolve("").is_err());
        let outside = std::env::temp_dir().join("somewhere-else");
        assert!(r.resolve(outside.to_str().unwrap()).is_err());
    }

    #[test]
    fn only_caches_can_be_hard_deleted() {
        let (r, dir) = roots();
        assert!(r
            .resolve_internal(dir.join("libraries").join("x.jar").to_str().unwrap())
            .is_ok());
        assert!(r.resolve_internal(dir.join("tmp").to_str().unwrap()).is_ok());
        assert!(r
            .resolve_internal(dir.join("instances").join("survival").to_str().unwrap())
            .is_err());
        assert!(r.resolve_internal(dir.join("settings.json").to_str().unwrap()).is_err());
    }

    #[test]
    fn picked_files_become_import_sources_only() {
        let (r, _dir) = roots();
        let picked = std::env::temp_dir().join("picked-mod.jar");
        assert!(r.resolve_source(picked.to_str().unwrap()).is_err());
        r.mark_importable([picked.clone()]);
        assert!(r.resolve_source(picked.to_str().unwrap()).is_ok());
        assert!(
            r.resolve(picked.to_str().unwrap()).is_err(),
            "import sources are not writable roots"
        );
    }

    #[test]
    fn picked_game_dirs_become_roots() {
        let (r, _dir) = roots();
        let game: PathBuf = std::env::temp_dir().join(format!("bloom-game-{}", std::process::id()));
        assert!(r.resolve(game.join("mods").to_str().unwrap()).is_err());
        r.add_root(game.clone()).unwrap();
        assert!(r.resolve(game.join("mods").to_str().unwrap()).is_ok());
    }
}
