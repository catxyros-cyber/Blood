import { OptionsFile } from "./options";

export type PresetId = "potato" | "balanced" | "competitive" | "extreme" | "custom";

/** Video settings BLOOD can manage, in version-independent form. */
export interface VideoSettings {
  renderDistance: number;
  simulationDistance: number;
  graphics: "fast" | "fancy";
  smoothLighting: boolean;
  particles: "all" | "decreased" | "minimal";
  entityShadows: boolean;
  clouds: "off" | "fast" | "fancy";
  biomeBlend: number;
  mipmapLevels: number;
  entityDistance: number;
  maxFps: number;
  vsync: boolean;
  viewBobbing: boolean;
}

export interface PresetDef {
  id: Exclude<PresetId, "custom">;
  name: string;
  tagline: string;
  premium: boolean;
  video: VideoSettings;
}

/** 260 is Minecraft's "Unlimited" frame-rate value. */
export const UNLIMITED_FPS = 260;

export const PRESETS: PresetDef[] = [
  {
    id: "potato",
    name: "Potato",
    tagline: "Lowest practical settings for older laptops and integrated graphics.",
    premium: false,
    video: {
      renderDistance: 5,
      simulationDistance: 5,
      graphics: "fast",
      smoothLighting: false,
      particles: "minimal",
      entityShadows: false,
      clouds: "off",
      biomeBlend: 0,
      mipmapLevels: 0,
      entityDistance: 0.5,
      maxFps: 120,
      vsync: false,
      viewBobbing: true,
    },
  },
  {
    id: "balanced",
    name: "Balanced",
    tagline: "Good-looking game with sensible limits. A safe default for most PCs.",
    premium: false,
    video: {
      renderDistance: 10,
      simulationDistance: 8,
      graphics: "fancy",
      smoothLighting: true,
      particles: "decreased",
      entityShadows: true,
      clouds: "fast",
      biomeBlend: 2,
      mipmapLevels: 4,
      entityDistance: 1.0,
      maxFps: UNLIMITED_FPS,
      vsync: false,
      viewBobbing: true,
    },
  },
  {
    id: "competitive",
    name: "Competitive",
    tagline: "Clear visuals and fast input: minimal particles, no clouds, no view bobbing.",
    premium: true,
    video: {
      renderDistance: 8,
      simulationDistance: 6,
      graphics: "fast",
      smoothLighting: true,
      particles: "minimal",
      entityShadows: false,
      clouds: "off",
      biomeBlend: 0,
      mipmapLevels: 2,
      entityDistance: 0.75,
      maxFps: UNLIMITED_FPS,
      vsync: false,
      viewBobbing: false,
    },
  },
  {
    id: "extreme",
    name: "Extreme FPS",
    tagline: "Aggressive (but fully reversible) settings that trade looks for frame rate.",
    premium: true,
    video: {
      renderDistance: 4,
      simulationDistance: 5,
      graphics: "fast",
      smoothLighting: false,
      particles: "minimal",
      entityShadows: false,
      clouds: "off",
      biomeBlend: 0,
      mipmapLevels: 0,
      entityDistance: 0.5,
      maxFps: UNLIMITED_FPS,
      vsync: false,
      viewBobbing: false,
    },
  },
];

export interface OptionsFormat {
  /** 1.16+: graphicsMode 0/1/2. Before: fancyGraphics true/false. */
  graphicsMode: boolean;
  /** 1.18+: simulation distance exists. */
  simulationDistance: boolean;
  /** 1.19+: ao is a boolean; before it was 0/1/2. */
  aoBoolean: boolean;
  /** 1.19+: renderClouds is a quoted string. */
  quotedClouds: boolean;
}

/** Decide how options.txt is written for a given Minecraft release date. */
export function optionsFormatFor(releaseTime: string | undefined): OptionsFormat {
  const t = releaseTime ? Date.parse(releaseTime) : Date.now();
  return {
    graphicsMode: t >= Date.parse("2020-06-23"),
    simulationDistance: t >= Date.parse("2021-11-30"),
    aoBoolean: t >= Date.parse("2022-06-07"),
    quotedClouds: t >= Date.parse("2020-06-23"),
  };
}

export interface AppliedChange {
  key: string;
  label: string;
  from: string | undefined;
  to: string;
}

const LABELS: Record<string, string> = {
  renderDistance: "Render distance",
  simulationDistance: "Simulation distance",
  graphicsMode: "Graphics",
  fancyGraphics: "Graphics",
  ao: "Smooth lighting",
  particles: "Particles",
  entityShadows: "Entity shadows",
  renderClouds: "Clouds",
  biomeBlendRadius: "Biome blend",
  mipmapLevels: "Mipmap levels",
  entityDistanceScaling: "Entity distance",
  maxFps: "Max framerate",
  enableVsync: "VSync",
  bobView: "View bobbing",
};

/** Translate video settings into options.txt key/values for a given format. */
export function toOptionValues(v: VideoSettings, fmt: OptionsFormat, current?: OptionsFile): [string, string][] {
  const out: [string, string][] = [];
  out.push(["renderDistance", String(v.renderDistance)]);
  if (fmt.simulationDistance) out.push(["simulationDistance", String(v.simulationDistance)]);
  if (fmt.graphicsMode) out.push(["graphicsMode", v.graphics === "fast" ? "0" : "1"]);
  else out.push(["fancyGraphics", v.graphics === "fancy" ? "true" : "false"]);

  // Respect whatever format the existing file uses for `ao`.
  const aoCurrent = current?.get("ao");
  const aoBool = aoCurrent !== undefined ? aoCurrent === "true" || aoCurrent === "false" : fmt.aoBoolean;
  out.push(["ao", aoBool ? String(v.smoothLighting) : v.smoothLighting ? "2" : "0"]);

  out.push(["particles", v.particles === "all" ? "0" : v.particles === "decreased" ? "1" : "2"]);
  out.push(["entityShadows", String(v.entityShadows)]);
  const clouds = v.clouds === "off" ? "false" : v.clouds === "fast" ? "fast" : "true";
  out.push(["renderClouds", fmt.quotedClouds ? `"${clouds}"` : clouds]);
  out.push(["biomeBlendRadius", String(v.biomeBlend)]);
  out.push(["mipmapLevels", String(v.mipmapLevels)]);
  out.push(["entityDistanceScaling", v.entityDistance.toFixed(2).replace(/0$/, "")]);
  out.push(["maxFps", String(v.maxFps)]);
  out.push(["enableVsync", String(v.vsync)]);
  out.push(["bobView", String(v.viewBobbing)]);
  return out;
}

/** Apply video settings to an options.txt, returning the new text and a checklist of what changed. */
export function applyVideoSettings(
  optionsText: string | null,
  v: VideoSettings,
  fmt: OptionsFormat,
): { text: string; changes: AppliedChange[] } {
  const file = new OptionsFile(optionsText ?? "");
  const changes: AppliedChange[] = [];
  for (const [key, value] of toOptionValues(v, fmt, file)) {
    const from = file.get(key);
    if (from !== value) {
      changes.push({ key, label: LABELS[key] ?? key, from, to: value });
      file.set(key, value);
    }
  }
  return { text: file.toString(), changes };
}

/** Read the current video settings back from options.txt (for the Custom editor). */
export function readVideoSettings(optionsText: string | null, fallback: VideoSettings): VideoSettings {
  const f = new OptionsFile(optionsText ?? "");
  const num = (k: string, d: number) => {
    const n = Number(f.get(k)?.replace(/"/g, ""));
    return Number.isFinite(n) ? n : d;
  };
  const bool = (k: string, d: boolean) => {
    const v = f.get(k);
    return v === undefined ? d : v === "true";
  };
  const gm = f.get("graphicsMode");
  const graphics = gm !== undefined ? (gm === "0" ? "fast" : "fancy") : f.get("fancyGraphics") === "false" ? "fast" : fallback.graphics;
  const ao = f.get("ao");
  const clouds = f.get("renderClouds")?.replace(/"/g, "");
  const particles = f.get("particles");
  return {
    renderDistance: num("renderDistance", fallback.renderDistance),
    simulationDistance: num("simulationDistance", fallback.simulationDistance),
    graphics,
    smoothLighting: ao === undefined ? fallback.smoothLighting : ao === "true" || ao === "1" || ao === "2",
    particles: particles === "0" ? "all" : particles === "1" ? "decreased" : particles === "2" ? "minimal" : fallback.particles,
    entityShadows: bool("entityShadows", fallback.entityShadows),
    clouds: clouds === "false" ? "off" : clouds === "fast" ? "fast" : clouds === "true" ? "fancy" : fallback.clouds,
    biomeBlend: num("biomeBlendRadius", fallback.biomeBlend),
    mipmapLevels: num("mipmapLevels", fallback.mipmapLevels),
    entityDistance: num("entityDistanceScaling", fallback.entityDistance),
    maxFps: num("maxFps", fallback.maxFps),
    vsync: bool("enableVsync", fallback.vsync),
    viewBobbing: bool("bobView", fallback.viewBobbing),
  };
}
