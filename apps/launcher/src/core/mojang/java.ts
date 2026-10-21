import { BloomError, type Arch, type DownloadItem, type OsName } from "../platform";
import { join } from "../util/path";
import type { JavaManifest, JavaRuntimeEntry, JavaRuntimeIndex } from "./types";

/** Key used by Mojang's Java runtime index for the current platform. */
export function runtimePlatformKey(os: OsName, arch: Arch): string {
  if (os === "windows") return arch === "arm64" ? "windows-arm64" : arch === "x86" ? "windows-x86" : "windows-x64";
  if (os === "osx") return arch === "arm64" ? "mac-os-arm64" : "mac-os";
  return arch === "x86" ? "linux-i386" : "linux";
}

export function pickRuntime(index: JavaRuntimeIndex, os: OsName, arch: Arch, component: string): JavaRuntimeEntry {
  const platform = index[runtimePlatformKey(os, arch)];
  const entry = platform?.[component]?.[0];
  if (!entry) {
    throw new BloomError(
      "java_unavailable",
      `Mojang does not provide Java "${component}" for this system. Set a custom Java path for this installation in its settings.`,
    );
  }
  return entry;
}

export function javaExecutable(runtimeDir: string, os: OsName, sep: string): string {
  if (os === "windows") return join(sep, runtimeDir, "bin", "javaw.exe");
  if (os === "osx") return join(sep, runtimeDir, "jre.bundle", "Contents", "Home", "bin", "java");
  return join(sep, runtimeDir, "bin", "java");
}

/** Download list for a runtime manifest (raw files; directories are created implicitly). */
export function planRuntime(manifest: JavaManifest, runtimeDir: string, sep: string): DownloadItem[] {
  const items: DownloadItem[] = [];
  for (const [rel, f] of Object.entries(manifest.files)) {
    if (f.type !== "file") continue;
    items.push({
      url: f.downloads.raw.url,
      path: join(sep, runtimeDir, ...rel.split("/")),
      sha1: f.downloads.raw.sha1,
      size: f.downloads.raw.size,
      executable: Boolean(f.executable),
    });
  }
  return items;
}
