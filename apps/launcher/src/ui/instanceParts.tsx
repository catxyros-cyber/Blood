import { useEffect, useMemo, useState } from "react";
import type { Instance } from "../core/instances/model";
import { LOADERS, pickLoaderVersion, type LoaderId, type LoaderVersion } from "../core/loaders/loaders";
import type { ManifestVersion } from "../core/mojang/types";
import { app, errorMessage, navigate, reportError } from "../services/app";
import { createInstance, updateInstance } from "../services/instances";
import { useStore } from "../services/store";
import { availableLoaderVersions, loaderSupports, minecraftVersions } from "../services/versions";
import { Button, Callout, cx, Field, Modal, Select, Spinner, TextInput, Toggle } from "./components";
import { Icon } from "./icons";

/** A small hash for deterministic pixel noise. */
function hash(x: number, y: number, seed: number): number {
  let h = (x * 374761393 + y * 668265263 + seed * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}

function shade(hex: number, k: number): string {
  const c = (v: number) => Math.max(0, Math.min(255, Math.round(v * k)));
  return `rgb(${c((hex >> 16) & 255)},${c((hex >> 8) & 255)},${c(hex & 255)})`;
}

/**
 * A 16×16 block face for installations without their own icon, by loader: a grass block side
 * for vanilla, light planks for Fabric, woven purple for Quilt, dark planks for NeoForge, stone
 * for Forge. The name seeds the noise, so two installations never look identical.
 */
function blockPixels(loader: LoaderId, seed: number): string[] {
  const px: string[] = [];
  for (let y = 0; y < 16; y++) {
    for (let x = 0; x < 16; x++) {
      const n = hash(x, y, seed);
      let c: string;
      if (loader === "vanilla") {
        const grassDepth = 3 + (hash(x, 99, seed) > 0.6 ? 1 : 0) + (hash(x, 98, seed) > 0.85 ? 1 : 0);
        c = y < grassDepth ? shade(0x5d9b3a, 0.82 + n * 0.3) : shade(0x8a5a3b, 0.78 + n * 0.32);
      } else if (loader === "fabric" || loader === "neoforge") {
        const base = loader === "fabric" ? 0xc8a46a : 0x5b3b22;
        const row = Math.floor(y / 4);
        const seam = y % 4 === 3 || x === ((row * 7 + seed) & 15);
        c = shade(base, seam ? 0.68 : 0.88 + n * 0.2 - (y % 4 === 0 ? 0.04 : 0));
      } else if (loader === "quilt") {
        const weave = ((x >> 1) + (y >> 1)) % 2 === 0;
        c = shade(weave ? 0x8f5fd6 : 0x6f45b0, 0.86 + n * 0.22);
      } else {
        c = shade(0x7e7e80, 0.8 + n * 0.3);
      }
      px.push(c);
    }
  }
  return px;
}

const iconCache = new Map<string, string>();

function blockIconUrl(loader: LoaderId, name: string): string {
  const key = `${loader}:${name}`;
  let url = iconCache.get(key);
  if (!url) {
    let seed = 7;
    for (const ch of name) seed = (seed * 31 + ch.charCodeAt(0)) | 0;
    const px = blockPixels(loader, seed);
    const rects = px.map((c, i) => `<rect x="${i % 16}" y="${Math.floor(i / 16)}" width="1" height="1" fill="${c}"/>`).join("");
    url = `data:image/svg+xml;utf8,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" shape-rendering="crispEdges">${rects}</svg>`)}`;
    iconCache.set(key, url);
  }
  return url;
}

export function InstanceIcon({ inst, size = 44, className }: { inst: Pick<Instance, "icon" | "name" | "loader">; size?: number; className?: string }) {
  return (
    <img
      src={inst.icon || blockIconUrl(inst.loader, inst.name)}
      alt=""
      width={size}
      height={size}
      draggable={false}
      className={cx("rounded-md object-cover shrink-0", !inst.icon && "pixelated", className)}
      style={{ width: size, height: size, imageRendering: inst.icon ? undefined : "pixelated" }}
    />
  );
}

export function loaderLabel(inst: Pick<Instance, "loader" | "loaderVersion">): string {
  const l = LOADERS.find((x) => x.id === inst.loader)?.name ?? inst.loader;
  return inst.loader === "vanilla" ? "Vanilla" : `${l}${inst.loaderVersion ? ` ${inst.loaderVersion}` : ""}`;
}

/** Version + loader pickers shared by "New installation" and the installation settings. */
export function VersionLoaderPicker({
  gameVersion,
  loader,
  loaderVersion,
  onChange,
}: {
  gameVersion: string;
  loader: LoaderId;
  loaderVersion?: string;
  onChange: (v: { gameVersion: string; loader: LoaderId; loaderVersion?: string }) => void;
}) {
  const showSnapshots = useStore(app, (s) => s.settings.showSnapshots);
  const [versions, setVersions] = useState<ManifestVersion[] | null>(null);
  const [snapshots, setSnapshots] = useState(showSnapshots);
  const [loaderVersions, setLoaderVersions] = useState<LoaderVersion[] | null>(null);
  const [loaderError, setLoaderError] = useState<string | null>(null);
  const [versionError, setVersionError] = useState<string | null>(null);

  useEffect(() => {
    minecraftVersions()
      .then((v) => {
        setVersions(v);
        if (!gameVersion) onChange({ gameVersion: v.find((x) => x.type === "release")?.id ?? v[0]!.id, loader, loaderVersion });
      })
      .catch((e) => setVersionError(errorMessage(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    setLoaderVersions(null);
    setLoaderError(null);
    if (loader === "vanilla" || !gameVersion) return;
    let cancelled = false;
    (async () => {
      try {
        if (!(await loaderSupports(loader, gameVersion))) {
          if (!cancelled) setLoaderError(`${LOADERS.find((l) => l.id === loader)?.name} isn't available for Minecraft ${gameVersion}.`);
          return;
        }
        const lv = await availableLoaderVersions(loader, gameVersion);
        if (cancelled) return;
        setLoaderVersions(lv);
        if (!loaderVersion || !lv.some((x) => x.version === loaderVersion)) onChange({ gameVersion, loader, loaderVersion: pickLoaderVersion(lv).version });
      } catch (e) {
        if (!cancelled) setLoaderError(errorMessage(e));
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loader, gameVersion]);

  const options = useMemo(
    () =>
      (versions ?? [])
        .filter((v) => snapshots || v.type === "release" || v.id === gameVersion)
        .map((v) => ({ value: v.id, label: v.type === "release" ? v.id : `${v.id} (${v.type.replace("old_", "")})` })),
    [versions, snapshots, gameVersion],
  );

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3">
        <Field label="Minecraft version">
          {versions ? (
            <Select value={gameVersion} onChange={(v) => onChange({ gameVersion: v, loader, loaderVersion: undefined })} options={options} />
          ) : versionError ? (
            <div className="text-danger text-[13px]">{versionError}</div>
          ) : (
            <div className="h-9 grid place-items-center text-muted">
              <Spinner />
            </div>
          )}
        </Field>
        <Field label="Mod loader">
          <Select
            value={loader}
            onChange={(v) => onChange({ gameVersion, loader: v, loaderVersion: undefined })}
            options={LOADERS.map((l) => ({ value: l.id, label: l.supported ? l.name : `${l.name} (coming soon)`, disabled: !l.supported }))}
          />
        </Field>
      </div>
      <Toggle checked={snapshots} onChange={setSnapshots} label="Show snapshots and old versions" />
      {(loader === "forge" || loader === "neoforge") && <p className="text-[13px] text-faint -mt-1">{LOADERS.find((l) => l.id === loader)?.note}</p>}
      {loader !== "vanilla" && (
        <Field label={`${LOADERS.find((l) => l.id === loader)?.name} version`}>
          {loaderError ? (
            <Callout tone="warn">{loaderError}</Callout>
          ) : loaderVersions ? (
            <Select
              value={loaderVersion ?? ""}
              onChange={(v) => onChange({ gameVersion, loader, loaderVersion: v })}
              options={loaderVersions.slice(0, 60).map((l) => ({ value: l.version, label: `${l.version}${l.recommended ? " (recommended)" : l.stable ? "" : " (beta)"}` }))}
            />
          ) : (
            <div className="h-9 grid place-items-center text-muted">
              <Spinner />
            </div>
          )}
        </Field>
      )}
    </div>
  );
}

export function RenameModal({ inst, open, onClose }: { inst: Instance; open: boolean; onClose: () => void }) {
  const [name, setName] = useState(inst.name);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) setName(inst.name);
  }, [open, inst.name]);
  async function save() {
    setBusy(true);
    try {
      await updateInstance(inst.id, { name: name.trim() });
      onClose();
    } catch (e) {
      reportError("Couldn't rename", e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Rename installation"
      width={420}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" onClick={save} loading={busy} disabled={!name.trim() || name.trim() === inst.name}>
            Save
          </Button>
        </>
      }
    >
      <div onClick={(e) => e.stopPropagation()}>
        <Field label="Name">
          <TextInput value={name} onChange={setName} autoFocus onEnter={() => void save()} />
        </Field>
        <p className="text-[13px] text-faint mt-2">The folder on disk keeps its name, so mods and worlds aren't moved.</p>
      </div>
    </Modal>
  );
}

export function NewInstanceModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [name, setName] = useState("");
  const [sel, setSel] = useState<{ gameVersion: string; loader: LoaderId; loaderVersion?: string }>({ gameVersion: "", loader: "fabric" });
  const [busy, setBusy] = useState(false);
  const autoName = sel.gameVersion ? `${sel.loader === "vanilla" ? "Minecraft" : LOADERS.find((l) => l.id === sel.loader)?.name} ${sel.gameVersion}` : "";
  const ready = sel.gameVersion && (sel.loader === "vanilla" || sel.loaderVersion);

  async function create() {
    setBusy(true);
    try {
      const inst = await createInstance({ name: name.trim() || autoName, gameVersion: sel.gameVersion, loader: sel.loader, loaderVersion: sel.loader === "vanilla" ? undefined : sel.loaderVersion });
      onClose();
      setName("");
      navigate({ page: "instance", id: inst.id });
    } catch (e) {
      reportError("Couldn't create the installation", e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="New installation"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" icon={<Icon.Plus size={16} />} onClick={create} loading={busy} disabled={!ready}>
            Create
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Name">
          <TextInput value={name} onChange={setName} placeholder={autoName || "My installation"} autoFocus />
        </Field>
        <VersionLoaderPicker gameVersion={sel.gameVersion} loader={sel.loader} loaderVersion={sel.loaderVersion} onChange={setSel} />
        <p className="text-[13px] text-faint leading-relaxed">
          Each installation has its own mods, resource packs, shaders, worlds and settings. Game files are downloaded from Mojang when you first press Play.
        </p>
      </div>
    </Modal>
  );
}
