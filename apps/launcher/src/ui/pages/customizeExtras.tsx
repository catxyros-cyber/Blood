/**
 * Customize → In-game background and Capes & accessories (per installation). Both write MOTION
 * Client's settings.json; a running game picks changes up within seconds.
 */

import { useEffect, useState, type ReactNode } from "react";
import type { Instance } from "../../core/instances/model";
import { reportError, toast } from "../../services/app";
import { menuBackgroundImage, setMenuBackgroundImage } from "../../services/bloomClient";
import { native } from "../../services/native";
import { Button, Card, cx, Slider, Toggle } from "../components";
import { Icon } from "../icons";

type Write = (settings: Record<string, string | number | boolean>) => Promise<void>;
type Get = <T>(key: string, fallback: T) => T;

/** The styles MOTION Client offers (ids match ui.menuBackground in the mod). */
const STYLES: { id: string; name: string; swatch: string; ink: string }[] = [
  { id: "vanilla", name: "Minecraft's own", swatch: "linear-gradient(135deg,#6d8fb5,#3b5a3a 60%,#2a3f2a)", ink: "#fff" },
  { id: "red", name: "Fully red", swatch: "linear-gradient(180deg,#d0001c,#8a0012)", ink: "#fff" },
  { id: "black", name: "Fully black", swatch: "#000", ink: "#fff" },
  { id: "white", name: "Fully white", swatch: "#fff", ink: "#111" },
  { id: "green", name: "Motion green", swatch: "linear-gradient(180deg,#0e2a1a 40%,#0b5a30 100%)", ink: "#c6ff3d" },
  { id: "custom", name: "Your image", swatch: "repeating-linear-gradient(45deg,#1b231e 0 8px,#141a16 8px 16px)", ink: "#fff" },
];

function Section({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <Card className="p-5">
      <div className="mb-4">
        <h2 className="font-display text-[16px] font-semibold">{title}</h2>
        {subtitle && <p className="text-[13px] text-muted mt-0.5">{subtitle}</p>}
      </div>
      {children}
    </Card>
  );
}

export function MenuBackgroundSection({ inst, get, write }: { inst: Instance; get: Get; write: Write }) {
  const mode = get<string>("ui.menuBackground", "vanilla");
  const [image, setImage] = useState<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = async () => {
    const p = await menuBackgroundImage(inst).catch(() => null);
    setImage(p);
    setPreview(p ? await native.readDataUrl(p, 40 * 1024 * 1024).catch(() => null) : null);
  };
  useEffect(() => {
    void refresh();
  }, [inst.id]);

  const pick = async () => {
    const picked = await native.pickFiles({ title: "Choose a background image", filters: [["Images", ["png", "jpg", "jpeg"]]] });
    if (!picked.length) return;
    setBusy(true);
    try {
      await setMenuBackgroundImage(inst, picked[0]!);
      await write({ "ui.menuBackground": "custom" });
      await refresh();
      toast({ kind: "success", title: "Background set", message: "Minecraft's menus now show your image. If the game is open, it updates within a few seconds." });
    } catch (e) {
      reportError("Couldn't use that image", e);
    } finally {
      setBusy(false);
    }
  };

  const inWorld = get<number>("ui.menuBackgroundInWorld", 0.85);
  return (
    <Section
      title="In-game background"
      subtitle="What Minecraft's own screens show behind them: the title screen, pause and option menus, and loading screens. Needs Minecraft 1.21.11."
    >
      <div className="grid grid-cols-3 xl:grid-cols-6 gap-2.5">
        {STYLES.map((st) => {
          const active = mode === st.id;
          const bg = st.id === "custom" && preview ? `center / cover no-repeat url("${preview}")` : st.swatch;
          return (
            <button
              key={st.id}
              type="button"
              onClick={() => (st.id === "custom" && !image ? void pick() : void write({ "ui.menuBackground": st.id }))}
              className={cx(
                "rounded-xl border overflow-hidden text-left cursor-pointer transition-all",
                active ? "border-accent ring-2 ring-accent/30" : "border-line hover:border-line-strong",
              )}
            >
              <div className="h-16 grid place-items-center text-[11px] font-semibold" style={{ background: bg, color: st.ink }}>
                {st.id === "custom" && !image && <span className="flex items-center gap-1"><Icon.Plus size={12} /> Choose…</span>}
              </div>
              <div className="px-2.5 py-1.5 text-[12px] flex items-center gap-1 bg-panel-2">
                {active && <Icon.Check size={12} className="text-accent" />}
                {st.name}
              </div>
            </button>
          );
        })}
      </div>
      {mode === "white" && <p className="text-[12px] text-faint mt-2.5">Minecraft draws white text on its menus, so some text is hard to read on pure white.</p>}
      <div className="flex items-center gap-3 mt-4">
        <Button size="sm" icon={<Icon.Image size={14} />} disabled={busy} onClick={() => void pick()}>
          {image ? "Change image" : "Use my own image"}
        </Button>
        <span className="text-[12px] text-faint">PNG or JPG, any size (4K is fine). It fills the screen and is cropped evenly.</span>
      </div>
      <div className="grid grid-cols-2 gap-x-8 gap-y-4 mt-5 pt-4 border-t border-line">
        <div>
          <div className="font-medium text-[14px]">Over the world</div>
          <div className="text-[13px] text-muted mb-2">How much the background covers the world behind pause and option menus while you play.</div>
          <Slider value={inWorld} min={0} max={1} step={0.05} format={(v) => `${Math.round(v * 100)}%`} onChange={(v) => void write({ "ui.menuBackgroundInWorld": v })} />
        </div>
        <Toggle
          checked={get<boolean>("ui.menuBackgroundLoading", true) === true}
          onChange={(v) => void write({ "ui.menuBackgroundLoading": v })}
          label="On loading screens"
          description="World loading and Minecraft's resource-loading screen use it too."
        />
      </div>
    </Section>
  );
}

export function CapeMotionSection({ get, write }: { get: Get; write: Write }) {
  return (
    <Section title="Capes, wings & auras" subtitle="How cosmetics move and which ones you see in game. Pick your own cape, wings and aura at the top of this page.">
      <div className="grid grid-cols-2 gap-x-8 gap-y-4">
        <Toggle
          checked={get<boolean>("capes.smooth", true) === true}
          onChange={(v) => void write({ "capes.smooth": v })}
          label="Smooth cape motion"
          description="Capes hang still while you stand and drift back gently as you move, instead of snapping."
        />
        <div>
          <div className="font-medium text-[14px]">Cape sway</div>
          <div className="text-[13px] text-muted mb-2">How far capes swing back while moving.</div>
          <Slider value={get<number>("capes.sway", 0.55)} min={0.1} max={1} step={0.05} format={(v) => `${Math.round(v * 100)}%`} onChange={(v) => void write({ "capes.sway": v })} />
        </div>
        <Toggle checked={get<boolean>("accessories.wings", true) === true} onChange={(v) => void write({ "accessories.wings": v })} label="Show wings" description="Wings MOTION players picked, yours too in third person." />
        <Toggle checked={get<boolean>("accessories.auras", true) === true} onChange={(v) => void write({ "accessories.auras": v })} label="Show auras" description="Glows and particle trails. Yours is hidden in first person." />
      </div>
    </Section>
  );
}
