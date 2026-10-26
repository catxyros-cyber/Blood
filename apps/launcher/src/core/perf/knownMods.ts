/**
 * Performance mods MOTION knows how to recommend, and well-known conflicts
 * between rendering/optimization mods. Availability for a specific Minecraft
 * version and loader is always checked live against Modrinth — nothing here
 * claims a mod exists for a version or promises an FPS gain.
 */

export interface PerfModInfo {
  /** Modrinth slug. */
  slug: string;
  /** Mod id(s) as they appear in the jar metadata. */
  modIds: string[];
  title: string;
  category: "rendering" | "logic" | "memory" | "startup" | "networking" | "overlay";
  what: string;
  /** Presets that recommend this mod. */
  presets: ("balanced" | "competitive" | "extreme" | "potato")[];
}

export const PERF_MODS: PerfModInfo[] = [
  {
    slug: "sodium",
    modIds: ["sodium"],
    title: "Sodium",
    category: "rendering",
    what: "Replaces the chunk renderer with a much faster one. Usually the single biggest improvement.",
    presets: ["potato", "balanced", "competitive", "extreme"],
  },
  {
    slug: "lithium",
    modIds: ["lithium"],
    title: "Lithium",
    category: "logic",
    what: "Optimizes game logic (physics, AI, chunk ticking) without changing behaviour. Helps most in singleplayer.",
    presets: ["potato", "balanced", "competitive", "extreme"],
  },
  {
    slug: "ferrite-core",
    modIds: ["ferritecore"],
    title: "FerriteCore",
    category: "memory",
    what: "Reduces memory usage, which means fewer garbage-collection stutters.",
    presets: ["potato", "balanced", "competitive", "extreme"],
  },
  {
    slug: "entityculling",
    modIds: ["entityculling"],
    title: "Entity Culling",
    category: "rendering",
    what: "Skips rendering entities and block entities you can't see.",
    presets: ["potato", "competitive", "extreme"],
  },
  {
    slug: "immediatelyfast",
    modIds: ["immediatelyfast"],
    title: "ImmediatelyFast",
    category: "rendering",
    what: "Speeds up immediate-mode rendering such as text, entities and the HUD.",
    presets: ["potato", "balanced", "competitive", "extreme"],
  },
  {
    slug: "modernfix",
    modIds: ["modernfix"],
    title: "ModernFix",
    category: "startup",
    what: "Faster launch and lower memory use, especially in larger modpacks.",
    presets: ["balanced", "extreme"],
  },
  {
    slug: "moreculling",
    modIds: ["moreculling"],
    title: "More Culling",
    category: "rendering",
    what: "Culls more hidden block faces (leaves, item frames…). Requires Sodium on recent versions.",
    presets: ["extreme", "potato"],
  },
  {
    slug: "krypton",
    modIds: ["krypton"],
    title: "Krypton",
    category: "networking",
    what: "Optimizes the networking stack. Mainly useful on busy servers.",
    presets: ["competitive"],
  },
  {
    slug: "sodium-extra",
    modIds: ["sodium-extra"],
    title: "Sodium Extra",
    category: "overlay",
    what: "Adds extra video options and the optional in-game FPS overlay MOTION configures.",
    presets: [],
  },
];

export interface KnownConflict {
  a: string[];
  b: string[];
  severity: "error" | "warning";
  message: string;
  /** Suggested resolution. */
  fix: string;
}

/** Mod-id based rules. `a`/`b` match ids from jar metadata (or "optifine" for OptiFine jars, detected by name). */
export const KNOWN_CONFLICTS: KnownConflict[] = [
  {
    a: ["sodium"],
    b: ["optifine"],
    severity: "error",
    message: "Sodium and OptiFine both replace the renderer and cannot run together.",
    fix: "Keep Sodium (plus Iris for shaders) and remove OptiFine.",
  },
  {
    a: ["sodium"],
    b: ["embeddium", "rubidium", "magnesium"],
    severity: "error",
    message: "Two versions of the Sodium renderer are installed.",
    fix: "Keep only one renderer: Sodium on Fabric/Quilt/NeoForge (1.20.6+), Embeddium on Forge.",
  },
  {
    a: ["sodium", "embeddium", "rubidium"],
    b: ["canvas"],
    severity: "error",
    message: "Canvas and Sodium-based renderers replace the same rendering pipeline.",
    fix: "Keep one renderer.",
  },
  {
    a: ["iris", "oculus"],
    b: ["optifine"],
    severity: "error",
    message: "Iris and OptiFine both implement shader support and conflict.",
    fix: "Use Iris (with Sodium) for shaders and remove OptiFine.",
  },
  {
    a: ["starlight"],
    b: ["phosphor"],
    severity: "error",
    message: "Starlight and Phosphor both rewrite the lighting engine.",
    fix: "Keep Starlight (or neither on Minecraft 1.20+, where vanilla lighting was rewritten).",
  },
];

/** OptiFine isn't a normal mod (no metadata); detect its jar by name. */
export function looksLikeOptifine(fileName: string): boolean {
  return /optifine/i.test(fileName) && !/optifabric/i.test(fileName);
}
