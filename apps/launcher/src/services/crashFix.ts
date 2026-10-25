/** Applies the fixes the Crash Doctor suggests (core/mojang/crash.ts). */

import { modrinthSlugFor, type CrashFix } from "../core/mojang/crash";
import { app, navigate, reportError, toast, updateSettings } from "./app";
import { listContent, toggleContent } from "./content";
import { play } from "./game";
import { getInstance, updateInstance } from "./instances";
import { bumpContent } from "../ui/contentEvents";

const gb = (mb: number) => `${(mb / 1024).toFixed(1).replace(/\.0$/, "")} GB`;

/** A short label for the fix button. */
export function fixLabel(fix: CrashFix): string {
  switch (fix.kind) {
    case "raise-memory":
      return `Give Minecraft ${gb(fix.toMb)}`;
    case "disable-mods":
      return fix.modIds.length === 1 ? `Turn off ${fix.modIds[0]}` : `Turn off ${fix.modIds.length} mods`;
    case "install-mods":
      return fix.modIds.length === 1 ? `Get ${fix.modIds[0]}` : `Get ${fix.modIds[0]} first`;
    case "repair":
      return "Repair and play";
    case "reset-java":
      return "Use MOTION's Java";
    case "open-shaders":
      return "Open shader packs";
  }
}

/** Mod files whose id (or file name) matches one of `ids`. */
function matches(item: { meta: { id: string } | null; baseName: string; modrinth?: { projectId: string; title?: string } }, ids: Set<string>): boolean {
  if (item.meta && ids.has(item.meta.id.toLowerCase())) return true;
  const file = item.baseName.toLowerCase().replace(/\.jar$/, "");
  for (const id of ids) {
    // "sodium-fabric-0.5.8+mc1.21.jar" for id "sodium": only when the id is the start of the file name.
    if (file === id || file.startsWith(`${id}-`) || file.startsWith(`${id}_`) || file.startsWith(`${id}+`)) return true;
  }
  return false;
}

export async function applyCrashFix(instanceId: string, fix: CrashFix): Promise<void> {
  const inst = getInstance(instanceId);
  if (!inst) return;
  try {
    switch (fix.kind) {
      case "raise-memory": {
        const cur = inst.memory ?? app.get().settings.memory ?? { minMb: 512, maxMb: 4096 };
        await updateInstance(inst.id, { memory: { minMb: Math.min(cur.minMb, fix.toMb), maxMb: fix.toMb } });
        toast({ kind: "success", title: `${inst.name} now gets ${gb(fix.toMb)}`, message: "Press Play to try again.", action: { label: "Play", run: () => void play(inst.id) } });
        return;
      }
      case "disable-mods": {
        const ids = new Set(fix.modIds.map((x) => x.toLowerCase()));
        const mods = await listContent(inst, "mods");
        const hits = mods.filter((m) => m.enabled && matches(m, ids));
        if (!hits.length) {
          toast({ kind: "info", title: "Nothing to turn off", message: `${fix.modIds.join(", ")} isn't an enabled mod file in ${inst.name}. It may be bundled inside another mod.` });
          navigate({ page: "instance", id: inst.id, tab: "content" });
          return;
        }
        for (const m of hits) await toggleContent(inst, "mods", m);
        bumpContent();
        toast({
          kind: "success",
          title: hits.length === 1 ? `Turned off ${hits[0]?.meta?.name ?? hits[0]?.baseName}` : `Turned off ${hits.length} mods`,
          message: "They're still in the Mods tab: switch them back on any time.",
          action: { label: "Play", run: () => void play(inst.id) },
        });
        return;
      }
      case "install-mods": {
        const first = fix.modIds[0];
        if (first) navigate({ page: "project", id: modrinthSlugFor(first), instanceId: inst.id });
        return;
      }
      case "repair":
        await play(inst.id, { repair: true });
        return;
      case "reset-java": {
        if (inst.javaPath) {
          await updateInstance(inst.id, { javaPath: undefined });
        }
        if (app.get().settings.javaPath) updateSettings({ javaPath: null });
        toast({ kind: "success", title: "MOTION picks Java now", message: "The right Java for this Minecraft version is downloaded automatically.", action: { label: "Play", run: () => void play(inst.id) } });
        return;
      }
      case "open-shaders":
        navigate({ page: "instance", id: inst.id, tab: "shaderpacks" });
        return;
    }
  } catch (e) {
    reportError("Couldn't apply the fix", e);
  }
}
