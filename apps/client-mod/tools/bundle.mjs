// Copies the built BLOOD Client jars into the launcher's bundled resources and
// writes manifest.json (version, Minecraft versions, file names, sha1, size).
//
//   node apps/client-mod/tools/bundle.mjs            (after ./gradlew build)
//
// The launcher reads this manifest (src-tauri/src/bloomclient.rs) and checks
// every jar's sha1 before installing it into an instance's mods folder.
import { createHash } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const modRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const out = resolve(modRoot, "../launcher/src-tauri/resources/bloomclient");

const props = Object.fromEntries(
  readFileSync(join(modRoot, "gradle.properties"), "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("=") && !l.trimStart().startsWith("#"))
    .map((l) => [l.slice(0, l.indexOf("=")).trim(), l.slice(l.indexOf("=") + 1).trim()]),
);
const { version, minecraft_version: mc, mod_id: modId } = props;
if (!version || !mc || !modId) throw new Error("gradle.properties is missing version, minecraft_version or mod_id");

// Every supported build must be present. A release cannot publish without the newest Minecraft port.
const builds = [
  { loader: "fabric", minecraft: mc, dir: "fabric" },
  { loader: "neoforge", minecraft: mc, dir: "neoforge" },
  { loader: "fabric", minecraft: "1.21.11", dir: "mc1.21.11" },
];
const files = [];
mkdirSync(out, { recursive: true });
// Remove jars from an earlier bundle, never anything else (README.md stays).
for (const f of readdirSync(out)) if (f.endsWith(".jar") || f === "manifest.json") rmSync(join(out, f));

for (const b of builds) {
  const name = `${modId}-${b.loader}-${b.minecraft}-${version}.jar`;
  const expected = join(modRoot, b.dir, "build", "libs", name);
  const searchRoot = join(modRoot, b.dir);
  const stack = [searchRoot];
  let src = expected;
  while (!existsSync(src) && stack.length) {
    const dir = stack.pop();
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const candidate = join(dir, entry.name);
      if (entry.isDirectory()) stack.push(candidate);
      else if (entry.isFile() && entry.name === name) {
        src = candidate;
        break;
      }
    }
  }
  if (!existsSync(src)) throw new Error(`Missing ${src}. Run ./gradlew build first.`);
  const bytes = readFileSync(src);
  copyFileSync(src, join(out, name));
  files.push({ loader: b.loader, minecraft: b.minecraft, file: name, sha1: createHash("sha1").update(bytes).digest("hex"), size: statSync(src).size });
  console.log(`bundled ${name} (${bytes.length} bytes)`);
}
const minecraft = [...new Set(files.map((f) => f.minecraft))];

writeFileSync(join(out, "manifest.json"), JSON.stringify({ version, minecraft, files }, null, 2) + "\n");
console.log(`wrote ${join(out, "manifest.json")}`);
