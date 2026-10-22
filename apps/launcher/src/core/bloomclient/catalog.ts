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
  // Fantasy Skybox FREE, converted by apps/client-mod/tools/import_fantasy_skies.py.
  ["fs_classic_day_1", "Cumulus Day", "Big painted cumulus clouds in a deep blue sky."],
  ["fs_classic_day_2", "Bright Cumulus", "Towering white clouds in a bright, airy sky."],
  ["fs_classic_day_3", "Deep Blue Day", "Rich blue sky with soft clouds along the horizon."],
  ["fs_classic_day_4", "Teal Breeze", "Light teal sky with drifting cloud puffs."],
  ["fs_classic_day_5", "Drifting Clouds", "Small clouds drifting across a clear blue sky."],
  ["fs_classic_day_6", "Puffy Clouds", "A clear sky full of round, puffy clouds."],
  ["fs_classic_night_1", "Blue Night", "A painted blue night with long, soft clouds."],
  ["fs_classic_night_2", "Cloud Islands", "Night sky with little floating cloud islands."],
  ["fs_classic_night_3", "Teal Night", "A glowing teal night horizon under dark clouds."],
  ["fs_002_day", "Azure Day", "Clear azure sky with wispy clouds."],
  ["fs_002_night", "Azure Night", "Deep blue night with a faint glow on the horizon."],
  ["fs_002_sunrise", "Azure Sunrise", "Gold sunrise under a bright blue sky."],
  ["fs_002_sunset", "Azure Sunset", "Warm red sunset fading into blue."],
  ["fs_002_rainy", "Azure Rain", "Grey rain clouds over a calm horizon."],
  ["fs_002_snowy", "Azure Snow", "Pale, cold sky for snowy days."],
  ["fs_003_day", "Cloudscape Day", "Fluffy anime-style clouds in a bright summer sky."],
  ["fs_003_night", "Cloudscape Night", "Moonlit clouds in a glowing blue night."],
  ["fs_003_sunrise", "Cloudscape Sunrise", "Golden clouds lit by a blazing sunrise."],
  ["fs_003_sunset", "Cloudscape Sunset", "Orange-gold clouds over a warm sunset."],
  ["fs_003_rainy", "Cloudscape Rain", "Heavy grey storm clouds."],
  ["fs_003_snowy", "Cloudscape Snow", "Soft white clouds in a cold blue sky."],
  ["fs_013_day", "Sunbeam Day", "Light rays breaking through blue cloud."],
  ["fs_013_night", "Crimson Night", "A dark, maroon night sky. Great for BLOOD."],
  ["fs_013_sunrise", "Ember Sunrise", "A fiery sun rising over dark hills."],
  ["fs_013_sunset", "Crimson Sunset", "A deep red sunset. BLOOD's default day sky."],
  ["fs_013_rainy", "Dark Storm", "Near-black storm clouds."],
  ["fs_013_snowy", "Whiteout Snow", "A bright white snowy sky."],
  ["fs_017_day", "Wisp Day", "Clear sky with long white wisps."],
  ["fs_017_night", "Wisp Night", "Dark teal night with faint golden clouds."],
  ["fs_017_sunrise", "Wisp Sunrise", "A soft yellow sunrise on the horizon."],
  ["fs_017_sunset", "Violet Sunset", "Purple sky over a burning red horizon."],
  ["fs_017_rainy", "Wisp Rain", "Dark rain clouds rolling in."],
  ["fs_017_snowy", "Wisp Snow", "Pale icy sky for snowy weather."],
  // Cloudy Skyboxes by Screaming Brain Studios (CC0).
  ["sbs_silver_morning", "Silver Morning", "Soft grey cloud over a rosy horizon with a pale sun."],
  ["sbs_golden_haze", "Golden Haze", "Bright golden cloud banks lit by a warm sun."],
  ["sbs_amber_dusk", "Amber Dusk", "Hazy amber clouds over a low evening sun."],
  ["sbs_summer_blue", "Summer Blue", "Deep summer blue with crisp white clouds."],
  ["sbs_fair_weather", "Fair Weather", "Light blue sky with scattered fair-weather clouds."],
  ["sbs_clear_noon", "Clear Noon", "Clear blue sky with a bright noon horizon."],
  ["sbs_lavender_clouds", "Lavender Clouds", "Lavender and pink clouds drifting over a soft sun."],
  ["sbs_soft_daylight", "Soft Daylight", "Pale, even daylight under thin cloud."],
  ["sbs_storm_gold", "Storm Gold", "Dark storm clouds over a burning gold horizon."],
  ["sbs_overcast", "Overcast", "Flat grey-green overcast. Calm and moody."],
  ["sbs_violet_night", "Violet Night", "Purple night clouds under a bright moon."],
  ["sbs_slate_dusk", "Slate Dusk", "Dark slate dusk with a small, distant sun."],
  ["sbs_sand_fog", "Sand Fog", "Warm, dusty fog with a hidden sun."],
  ["sbs_emerald_haze", "Emerald Haze", "An eerie green haze, great for spooky worlds."],
  ["sbs_midnight_glow", "Midnight Glow", "Near-black night with a single glowing light on the horizon."],
  ["sbs_cloud_tide", "Cloud Tide", "Dramatic blue-white clouds sweeping overhead."],
  ["sbs_plum_sunset", "Plum Sunset", "Plum and magenta clouds over a red sunset."],
  ["sbs_steel_horizon", "Steel Horizon", "Steel-blue cloud layers over a bright horizon."],
  ["sbs_crimson_storm", "Crimson Storm", "A red sky under heavy crimson cloud. Made for BLOOD."],
  ["sbs_whiteout", "Whiteout", "Bright white fog with a soft glowing sun."],
  ["sbs_fire_sunset", "Fire Sunset", "Orange fire-lit clouds over a blazing sunset."],
  ["sbs_rust_sunset", "Rust Sunset", "Rust-brown clouds and a deep orange sunset."],
  ["sbs_rose_swirl", "Rose Swirl", "Swirling pink and white clouds."],
  ["sbs_pale_clouds", "Pale Clouds", "Pale cream clouds in soft light."],
  ["sbs_dream_mist", "Dream Mist", "Lavender-grey mist with gentle cloud."],
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
  ["competitive", "Clean PvP", "Clear visibility for PvP: crisp, slightly punchier colours, thinner fog, no decorative particles or skybox, a clear block outline and low fire."],
  ["minimal", "Minimal", "Vanilla-like visuals with a light, tidy HUD."],
  ["snowfall", "Snowfall", "Gentle decorative snow everywhere under a cool, pale sky."],
  ["rainy_day", "Rainy Day", "Steady decorative rain, grey-blue sky and closer fog."],
  ["fantasy", "Fantasy", "Pastel clouds, aurora nights, fireflies and petals everywhere."],
  ["night_sky", "Night Sky", "Always night (client-side only): a galaxy sky, dense stars and fireflies."],
  ["cinematic", "Cinematic", "Soft golden-hour colours, atmospheric haze, film-like glow and vignette for screenshots and videos."],
  ["performance", "Performance", "Lightweight: plain sky, no particles or glow, half-size textures, colour grading off. For low-end PCs."],
  ["vanilla", "Vanilla", "Plain Minecraft visuals: BLOOD's sky, shaders, fog changes, particles and outline are all off. The HUD still works."],
].map(([id, name, description]) => ({ id: id!, name: name!, description: description!, builtIn: true }));

/** In-game menu themes (Theme.java); the same ids and names as the launcher's themes. */
export const MENU_THEMES = [
  { id: "crimson", name: "Classic BLOOD" },
  { id: "blood_moon", name: "Blood Moon" },
  { id: "pumpkin_night", name: "Pumpkin Night" },
  { id: "haunted_forest", name: "Haunted Forest" },
  { id: "midnight_halloween", name: "Midnight Halloween" },
  { id: "ghostfire", name: "Ghostfire" },
  { id: "spiderweb", name: "Spiderweb" },
  { id: "crimson_eclipse", name: "Crimson Eclipse" },
  { id: "obsidian", name: "Obsidian" },
  { id: "ash", name: "Ash" },
  { id: "ember", name: "Ember" },
  { id: "mono", name: "Monochrome" },
] as const;

export const DEFAULT_PROFILE = "blood_moon";
export const DEFAULT_SKY_DAY = "fs_013_sunset";
export const DEFAULT_SKY_NIGHT = "fs_013_night";

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

/** Skies removed from BLOOD, mapped to the closest built-in (same table as SkyboxCatalog.migrate). */
const RENAMED_SKIES: Record<string, string> = {
  "crimson_dusk": "fs_013_sunset",
  "blood_moon": "fs_013_night",
  "cherry_blossom": "fs_002_sunset",
  "pink_sunset": "fs_017_sunset",
  "sakura_night": "fs_classic_night_2",
  "starry_night": "fs_002_night",
  "galaxy": "fs_classic_night_3",
  "fantasy_clouds": "fs_classic_day_1",
  "aurora": "fs_017_night",
  "dreamy_pastel": "fs_classic_day_4",
  "clear_blue": "fs_classic_day_5",
  "hw_blood_moon": "fs_013_night",
  "hw_halloween_night": "fs_classic_night_1",
  "hw_pumpkin_sky": "fs_003_sunset",
  "hw_haunted_forest": "fs_017_night",
  "hw_midnight_purple": "fs_002_night",
  "hw_ghostfire": "fs_013_sunset",
  "hw_eclipse": "fs_013_rainy",
  "hw_blood_storm": "fs_013_rainy",
  "hw_monochrome_night": "fs_017_rainy"
};

export function migrateSky(id: string): string {
  return RENAMED_SKIES[id] ?? id;
}
