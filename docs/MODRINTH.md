# Modrinth integration

MOTION browses and installs mods, modpacks, resource packs and shaders from the public
[Modrinth API v2](https://docs.modrinth.com/api/). No API key is needed for public data.

## Being a good API citizen

- **User-Agent.** Every request carries `bloom-launcher/<version> (<contact>)`. Set `BLOOM_CONTACT` at build time, e.g. `github.com/you/bloom` or a support email. Modrinth may block generic user agents, so use your own contact for your builds.
- **Rate limits.** Modrinth allows about 300 requests per minute per IP. `core/modrinth/api.ts` tracks the `X-Ratelimit-Remaining`/`X-Ratelimit-Reset` headers, pauses when the budget runs out, and retries once on HTTP 429. GET responses are cached for 60 seconds.
- **Batching.** Update checks hash local files and send a single `POST /version_files` / `/version_files/update` request instead of one per mod.

## Installing content

- **Dependencies.** `core/modrinth/resolve.ts` picks the newest version that matches the installation's Minecraft version and loader, then resolves required dependencies the same way. Optional dependencies are listed, never installed automatically. Incompatible projects are listed with the reason.
- **Verification.** Every download is checked against Modrinth's sha512/sha1 before it is moved into place.
- **Existing files.** An existing file with the same name is never overwritten; that file is skipped and reported. Updating a mod downloads the new version first, then moves the old file to the Recycle Bin.
- **Modpacks (`.mrpack`).**
  - **Paths:** file paths are validated, so nothing is written outside the installation.
  - **Download hosts:** URLs must be https on Modrinth's allowed hosts (`cdn.modrinth.com`, `github.com`, `raw.githubusercontent.com`, `gitlab.com`).
  - **Server-only files:** files marked `client: unsupported` are skipped.
  - **Overrides:** the `overrides/` and `client-overrides/` folders are extracted into the new installation's folder (a modpack always gets a fresh installation, so none of your files are touched).

## Your own files

Mods you drop on the window are added from local files. MOTION reads their `fabric.mod.json`, `quilt.mod.json` or `mods.toml` to show the name, version and loader, and warns if a mod is for a different loader or Minecraft version. Jars without readable metadata can still be added; they are listed by file name.
