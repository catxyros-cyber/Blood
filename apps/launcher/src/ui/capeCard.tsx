import { useEffect, useState } from "react";
import { activeAccount } from "../services/accounts";
import { reportError } from "../services/app";
import { capeImage, capeState, chooseCape, loadCapes, type CapeChoice } from "../services/capes";
import { useStore } from "../services/store";
import { Button, Card, cx, Spinner } from "./components";
import { Icon } from "./icons";

/** The front of the cape (Minecraft's layout: 10×16 at (1,1) of a 64×32 texture), scaled up crisply. */
function CapeFront({ cape, size = 4 }: { cape: CapeChoice; size?: number }) {
  return (
    <div
      className="rounded-sm"
      style={{
        width: 10 * size,
        height: 16 * size,
        backgroundImage: `url("${capeImage(cape)}")`,
        backgroundSize: `${64 * size}px ${32 * size}px`,
        backgroundPosition: `${-1 * size}px ${-1 * size}px`,
        imageRendering: "pixelated",
      }}
    />
  );
}

export function CapeCard() {
  const st = useStore(capeState, (s) => s);
  const account = activeAccount();
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    if (account && st.status === "idle") void loadCapes();
  }, [account?.id]);

  const pick = async (id: string) => {
    setBusy(id);
    try {
      await chooseCape(id);
    } catch (e) {
      reportError("Couldn't change your cape", e);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Card className="p-5 mb-6">
      <div className="flex items-center gap-2">
        <Icon.Crown size={17} className="text-accent" />
        <h3 className="font-semibold flex-1">Cape</h3>
        {st.status === "loading" && <Spinner size={14} />}
      </div>
      <p className="text-[13px] text-muted mt-1">BLOOD capes are shown by BLOOD Client: you and other BLOOD players see them in game. Players on other launchers see your normal Minecraft cape.</p>
      {!account ? (
        <p className="text-[13px] text-faint mt-3">Sign in with Microsoft to choose a cape.</p>
      ) : st.status === "error" ? (
        <div className="mt-3 flex items-center gap-3">
          <span className="text-[13px] text-warn">{st.error}</span>
          <Button size="sm" onClick={() => void loadCapes()}>Try again</Button>
        </div>
      ) : st.status === "ready" && st.capes.length === 0 ? (
        <p className="text-[13px] text-faint mt-3">No capes have been published yet.</p>
      ) : (
        <div className="flex flex-wrap gap-3 mt-4">
          <button
            type="button"
            onClick={() => void pick("")}
            className={cx("w-[84px] h-[110px] rounded-xl border grid place-items-center text-[12px] text-muted cursor-pointer",
              st.capeId === null ? "border-accent bg-accent/10" : "border-line bg-panel-2 hover:border-white/20")}
          >
            {busy === "" ? <Spinner size={14} /> : "No cape"}
          </button>
          {st.capes.map((c) => (
            <button
              key={c.id}
              type="button"
              disabled={!c.allowed || busy !== null}
              onClick={() => void pick(c.id)}
              title={c.allowed ? c.name : `${c.name} · BLOOD Premium`}
              className={cx("w-[84px] h-[110px] rounded-xl border flex flex-col items-center justify-center gap-1.5 cursor-pointer disabled:cursor-default",
                st.capeId === c.id ? "border-accent bg-accent/10" : "border-line bg-panel-2 hover:border-white/20", !c.allowed && "opacity-50")}
            >
              {busy === c.id ? <Spinner size={14} /> : <CapeFront cape={c} />}
              <span className="text-[11.5px] truncate max-w-[76px]">{c.name}</span>
              {!c.allowed && <span className="text-[10px] text-amber-200">Premium</span>}
            </button>
          ))}
        </div>
      )}
    </Card>
  );
}
