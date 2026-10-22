/**
 * Cloud sync of settings and installation profiles (MOTION Premium).
 * Push after local changes (debounced), pull at startup. Conflicts — both this
 * PC and another one changed since the last sync — are left for the user to
 * resolve; nothing local is ever deleted by a sync.
 */

import type { SyncDocument } from "@bloom/shared";
import { CONTENT_FOLDERS, newInstanceId, type Instance } from "../core/instances/model";
import { toBloomError } from "../core/platform";
import { buildSyncData, fingerprint, planMerge, type SyncData, type SyncedContent } from "../core/sync/sync";
import { app, endTask, migrateSettings, P, path, startTask, toast, updateSettings, type PremiumState } from "./app";
import { contentRefs, installRefs } from "./content";
import { getInstance, instanceGameDir, saveInstance, updateInstance } from "./instances";
import { hasAccountPremium, premiumRequest } from "./premium";

interface SyncState {
  version: number;
  fingerprint: string;
  at: number;
}

const statePath = () => path(P().dataDir, "sync-state.json");

async function readState(): Promise<SyncState> {
  try {
    const raw = await P().readText(statePath());
    return raw ? (JSON.parse(raw) as SyncState) : { version: 0, fingerprint: "", at: 0 };
  } catch {
    return { version: 0, fingerprint: "", at: 0 };
  }
}

async function writeState(s: SyncState) {
  await P().writeText(statePath(), JSON.stringify(s));
}

function setSync(patch: Partial<PremiumState["sync"]>) {
  app.set((s) => ({ premium: { ...s.premium, sync: { ...s.premium.sync, ...patch } } }));
}

async function localData(): Promise<SyncData> {
  const s = app.get();
  const content: Record<string, SyncedContent[]> = {};
  for (const inst of s.instances) content[inst.id] = await contentRefs(inst).catch(() => []);
  return buildSyncData(s.settings as unknown as Record<string, unknown>, s.instances, content);
}

let conflictRemote: SyncDocument | null = null;

export function canSync(): boolean {
  const s = app.get();
  return s.premium.configured && s.settings.cloudSync && hasAccountPremium() && s.premium.status === "ready" && !s.premium.cached;
}

/** Upload this PC's state. With `force`, overwrite the cloud copy after a conflict. */
export async function pushSync(opts: { force?: boolean } = {}): Promise<void> {
  if (!canSync()) return;
  setSync({ state: "syncing", error: undefined });
  try {
    const data = await localData();
    const fp = fingerprint(data);
    const st = await readState();
    if (!opts.force && fp === st.fingerprint && st.version > 0) {
      setSync({ state: "idle" });
      return;
    }
    const base = opts.force && conflictRemote ? conflictRemote.version : st.version;
    try {
      const doc = await premiumRequest<SyncDocument>("PUT", "/v1/sync", { baseVersion: base, device: "MOTION for Windows", data });
      await writeState({ version: doc.version, fingerprint: fp, at: Date.now() });
      conflictRemote = null;
      setSync({ state: "idle", lastSyncAt: Date.now() });
    } catch (e) {
      if (toBloomError(e).code !== "sync_conflict") throw e;
      // Someone else pushed. If this PC has no unsynced changes, just take theirs.
      const remote = await premiumRequest<SyncDocument>("GET", "/v1/sync");
      if (st.fingerprint && fp === st.fingerprint) await applyRemote(remote);
      else {
        conflictRemote = remote;
        setSync({ state: "conflict" });
      }
    }
  } catch (e) {
    setSync({ state: "error", error: toBloomError(e).message });
  }
}

/** Download the cloud copy and merge it in (startup, or "Sync now"). */
export async function pullSync(): Promise<void> {
  if (!canSync()) return;
  setSync({ state: "syncing", error: undefined });
  try {
    const remote = await premiumRequest<SyncDocument>("GET", "/v1/sync");
    const st = await readState();
    const local = await localData();
    const localChanged = st.version > 0 && fingerprint(local) !== st.fingerprint;
    if (remote.version === 0) {
      // Nothing in the cloud yet: this PC seeds it.
      setSync({ state: "idle" });
      await pushSync();
      return;
    }
    if (remote.version === st.version) {
      setSync({ state: "idle", lastSyncAt: Date.now() });
      if (localChanged) await pushSync();
      return;
    }
    if (localChanged) {
      conflictRemote = remote;
      setSync({ state: "conflict" });
      return;
    }
    await applyRemote(remote);
    // Installations that exist only on this PC are added to the cloud copy.
    await pushSync();
  } catch (e) {
    setSync({ state: "error", error: toBloomError(e).message });
  }
}

/** Resolve a conflict: "cloud" takes the other PC's version, "local" keeps this PC's. */
export async function resolveConflict(choice: "cloud" | "local") {
  if (!conflictRemote) return;
  if (choice === "cloud") {
    await applyRemote(conflictRemote);
    conflictRemote = null;
    await pushSync();
  } else {
    await pushSync({ force: true });
  }
}

async function applyRemote(remote: SyncDocument) {
  const s = app.get();
  const plan = planMerge({ settings: s.settings as unknown as Record<string, unknown>, instances: s.instances }, remote.data);
  if (Object.keys(plan.settingsPatch).length) updateSettings(migrateSettings(plan.settingsPatch as Partial<typeof s.settings>));

  for (const u of plan.update) {
    await updateInstance(u.id, u.patch as Partial<Instance>).catch(() => undefined);
  }

  let added = 0;
  for (const r of plan.create) {
    const ids = new Set(app.get().instances.map((i) => i.id));
    for (const e of await P().list(path(P().dataDir, "instances")).catch(() => [])) ids.add(e.name);
    const id = ids.has(r.id) ? newInstanceId(r.name, ids) : r.id;
    const { content, ...meta } = r;
    const inst: Instance = { ...meta, id, createdAt: r.createdAt ?? Date.now() };
    for (const sub of [CONTENT_FOLDERS.mods, CONTENT_FOLDERS.resourcepacks, CONTENT_FOLDERS.shaderpacks, CONTENT_FOLDERS.saves]) {
      await P().mkdir(path(instanceGameDir(inst), sub));
    }
    await saveInstance(inst);
    added++;
    if (content.length) {
      const taskId = `sync-${id}`;
      startTask({ id: taskId, title: `Downloading synced content for ${inst.name}`, instanceId: id, cancellable: true, stage: `${content.length} files from Modrinth` });
      try {
        await installRefs(getInstance(id) ?? inst, content, `${taskId}:files`);
      } catch (e) {
        toast({ kind: "warning", title: `Some content for ${inst.name} couldn't be downloaded`, message: toBloomError(e).message });
      } finally {
        endTask(taskId);
      }
    }
  }
  const fp = fingerprint(await localData());
  await writeState({ version: remote.version, fingerprint: fp, at: Date.now() });
  setSync({ state: "idle", lastSyncAt: Date.now() });
  if (added || plan.update.length) {
    toast({
      kind: "success",
      title: "Synced from the cloud",
      message: [added ? `${added} installation${added === 1 ? "" : "s"} added.` : "", plan.update.length ? `${plan.update.length} updated.` : ""].filter(Boolean).join(" "),
    });
  }
}

/** Push automatically shortly after settings or installations change. */
export function watchForChanges() {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let last = { settings: app.get().settings, instances: app.get().instances };
  app.subscribe(() => {
    const s = app.get();
    if (s.settings === last.settings && s.instances === last.instances) return;
    last = { settings: s.settings, instances: s.instances };
    if (!canSync()) return;
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => void pushSync(), 20_000);
  });
}
