import { useEffect, useRef, useState } from "react";
import { activeAccount } from "../services/accounts";
import { reportError } from "../services/app";
import { capeImage, capeState, chooseCape, loadCapes, type CapeChoice } from "../services/capes";
import { useStore } from "../services/store";
import { Button, Card, cx, Spinner } from "./components";
import { Icon } from "./icons";

const capeImages = new Map<string, Promise<HTMLImageElement>>();

function loadCapeImage(src: string): Promise<HTMLImageElement> {
  let p = capeImages.get(src);
  if (!p) {
    p = new Promise((resolve, reject) => {
      const img = new Image();
      img.decoding = "async";
      img.onload = () => resolve(img);
      img.onerror = () => {
        capeImages.delete(src);
        reject(new Error("cape image failed to load"));
      };
      img.src = src;
    });
    capeImages.set(src, p);
  }
  return p;
}

/**
 * The outside of the cape (Minecraft's layout: 10×16 at (1,1) of a 64×32 frame), drawn on a
 * canvas at the screen's real pixel density. Classic 64×32 capes stay pixel-sharp; HD capes
 * (128–1024 wide) are drawn smoothly at full detail instead of being blown up into blocks.
 * Animated capes play.
 */
function CapeFront({ cape, size = 6 }: { cape: CapeChoice; size?: number }) {
  const frames = Math.max(1, cape.frames ?? 1);
  const [frame, setFrame] = useState(0);
  const [img, setImg] = useState<HTMLImageElement | null>(null);
  const ref = useRef<HTMLCanvasElement>(null);
  const src = capeImage(cape);

  useEffect(() => {
    let live = true;
    loadCapeImage(src).then((i) => live && setImg(i), () => undefined);
    return () => {
      live = false;
    };
  }, [src]);

  useEffect(() => {
    if (frames <= 1) return;
    const t = setInterval(() => setFrame((f) => (f + 1) % frames), Math.max(20, cape.frameMs ?? 100));
    return () => clearInterval(t);
  }, [frames, cape.frameMs]);

  const w = 10 * size, h = 16 * size;
  useEffect(() => {
    const c = ref.current;
    if (!c || !img) return;
    const dpr = Math.min(3, window.devicePixelRatio || 1);
    c.width = Math.round(w * dpr);
    c.height = Math.round(h * dpr);
    const g = c.getContext("2d");
    if (!g) return;
    const u = img.naturalWidth / 64; // texels per cape "pixel"
    const hd = u > 1;
    g.imageSmoothingEnabled = hd;
    if (hd) g.imageSmoothingQuality = "high";
    g.clearRect(0, 0, c.width, c.height);
    g.drawImage(img, 1 * u, frame * 32 * u + 1 * u, 10 * u, 16 * u, 0, 0, c.width, c.height);
  }, [img, frame, w, h]);

  return (
    <div className="relative rounded-[3px] overflow-hidden shadow-[0_6px_18px_-6px_rgba(0,0,0,0.8)]" style={{ width: w, height: h }}>
      <canvas ref={ref} style={{ width: w, height: h, imageRendering: (cape.width ?? img?.naturalWidth ?? 64) > 64 ? "auto" : "pixelated" }} />
      {!img && <div className="absolute inset-0 bg-white/5 animate-pulse" />}
    </div>
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
            className={cx("w-[104px] h-[146px] rounded-xl border grid place-items-center text-[12px] text-muted cursor-pointer",
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
              className={cx("w-[104px] h-[146px] rounded-xl border flex flex-col items-center justify-center gap-1.5 cursor-pointer disabled:cursor-default",
                st.capeId === c.id ? "border-accent bg-accent/10" : "border-line bg-panel-2 hover:border-white/20", !c.allowed && "opacity-50")}
            >
              {busy === c.id ? <Spinner size={14} /> : <CapeFront cape={c} />}
              <span className="text-[11.5px] truncate max-w-[94px]">{c.name}</span>
              {(c.width ?? 64) > 64 && c.allowed && <span className="text-[9.5px] font-semibold tracking-wider text-accent">HD</span>}
              {!c.allowed && <span className="text-[10px] text-amber-200">Premium</span>}
            </button>
          ))}
        </div>
      )}
    </Card>
  );
}
