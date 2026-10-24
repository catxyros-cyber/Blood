#!/usr/bin/env node
/**
 * Cut a MOTION release in one step:
 *
 *   npm run release -- 1.0.1 "Fixed the crosshair on NeoForge. Faster startup."
 *   git push --follow-tags
 *
 * It sets the launcher version everywhere it's stored, bumps MOTION Client's version when the
 * mod changed since the last release (so launchers replace the in-game jar too), adds the notes
 * to apps/api/content/releases.json, then commits and tags vX.Y.Z. Pushing the tag runs the
 * Release workflow, which publishes the installer and latest.json; every installed launcher
 * then updates itself.
 *
 * Options: --dry-run (show what would change), --no-git (edit files only).
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const flags = new Set(args.filter((a) => a.startsWith("--")));
const [version, ...noteParts] = args.filter((a) => !a.startsWith("--"));
const dry = flags.has("--dry-run");
const useGit = !flags.has("--no-git");

function fail(msg) {
  console.error(`release: ${msg}`);
  process.exit(1);
}

const SEMVER = /^(\d+)\.(\d+)\.(\d+)$/;
if (!version || !SEMVER.test(version)) fail('usage: npm run release -- <x.y.z> ["release notes"]');

const read = (p) => readFileSync(join(ROOT, p), "utf8");
const write = (p, s) => {
  if (dry) console.log(`would write ${p}`);
  else writeFileSync(join(ROOT, p), s);
};
const git = (...a) => execFileSync("git", a, { cwd: ROOT, encoding: "utf8" }).trim();

function cmp(a, b) {
  const pa = a.split(".").map(Number), pb = b.split(".").map(Number);
  for (let i = 0; i < 3; i++) if (pa[i] !== pb[i]) return pa[i] - pb[i];
  return 0;
}

// ---------------------------------------------------------------- launcher version
const tauriPath = "apps/launcher/src-tauri/tauri.conf.json";
const current = JSON.parse(read(tauriPath)).version;
if (cmp(version, current) <= 0) fail(`${version} must be higher than the current version ${current}, or installed launchers won't see it as an update.`);

if (useGit && !dry) {
  if (git("status", "--porcelain")) fail("commit or stash your changes first, so the release commit only contains the version bump.");
  if (git("tag", "--list", `v${version}`)) fail(`tag v${version} already exists.`);
}

const setJsonVersion = (p) => write(p, read(p).replace(/("version"\s*:\s*")[^"]+(")/, `$1${version}$2`));
setJsonVersion(tauriPath);
setJsonVersion("apps/launcher/package.json");
setJsonVersion("package.json");
const cargo = "apps/launcher/src-tauri/Cargo.toml";
write(cargo, read(cargo).replace(/^version\s*=\s*"[^"]+"/m, `version = "${version}"`));

// ---------------------------------------------------------------- MOTION Client version
// The launcher only replaces the in-game jar when the bundled version is newer, so bump the
// mod's patch version whenever its sources changed since the previous release tag.
const gradleProps = "apps/client-mod/gradle.properties";
let modBumped = null;
if (useGit) {
  let lastTag = "";
  try {
    lastTag = git("describe", "--tags", "--abbrev=0", "--match", "v*");
  } catch {
    /* first release */
  }
  const changed = lastTag ? git("diff", "--name-only", lastTag, "HEAD", "--", "apps/client-mod") !== "" : true;
  if (changed && lastTag) {
    const props = read(gradleProps);
    const m = props.match(/^version=(\d+)\.(\d+)\.(\d+)/m);
    if (m) {
      modBumped = `${m[1]}.${m[2]}.${Number(m[3]) + 1}`;
      write(gradleProps, props.replace(/^version=.*$/m, `version=${modBumped}`));
    }
  }
}

// ---------------------------------------------------------------- release notes
const notesPath = "apps/api/content/releases.json";
const releases = JSON.parse(read(notesPath));
const notes = noteParts.join(" ").trim() || "Bug fixes and improvements.";
const entry = {
  version,
  date: new Date().toISOString().slice(0, 10),
  notes: modBumped ? `${notes}\n\nMOTION Client ${modBumped}.` : notes,
};
write(notesPath, JSON.stringify([entry, ...releases.filter((r) => r.version !== version)], null, 2) + "\n");

// ---------------------------------------------------------------- commit + tag
if (useGit && !dry) {
  git("add", "-A");
  git("commit", "-m", `Release v${version}`);
  git("tag", "-a", `v${version}`, "-m", `MOTION ${version}`);
}

console.log(`\nMOTION ${current} → ${version}${modBumped ? ` (MOTION Client → ${modBumped})` : ""}`);
if (dry) console.log("Dry run: nothing was changed.");
else if (useGit) console.log(`Committed and tagged v${version}. Publish it with:\n\n  git push --follow-tags\n`);
else console.log(`Files updated. Commit, then tag v${version} and push the tag.`);
