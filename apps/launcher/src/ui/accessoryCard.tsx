import { useEffect, useState } from "react";
import type { AccessoryKind } from "@bloom/shared";
import { activeAccount } from "../services/accounts";
import { reportError } from "../services/app";
import { accessoryImage, accessoryState, chooseAccessory, loadAccessories, type AccessoryChoice } from "../services/accessories";
import { useStore } from "../services/store";
import { Button, Card, cx, Spinner } from "./components";
import { Icon } from "./icons";

/** A pair of wings: the image for the left wing, mirrored for the right, on a tiny silhouette. */
function WingsPreview({ a }: { a: AccessoryChoice }) {
  const src = accessoryImage(a);
  return (
    <div className="relative h-[64px] w-[92px] grid place-items-center">
      {src && (
        <>
          <img src={src} alt="" className="absolute right-1/2 top-2 h-[40px] w-auto max-w-[46px] object-contain -scale-x-100 origin-right wing-l" draggable={false} />
          <img src={src} alt="" className="absolute left-1/2 top-2 h-[40px] w-auto max-w-[46px] object-contain wing-r" draggable={false} />
        </>
      )}
      <div className="relative h-[44px] w-[14px] rounded-[3px] bg-[#2a3a31]" />
    </div>
  );
}

function AuraPreview({ a }: { a: AccessoryChoice }) {
  return (
    <div className="relative h-[64px] w-[92px] grid place-items-center">
      <div className="aura-glow absolute inset-1 rounded-full" style={{ ["--aura" as string]: a.color }} />
      <div className="relative h-[44px] w-[14px] rounded-[3px] bg-[#2a3a31]" />
    </div>
  );
}

/** The 3D avatar as MOTION Client draws it (pre-rendered from the same model). */
function AvatarPreview({ a }: { a: AccessoryChoice }) {
  return (
    <div className="relative h-[64px] w-[92px] grid place-items-center">
      <div className="absolute inset-x-4 bottom-0 h-3 rounded-full blur-md opacity-60" style={{ background: a.color }} />
      <img src={`/avatars/${a.style}.png`} alt="" className="relative h-[64px] w-auto object-contain" draggable={false} />
    </div>
  );
}

function Picker({ kind, title, items, current, busy, onPick }: {
  kind: AccessoryKind;
  title: string;
  items: AccessoryChoice[];
  current: string | null;
  busy: string | null;
  onPick: (kind: AccessoryKind, id: string) => void;
}) {
  return (
    <div>
      <div className="text-[13px] font-medium text-muted mb-2">{title}</div>
      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          onClick={() => onPick(kind, "")}
          className={cx("w-[104px] h-[112px] rounded-xl border grid place-items-center text-[12px] text-muted cursor-pointer",
            current === null ? "border-accent bg-accent/10" : "border-line bg-panel-2 hover:border-white/20")}
        >
          {busy === `${kind}:` ? <Spinner size={14} /> : "None"}
        </button>
        {items.map((a) => (
          <button
            key={a.id}
            type="button"
            disabled={!a.allowed || busy !== null}
            onClick={() => onPick(kind, a.id)}
            title={a.allowed ? a.name : `${a.name} · Premium`}
            className={cx("w-[104px] h-[112px] rounded-xl border flex flex-col items-center justify-center gap-1 cursor-pointer disabled:cursor-default",
              current === a.id ? "border-accent bg-accent/10" : "border-line bg-panel-2 hover:border-white/20", !a.allowed && "opacity-50")}
          >
            {busy === `${kind}:${a.id}` ? <Spinner size={14} /> : kind === "wings" ? <WingsPreview a={a} /> : kind === "avatar" ? <AvatarPreview a={a} /> : <AuraPreview a={a} />}
            <span className="text-[12px] truncate max-w-[94px]">{a.name}</span>
            {!a.allowed && <span className="text-[11px] text-amber-200">Premium</span>}
          </button>
        ))}
        {items.length === 0 && <div className="text-[13px] text-faint self-center">None published yet.</div>}
      </div>
    </div>
  );
}

export function AccessoryCard() {
  const st = useStore(accessoryState, (s) => s);
  const account = activeAccount();
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    if (account && st.status === "idle") void loadAccessories();
  }, [account?.id]);

  const pick = async (kind: AccessoryKind, id: string) => {
    setBusy(`${kind}:${id}`);
    try {
      await chooseAccessory(kind, id);
    } catch (e) {
      reportError(`Couldn't change your ${kind}`, e);
    } finally {
      setBusy(null);
    }
  };

  const wings = st.accessories.filter((a) => a.kind === "wings");
  const auras = st.accessories.filter((a) => a.kind === "aura");
  const avatars = st.accessories.filter((a) => a.kind === "avatar");
  return (
    <Card className="p-5 mb-6">
      <div className="flex items-center gap-2">
        <Icon.Sparkle size={17} className="text-accent" />
        <h3 className="font-semibold flex-1">Wings, aura &amp; 3D avatar</h3>
        {st.status === "loading" && <Spinner size={14} />}
      </div>
      <p className="text-[13px] text-muted mt-1">
        Shown by MOTION Client: you and other MOTION players see them in game (Minecraft 1.21.11). Wings fold when you stand still and spread as you move. A 3D avatar replaces your player model, at normal player height.
      </p>
      {!account ? (
        <p className="text-[13px] text-faint mt-3">Sign in with Microsoft to choose wings, an aura and an avatar.</p>
      ) : st.status === "error" ? (
        <div className="mt-3 flex items-center gap-3">
          <span className="text-[13px] text-warn">{st.error}</span>
          <Button size="sm" onClick={() => void loadAccessories()}>Try again</Button>
        </div>
      ) : (
        <div className="space-y-5 mt-4">
          <Picker kind="wings" title="Wings" items={wings} current={st.wings} busy={busy} onPick={(k, id) => void pick(k, id)} />
          <Picker kind="aura" title="Aura" items={auras} current={st.aura} busy={busy} onPick={(k, id) => void pick(k, id)} />
          <Picker kind="avatar" title="3D avatar" items={avatars} current={st.avatar} busy={busy} onPick={(k, id) => void pick(k, id)} />
        </div>
      )}
    </Card>
  );
}
