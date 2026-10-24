package dev.bloom.client.core.settings;

import dev.bloom.client.core.sky.SkyboxCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.bloom.client.core.settings.Category.*;
import static dev.bloom.client.core.settings.NumberSetting.Format.*;

/**
 * Every MOTION Client option, with its default, range and description. Render and tick
 * code reads these fields directly (e.g. {@code BloomSettings.PETALS.on()}).
 *
 * <p>Each option here is implemented somewhere; nothing is decorative. Options that only
 * work in some situations publish a reason through {@link Setting#setAvailability}.
 */
public final class BloomSettings {
    private static final Map<String, Setting> BY_KEY = new LinkedHashMap<>();
    private static final List<Setting> ALL = new ArrayList<>();

    private static <S extends Setting> S reg(S s) {
        if (BY_KEY.put(s.key(), s) != null) throw new IllegalStateException("Duplicate setting " + s.key());
        ALL.add(s);
        return s;
    }

    private static BoolSetting bool(String key, Category c, String group, String label, String desc, boolean def, String... kw) {
        return reg(new BoolSetting(key, c, group, label, desc, def, kw));
    }

    private static NumberSetting num(String key, Category c, String group, String label, String desc, float def, float min, float max, float step, NumberSetting.Format f, String unit, String... kw) {
        return reg(new NumberSetting(key, c, group, label, desc, def, min, max, step, f, unit, kw));
    }

    private static ColorSetting color(String key, Category c, String group, String label, String desc, int def, String... kw) {
        return reg(new ColorSetting(key, c, group, label, desc, def, kw));
    }

    private static ChoiceSetting choice(String key, Category c, String group, String label, String desc, String def, List<ChoiceSetting.Option> opts, String... kw) {
        return reg(new ChoiceSetting(key, c, group, label, desc, def, opts, kw));
    }

    /** Biome groups used by biome-aware effects (see BiomeGroups in the game layer). */
    public static final List<ChoiceSetting.Option> BIOME_GROUPS = ChoiceSetting.of(
            "cherry_grove", "Cherry groves",
            "flower", "Flower forests & meadows",
            "forest", "Forests",
            "plains", "Plains",
            "taiga", "Taiga",
            "jungle", "Jungles",
            "savanna", "Savannas",
            "swamp", "Swamps",
            "beach", "Beaches & oceans",
            "mountain", "Mountains",
            "snowy", "Snowy biomes",
            "dry", "Deserts & badlands"
    );

    // =====================================================================
    // General
    // =====================================================================
    public static final BoolSetting ENABLED = bool("general.enabled", GENERAL, "MOTION Client", "MOTION visuals",
            "Master switch for every MOTION visual effect (sky, particles, HUD). Turn off to see plain Minecraft instantly.", true, "master", "disable", "all");
    public static final ChoiceSetting THEME = choice("ui.theme", GENERAL, "Appearance", "Menu theme",
            "Colour scheme of the MOTION menu and HUD accents.", "motion",
            ChoiceSetting.of("motion", "Motion (green)", "crimson", "Crimson (classic)", "blood_moon", "Blood Moon", "pumpkin_night", "Pumpkin Night", "haunted_forest", "Haunted Forest",
                    "midnight_halloween", "Midnight Halloween", "ghostfire", "Ghostfire", "spiderweb", "Spiderweb", "crimson_eclipse", "Crimson Eclipse",
                    "obsidian", "Obsidian", "ash", "Ash", "ember", "Ember", "mono", "Monochrome"),
            "colour", "color", "green", "red", "black", "grey").profile();
    public static final ColorSetting ACCENT = color("ui.accent", GENERAL, "Appearance", "Accent colour",
            "Highlight colour for toggles, sliders and selected items. Follows the theme until you change it.", 0xE3192F, "highlight").profile();
    public static final BoolSetting ACCENT_FROM_THEME = bool("ui.accentFromTheme", GENERAL, "Appearance", "Use the theme's accent",
            "Use the selected theme's accent colour instead of your own.", true).profile();
    public static final ChoiceSetting UI_EFFECTS = choice("ui.effects", GENERAL, "Interface", "Interface effects",
            "Full: all animations. Reduced: quick, subtle animations. Performance: no animations or decorative effects in MOTION menus.",
            "full", ChoiceSetting.of("full", "Full", "reduced", "Reduced", "performance", "Performance"), "animation", "motion");
    public static final BoolSetting REDUCE_MOTION = bool("ui.reduceMotion", GENERAL, "Interface", "Reduce motion",
            "Turns off sliding and scaling animations in MOTION menus and the decorative petals in the menu background.", false, "accessibility", "animation");
    public static final NumberSetting MENU_DIM = num("ui.menuDim", GENERAL, "Interface", "Menu backdrop",
            "How much the game is dimmed behind the MOTION menu. The game keeps running behind it.", 0.45f, 0f, 0.9f, 0.05f, PERCENT, "", "background", "dim");
    public static final BoolSetting PAUSE_SINGLEPLAYER = bool("ui.pauseSingleplayer", GENERAL, "Interface", "Pause singleplayer",
            "Pause the game while the MOTION menu is open in singleplayer. Multiplayer never pauses.", false);
    public static final NumberSetting ZOOM_LEVEL = num("zoom.level", GENERAL, "Zoom", "Zoom amount",
            "How far the Zoom key zooms in. 4× is OptiFine's zoom. While zooming you can scroll to change it.", 4f, 1.5f, 30f, 0.5f, MULTIPLIER, "",
            "zoom", "spyglass", "optifine", "magnify");
    public static final BoolSetting ZOOM_SMOOTH = bool("zoom.smooth", GENERAL, "Zoom", "Smooth zoom",
            "Ease in and out of zoom instead of snapping.", true, "zoom", "animation");
    public static final BoolSetting ZOOM_SCROLL = bool("zoom.scroll", GENERAL, "Zoom", "Scroll to adjust",
            "While zooming, the mouse wheel zooms further in or out instead of changing the hotbar slot. The amount resets when you let go.",
            true, "zoom", "scroll", "wheel");
    public static final BoolSetting ZOOM_CINEMATIC = bool("zoom.cinematic", GENERAL, "Zoom", "Cinematic camera while zoomed",
            "Smooths mouse movement while zoomed, like OptiFine's zoom, so far-away aiming isn't jittery.", false, "zoom", "smooth", "camera");
    public static final BoolSetting ZOOM_TOGGLE = bool("zoom.toggle", GENERAL, "Zoom", "Toggle instead of hold",
            "Press the Zoom key once to zoom in and again to zoom out, instead of holding it.", false, "zoom", "toggle", "hold");
    public static final BoolSetting CAPES = bool("capes.enabled", GENERAL, "Capes", "MOTION capes",
            "Show the capes MOTION players chose (yours too). Only MOTION users see them. Pick yours in the launcher: Visuals → Cape.",
            true, "cape", "cosmetic");
    public static final BoolSetting TAGS = bool("tags.enabled", GENERAL, "Capes", "MOTION tags",
            "Show tags like [OWNER] before the names of MOTION team members. Only MOTION users see them.",
            true, "tag", "owner", "rank", "nametag");
    public static final BoolSetting CAPE_SMOOTH = bool("capes.smooth", GENERAL, "Capes", "Smooth cape motion",
            "Capes ease into position instead of snapping: they hang still while you stand, and drift back gently as you move. Applies to every cape you see.",
            true, "cape", "sway", "physics", "smooth");
    public static final NumberSetting CAPE_SWAY = num("capes.sway", GENERAL, "Capes", "Cape sway",
            "How far capes swing back while moving. Lower is calmer; 100% is as far as Minecraft's own cape.", 0.55f, 0.1f, 1f, 0.05f, PERCENT, "",
            "cape", "sway", "lean");
    public static final BoolSetting WINGS = bool("accessories.wings", GENERAL, "Accessories", "Wings",
            "Show the wings MOTION players picked in the launcher (yours too, in third person). Only MOTION users see them.",
            true, "wings", "cosmetic", "accessory");
    public static final BoolSetting AURAS = bool("accessories.auras", GENERAL, "Accessories", "Auras",
            "Show the glow and particle auras MOTION players picked in the launcher. Yours is hidden in first person so it never blocks your view.",
            true, "aura", "particles", "cosmetic", "accessory");
    public static final BoolSetting MACE_VISUALS = bool("visuals.mace", GENERAL, "Accessories", "Mace visuals",
            "Add a subtle colored particle trail around players holding a mace. Visual only; it does not change attacks or damage.",
            true, "mace", "trail", "weapon", "particles");
    public static final ChoiceSetting MENU_BACKGROUND = choice("ui.menuBackground", GENERAL, "Menu background", "Background",
            "What Minecraft's own screens show behind them: the title screen, pause and option menus, and loading screens. Pick a custom image in the launcher (Customize).",
            "vanilla", ChoiceSetting.of("vanilla", "Minecraft's own", "red", "Fully red", "black", "Fully black", "white", "Fully white", "green", "Motion green", "custom", "Custom image"),
            "background", "panorama", "title", "menu", "loading");
    public static final NumberSetting MENU_BACKGROUND_INWORLD = num("ui.menuBackgroundInWorld", GENERAL, "Menu background", "Over the world",
            "How much the background covers the world behind pause and option menus while you play. 100% hides the world completely.", 0.85f, 0f, 1f, 0.05f, PERCENT, "",
            "background", "pause", "opacity");
    public static final BoolSetting MENU_BACKGROUND_LOADING = bool("ui.menuBackgroundLoading", GENERAL, "Menu background", "On loading screens",
            "Also use the background on world-loading screens and Minecraft's resource-loading screen.", true, "loading", "background");
    public static final BoolSetting THEME_DECOR = bool("ui.themeDecor", GENERAL, "Appearance", "Theme decorations",
            "Embers, bats, mist and webs from the Halloween themes, drawn behind the menus. Off for a plain backdrop.", true, "particles", "halloween");
    public static final ChoiceSetting VISUALS_POSITION = choice("ui.visualsPosition", GENERAL, "Visuals menu (Right Ctrl)", "Menu position",
            "Where the Right Ctrl visuals menu opens. At the side, you can watch your changes in the world.", "left",
            ChoiceSetting.of("left", "Left", "center", "Centre", "right", "Right"), "dock");
    public static final NumberSetting VISUALS_DIM = num("ui.visualsDim", GENERAL, "Visuals menu (Right Ctrl)", "Backdrop",
            "How much the world behind the visuals menu is darkened.", 0.1f, 0f, 0.8f, 0.05f, PERCENT, "", "dim");
    public static final BoolSetting TOASTS = bool("ui.toasts", GENERAL, "Interface", "MOTION notifications",
            "Small notifications from MOTION, such as a profile switch or a performance tip.", true, "toast", "popup");

    // =====================================================================
    // Skyboxes
    // =====================================================================
    public static final BoolSetting SKY_ENABLED = bool("sky.enabled", SKYBOX, "Skybox", "Custom skybox",
            "Draw a MOTION skybox in the Overworld. Turn off for the vanilla sky.", true, "sky").profile();
    public static final ChoiceSetting SKY_DAY = reg(new ChoiceSetting("sky.day", SKYBOX, "Skybox", "Daytime skybox",
            "Skybox shown during the day. Import your own in the Custom skyboxes section below.", "fs_013_sunset",
            SkyboxCatalog::options, "day", "sky").aliases(SkyboxCatalog::migrate)).<ChoiceSetting>profile().dependsOn(SKY_ENABLED);
    public static final ChoiceSetting SKY_NIGHT = reg(new ChoiceSetting("sky.night", SKYBOX, "Skybox", "Night-time skybox",
            "Skybox shown at night. MOTION crossfades between the day and night skyboxes.", "fs_013_night",
            SkyboxCatalog::options, "night", "sky").aliases(SkyboxCatalog::migrate)).<ChoiceSetting>profile().dependsOn(SKY_ENABLED);
    public static final NumberSetting SKY_DAY_OPACITY = num("sky.dayOpacity", SKYBOX, "Day and night", "Daytime appearance",
            "How strongly the daytime skybox covers the vanilla sky.", 1f, 0f, 1f, 0.05f, PERCENT, "", "opacity", "day")
            .<NumberSetting>profile().dependsOn(SKY_ENABLED);
    public static final NumberSetting SKY_NIGHT_OPACITY = num("sky.nightOpacity", SKYBOX, "Day and night", "Night-time appearance",
            "How strongly the night skybox covers the vanilla sky.", 1f, 0f, 1f, 0.05f, PERCENT, "", "opacity", "night")
            .<NumberSetting>profile().dependsOn(SKY_ENABLED);
    public static final NumberSetting SKY_FADE_TIME = num("sky.fadeTime", SKYBOX, "Day and night", "Skybox switch fade",
            "How long switching skyboxes (or back to Minecraft's sky) crossfades. 0 switches instantly.", 1.2f, 0f, 5f, 0.1f, DECIMAL, " s",
            "transition", "crossfade").profile();
    public static final NumberSetting SKY_TRANSITION = num("sky.transition", SKYBOX, "Day and night", "Transition intensity",
            "0% gives long, gentle sunrises and sunsets; 100% switches quickly between day and night skyboxes.",
            0.5f, 0f, 1f, 0.05f, PERCENT, "", "sunrise", "sunset", "fade").<NumberSetting>profile().dependsOn(SKY_ENABLED);
    public static final NumberSetting SKY_BRIGHTNESS = num("sky.brightness", SKYBOX, "Look", "Brightness",
            "Brightness of the skybox texture.", 1f, 0.2f, 2f, 0.05f, MULTIPLIER, "", "light").<NumberSetting>profile().dependsOn(SKY_ENABLED);
    public static final NumberSetting SKY_SATURATION = num("sky.saturation", SKYBOX, "Look", "Saturation",
            "Colour intensity of the skybox. 0% is greyscale.", 1f, 0f, 2f, 0.05f, MULTIPLIER, "", "colour", "vivid").<NumberSetting>profile().dependsOn(SKY_ENABLED);
    public static final ColorSetting SKY_TINT = color("sky.tint", SKYBOX, "Look", "Colour tint",
            "Colour mixed into the skybox.", 0xFFFFFF, "colour", "tint").<ColorSetting>profile().dependsOn(SKY_ENABLED);
    public static final NumberSetting SKY_TINT_STRENGTH = num("sky.tintStrength", SKYBOX, "Look", "Tint strength",
            "How much of the tint colour is applied.", 0f, 0f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(SKY_ENABLED);
    public static final NumberSetting SKY_ROTATION = num("sky.rotation", SKYBOX, "Motion", "Rotation",
            "Turns the skybox around the vertical axis.", 0f, 0f, 360f, 1f, DEGREES, "", "angle", "turn").<NumberSetting>profile().dependsOn(SKY_ENABLED);
    public static final NumberSetting SKY_ROTATION_SPEED = num("sky.rotationSpeed", SKYBOX, "Motion", "Rotation speed",
            "Slowly turns the skybox, in degrees per minute. Negative values turn the other way.", 0.5f, -10f, 10f, 0.1f, DECIMAL, "°/min", "spin")
            .<NumberSetting>profile().dependsOn(SKY_ENABLED);
    public static final NumberSetting SKY_HORIZON = num("sky.horizonBlend", SKYBOX, "Look", "Horizon blending",
            "Fades the lower part of the skybox into the fog colour so it meets the terrain smoothly.", 0.35f, 0f, 1f, 0.05f, PERCENT, "", "horizon", "fog")
            .<NumberSetting>profile().dependsOn(SKY_ENABLED);
    public static final BoolSetting SKY_SUN_MOON = bool("sky.sunMoon", SKYBOX, "Sky objects", "Sun and moon",
            "Draw the sun and moon in front of the skybox.", true, "sun", "moon").<BoolSetting>profile().dependsOn(SKY_ENABLED);
    public static final BoolSetting SKY_STARS = bool("sky.stars", SKYBOX, "Sky objects", "Stars",
            "MOTION's twinkling starfield at night (in addition to any stars painted into the skybox).", true, "stars", "night").profile();
    public static final NumberSetting SKY_STAR_DENSITY = num("sky.starDensity", SKYBOX, "Sky objects", "Star density",
            "How many stars MOTION draws.", 0.6f, 0.1f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(SKY_STARS);
    public static final BoolSetting SKY_CLOUDS = bool("sky.clouds", SKYBOX, "Sky objects", "Clouds",
            "Show Minecraft's clouds. This hides clouds without changing your Minecraft video settings.", true, "clouds").profile();
    public static final BoolSetting SKY_WITH_SHADERS = bool("sky.withShaders", SKYBOX, "Compatibility", "Use with shader packs",
            "Shader packs (Iris/Oculus) draw their own sky. MOTION's skybox and world effects are paused while a shader pack is active unless you enable this. Results depend on the shader pack.",
            false, "iris", "oculus", "shaders");

    // =====================================================================
    // Weather & environment
    // =====================================================================
    public static final BoolSetting RAIN = bool("rain.enabled", WEATHER, "Rain", "Enhanced rain",
            "MOTION's rain streaks and splashes. Purely visual; real weather is unchanged.", true, "weather").profile();
    public static final ChoiceSetting RAIN_MODE = choice("rain.mode", WEATHER, "Rain", "When it rains",
            "Follow the world's weather, or show decorative rain all the time (visual only).", "follow",
            ChoiceSetting.of("follow", "With the world's weather", "always", "Always (decorative)"), "weather")
            .<ChoiceSetting>profile().dependsOn(RAIN);
    public static final NumberSetting RAIN_DENSITY = num("rain.density", WEATHER, "Rain", "Rain density",
            "Number of MOTION raindrops.", 0.6f, 0.05f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(RAIN);
    public static final ColorSetting RAIN_TINT = color("rain.tint", WEATHER, "Rain", "Rain colour",
            "Tint of MOTION's raindrops.", 0xB4CCF5, "tint").<ColorSetting>profile().dependsOn(RAIN);
    public static final BoolSetting RAIN_SPLASHES = bool("rain.splashes", WEATHER, "Rain", "Splashes",
            "Small splash particles where raindrops land.", true).<BoolSetting>profile().dependsOn(RAIN);
    public static final BoolSetting RAIN_HIDE_VANILLA = bool("rain.hideVanilla", WEATHER, "Rain", "Hide vanilla rain and snow",
            "Hide Minecraft's own rain and snow sheets so only MOTION's weather shows. Sounds are unaffected.", false, "vanilla")
            .profile();

    public static final BoolSetting SNOW = bool("snow.enabled", WEATHER, "Snow", "Decorative snowfall",
            "Gentle snowflakes. Visual only: no snow layers, no weather change, nothing sent to the server.", false, "winter").profile();
    public static final ChoiceSetting SNOW_WHERE = choice("snow.where", WEATHER, "Snow", "Where it snows",
            "Everywhere, only in snowy biomes, or only when the world is actually snowing near you.", "cold",
            ChoiceSetting.of("everywhere", "Everywhere", "cold", "Snowy biomes", "weather", "When it's snowing"))
            .<ChoiceSetting>profile().dependsOn(SNOW);
    public static final ChoiceSetting SNOW_INTENSITY = choice("snow.intensity", WEATHER, "Snow", "Snowfall",
            "Light flurries or heavy snow.", "light", ChoiceSetting.of("light", "Light", "medium", "Medium", "heavy", "Heavy"), "heavy", "light")
            .<ChoiceSetting>profile().dependsOn(SNOW);
    public static final NumberSetting SNOW_DENSITY = num("snow.density", WEATHER, "Snow", "Snow density",
            "Fine control on top of the snowfall level.", 0.6f, 0.05f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(SNOW);
    public static final NumberSetting SNOW_SIZE = num("snow.size", WEATHER, "Snow", "Flake size",
            "Size of the snowflakes.", 1f, 0.5f, 2.5f, 0.05f, MULTIPLIER, "").<NumberSetting>profile().dependsOn(SNOW);
    public static final NumberSetting SNOW_SPEED = num("snow.speed", WEATHER, "Snow", "Fall speed",
            "How fast flakes fall.", 1f, 0.25f, 2f, 0.05f, MULTIPLIER, "").<NumberSetting>profile().dependsOn(SNOW);
    public static final ChoiceSetting SNOW_SHAPE = choice("snow.shape", WEATHER, "Snow", "Snowflake look",
            "Soft round flakes, six-pointed crystals or square pixels.", "soft",
            ChoiceSetting.of("soft", "Soft", "crystal", "Crystal", "pixel", "Pixel")).<ChoiceSetting>profile().dependsOn(SNOW);
    public static final ColorSetting SNOW_COLOR = color("snow.color", WEATHER, "Snow", "Snow colour",
            "Tint of the snowflakes.", 0xFFFFFF).<ColorSetting>profile().dependsOn(SNOW);

    public static final BoolSetting WIND = bool("wind.enabled", WEATHER, "Wind and atmosphere", "Wind",
            "Wind pushes MOTION's petals, leaves, snow and rain. It never affects players, mobs or blocks.", true, "breeze").profile();
    public static final NumberSetting WIND_STRENGTH = num("wind.strength", WEATHER, "Wind and atmosphere", "Wind strength",
            "How hard the wind blows.", 0.35f, 0f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(WIND);
    public static final NumberSetting WIND_DIRECTION = num("wind.direction", WEATHER, "Wind and atmosphere", "Wind direction",
            "Compass direction the wind blows towards (0° = south, 90° = west, 180° = north, 270° = east, like Minecraft's F3 yaw).",
            60f, 0f, 359f, 1f, DEGREES, "", "compass").<NumberSetting>profile().dependsOn(WIND);
    public static final NumberSetting WIND_VARIATION = num("wind.variation", WEATHER, "Wind and atmosphere", "Wind variation",
            "How much the wind direction wanders.", 0.4f, 0f, 1f, 0.05f, PERCENT, "", "turbulence").<NumberSetting>profile().dependsOn(WIND);
    public static final NumberSetting WIND_GUSTS = num("wind.gusts", WEATHER, "Wind and atmosphere", "Gust frequency",
            "How often stronger gusts sweep through.", 0.3f, 0f, 1f, 0.05f, PERCENT, "", "gust").<NumberSetting>profile().dependsOn(WIND);
    public static final NumberSetting WIND_DRIFT = num("wind.drift", WEATHER, "Wind and atmosphere", "Particle drift",
            "How much light particles (petals, leaves, snow) follow the wind.", 0.7f, 0f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(WIND);
    public static final NumberSetting HAZE = num("env.haze", WEATHER, "Wind and atmosphere", "Atmospheric haze",
            "Adds a soft haze: thicker distance fog in the Overworld and a glow along the skybox horizon.", 0.2f, 0f, 1f, 0.05f, PERCENT, "", "fog", "mist")
            .<NumberSetting>profile().alsoIn(SKYBOX, WORLD);
    public static final ColorSetting HAZE_COLOR = color("env.hazeColor", WEATHER, "Wind and atmosphere", "Haze colour",
            "Colour of the horizon haze.", 0xA3172B, "fog").<ColorSetting>profile().alsoIn(SKYBOX);

    // =====================================================================
    // Particles
    // =====================================================================
    public static final BoolSetting PETALS = bool("petals.enabled", PARTICLES, "Cherry blossom petals", "Drifting petals",
            "Floating petals that drift on the wind (crimson by default; pick any colour below).", true, "sakura", "blossom", "cherry").profile();
    public static final BoolSetting PETALS_EVERYWHERE = bool("petals.everywhere", PARTICLES, "Cherry blossom petals", "Petals everywhere",
            "Show petals in every Overworld biome. Turn off to choose biomes below.", false, "biome").<BoolSetting>profile().dependsOn(PETALS);
    public static final MultiChoiceSetting PETAL_BIOMES = reg(new MultiChoiceSetting("petals.biomes", PARTICLES, "Cherry blossom petals",
            "Petal biomes", "Biome-aware petals: only fall in these biomes (when \"Petals everywhere\" is off).",
            BIOME_GROUPS, Set.of("cherry_grove", "flower"), "biome")).<MultiChoiceSetting>profile().dependsOn(PETALS);
    public static final NumberSetting PETAL_DENSITY = num("petals.density", PARTICLES, "Cherry blossom petals", "Petal density",
            "Number of petals around you.", 0.5f, 0.05f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(PETALS);
    public static final NumberSetting PETAL_SPEED = num("petals.speed", PARTICLES, "Cherry blossom petals", "Falling speed",
            "How fast petals fall.", 1f, 0.25f, 2f, 0.05f, MULTIPLIER, "").<NumberSetting>profile().dependsOn(PETALS);
    public static final NumberSetting PETAL_SIZE = num("petals.size", PARTICLES, "Cherry blossom petals", "Petal size",
            "Size of each petal.", 1f, 0.5f, 2f, 0.05f, MULTIPLIER, "").<NumberSetting>profile().dependsOn(PETALS);
    public static final NumberSetting PETAL_SPIN = num("petals.spin", PARTICLES, "Cherry blossom petals", "Rotation and drift",
            "How much petals spin and flutter as they fall.", 1f, 0f, 2f, 0.05f, MULTIPLIER, "", "rotation", "flutter").<NumberSetting>profile().dependsOn(PETALS);
    public static final MultiChoiceSetting PETAL_SHAPES = reg(new MultiChoiceSetting("petals.shapes", PARTICLES, "Cherry blossom petals",
            "Petal shapes", "Mix of petal shapes.",
            ChoiceSetting.of("round", "Round", "notched", "Notched", "oval", "Oval", "curled", "Curled"),
            Set.of("round", "notched", "oval", "curled"))).<MultiChoiceSetting>profile().dependsOn(PETALS);
    public static final ColorSetting PETAL_COLOR_A = color("petals.colorA", PARTICLES, "Cherry blossom petals", "Petal colour",
            "Main petal colour.", 0xD3203A, "red", "pink").<ColorSetting>profile().dependsOn(PETALS);
    public static final ColorSetting PETAL_COLOR_B = color("petals.colorB", PARTICLES, "Cherry blossom petals", "Second petal colour",
            "Petals vary between the two colours.", 0x7A0C1C, "red", "pink").<ColorSetting>profile().dependsOn(PETALS);
    public static final NumberSetting PETAL_VARIATION = num("petals.colorVariation", PARTICLES, "Cherry blossom petals", "Colour variation",
            "How much each petal's colour varies.", 0.5f, 0f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(PETALS);
    public static final NumberSetting PETAL_RADIUS = num("petals.spawnRadius", PARTICLES, "Cherry blossom petals", "Spawn area",
            "Distance around you where petals appear.", 24f, 8f, 48f, 1f, INTEGER, " blocks", "radius", "area").<NumberSetting>profile().dependsOn(PETALS);
    public static final NumberSetting PETAL_HEIGHT = num("petals.spawnHeight", PARTICLES, "Cherry blossom petals", "Spawn height",
            "How high above you petals start.", 14f, 4f, 32f, 1f, INTEGER, " blocks").<NumberSetting>profile().dependsOn(PETALS);
    public static final BoolSetting PETAL_SETTLE = bool("petals.settle", PARTICLES, "Cherry blossom petals", "Petals settle on the ground",
            "Petals rest on blocks for a moment before fading away.", true).<BoolSetting>profile().dependsOn(PETALS);

    public static final BoolSetting LEAVES = bool("leaves.enabled", PARTICLES, "Ambient particles", "Falling leaves",
            "Leaves drift down in forests, coloured to match the biome.", true, "autumn").profile();
    public static final NumberSetting LEAF_DENSITY = num("leaves.density", PARTICLES, "Ambient particles", "Leaf density",
            "Number of falling leaves.", 0.4f, 0.05f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(LEAVES);
    public static final BoolSetting FIREFLIES = bool("fireflies.enabled", PARTICLES, "Ambient particles", "Fireflies",
            "Softly glowing fireflies at night in plains, forests and swamps.", true, "night", "glow").profile();
    public static final NumberSetting FIREFLY_DENSITY = num("fireflies.density", PARTICLES, "Ambient particles", "Firefly density",
            "Number of fireflies.", 0.4f, 0.05f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(FIREFLIES);
    public static final BoolSetting DUST = bool("dust.enabled", PARTICLES, "Ambient particles", "Floating motes",
            "Tiny motes drifting in the air during the day.", false, "dust", "pollen").profile();
    public static final NumberSetting DUST_DENSITY = num("dust.density", PARTICLES, "Ambient particles", "Mote density",
            "Number of motes.", 0.35f, 0.05f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(DUST);

    // =====================================================================
    // Visual effects
    // =====================================================================
    public static final BoolSetting OUTLINE = bool("outline.enabled", VISUAL_EFFECTS, "Block outline", "Custom block outline",
            "Replace the black outline around the block you're looking at.", false, "selection", "highlight").profile();
    public static final ColorSetting OUTLINE_COLOR = color("outline.color", VISUAL_EFFECTS, "Block outline", "Outline colour",
            "Colour of the outline.", 0xE3192F).<ColorSetting>profile().dependsOn(OUTLINE);
    public static final NumberSetting OUTLINE_OPACITY = num("outline.opacity", VISUAL_EFFECTS, "Block outline", "Outline opacity",
            "Opacity of the outline lines.", 0.9f, 0.1f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(OUTLINE);
    public static final NumberSetting OUTLINE_WIDTH = num("outline.width", VISUAL_EFFECTS, "Block outline", "Outline thickness",
            "Line thickness in pixels.", 2.5f, 1f, 6f, 0.5f, DECIMAL, " px", "width").<NumberSetting>profile().dependsOn(OUTLINE);
    public static final NumberSetting OUTLINE_FILL = num("outline.fill", VISUAL_EFFECTS, "Block outline", "Fill",
            "Tint the faces of the targeted block.", 0f, 0f, 0.6f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(OUTLINE);
    public static final BoolSetting SUN_GLOW = bool("glow.sun", VISUAL_EFFECTS, "Sky glow", "Sun and moon glow",
            "A soft halo around the sun and moon.", true, "bloom", "halo").profile();
    public static final NumberSetting SUN_GLOW_STRENGTH = num("glow.strength", VISUAL_EFFECTS, "Sky glow", "Glow strength",
            "Brightness of the halo.", 0.5f, 0.05f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(SUN_GLOW);
    // ---------------------------------------------------------------- shader effects (post-processing)
    public static final BoolSetting POST = bool("post.enabled", VISUAL_EFFECTS, "Shader effects", "Shader effects",
            "Real GPU post-processing after the world is drawn (the HUD and menus are not affected): colour grading, glow, sharpening and vignette.",
            true, "shader", "post", "grading", "filter").profile();
    public static final NumberSetting POST_BRIGHTNESS = num("post.brightness", VISUAL_EFFECTS, "Colour grading", "Brightness",
            "Exposure of the whole image, in stops (+1 doubles the brightness).", 0f, -1f, 1f, 0.05f, DECIMAL, " EV", "exposure")
            .<NumberSetting>profile().dependsOn(POST);
    public static final NumberSetting POST_CONTRAST = num("post.contrast", VISUAL_EFFECTS, "Colour grading", "Contrast",
            "Darker darks and brighter lights above 100%, flatter below.", 1f, 0.6f, 1.5f, 0.02f, PERCENT, "")
            .<NumberSetting>profile().dependsOn(POST);
    public static final NumberSetting POST_SATURATION = num("post.saturation", VISUAL_EFFECTS, "Colour grading", "Saturation",
            "Colour intensity. 0% is black and white.", 1f, 0f, 2f, 0.05f, PERCENT, "", "vibrance", "colour")
            .<NumberSetting>profile().dependsOn(POST);
    public static final NumberSetting POST_WARMTH = num("post.warmth", VISUAL_EFFECTS, "Colour grading", "Colour temperature",
            "Warmer (orange) above 0, cooler (blue) below.", 0f, -1f, 1f, 0.05f, PERCENT, "", "white balance", "warm", "cool")
            .<NumberSetting>profile().dependsOn(POST);
    public static final ColorSetting POST_TINT = color("post.tint", VISUAL_EFFECTS, "Colour grading", "Colour filter",
            "A coloured filter over the whole image. Brightness is kept.", 0xE3192F, "tint", "correction")
            .<ColorSetting>profile().dependsOn(POST);
    public static final NumberSetting POST_TINT_STRENGTH = num("post.tintStrength", VISUAL_EFFECTS, "Colour grading", "Filter strength",
            "How much of the colour filter is applied.", 0f, 0f, 0.6f, 0.02f, PERCENT, "")
            .<NumberSetting>profile().dependsOn(POST);
    public static final BoolSetting POST_SOFT_HIGHLIGHTS = bool("post.softHighlights", VISUAL_EFFECTS, "Colour grading", "Soft highlights",
            "Bright areas roll off smoothly like film instead of clipping to flat white.", true, "tonemap", "filmic")
            .<BoolSetting>profile().dependsOn(POST);
    public static final NumberSetting POST_VIGNETTE = num("post.vignette", VISUAL_EFFECTS, "Lens", "Vignette",
            "Gently darkens the screen corners.", 0f, 0f, 1f, 0.05f, PERCENT, "")
            .<NumberSetting>profile().dependsOn(POST);
    public static final NumberSetting POST_SHARPEN = num("post.sharpen", VISUAL_EFFECTS, "Lens", "Sharpen",
            "Crisper block edges and textures. Limited so it doesn't create halos.", 0f, 0f, 1f, 0.05f, PERCENT, "", "clarity")
            .<NumberSetting>profile().dependsOn(POST);
    public static final BoolSetting POST_GLOW = bool("post.glow", VISUAL_EFFECTS, "Glow", "Glow",
            "Bright things (sun, lava, torches, glowing blocks) bleed soft light into their surroundings. Costs a little GPU time; see Performance → Shader quality.",
            false, "bloom").<BoolSetting>profile().dependsOn(POST);
    public static final NumberSetting POST_GLOW_STRENGTH = num("post.glowStrength", VISUAL_EFFECTS, "Glow", "Glow strength",
            "How much light the glow adds.", 0.35f, 0.05f, 1.2f, 0.05f, PERCENT, "")
            .<NumberSetting>profile().dependsOn(POST_GLOW);
    public static final NumberSetting POST_GLOW_THRESHOLD = num("post.glowThreshold", VISUAL_EFFECTS, "Glow", "Glow threshold",
            "Only parts brighter than this glow. Lower = more of the scene glows.", 0.72f, 0.3f, 0.95f, 0.01f, PERCENT, "")
            .<NumberSetting>profile().dependsOn(POST_GLOW);
    public static final BoolSetting POST_WEATHER = bool("post.weatherMood", VISUAL_EFFECTS, "Atmosphere", "Weather mood",
            "Rain and thunder make the colours a little cooler, greyer and flatter.", true, "rain", "storm")
            .<BoolSetting>profile().dependsOn(POST);
    public static final MultiChoiceSetting POST_DIMENSIONS = reg(new MultiChoiceSetting("post.dimensions", VISUAL_EFFECTS, "Atmosphere",
            "Shader effects in", "Dimensions where shader effects run. Other dimensions get plain Minecraft colours.",
            ChoiceSetting.of("overworld", "Overworld", "nether", "Nether", "end", "The End"), Set.of("overworld", "nether", "end"), "dimension"))
            .<MultiChoiceSetting>profile().dependsOn(POST);

    public static final NumberSetting FIRE_OFFSET = num("fire.offset", VISUAL_EFFECTS, "Overlays", "Fire overlay height",
            "Lowers the burning overlay at the bottom of the screen so it covers less of your view.", 0f, 0f, 0.4f, 0.02f, PERCENT, "", "low fire")
            .profile();

    // =====================================================================
    // World appearance
    // =====================================================================
    public static final BoolSetting FOG_CUSTOM = bool("world.fogEnabled", WORLD, "Fog", "Custom fog",
            "MOTION's fog changes: distance, tint and haze. Off = Minecraft's own fog.", true, "atmospheric", "distance fog").profile();
    public static final BoolSetting FOG_SMOOTH = bool("world.fogSmooth", WORLD, "Fog", "Smooth fog transitions",
            "MOTION's fog distance and colour glide to new values over about a second (presets, sliders, weather) instead of jumping.",
            true, "transition").profile();
    public static final NumberSetting FOG_DISTANCE = num("world.fogDistance", WORLD, "Fog", "Fog distance",
            "Moves the Overworld's distance fog closer (below 100%) or further away (above 100%). Fog never extends past your render distance.",
            1f, 0.4f, 1.5f, 0.05f, MULTIPLIER, "", "fog").<NumberSetting>profile().dependsOn(FOG_CUSTOM);
    public static final ColorSetting FOG_TINT = color("world.fogTint", WORLD, "Fog", "Fog tint",
            "Colour mixed into the distance fog.", 0xC81E32).<ColorSetting>profile().dependsOn(FOG_CUSTOM);
    public static final NumberSetting FOG_TINT_STRENGTH = num("world.fogTintStrength", WORLD, "Fog", "Fog tint strength",
            "How much of the fog tint is applied.", 0f, 0f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(FOG_CUSTOM);
    public static final ColorSetting NETHER_FOG_TINT = color("world.netherFogTint", WORLD, "Dimensions", "Nether fog tint",
            "Colour mixed into the Nether's fog.", 0x6B0F14, "nether").profile();
    public static final NumberSetting NETHER_FOG_STRENGTH = num("world.netherFogStrength", WORLD, "Dimensions", "Nether fog tint strength",
            "0% keeps each Nether biome's own fog colour.", 0f, 0f, 1f, 0.05f, PERCENT, "", "nether").profile();
    public static final ColorSetting END_FOG_TINT = color("world.endFogTint", WORLD, "Dimensions", "End fog tint",
            "Colour mixed into the End's fog.", 0x3A1F4D, "end").profile();
    public static final NumberSetting END_FOG_STRENGTH = num("world.endFogStrength", WORLD, "Dimensions", "End fog tint strength",
            "0% keeps the End's own fog colour.", 0f, 0f, 1f, 0.05f, PERCENT, "", "end").profile();
    public static final ColorSetting SKY_COLOR_TINT = color("world.skyTint", WORLD, "Sky colour", "Vanilla sky tint",
            "Tints Minecraft's own sky colour (visible where the skybox is transparent or off).", 0xC81E32).profile();
    public static final NumberSetting SKY_COLOR_TINT_STRENGTH = num("world.skyTintStrength", WORLD, "Sky colour", "Sky tint strength",
            "How much of the sky tint is applied.", 0f, 0f, 1f, 0.05f, PERCENT, "").profile();
    public static final BoolSetting TIME_LOCK = bool("world.timeLock", WORLD, "Time of day", "Visual time of day",
            "Show the sky at a fixed time. Client-side only: the server's time, mob spawning and crops are unaffected.", false, "time", "day", "night")
            .profile();
    public static final NumberSetting TIME_VALUE = num("world.time", WORLD, "Time of day", "Time",
            "0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight.", 6000f, 0f, 23999f, 250f, INTEGER, "", "clock")
            .<NumberSetting>profile().dependsOn(TIME_LOCK);

    // =====================================================================
    // Performance
    // =====================================================================
    public static final ChoiceSetting QUALITY = choice("perf.quality", PERFORMANCE, "Effect quality", "Quality preset",
            "Particle limits and effect distance. Choose Custom to set them yourself.", "medium",
            ChoiceSetting.of("low", "Low", "medium", "Medium", "high", "High", "custom", "Custom"), "preset", "fps")
            .<ChoiceSetting>profile().alsoIn(WEATHER, PARTICLES);
    public static final NumberSetting MAX_PARTICLES = num("perf.maxParticles", PERFORMANCE, "Effect quality", "Particle limit",
            "Maximum number of MOTION particles (all effects together).", 1500f, 100f, 6000f, 100f, INTEGER, "", "limit", "count").profile();
    public static final NumberSetting EFFECT_DISTANCE = num("perf.effectDistance", PERFORMANCE, "Effect quality", "Effect distance",
            "Particles beyond this distance are thinned out and then hidden.", 36f, 12f, 72f, 2f, INTEGER, " blocks", "lod", "cull").profile();
    public static final ChoiceSetting POST_QUALITY = choice("perf.postQuality", PERFORMANCE, "Shader effects", "Shader quality",
            "Low: colour grading only (one pass, glow off). Medium: adds glow from a 320×180 buffer. High: wider, smoother glow with three blur rounds.",
            "medium", ChoiceSetting.of("low", "Low", "medium", "Medium", "high", "High"), "glow", "bloom").profile();
    public static final BoolSetting LITE_MODE = bool("perf.lite", PERFORMANCE, "Shader effects", "Performance-friendly mode",
            "Keeps colour grading but turns off glow and sharpening, halves MOTION's particle budget and drops MOTION's starfield. Your other settings are kept.",
            false, "fps", "potato", "light").profile();
    public static final ChoiceSetting SKY_QUALITY = choice("perf.skyQuality", PERFORMANCE, "Skyboxes", "Skybox quality",
            "Largest skybox face MOTION loads: Performance 256 px, Balanced 512 px, High 1024 px (full 4K for the Halloween skies), Ultra up to 2048 px for your own imports. "
                    + "Larger skies are shrunk on a background thread. Each step down uses a quarter of the video memory.",
            "high", ChoiceSetting.of("performance", "Performance", "balanced", "Balanced", "high", "High", "ultra", "Ultra"),
            "texture", "vram", "resolution", "4k", "8k").profile();
    public static final NumberSetting SKY_CACHE = num("perf.skyCache", PERFORMANCE, "Skyboxes", "Skybox cache",
            "How many skyboxes stay loaded on the GPU for instant switching. Older ones are released.",
            3f, 1f, 6f, 1f, INTEGER, "", "memory", "cache");
    public static final BoolSetting SKY_LOAD_CUSTOM = bool("sky.loadCustom", PERFORMANCE, "Skyboxes", "Load custom skyboxes",
            "Off: your imported skyboxes are hidden and never loaded (the built-in ones still work).", true, "import");
    public static final BoolSetting ADAPTIVE = bool("perf.adaptive", PERFORMANCE, "Adaptive effects", "Reduce effects when FPS drops",
            "While your frame rate is below the target, MOTION temporarily draws fewer particles and, if needed, skips glow. Your saved settings are not changed.",
            true, "auto", "dynamic");
    public static final NumberSetting ADAPTIVE_TARGET = num("perf.adaptiveTarget", PERFORMANCE, "Adaptive effects", "Target FPS",
            "Frame rate MOTION tries to protect.", 60f, 30f, 240f, 5f, INTEGER, " FPS").dependsOn(ADAPTIVE);
    public static final BoolSetting LOW_FPS_TIP = bool("perf.lowFpsTip", PERFORMANCE, "Recommendations", "Low FPS suggestions",
            "If your FPS stays below the threshold, MOTION suggests effects you could lower. It never changes them for you.", true, "tip");
    public static final NumberSetting LOW_FPS_THRESHOLD = num("perf.lowFpsThreshold", PERFORMANCE, "Recommendations", "Suggestion threshold",
            "Suggest lowering effects when FPS stays below this for 15 seconds.", 40f, 15f, 144f, 5f, INTEGER, " FPS").dependsOn(LOW_FPS_TIP);

    // =====================================================================
    // HUD (style; widget positions live in the HUD layout)
    // =====================================================================
    public static final BoolSetting HUD_ENABLED = bool("hud.enabled", Category.HUD, "HUD", "MOTION HUD",
            "Show MOTION's HUD widgets (FPS, coordinates, keystrokes…). Arrange them in the HUD editor.", true, "overlay", "widgets");
    public static final BoolSetting HUD_SHADOW = bool("hud.shadow", HUD, "Style", "Text shadow",
            "Draw a shadow behind HUD text.", true).<BoolSetting>profile().dependsOn(HUD_ENABLED);
    public static final BoolSetting HUD_BACKGROUND = bool("hud.background", HUD, "Style", "Backgrounds",
            "Draw a translucent panel behind each widget.", true).<BoolSetting>profile().dependsOn(HUD_ENABLED);
    public static final ColorSetting HUD_BG_COLOR = color("hud.backgroundColor", HUD, "Style", "Background colour",
            "Default background colour for widgets.", 0x0B0809).<ColorSetting>profile().dependsOn(HUD_ENABLED);
    public static final NumberSetting HUD_BG_OPACITY = num("hud.backgroundOpacity", HUD, "Style", "Background opacity",
            "Default background opacity for widgets.", 0.45f, 0f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(HUD_ENABLED);
    public static final ColorSetting HUD_TEXT = color("hud.textColor", HUD, "Style", "Text colour",
            "Default text colour for widgets.", 0xFFFFFF).<ColorSetting>profile().dependsOn(HUD_ENABLED);
    public static final BoolSetting HUD_HIDE_IN_F3 = bool("hud.hideWithDebug", HUD, "HUD", "Hide with debug screen",
            "Hide MOTION widgets while the F3 debug screen is open.", true).dependsOn(HUD_ENABLED);

    /** Vanilla status effects the potion widget can hide one by one (registry paths). */
    public static final List<ChoiceSetting.Option> EFFECTS = ChoiceSetting.of(
            "speed", "Speed", "slowness", "Slowness", "haste", "Haste", "mining_fatigue", "Mining Fatigue", "strength", "Strength",
            "jump_boost", "Jump Boost", "nausea", "Nausea", "regeneration", "Regeneration", "resistance", "Resistance",
            "fire_resistance", "Fire Resistance", "water_breathing", "Water Breathing", "invisibility", "Invisibility",
            "blindness", "Blindness", "night_vision", "Night Vision", "hunger", "Hunger", "weakness", "Weakness", "poison", "Poison",
            "wither", "Wither", "health_boost", "Health Boost", "absorption", "Absorption", "saturation", "Saturation",
            "glowing", "Glowing", "levitation", "Levitation", "luck", "Luck", "unluck", "Bad Luck", "slow_falling", "Slow Falling",
            "conduit_power", "Conduit Power", "dolphins_grace", "Dolphin's Grace", "bad_omen", "Bad Omen",
            "hero_of_the_village", "Hero of the Village", "darkness", "Darkness", "trial_omen", "Trial Omen", "raid_omen", "Raid Omen",
            "wind_charged", "Wind Charged", "weaving", "Weaving", "oozing", "Oozing", "infested", "Infested"
    );
    public static final MultiChoiceSetting HUD_POTIONS_HIDDEN = reg(new MultiChoiceSetting("hud.potions.hidden", HUD, "Potion effects",
            "Hidden effects", "Effects the potion widget never shows (for example Night Vision from a helmet you always wear).",
            EFFECTS, Set.of(), "potion", "effect", "hide")).<MultiChoiceSetting>profile().dependsOn(HUD_ENABLED);

    // ---------------------------------------------------------------- crosshair
    public static final BoolSetting CROSSHAIR = bool("crosshair.enabled", HUD, "Crosshair", "Custom crosshair",
            "Draw MOTION's crosshair instead of Minecraft's. While it's on, the crosshair-style attack indicator isn't drawn: set Attack Indicator to Hotbar in Minecraft's options to keep one.",
            false, "aim", "reticle").profile();
    public static final ChoiceSetting CROSSHAIR_STYLE = choice("crosshair.style", HUD, "Crosshair", "Style",
            "Shape of the crosshair.", "cross",
            ChoiceSetting.of("cross", "Cross", "cross_dot", "Cross + dot", "t", "T-shape", "dot", "Dot", "square", "Square"))
            .<ChoiceSetting>profile().dependsOn(CROSSHAIR);
    public static final NumberSetting CROSSHAIR_SIZE = num("crosshair.size", HUD, "Crosshair", "Size",
            "Length of each arm in GUI pixels.", 5f, 1f, 20f, 1f, INTEGER, " px", "length").<NumberSetting>profile().dependsOn(CROSSHAIR);
    public static final NumberSetting CROSSHAIR_GAP = num("crosshair.gap", HUD, "Crosshair", "Gap",
            "Empty space between the centre and the arms.", 2f, 0f, 12f, 1f, INTEGER, " px").<NumberSetting>profile().dependsOn(CROSSHAIR);
    public static final NumberSetting CROSSHAIR_THICKNESS = num("crosshair.thickness", HUD, "Crosshair", "Thickness",
            "Width of the lines.", 1f, 1f, 5f, 1f, INTEGER, " px", "width").<NumberSetting>profile().dependsOn(CROSSHAIR);
    public static final ColorSetting CROSSHAIR_COLOR = color("crosshair.color", HUD, "Crosshair", "Colour",
            "Crosshair colour.", 0xFFFFFF).<ColorSetting>profile().dependsOn(CROSSHAIR);
    public static final NumberSetting CROSSHAIR_OPACITY = num("crosshair.opacity", HUD, "Crosshair", "Opacity",
            "How solid the crosshair is.", 1f, 0.1f, 1f, 0.05f, PERCENT, "").<NumberSetting>profile().dependsOn(CROSSHAIR);
    public static final BoolSetting CROSSHAIR_OUTLINE = bool("crosshair.outline", HUD, "Crosshair", "Dark outline",
            "A thin dark border so the crosshair stays visible on bright blocks and sky.", true).<BoolSetting>profile().dependsOn(CROSSHAIR);

    // =====================================================================
    // Profiles
    // =====================================================================
    public static final BoolSetting PROFILE_BY_BIOME = bool("profiles.byBiome", PROFILES, "Automatic switching", "Switch profile by biome",
            "Apply the visual profile you assign to each biome group (set rules below). Off by default.", false, "auto", "biome");
    public static final BoolSetting SMOOTH_TRANSITIONS = bool("profiles.smooth", PROFILES, "Automatic switching", "Smooth transitions",
            "Crossfade to the new skybox over about a second when a profile (or the chosen skybox) changes, instead of switching instantly.", true, "fade", "crossfade");

    // =====================================================================
    // Combat (all visual: nothing here changes hits, reach, damage or what the server sees)
    // =====================================================================
    public static final BoolSetting HITS = bool("hits.enabled", COMBAT, "Hit effects", "Hit effects",
            "Show an effect on whatever you hit: rings, a slash trail and sparks in your colour. Only you see it; it never changes the hit itself.",
            true, "hit", "attack", "pvp", "particles");
    public static final BoolSetting HIT_RINGS = bool("hits.rings", COMBAT, "Hit effects", "Shockwave rings",
            "A glowing ring that spreads out at the feet of what you hit.", true, "ring", "shockwave").dependsOn(HITS);
    public static final BoolSetting HIT_SLASH = bool("hits.slash", COMBAT, "Hit effects", "Slash trail",
            "A curved slash across the target in the direction you swung.", true, "slash", "trail", "arc").dependsOn(HITS);
    public static final BoolSetting HIT_SPARKS = bool("hits.sparks", COMBAT, "Hit effects", "Sparks",
            "A short burst of bright sparks where your hit landed.", true, "sparks", "crit").dependsOn(HITS);
    public static final ColorSetting HIT_COLOR = color("hits.color", COMBAT, "Hit effects", "Effect colour",
            "Colour of the rings, slash and sparks.", 0x39FF88, "colour", "color").dependsOn(HITS);
    public static final BoolSetting HIT_RAINBOW = bool("hits.rainbow", COMBAT, "Hit effects", "Rainbow",
            "Cycle through colours instead of using one.", false, "rainbow", "rgb", "chroma").dependsOn(HITS);
    public static final NumberSetting HIT_INTENSITY = num("hits.intensity", COMBAT, "Hit effects", "Amount",
            "How many particles each effect uses. Lower it if hits feel cluttered or slow.", 1f, 0.25f, 2f, 0.05f, MULTIPLIER, "",
            "particles", "density").dependsOn(HITS);
    public static final ChoiceSetting SWORD_ANIMATION = choice("hits.swordAnimation", COMBAT, "Sword animation", "Swing animation",
            "How your sword moves in first person when you swing. Spin twirls it a full turn, Flip somersaults it forward, Whirl spins it flat. Only you see it.",
            "spin", ChoiceSetting.of("off", "Minecraft's swing", "spin", "Spin", "flip", "Flip", "whirl", "Whirl"),
            "sword", "spin", "swing", "animation", "1.7", "blockhit");
    public static final BoolSetting SWORD_ANIMATION_ALL = bool("hits.animateAllWeapons", COMBAT, "Sword animation", "Axes, maces and tridents too",
            "Use the swing animation for every weapon, not only swords.", false, "axe", "mace", "trident", "weapon");
    public static final ChoiceSetting HEALTH_INDICATOR = choice("nametags.health", COMBAT, "Name tags", "Health indicators",
            "Show other players' health above their heads. Only players you can actually see are labelled, never through walls.",
            "hearts", ChoiceSetting.of("off", "Off", "hearts", "Hearts", "number", "Number", "bar", "Bar"),
            "health", "hp", "indicator", "hearts", "nametag");
    public static final BoolSetting OWN_NAMETAG = bool("nametags.self", COMBAT, "Name tags", "Your own name tag",
            "Show your name above your head in third person (F5), like other players see it.", true, "nametag", "self", "f5", "third person");
    public static final BoolSetting NAMETAG_SHADOW = bool("nametags.shadow", COMBAT, "Name tags", "Text shadow",
            "Draw MOTION's name tags and health labels with a shadow for contrast.", true, "shadow", "nametag");
    public static final NumberSetting NAMETAG_RANGE = num("nametags.range", COMBAT, "Name tags", "Range",
            "How far away players get a health label.", 32f, 8f, 64f, 1f, INTEGER, " blocks", "distance", "nametag");

    // =====================================================================
    // Waypoints
    // =====================================================================
    public static final BoolSetting WAYPOINTS = bool("waypoints.enabled", Category.WAYPOINTS, "Waypoints", "Show waypoints",
            "Draw your waypoints on screen with their name and distance. Add one with the Add waypoint key (B by default).",
            true, "waypoint", "marker", "home", "coords");
    public static final BoolSetting WAYPOINT_DISTANCE = bool("waypoints.distance", Category.WAYPOINTS, "Waypoints", "Show distance",
            "Show how many blocks away each waypoint is.", true, "distance", "blocks").dependsOn(WAYPOINTS);
    public static final BoolSetting WAYPOINT_DEATH = bool("waypoints.death", Category.WAYPOINTS, "Waypoints", "Death waypoint",
            "Mark where you last died so you can get your items back. It disappears when you reach it.", true, "death", "grave", "items");
    public static final BoolSetting WAYPOINT_OFFSCREEN = bool("waypoints.edge", Category.WAYPOINTS, "Waypoints", "Arrows at the screen edge",
            "When a waypoint is behind you or off screen, show an arrow at the edge pointing to it.", true, "arrow", "edge", "offscreen").dependsOn(WAYPOINTS);
    public static final NumberSetting WAYPOINT_SCALE = num("waypoints.scale", Category.WAYPOINTS, "Waypoints", "Marker size",
            "Size of waypoint markers and labels.", 1f, 0.5f, 2f, 0.05f, MULTIPLIER, "", "size", "scale").dependsOn(WAYPOINTS);

    // =====================================================================
    // Keybinds (the keys themselves are Minecraft key mappings, stored in options.txt)
    // =====================================================================
    public static final BoolSetting KEY_CONFLICT_WARNINGS = bool("keys.conflictWarnings", KEYBINDS, "Keybinds", "Warn about key conflicts",
            "Show a warning in the menu when a MOTION key is also used by Minecraft or another mod.", true, "conflict");

    // ---------------------------------------------------------------------

    /** Particle limit and effect distance for each quality preset. */
    public record QualityPreset(String id, int maxParticles, int effectDistance) {
    }

    public static final List<QualityPreset> QUALITY_PRESETS = List.of(
            new QualityPreset("low", 500, 22),
            new QualityPreset("medium", 1500, 36),
            new QualityPreset("high", 3500, 56)
    );

    private static boolean applyingPreset;

    static {
        QUALITY.addListener(s -> applyQualityPreset());
        Setting.Listener toCustom = s -> {
            if (!applyingPreset && !QUALITY.is("custom")) {
                QualityPreset p = presetFor(QUALITY.id());
                if (p != null && (MAX_PARTICLES.intValue() != p.maxParticles() || EFFECT_DISTANCE.intValue() != p.effectDistance())) {
                    applyingPreset = true;
                    try {
                        QUALITY.set("custom");
                    } finally {
                        applyingPreset = false;
                    }
                }
            }
        };
        MAX_PARTICLES.addListener(toCustom);
        EFFECT_DISTANCE.addListener(toCustom);
    }

    public static QualityPreset presetFor(String id) {
        for (QualityPreset p : QUALITY_PRESETS) if (p.id().equals(id)) return p;
        return null;
    }

    private static void applyQualityPreset() {
        if (applyingPreset) return;
        QualityPreset p = presetFor(QUALITY.id());
        if (p == null) return;
        applyingPreset = true;
        try {
            MAX_PARTICLES.set(p.maxParticles());
            EFFECT_DISTANCE.set(p.effectDistance());
        } finally {
            applyingPreset = false;
        }
    }

    private BloomSettings() {
    }

    public static List<Setting> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Setting get(String key) {
        return BY_KEY.get(key);
    }

    public static List<Setting> in(Category c) {
        List<Setting> out = new ArrayList<>();
        for (Setting s : ALL) if (s.listedIn(c)) out.add(s);
        return out;
    }

    public static List<Setting> search(String query) {
        List<Setting> out = new ArrayList<>();
        for (Setting s : ALL) if (s.matches(query)) out.add(s);
        return out;
    }

    public static void resetAll() {
        for (Setting s : ALL) s.reset();
    }

    /** Force class initialisation (registers every setting). */
    public static void init() {
    }
}
