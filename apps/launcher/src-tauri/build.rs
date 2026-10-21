/// Read a value from the environment, or from apps/launcher/.env (which Vite
/// reads for the UI but Cargo doesn't), so one file configures both halves.
fn setting(key: &str) -> Option<String> {
    if let Ok(v) = std::env::var(key) {
        return Some(v);
    }
    let text = std::fs::read_to_string("../.env").ok()?;
    text.lines().find_map(|line| {
        let line = line.trim();
        let (k, v) = line.split_once('=')?;
        (k.trim() == key && !line.starts_with('#')).then(|| v.trim().trim_matches('"').to_string())
    })
}

fn main() {
    println!("cargo:rerun-if-changed=../.env");
    for key in ["VITE_BLOOM_API_URL", "BLOOM_API_HOST", "BLOOM_CONTACT"] {
        println!("cargo:rerun-if-env-changed={key}");
    }

    // The native HTTP allowlist needs the BLOOD API's host (derived from the URL the UI uses).
    let host = setting("BLOOM_API_HOST").or_else(|| {
        setting("VITE_BLOOM_API_URL").map(|url| {
            url.split("://")
                .nth(1)
                .unwrap_or("")
                .split(['/', ':', '?', '#'])
                .next()
                .unwrap_or("")
                .to_ascii_lowercase()
        })
    });
    if let Some(host) = host.filter(|h| !h.is_empty()) {
        println!("cargo:rustc-env=BLOOM_API_HOST={host}");
    }
    if let Some(contact) = setting("BLOOM_CONTACT").filter(|c| !c.is_empty()) {
        println!("cargo:rustc-env=BLOOM_CONTACT={contact}");
    }
    tauri_build::build()
}
