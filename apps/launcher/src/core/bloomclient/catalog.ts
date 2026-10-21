/**
 * What the BLOOD Client mod offers, mirrored for the launcher's Customize page.
 * Keep in sync with apps/client-mod (SkyboxCatalog, BuiltInProfiles, BloomSettings).
 */

export interface SkyboxInfo {
  id: string;
  name: string;
  description: string;
  /** Preview image bundled with the launcher (public/bloomclient/sky/<id>.png). */
  preview: string;
}

export const BUILT_IN_SKYBOXES: SkyboxInfo[] = [
  ["crimson_dusk", "Crimson Dusk", "A low red sun under heavy crimson cloud. BLOOD's default day sky."],
  ["blood_moon", "Blood Moon", "A huge red moon over a near-black sky with faint stars. BLOOD's default night sky."],
  ["cherry_blossom", "Cherry Blossom Sky", "Warm pink dusk over a cherry grove, with soft drifting clouds."],
  ["pink_sunset", "Pink Sunset", "A glowing low sun melting into rose and violet."],
  ["sakura_night", "Sakura Night", "Deep indigo night with a faint pink horizon and a bright starfield."],
  ["starry_night", "Starry Night", "Clear dark sky with dense, crisp stars."],
  ["galaxy", "Galaxy", "A colourful band of the galaxy arching overhead."],
  ["fantasy_clouds", "Fantasy Clouds", "Towering pastel clouds in a bright, dreamy sky."],
  ["aurora", "Aurora", "Green and violet aurora curtains above a dark horizon."],
  ["dreamy_pastel", "Dreamy Pastel", "Soft lavender, peach and mint gradients."],
  ["clear_blue", "Clear Blue Sky", "A crisp, cloudless summer sky."],
].map(([id, name, description]) => ({ id: id!, name: name!, description: description!, preview: `/bloomclient/sky/${id}.png` }));

export const VANILLA_SKY = "none";
export const CUSTOM_PREFIX = "custom:";

export interface ProfileInfo {
  id: string;
  name: string;
  description: string;
  builtIn: boolean;
}

export const BUILT_IN_PROFILES: ProfileInfo[] = [
  ["blood_moon", "Blood Moon", "BLOOD's signature look: crimson dusk, a blood-red moon, drifting red petals and a red haze."],
  ["cherry_blossom", "Cherry Blossom", "Pink skies, drifting cherry petals and soft pink haze."],
  ["competitive", "Competitive", "Clean visuals for PvP: no decorative particles or skybox, clear block outline, low fire."],
  ["minimal", "Minimal", "Vanilla-like visuals with a light, tidy HUD."],
  ["snowfall", "Snowfall", "Gentle decorative snow everywhere under a cool, pale sky."],
  ["rainy_day", "Rainy Day", "Steady decorative rain, grey-blue sky and closer fog."],
  ["fantasy", "Fantasy", "Pastel clouds, aurora nights, fireflies and petals everywhere."],
  ["night_sky", "Night Sky", "Always night (client-side only): a galaxy sky, dense stars and fireflies."],
  ["cinematic", "Cinematic", "Golden-hour skies, rich haze and dense ambient particles for screenshots and videos."],
].map(([id, name, description]) => ({ id: id!, name: name!, description: description!, builtIn: true }));

/** In-game menu themes (Theme.java); the same ids and names as the launcher's themes. */
export const MENU_THEMES = [
  { id: "crimson", name: "Crimson" },
  { id: "obsidian", name: "Obsidian" },
  { id: "ash", name: "Ash" },
  { id: "ember", name: "Ember" },
  { id: "mono", name: "Monochrome" },
] as const;

export const DEFAULT_PROFILE = "blood_moon";
export const DEFAULT_SKY_DAY = "crimson_dusk";
export const DEFAULT_SKY_NIGHT = "blood_moon";

export const QUALITY_PRESETS = [
  { id: "low", name: "Low" },
  { id: "medium", name: "Medium" },
  { id: "high", name: "High" },
] as const;

/** Keys offered for opening the in-game menu (Minecraft key names as stored in options.txt). */
export const MENU_KEYS: { key: string; label: string }[] = [
  { key: "key.keyboard.right.shift", label: "Right Shift" },
  { key: "key.keyboard.right.control", label: "Right Ctrl" },
  { key: "key.keyboard.right.alt", label: "Right Alt" },
  { key: "key.keyboard.insert", label: "Insert" },
  { key: "key.keyboard.home", label: "Home" },
  { key: "key.keyboard.grave.accent", label: "` (backtick)" },
  { key: "key.keyboard.b", label: "B" },
  { key: "key.keyboard.m", label: "M" },
  { key: "key.keyboard.unknown", label: "Not bound" },
];

export function menuKeyLabel(key: string | null): string {
  if (!key) return "Right Shift";
  return MENU_KEYS.find((k) => k.key === key)?.label ?? key.replace(/^key\.(keyboard|mouse)\./, "").replace(/\./g, " ");
}
