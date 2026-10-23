# Releasing MOTION

## How players get updates

Every installed MOTION checks `https://github.com/<your repo>/releases/latest/download/latest.json`
4 seconds after it starts and then every 4 hours. When there's a newer version it:

1. downloads it quietly in the background and verifies its signature;
2. waits until nobody is playing (no Minecraft running, no downloads or launches in progress);
3. shows "Updating MOTION to x.y.z in 15 s" with a **Later** button (Later or closing the
   notification postpones it by an hour, or until the next start);
4. installs it (a small progress window, no questions) and restarts MOTION.

Players can turn this off in Settings → Launcher → *Update MOTION automatically*; they then get a
notification with an **Update now** button instead. **MOTION Client updates too.** The mod jar ships
inside the launcher, and before each launch MOTION replaces the jar in installations that use it when
the bundled version is newer.

## One-time setup

1. **Generate the updater key pair** on your PC, and keep the private key safe. If you lose it,
   installed launchers can't accept new updates and players would have to reinstall by hand.
   ```bash
   npm install
   npm run tauri -w @bloom/launcher -- signer generate -w ~/.tauri/blood.key
   ```
   This prints a **public key** and writes the **private key** to `~/.tauri/blood.key` (plus `.pub`).
2. **Add them to the GitHub repository** (Settings → Secrets and variables → Actions):
   - Secret `TAURI_SIGNING_PRIVATE_KEY`: the contents of `~/.tauri/blood.key`.
   - Secret `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`: the password you chose (empty if none).
   - Variable `TAURI_UPDATER_PUBKEY`: the public key (the contents of `~/.tauri/blood.key.pub`).

   The Release workflow bakes the public key and this repository's update URL into every build,
   so there's nothing to edit in `tauri.conf.json`. A release fails early with a clear message if
   any of these is missing, so you can't accidentally ship a build that can't update itself.
3. **Add the code-signing certificate (recommended).** An Authenticode certificate (OV or EV) avoids SmartScreen's "unknown publisher" warning.
   - Store a base64 `.pfx` in `WINDOWS_CERTIFICATE` and its password in `WINDOWS_CERTIFICATE_PASSWORD`.
   - Without them the release still builds, unsigned, and the workflow prints a warning.
   - EV certificates on hardware tokens or in cloud HSMs need the vendor's signing tool instead. Set `bundle.windows.signCommand` accordingly.
4. **Set repository variables.**
   - `VITE_MS_CLIENT_ID` (see [MICROSOFT_AUTH.md](MICROSOFT_AUTH.md))
   - `VITE_BLOOM_API_URL`
   - `BLOOM_CONTACT` (see [MODRINTH.md](MODRINTH.md))
   - `MOTION_DISCORD_CLIENT_ID` (the Discord Application ID from Developer Portal → General Information; this enables in-game Rich Presence)
5. **Make the repository's releases public.** Launchers download `latest.json` and the installer
   anonymously. If the code must stay private, publish releases from a separate public repository
   instead.

## Cutting a release (every update)

```bash
npm run release -- 1.0.1 "What changed, in a sentence or two."
git push --follow-tags
```

`npm run release`:

- sets the version in `tauri.conf.json`, both `package.json` files and `Cargo.toml`, and refuses a
  version that isn't higher than the current one, because launchers only update to higher versions;
- bumps MOTION Client's patch version if anything under `apps/client-mod` changed since the last
  release, so players' in-game mod is replaced too;
- adds the notes to `apps/api/content/releases.json`;
- commits and tags `vX.Y.Z`.

`--dry-run` shows what would change. `--no-git` only edits the files.

Pushing the tag runs the **Release** workflow:

1. It builds the MOTION Client jars on Ubuntu (JDK 21) and runs their tests.
2. On Windows, it checks that the tag matches the version and that the updater keys are set, then
   bundles the jars into the launcher.
3. It runs the TypeScript and Rust tests.
4. It builds the NSIS installer and MSI, signing them if a certificate is configured, and signs the
   update bundle.
5. It **publishes** the GitHub release with `latest.json` and your notes.

Installed launchers pick it up within about 4 hours, or straight away the next time they start.
To try a build before players get it, run the workflow on a fork, or build locally (below) and
install that by hand.

**First release:** auto-update only works in launchers that were built with the updater keys. The
first public release must therefore come from this workflow after the one-time setup. Anyone on a
build made before that has to install the new version once by hand.

## What is and isn't in the installer

- **Included:** the launcher, its icons and theme art, and the MOTION Client jars with their manifest.
- **Not included:** any Minecraft game files, Java runtimes or loader libraries. Those are downloaded from Mojang (game files and Mojang's own Java runtimes), Fabric, Quilt, Forge and NeoForge when an installation is created, and checked against their published hashes.

## Local builds

```bash
cd apps/client-mod && ./gradlew build && node tools/bundle.mjs   # optional: include MOTION Client
cd ../.. && npm run build                                         # tauri build (Windows)
```

Without the mod step, the launcher still builds. The Customize page then says MOTION Client is "not included in this build".
