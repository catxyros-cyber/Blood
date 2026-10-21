import { useState } from "react";
import type { Instance } from "../core/instances/model";
import type { InstallPlan } from "../core/modrinth/resolve";
import type { Project, Version } from "../core/modrinth/types";
import { navigate, reportError } from "../services/app";
import { applyInstallPlan, installModpackVersion, modrinth, planProjectInstall } from "../services/content";
import { createStore, useStore } from "../services/store";
import { Badge, Button, Callout, Modal, Spinner } from "./components";
import { bumpContent } from "./contentEvents";
import { Icon } from "./icons";

interface FlowState {
  project: Project | null;
  inst: Instance | null;
  plan: InstallPlan | null;
  loading: boolean;
  error: string | null;
}

const flow = createStore<FlowState>({ project: null, inst: null, plan: null, loading: false, error: null });

/** In-flight installs, keyed by "<instanceId>:<projectId>" (for button states). */
export const installing = createStore<{ keys: Record<string, true> }>({ keys: {} });

function mark(key: string, on: boolean) {
  installing.set((s) => {
    const keys = { ...s.keys };
    if (on) keys[key] = true;
    else delete keys[key];
    return { keys };
  });
}

export function useInstalling(instId: string | undefined, projectId: string): boolean {
  return useStore(installing, (s) => Boolean(instId && s.keys[`${instId}:${projectId}`]));
}

async function run(project: Project, inst: Instance, plan: InstallPlan) {
  const key = `${inst.id}:${project.id}`;
  mark(key, true);
  try {
    await applyInstallPlan(inst, project, plan);
    bumpContent();
  } catch (e) {
    reportError(`Couldn't install ${project.title}`, e);
  } finally {
    mark(key, false);
  }
}

/**
 * Install a Modrinth project (mod, resource pack, shader) into an installation.
 * Plans dependencies first; shows a confirmation when there's anything to review.
 */
export async function installProject(projectId: string, inst: Instance, version?: Version) {
  const key = `${inst.id}:${projectId}`;
  mark(key, true);
  try {
    const project = await modrinth().project(projectId);
    const plan = await planProjectInstall(inst, project, version);
    mark(key, false);
    const needsReview = plan.installs.length > 1 || plan.conflicts.length || plan.unresolved.length || plan.warnings.length;
    if (needsReview) flow.set({ project, inst, plan, loading: false, error: null });
    else await run(project, inst, plan);
  } catch (e) {
    mark(key, false);
    reportError("Couldn't install", e);
  }
}

/** Modpacks become a new installation. */
export async function installModpack(projectId: string, version?: Version) {
  const key = `new:${projectId}`;
  mark(key, true);
  try {
    const project = await modrinth().project(projectId);
    const v = version ?? (await modrinth().versions(projectId)).find((x) => x.version_type === "release") ?? (await modrinth().versions(projectId))[0];
    if (!v) throw new Error(`${project.title} has no downloadable versions.`);
    const inst = await installModpackVersion(project, v);
    if (inst) navigate({ page: "instance", id: inst.id });
  } catch (e) {
    reportError("Couldn't install the modpack", e);
  } finally {
    mark(key, false);
  }
}

export function InstallPlanHost() {
  const s = useStore(flow, (x) => x);
  const [busy, setBusy] = useState(false);
  const close = () => flow.set({ project: null, inst: null, plan: null, error: null });
  if (!s.project || !s.inst || !s.plan) return null;
  const { project, inst, plan } = s;
  const deps = plan.installs.filter((i) => i.reason === "dependency");
  const blocking = plan.conflicts.length > 0;
  return (
    <Modal
      open
      onClose={close}
      title={`Install ${project.title}`}
      width={560}
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            Cancel
          </Button>
          <Button
            variant={blocking || plan.unresolved.length ? "outline" : "primary"}
            loading={busy}
            onClick={async () => {
              setBusy(true);
              await run(project, inst, plan);
              setBusy(false);
              close();
            }}
          >
            {blocking || plan.unresolved.length ? "Install anyway" : `Install ${plan.installs.length} file${plan.installs.length === 1 ? "" : "s"}`}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="text-muted text-[13.5px]">
          Into <span className="text-fg font-medium">{inst.name}</span> (Minecraft {inst.gameVersion}).
        </div>
        {plan.warnings.map((w) => (
          <Callout key={w} tone="warn">
            {w}
          </Callout>
        ))}
        {plan.conflicts.map((c) => (
          <Callout key={c.projectId} tone="danger" title="Incompatible with an installed mod">
            {c.with} declares it doesn't work with {c.title}. Remove one of them, or the game may crash.
          </Callout>
        ))}
        {plan.unresolved.map((u) => (
          <Callout key={u.projectId} tone="danger" title={`Missing dependency: ${u.title}`}>
            {u.requiredBy} needs {u.title}, but Modrinth has no version of it for this Minecraft version and loader.
          </Callout>
        ))}
        <div>
          <div className="text-[12px] uppercase tracking-[0.08em] text-muted font-semibold mb-2">Will be installed</div>
          <div className="space-y-1.5">
            {plan.installs.map((i) => (
              <div key={i.projectId} className="flex items-center gap-2.5 text-[13.5px] rounded-lg bg-panel-2 border border-line px-3 py-2">
                <Icon.Download size={15} className="text-accent shrink-0" />
                <span className="font-medium truncate">{i.title}</span>
                <span className="text-faint truncate">{i.version.version_number}</span>
                <span className="flex-1" />
                {i.reason === "dependency" ? <Badge>Required by {i.requiredBy}</Badge> : <Badge tone="accent">Requested</Badge>}
              </div>
            ))}
          </div>
          {deps.length > 0 && <p className="text-[12.5px] text-faint mt-2">Required libraries are installed automatically from Modrinth. Files already in the folder are kept.</p>}
        </div>
        {plan.optional.length > 0 && (
          <div className="text-[12.5px] text-muted">
            Optional extras you can add later: {plan.optional.map((o) => o.title).join(", ")}.
          </div>
        )}
      </div>
    </Modal>
  );
}

export function LoadingInline({ label }: { label: string }) {
  return (
    <span className="inline-flex items-center gap-2 text-muted text-[13px]">
      <Spinner size={13} /> {label}
    </span>
  );
}
