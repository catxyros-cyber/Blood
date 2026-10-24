/**
 * MOTION Mods: everything the MOTION Mods page can switch on and off, with where each switch
 * actually lives. Nothing here is decorative — every module maps to one of:
 *
 *  - "hud":     a MOTION Client HUD widget (visibility + options in the active HUD layout,
 *               stored in the "hud" block of config/bloomclient/settings.json)
 *  - "setting": a MOTION Client setting key in the same file (e.g. the custom crosshair)
 *  - "perf":    a performance mod from Modrinth (installed / enabled / disabled as a file)
 *  - "vanilla": a Minecraft option in options.txt (e.g. Toggle Sprint)
 *
 * Keep the HUD part in sync with apps/client-mod (HudWidgetType, BloomSettings).
 */

export type ModuleCategory = "hud" | "performance" | "visuals" | "utility";
export type Anchor =
  | "top_left"
  | "top_center"
  | "top_right"
  | "middle_left"
  | "center"
  | "middle_right"
  | "bottom_left"
  | "bottom_center"
  | "bottom_right";

export interface WidgetOption {
  id: string;
  label: string;
  default: boolean;
}

export interface HudWidgetInfo {
  id: string;
  name: string;
  description: string;
  anchor: Anchor;
  x: number;
  y: number;
  visibleByDefault: boolean;
  options: WidgetOption[];
}

const o = (id: string, label: string, def: boolean): WidgetOption => ({ id, label, default: def });

/** Mirrors HudWidgetType.java (ids, defaults and options). */
export const HUD_WIDGETS: HudWidgetInfo[] = [
  { id: "fps", name: "FPS", description: "Current frames per second, optionally colour-coded.", anchor: "top_left", x: 4, y: 4, visibleByDefault: true,
    options: [o("label", 'Show "FPS" label', true), o("colorCoded", "Colour by speed", false)] },
  { id: "frametime", name: "Frame time", description: "Average and slowest recent frame in milliseconds, with a small graph.", anchor: "top_left", x: 4, y: 18, visibleByDefault: false,
    options: [o("graph", "Show graph", true), o("worst", "Show slowest frame", true)] },
  { id: "memory", name: "Memory", description: "Java memory in use and the RAM allocated to the game (-Xmx).", anchor: "top_left", x: 4, y: 32, visibleByDefault: false,
    options: [o("percent", "Show percentage", true), o("bar", "Show bar", false)] },
  { id: "coords", name: "Coordinates", description: "Your X, Y and Z position, with optional facing, dimension and biome.", anchor: "top_left", x: 4, y: 46, visibleByDefault: true,
    options: [o("compact", "One line", false), o("facing", "Show facing direction", false), o("dimension", "Show dimension", false), o("biome", "Show biome", false), o("nether", "Show Nether/Overworld equivalent", false)] },
  { id: "direction", name: "Direction", description: "A compass strip or the direction you're facing.", anchor: "top_center", x: 0, y: 4, visibleByDefault: false,
    options: [o("compass", "Compass strip", true), o("degrees", "Show degrees", false)] },
  { id: "keystrokes", name: "Keystrokes", description: "Movement keys and mouse buttons, lit while held.", anchor: "bottom_right", x: -4, y: -4, visibleByDefault: false,
    options: [o("mouse", "Show mouse buttons", true), o("space", "Show jump bar", true), o("cps", "Show CPS on mouse buttons", true)] },
  { id: "cps", name: "CPS", description: "Clicks per second for the left and right mouse buttons.", anchor: "top_left", x: 4, y: 74, visibleByDefault: false,
    options: [o("right", "Include right clicks", true)] },
  { id: "armor", name: "Armor", description: "Equipped armor with remaining durability, as numbers, percentages or bars.", anchor: "middle_right", x: -4, y: 0, visibleByDefault: false,
    options: [o("durability", "Show durability", true), o("percent", "Durability as %", false), o("bars", "Durability bars", false), o("held", "Include held item", true), o("horizontal", "Horizontal", false)] },
  { id: "potions", name: "Potion effects", description: "Active effects with icons, level and a live countdown. Ending effects flash.", anchor: "middle_left", x: 4, y: 0, visibleByDefault: false,
    options: [o("icons", "Show icons", true), o("time", "Show time left", true), o("level", "Show level (II, III…)", true), o("compact", "Compact layout", false), o("hideBeneficial", "Hide beneficial effects", false), o("hideHarmful", "Hide harmful effects", false), o("hideInfinite", "Hide infinite effects", false)] },
  { id: "clock", name: "Clock & session", description: "Your computer's clock and how long you've been in this world.", anchor: "top_right", x: -4, y: 4, visibleByDefault: false,
    options: [o("clock", "Show clock", true), o("h24", "24-hour clock", true), o("seconds", "Show seconds", false), o("session", "Show session timer", true)] },
  { id: "held", name: "Held item", description: "Main-hand and off-hand items with durability, so you know when a tool is about to break.", anchor: "bottom_left", x: 4, y: -4, visibleByDefault: false,
    options: [o("offhand", "Include off-hand", true), o("percent", "Durability as %", false), o("bar", "Durability bar", true), o("count", "Show item counts", true)] },
  { id: "status", name: "Sprint & sneak", description: "Shows when you're sprinting or sneaking, and whether Minecraft's Toggle Sprint/Sneak is holding it.", anchor: "top_left", x: 4, y: 88, visibleByDefault: false,
    options: [o("sprint", "Sprint", true), o("sneak", "Sneak", true), o("mode", 'Show "Toggled" / "Held"', true), o("flying", "Flying", false)] },
  { id: "info", name: "Info panel", description: "Ping, server address, world day and in-game time in one small panel.", anchor: "top_right", x: -4, y: 32, visibleByDefault: false,
    options: [o("ping", "Ping", true), o("server", "Server address", false), o("day", "World day", true), o("time", "In-game time", false)] },
  { id: "music", name: "Now playing", description: "Current Windows media track, artist, album artwork and synced local lyrics.", anchor: "bottom_left", x: 4, y: -42, visibleByDefault: false,
    options: [o("album", "Show album", true), o("lyrics", "Show synced lyrics", true)] },
  { id: "target", name: "Target player", description: "Crosshair target's name, health, armor, and durability values received from the server.", anchor: "top_center", x: 0, y: 28, visibleByDefault: false,
    options: [o("name", "Show player name", true), o("health", "Show health", true), o("armor", "Show armor items", true), o("durability", "Show durability", true), o("percent", "Durability as %", false), o("bars", "Durability bars", true), o("distance", "Show distance", false)] },
  { id: "low_health", name: "Low health warning", description: "A compact warning when your health is low (30% by default).", anchor: "top_center", x: 0, y: 84, visibleByDefault: true,
    options: [o("value", "Show health value", true), o("bar", "Show health bar", true), o("pulse", "Pulse while critical", true), o("early", "Warn at 50% health", false), o("critical", "Warn only below 20%", false)] },
];

export const CROSSHAIR_STYLES = [
  { value: "cross", label: "Cross" },
  { value: "cross_dot", label: "Cross + dot" },
  { value: "t", label: "T-shape" },
  { value: "dot", label: "Dot" },
  { value: "square", label: "Square" },
] as const;

/** Crosshair settings (BloomSettings.CROSSHAIR_*), with defaults and ranges. */
export const CROSSHAIR = {
  enabled: { key: "crosshair.enabled", default: false },
  style: { key: "crosshair.style", default: "cross" },
  size: { key: "crosshair.size", default: 5, min: 1, max: 20, step: 1 },
  gap: { key: "crosshair.gap", default: 2, min: 0, max: 12, step: 1 },
  thickness: { key: "crosshair.thickness", default: 1, min: 1, max: 5, step: 1 },
  color: { key: "crosshair.color", default: "#ffffff" },
  opacity: { key: "crosshair.opacity", default: 1, min: 0.1, max: 1, step: 0.05 },
  outline: { key: "crosshair.outline", default: true },
} as const;

/** Vanilla effects the potion HUD can hide individually (setting "hud.potions.hidden"). */
export const POTION_EFFECTS: { id: string; name: string }[] = [
  ["speed", "Speed"], ["slowness", "Slowness"], ["haste", "Haste"], ["mining_fatigue", "Mining Fatigue"], ["strength", "Strength"],
  ["jump_boost", "Jump Boost"], ["nausea", "Nausea"], ["regeneration", "Regeneration"], ["resistance", "Resistance"],
  ["fire_resistance", "Fire Resistance"], ["water_breathing", "Water Breathing"], ["invisibility", "Invisibility"], ["blindness", "Blindness"],
  ["night_vision", "Night Vision"], ["hunger", "Hunger"], ["weakness", "Weakness"], ["poison", "Poison"], ["wither", "Wither"],
  ["health_boost", "Health Boost"], ["absorption", "Absorption"], ["saturation", "Saturation"], ["glowing", "Glowing"], ["levitation", "Levitation"],
  ["luck", "Luck"], ["unluck", "Bad Luck"], ["slow_falling", "Slow Falling"], ["conduit_power", "Conduit Power"], ["dolphins_grace", "Dolphin's Grace"],
  ["bad_omen", "Bad Omen"], ["hero_of_the_village", "Hero of the Village"], ["darkness", "Darkness"], ["trial_omen", "Trial Omen"],
  ["raid_omen", "Raid Omen"], ["wind_charged", "Wind Charged"], ["weaving", "Weaving"], ["oozing", "Oozing"], ["infested", "Infested"],
].map(([id, name]) => ({ id: id!, name: name! }));
export const POTION_HIDDEN_KEY = "hud.potions.hidden";

export interface SettingToggle {
  /** Every key is written together; the module counts as on when the first key is on. */
  keys: string[];
  /** Value written when switched on / off. */
  on: string | number | boolean;
  off: string | number | boolean;
  default: string | number | boolean;
}

export interface ModuleInfo {
  id: string;
  name: string;
  description: string;
  category: ModuleCategory;
  kind: "hud" | "setting" | "perf" | "vanilla";
  /** HUD widget id (kind "hud"). */
  widget?: string;
  /** Setting switched by the module (kind "setting"). */
  setting?: SettingToggle;
  /** Modrinth slug (kind "perf"); details live in PERF_MODS. */
  perfSlug?: string;
  /** options.txt key (kind "vanilla"). */
  vanillaOption?: string;
  /** Keywords for search. */
  keywords?: string[];
  /** Short note shown under the description (e.g. a trade-off). */
  note?: string;
}

export const MODULES: ModuleInfo[] = [
  ...HUD_WIDGETS.map<ModuleInfo>((w) => ({ id: `hud.${w.id}`, name: w.name, description: w.description, category: "hud", kind: "hud", widget: w.id })),
  {
    id: "crosshair",
    name: "Custom crosshair",
    description: "Replace the crosshair with your own size, gap, thickness, colour and style.",
    category: "hud",
    kind: "setting",
    setting: { keys: [CROSSHAIR.enabled.key], on: true, off: false, default: false },
    keywords: ["aim", "reticle"],
    note: "While it's on, the crosshair attack indicator isn't drawn. Set Attack Indicator to Hotbar in Minecraft's options to keep it.",
  },
  { id: "perf.sodium", name: "Sodium", description: "", category: "performance", kind: "perf", perfSlug: "sodium" },
  { id: "perf.lithium", name: "Lithium", description: "", category: "performance", kind: "perf", perfSlug: "lithium" },
  { id: "perf.ferritecore", name: "FerriteCore", description: "", category: "performance", kind: "perf", perfSlug: "ferrite-core" },
  { id: "perf.immediatelyfast", name: "ImmediatelyFast", description: "", category: "performance", kind: "perf", perfSlug: "immediatelyfast" },
  { id: "perf.entityculling", name: "Entity Culling", description: "", category: "performance", kind: "perf", perfSlug: "entityculling" },
  { id: "perf.modernfix", name: "ModernFix", description: "", category: "performance", kind: "perf", perfSlug: "modernfix" },
  {
    id: "visual.outline",
    name: "Block outline",
    description: "A clearer outline (crimson by default) around the block you're looking at.",
    category: "visuals",
    kind: "setting",
    setting: { keys: ["outline.enabled"], on: true, off: false, default: false },
    keywords: ["selection", "highlight"],
  },
  {
    id: "visual.lowfire",
    name: "Low fire",
    description: "Lowers the burning overlay so it covers less of your view.",
    category: "visuals",
    kind: "setting",
    setting: { keys: ["fire.offset"], on: 0.3, off: 0, default: 0 },
    keywords: ["fire", "overlay"],
  },
  {
    id: "visual.sky",
    name: "Custom skybox",
    description: "MOTION Client's skyboxes (pick one on the Visuals page). Off shows the vanilla sky.",
    category: "visuals",
    kind: "setting",
    setting: { keys: ["sky.enabled"], on: true, off: false, default: true },
    note: "Skipped automatically while a shader pack is active.",
  },
  {
    id: "visual.particles",
    name: "Ambient particles",
    description: "Decorative petals, leaves and fireflies. Visual only and off in the Clean PvP and Performance presets.",
    category: "visuals",
    kind: "setting",
    setting: { keys: ["leaves.enabled", "petals.enabled", "fireflies.enabled"], on: true, off: false, default: true },
    keywords: ["petals", "leaves", "fireflies"],
  },
  {
    id: "util.togglesprint",
    name: "Toggle Sprint",
    description: "Minecraft's own Toggle Sprint option: tap the sprint key instead of holding it.",
    category: "utility",
    kind: "vanilla",
    vanillaOption: "toggleSprint",
    keywords: ["sprint"],
    note: "Changes Minecraft's options.txt, so close the game first.",
  },
  {
    id: "util.togglesneak",
    name: "Toggle Sneak",
    description: "Minecraft's own Toggle Sneak option: tap the sneak key instead of holding it.",
    category: "utility",
    kind: "vanilla",
    vanillaOption: "toggleCrouch",
    keywords: ["crouch", "shift"],
    note: "Changes Minecraft's options.txt, so close the game first.",
  },
  {
    id: "util.hidef3",
    name: "Hide HUD with F3",
    description: "Hide MOTION widgets while the F3 debug screen is open.",
    category: "utility",
    kind: "setting",
    setting: { keys: ["hud.hideWithDebug"], on: true, off: false, default: true },
  },
  {
    id: "util.hudbg",
    name: "Widget backgrounds",
    description: "Dark translucent panels behind HUD widgets for readability.",
    category: "utility",
    kind: "setting",
    setting: { keys: ["hud.background"], on: true, off: false, default: true },
  },
];

// ---------------------------------------------------------------- HUD block editing

export interface WidgetJson {
  visible?: boolean;
  anchor?: Anchor;
  x?: number;
  y?: number;
  scale?: number;
  textColor?: string;
  backgroundColor?: string;
  backgroundOpacity?: number;
  options?: Record<string, boolean>;
}

export interface LayoutJson {
  id: string;
  name: string;
  widgets: Record<string, WidgetJson>;
}

export interface HudBlock {
  activeLayout: string;
  layouts: LayoutJson[];
}

export function widgetInfo(id: string): HudWidgetInfo | undefined {
  return HUD_WIDGETS.find((w) => w.id === id);
}

/** The PvP layout the game ships (HudLayouts.preset("pvp")): visibility differences only. */
const PVP_VISIBLE = new Set(["fps", "cps", "keystrokes", "armor", "potions"]);

/** A valid "hud" block, even when the file has none yet (the game then uses its defaults). */
export function readHud(raw: unknown): HudBlock {
  const fallback: HudBlock = {
    activeLayout: "default",
    layouts: [
      { id: "default", name: "Default", widgets: {} },
      { id: "pvp", name: "PvP", widgets: {} },
    ],
  };
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return fallback;
  const r = raw as { activeLayout?: unknown; layouts?: unknown };
  if (!Array.isArray(r.layouts)) return fallback;
  const layouts: LayoutJson[] = [];
  for (const l of r.layouts) {
    if (!l || typeof l !== "object") continue;
    const lo = l as { id?: unknown; name?: unknown; widgets?: unknown };
    if (typeof lo.id !== "string" || !/^[a-z0-9][a-z0-9_-]{0,47}$/.test(lo.id)) continue;
    const widgets = lo.widgets && typeof lo.widgets === "object" && !Array.isArray(lo.widgets) ? (lo.widgets as Record<string, WidgetJson>) : {};
    layouts.push({ id: lo.id, name: typeof lo.name === "string" ? lo.name : lo.id, widgets: { ...widgets } });
  }
  if (layouts.length === 0) return fallback;
  const active = typeof r.activeLayout === "string" && layouts.some((l) => l.id === r.activeLayout) ? r.activeLayout : layouts[0]!.id;
  return { activeLayout: active, layouts };
}

export function activeLayout(hud: HudBlock): LayoutJson {
  return hud.layouts.find((l) => l.id === hud.activeLayout) ?? hud.layouts[0]!;
}

/** Effective visibility of a widget in a layout (stored value, else the game's default for that layout). */
export function widgetVisible(layout: LayoutJson, id: string): boolean {
  const stored = layout.widgets[id]?.visible;
  if (typeof stored === "boolean") return stored;
  if (layout.id === "pvp") return PVP_VISIBLE.has(id);
  return widgetInfo(id)?.visibleByDefault ?? false;
}

export function widgetOption(layout: LayoutJson, widgetId: string, optionId: string): boolean {
  const stored = layout.widgets[widgetId]?.options?.[optionId];
  if (typeof stored === "boolean") return stored;
  return widgetInfo(widgetId)?.options.find((x) => x.id === optionId)?.default ?? false;
}

export interface HudPatch {
  /** Per-widget changes merged into the active layout. */
  widgets?: Record<string, WidgetJson>;
  /** Hide every widget not mentioned in `widgets` (used by presets). */
  hideOthers?: boolean;
}

/** Returns a new HUD block with the patch applied to the active layout. Unknown widget ids are rejected. */
export function patchHud(hud: HudBlock, patch: HudPatch): HudBlock {
  const layout = activeLayout(hud);
  const widgets: Record<string, WidgetJson> = { ...layout.widgets };
  for (const [id, change] of Object.entries(patch.widgets ?? {})) {
    if (!widgetInfo(id)) throw new Error(`Unknown HUD widget ${id}`);
    const prev = widgets[id] ?? {};
    const options = change.options ? { ...(prev.options ?? {}), ...change.options } : prev.options;
    widgets[id] = { ...prev, ...change, ...(options ? { options } : {}) };
  }
  if (patch.hideOthers) {
    for (const w of HUD_WIDGETS) {
      if (!patch.widgets?.[w.id]) widgets[w.id] = { ...(widgets[w.id] ?? {}), visible: false };
    }
  }
  return {
    activeLayout: hud.activeLayout,
    layouts: hud.layouts.map((l) => (l.id === layout.id ? { ...l, widgets } : l)),
  };
}

// ---------------------------------------------------------------- presets

export interface ModPreset {
  id: "performance" | "minimal" | "full";
  name: string;
  description: string;
  hud: HudPatch;
  settings: Record<string, string | number | boolean>;
  /** MOTION Client visual profile to select. */
  profile?: string;
  /** Performance mods to install (Modrinth slugs), when compatible. */
  perfMods?: string[];
}

export const MOD_PRESETS: ModPreset[] = [
  {
    id: "performance",
    name: "Performance",
    description: "Installs the core performance mods, keeps only an FPS counter and switches MOTION Client to its Clean PvP look (no decorative particles or skybox).",
    hud: { hideOthers: true, widgets: { fps: { visible: true, anchor: "top_left", x: 4, y: 4, options: { colorCoded: true } } } },
    settings: { "hud.enabled": true },
    profile: "competitive",
    perfMods: ["sodium", "lithium", "ferrite-core", "immediatelyfast", "entityculling"],
  },
  {
    id: "minimal",
    name: "Minimal HUD",
    description: "FPS and one-line coordinates in the top-left corner. Nothing else on screen.",
    hud: {
      hideOthers: true,
      widgets: {
        fps: { visible: true, anchor: "top_left", x: 4, y: 4 },
        coords: { visible: true, anchor: "top_left", x: 4, y: 20, options: { compact: true } },
      },
    },
    settings: { "hud.enabled": true },
  },
  {
    id: "full",
    name: "Full HUD",
    description: "Every widget, including now playing, target-player status and low-health alerts, arranged around the screen edges.",
    hud: {
      hideOthers: true,
      widgets: {
        fps: { visible: true, anchor: "top_left", x: 4, y: 4 },
        memory: { visible: true, anchor: "top_left", x: 4, y: 20 },
        coords: { visible: true, anchor: "top_left", x: 4, y: 36, options: { compact: false } },
        cps: { visible: true, anchor: "top_left", x: 4, y: 74 },
        status: { visible: true, anchor: "top_left", x: 4, y: 90 },
        direction: { visible: true, anchor: "top_center", x: 0, y: 4 },
        clock: { visible: true, anchor: "top_right", x: -4, y: 4 },
        info: { visible: true, anchor: "top_right", x: -4, y: 32 },
        frametime: { visible: true, anchor: "top_right", x: -4, y: 84 },
        potions: { visible: true, anchor: "middle_left", x: 4, y: 0 },
        armor: { visible: true, anchor: "middle_right", x: -4, y: 0 },
        held: { visible: true, anchor: "bottom_left", x: 4, y: -4 },
        keystrokes: { visible: true, anchor: "bottom_right", x: -4, y: -4 },
        music: { visible: true, anchor: "bottom_left", x: 4, y: -42 },
        target: { visible: true, anchor: "top_center", x: 0, y: 28 },
        low_health: { visible: true, anchor: "top_center", x: 0, y: 84 },
      },
    },
    settings: { "hud.enabled": true },
  },
];
