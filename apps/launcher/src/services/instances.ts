import { CONTENT_FOLDERS, gameDirOf, instanceDir, instanceMetaPath, newInstanceId, validateInstance, type Instance } from "../core/instances/model";
import { BloomError } from "../core/platform";
import { app, L, P, path, updateSettings } from "./app";
import { native } from "./native";

export async function loadInstances(): Promise<Instance[]> {
  const p = P();
  const l = L();
  const dirs = await p.list(l.instances);
  const out: Instance[] = [];
  for (const d of dirs) {
    if (!d.isDir) continue;
    const text = await p.readText(instanceMetaPath(l, p.sep, d.name)).catch(() => null);
    if (!text) continue;
    try {
      const inst = JSON.parse(text) as Instance;
      if (inst.id === d.name) out.push(inst);
    } catch {
      console.warn(`Skipping unreadable installation ${d.name}`);
    }
  }
  return out.sort((a, b) => (b.lastPlayedAt ?? b.createdAt) - (a.lastPlayedAt ?? a.createdAt));
}

export async function saveInstance(inst: Instance): Promise<void> {
  const p = P();
  await p.writeText(instanceMetaPath(L(), p.sep, inst.id), JSON.stringify(inst, null, 2));
  app.set((s) => {
    const exists = s.instances.some((i) => i.id === inst.id);
    return { instances: exists ? s.instances.map((i) => (i.id === inst.id ? inst : i)) : [inst, ...s.instances] };
  });
}

export function getInstance(id: string): Instance | undefined {
  return app.get().instances.find((i) => i.id === id);
}

export function selectedInstance(): Instance | undefined {
  const s = app.get();
  return s.instances.find((i) => i.id === s.settings.selectedInstanceId) ?? s.instances[0];
}

export async function createInstance(draft: Omit<Instance, "id" | "createdAt">): Promise<Instance> {
  const errors = validateInstance(draft);
  if (errors.length) throw new BloomError("invalid", errors.join(" "));
  const ids = new Set(app.get().instances.map((i) => i.id));
  // Also avoid folders left behind on disk.
  for (const e of await P().list(L().instances)) ids.add(e.name);
  const id = newInstanceId(draft.name, ids);
  const root = app.get().settings.defaultGameRoot;
  const inst: Instance = { ...draft, id, createdAt: Date.now(), ...(root && !draft.gameDir ? { gameDir: path(root, id) } : {}) };
  const p = P();
  const dir = gameDirOf(L(), p.sep, inst);
  for (const sub of [CONTENT_FOLDERS.mods, CONTENT_FOLDERS.resourcepacks, CONTENT_FOLDERS.shaderpacks, CONTENT_FOLDERS.saves]) {
    await p.mkdir(path(dir, sub));
  }
  await saveInstance(inst);
  if (!app.get().settings.selectedInstanceId) updateSettings({ selectedInstanceId: inst.id });
  return inst;
}

export async function updateInstance(id: string, patch: Partial<Instance>): Promise<Instance> {
  const cur = getInstance(id);
  if (!cur) throw new BloomError("not_found", "Installation not found");
  const next = { ...cur, ...patch, id: cur.id };
  const errors = validateInstance(next);
  if (errors.length) throw new BloomError("invalid", errors.join(" "));
  await saveInstance(next);
  return next;
}

/** Copy an installation, including its mods, configs, packs and worlds (but not logs or crash reports). */
export async function duplicateInstance(id: string): Promise<Instance> {
  const src = getInstance(id);
  if (!src) throw new BloomError("not_found", "Installation not found");
  const p = P();
  const ids = new Set(app.get().instances.map((i) => i.id));
  for (const e of await p.list(L().instances)) ids.add(e.name);
  const name = `${src.name} (copy)`.slice(0, 64);
  const copy: Instance = { ...src, id: newInstanceId(name, ids), name, createdAt: Date.now(), lastPlayedAt: undefined, totalPlayMs: 0, gameDir: undefined };
  await native.copyDir(gameDirOf(L(), p.sep, src), instanceDir(L(), p.sep, copy.id), ["logs", "crash-reports"]);
  await saveInstance(copy);
  return copy;
}

/**
 * Remove an installation. Its folder is moved to the Recycle Bin, so worlds and
 * mods can still be recovered. Custom game directories are left untouched.
 */
export async function deleteInstance(id: string): Promise<void> {
  const inst = getInstance(id);
  if (!inst) return;
  const p = P();
  await p.trash(instanceDir(L(), p.sep, id));
  await p.removeInternal(path(L().natives, id)).catch(() => undefined);
  app.set((s) => ({ instances: s.instances.filter((i) => i.id !== id) }));
  if (app.get().settings.selectedInstanceId === id) updateSettings({ selectedInstanceId: app.get().instances[0]?.id ?? null });
}

export function instanceGameDir(inst: Instance): string {
  return gameDirOf(L(), P().sep, inst);
}
