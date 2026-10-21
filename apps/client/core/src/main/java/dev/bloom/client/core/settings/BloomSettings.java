package dev.bloom.client.core.settings;

import static dev.bloom.client.core.settings.Category.*;
import static dev.bloom.client.core.settings.Setting.Scope.GLOBAL;
import static dev.bloom.client.core.settings.Setting.Scope.PROFILE;

import dev.bloom.client.core.settings.FloatSetting.Format;
import dev.bloom.client.core.settings.Opts.*;
import dev.bloom.client.core.sky.SkyCatalog;
import java.util.List;

/**
 * Every BLOOD Client setting. The Right Shift menu, search, profiles, import
 * validation and the launcher's schema are all generated from this list.
 * GLOBAL settings live in the main config; PROFILE settings belong to the
 * active visual profile.
 */
public final class BloomSettings extends SettingsRegistry {

    // ---------------------------------------------------------------- General
    public final EnumSetting<Theme> theme = add(new EnumSetting<>("general.theme", GENERAL, "Appearance", "Menu theme",
            "Colours used by the BLOOD menu, HUD editor and notifications.", GLOBAL, Theme.CHERRY));
    public final FloatSetting menuOpacity = add(new FloatSetting("general.menuOpacity", GENERAL, "Appearance", "Panel opacity",
            "How see-through the menu panels are.", GLOBAL, 0.88f, 0.5f, 1f, 0.01f, Format.PERCENT));
    public final BoolSetting pauseSingleplayer = add(new BoolSetting("general.pauseSingleplayer", GENERAL, "Behaviour", "Pause singleplayer while the menu is open",
            "Off keeps the world running so the menu never interrupts play.", GLOBAL, false));
    public final BoolSetting notifications = add(new BoolSetting("general.notifications", GENERAL, "Behaviour", "BLOOD notifications",
            "Small corner messages, e.g. when a profile switches or an effect is unavailable.", GLOBAL, true));
    public final BoolSetting hudEnabled = add(new BoolSetting("hud.enabled", HUD, "HUD", "Show BLOOD HUD",
            "Master switch for all BLOOD HUD widgets.", PROFILE, true));
    public final BoolSetting hudInDebug = add(new BoolSetting("hud.showWithDebug", HUD, "HUD", "Show HUD with the F3 screen",
            "When off, BLOOD widgets hide while the debug screen is open.", PROFILE, false));

    // ---------------------------------------------------------------- Sky
    public final BoolSetting skyEnabled = add(new BoolSetting("sky.enabled", SKY, "Sky", "BLOOD sky",
            "Replace the Overworld sky with BLOOD skyboxes. Nether and End skies are never changed.", PROFILE, true));
    public final ChoiceSetting skyDay = add(new ChoiceSetting("sky.day", SKY, "Sky", "Daytime sky",
            "Skybox shown during the day.", PROFILE, "cherry_blossom", SkyCatalog::options));
    public final ChoiceSetting skyNight = add(new ChoiceSetting("sky.night", SKY, "Sky", "Night-time sky",
            "Skybox shown at night. BLOOD crossfades between day and night.", PROFILE, "sakura_night", SkyCatalog::options));
    public final FloatSetting skyTransition = add(new FloatSetting("sky.transition", SKY, "Sky", "Day/night transition intensity",
            "Low values blend slowly through dusk; high values switch quickly.", PROFILE, 0.5f, 0f, 1f, 0.01f, Format.PERCENT));
    public final FloatSetting skyBrightness = add(new FloatSetting("sky.brightness", SKY, "Colour", "Brightness",
            "Overall sky brightness.", PROFILE, 1f, 0.2f, 2f, 0.01f, Format.MULTIPLIER));
    public final FloatSetting skySaturation = add(new FloatSetting("sky.saturation", SKY, "Colour", "Saturation",
            "0% is greyscale, 100% is unchanged.", PROFILE, 1f, 0f, 2f, 0.01f, Format.PERCENT));
    public final ColorSetting skyTint = add(new ColorSetting("sky.tint", SKY, "Colour", "Colour tint",
            "Colour mixed into the sky.", PROFILE, 0xFFFFC2DA, false));
    public final FloatSetting skyTintStrength = add(new FloatSetting("sky.tintStrength", SKY, "Colour", "Tint strength",
            "How much of the tint colour is mixed in.", PROFILE, 0f, 0f, 1f, 0.01f, Format.PERCENT));
    public final FloatSetting skyRotation = add(new FloatSetting("sky.rotation", SKY, "Motion", "Rotation",
            "Turns the skybox around the vertical axis.", PROFILE, 0f, 0f, 360f, 1f, Format.DEGREES));
    public final FloatSetting skyRotationSpeed = add(new FloatSetting("sky.rotationSpeed", SKY, "Motion", "Rotation speed",
            "Slow drift of the sky. Paused when Reduce motion is on.", PROFILE, 0.6f, -10f, 10f, 0.1f, Format.DEGREES_PER_MINUTE));
    public final FloatSetting skyHorizonBlend = add(new FloatSetting("sky.horizonBlend", SKY, "Atmosphere", "Horizon blending",
            "Fades the bottom of the sky into the fog colour so terrain blends in.", PROFILE, 0.55f, 0f, 1f, 0.01f, Format.PERCENT));
    public final FloatSetting skyHaze = add(new FloatSetting("sky.haze", SKY, "Atmosphere", "Atmospheric haze",
            "A soft glowing band of haze along the horizon.", PROFILE, 0.35f, 0f, 1f, 0.01f, Format.PERCENT));
    public final BoolSetting skyStars = add(new BoolSetting("sky.stars", SKY, "Celestial", "Stars",
            "BLOOD's star field at night.", PROFILE, true));
    public final FloatSetting skyStarBrightness = add(new FloatSetting("sky.starBrightness", SKY, "Celestial", "Star brightness",
            "", PROFILE, 1f, 0.2f, 2f, 0.01f, Format.MULTIPLIER));
    public final BoolSetting skySunMoon = add(new BoolSetting("sky.sunMoon", SKY, "Celestial", "Sun and moon",
            "Draw the vanilla sun and moon (with moon phases) over BLOOD skies.", PROFILE, true));
    public final BoolSetting skyFollowWeather = add(new BoolSetting("sky.followWeather", SKY, "Atmosphere", "Darken in rain and storms",
            "Dims the sky when it rains in the world, like vanilla.", PROFILE, true));
    public final BoolSetting skyResourcePacks = add(new BoolSetting("sky.resourcePackLayers", SKY, "Compatibility", "Resource-pack sky layers",
            "Draw OptiFine-format sky layers (optifine/sky/world0) from enabled resource packs.", PROFILE, true));
    public final EnumSetting<SkyResolution> skyResolution = add(new EnumSetting<>("sky.resolution", SKY, "Compatibility", "Built-in sky resolution",
            "Texture size for BLOOD's generated skies. Higher uses more video memory.", PROFILE, SkyResolution.MEDIUM));

    // ---------------------------------------------------------------- Weather
    public final EnumSetting<RainMode> rainMode = add(new EnumSetting<>("rain.mode", WEATHER, "Rain", "Decorative rain",
            "Extra rain streaks drawn by BLOOD. Purely visual: world weather is never changed.", PROFILE, RainMode.FOLLOW_WEATHER));
    public final FloatSetting rainDensity = add(new FloatSetting("rain.density", WEATHER, "Rain", "Rain density",
            "", PROFILE, 0.6f, 0.05f, 1f, 0.01f, Format.PERCENT));
    public final ColorSetting rainTint = add(new ColorSetting("rain.tint", WEATHER, "Rain", "Rain colour",
            "Colour of BLOOD's rain streaks.", PROFILE, 0xB4C6D8F0, true));
    public final FloatSetting rainSpeed = add(new FloatSetting("rain.speed", WEATHER, "Rain", "Rain speed",
            "", PROFILE, 1f, 0.5f, 2f, 0.01f, Format.MULTIPLIER));
    public final BoolSetting rainSplashes = add(new BoolSetting("rain.splashes", WEATHER, "Rain", "Splash droplets",
            "Tiny splashes where BLOOD's raindrops hit the ground.", PROFILE, true));

    public final EnumSetting<SnowMode> snowMode = add(new EnumSetting<>("snow.mode", WEATHER, "Snow", "Decorative snow",
            "Snowflakes drawn by BLOOD. Purely visual: no snow layers, no gameplay changes.", PROFILE, SnowMode.OFF));
    public final EnumSetting<SnowIntensity> snowIntensity = add(new EnumSetting<>("snow.intensity", WEATHER, "Snow", "Snowfall",
            "", PROFILE, SnowIntensity.LIGHT));
    public final FloatSetting snowDensity = add(new FloatSetting("snow.density", WEATHER, "Snow", "Snow density",
            "", PROFILE, 0.5f, 0.05f, 1f, 0.01f, Format.PERCENT));
    public final FloatSetting snowSize = add(new FloatSetting("snow.size", WEATHER, "Snow", "Flake size",
            "", PROFILE, 1f, 0.5f, 2.5f, 0.01f, Format.MULTIPLIER));
    public final FloatSetting snowSpeed = add(new FloatSetting("snow.speed", WEATHER, "Snow", "Fall speed",
            "", PROFILE, 1f, 0.3f, 2f, 0.01f, Format.MULTIPLIER));
    public final EnumSetting<FlakeStyle> snowStyle = add(new EnumSetting<>("snow.style", WEATHER, "Snow", "Snowflake appearance",
            "", PROFILE, FlakeStyle.MIXED));
    public final ColorSetting snowColor = add(new ColorSetting("snow.color", WEATHER, "Snow", "Snow colour",
            "", PROFILE, 0xF0FFFFFF, true));

    public final BoolSetting windEnabled = add(new BoolSetting("wind.enabled", WEATHER, "Wind", "Wind",
            "Moves BLOOD's petals, leaves, snow and rain. Never affects real game physics.", PROFILE, true));
    public final FloatSetting windStrength = add(new FloatSetting("wind.strength", WEATHER, "Wind", "Wind strength",
            "", PROFILE, 0.35f, 0f, 1f, 0.01f, Format.PERCENT));
    public final FloatSetting windDirection = add(new FloatSetting("wind.direction", WEATHER, "Wind", "Wind direction",
            "The compass direction the wind blows towards (0° = south, 90° = west).", PROFILE, 45f, 0f, 359f, 1f, Format.DEGREES));
    public final FloatSetting windVariation = add(new FloatSetting("wind.variation", WEATHER, "Wind", "Wind variation",
            "How much the wind's direction and strength wander.", PROFILE, 0.4f, 0f, 1f, 0.01f, Format.PERCENT));
    public final FloatSetting windGusts = add(new FloatSetting("wind.gusts", WEATHER, "Wind", "Gust frequency",
            "", PROFILE, 0.3f, 0f, 1f, 0.01f, Format.PERCENT));
    public final FloatSetting windDrift = add(new FloatSetting("wind.drift", WEATHER, "Wind", "Particle drift",
            "How strongly particles follow the wind.", PROFILE, 0.7f, 0f, 1f, 0.01f, Format.PERCENT));

    // ---------------------------------------------------------------- Particles
    public final BoolSetting petals = add(new BoolSetting("petals.enabled", PARTICLES, "Cherry petals", "Cherry blossom petals",
            "Floating petals that drift and tumble in the wind.", PROFILE, true));
    public final EnumSetting<PetalArea> petalArea = add(new EnumSetting<>("petals.area", PARTICLES, "Cherry petals", "Where petals appear",
            "Everywhere, only in cherry groves, or in the biomes you choose.", PROFILE, PetalArea.EVERYWHERE));
    public final StringListSetting petalBiomes = add(new StringListSetting("petals.biomes", PARTICLES, "Cherry petals", "Petal biomes",
            "Used when 'Where petals appear' is set to selected biomes.", PROFILE, List.of("minecraft:cherry_grove", "minecraft:flower_forest", "minecraft:meadow"), 32));
    public final FloatSetting petalDensity = add(new FloatSetting("petals.density", PARTICLES, "Cherry petals", "Petal density",
            "", PROFILE, 0.45f, 0.02f, 1f, 0.01f, Format.PERCENT));
    public final FloatSetting petalFallSpeed = add(new FloatSetting("petals.fallSpeed", PARTICLES, "Cherry petals", "Falling speed",
            "", PROFILE, 1f, 0.2f, 2.5f, 0.01f, Format.MULTIPLIER));
    public final FloatSetting petalSize = add(new FloatSetting("petals.size", PARTICLES, "Cherry petals", "Petal size",
            "", PROFILE, 1f, 0.5f, 2.5f, 0.01f, Format.MULTIPLIER));
    public final FloatSetting petalSpin = add(new FloatSetting("petals.spin", PARTICLES, "Cherry petals", "Rotation and tumbling",
            "", PROFILE, 1f, 0f, 2.5f, 0.01f, Format.MULTIPLIER));
    public final FloatSetting petalWind = add(new FloatSetting("petals.windInfluence", PARTICLES, "Cherry petals", "Wind influence",
            "", PROFILE, 0.9f, 0f, 1f, 0.01f, Format.PERCENT));
    public final EnumSetting<PetalShape> petalShape = add(new EnumSetting<>("petals.shape", PARTICLES, "Cherry petals", "Petal shapes",
            "", PROFILE, PetalShape.MIXED));
    public final ColorSetting petalColor = add(new ColorSetting("petals.color", PARTICLES, "Cherry petals", "Petal colour",
            "", PROFILE, 0xFFF7B2CF, false));
    public final FloatSetting petalColorVariation = add(new FloatSetting("petals.colorVariation", PARTICLES, "Cherry petals", "Colour variation",
            "Random shade differences between petals.", PROFILE, 0.35f, 0f, 1f, 0.01f, Format.PERCENT));
    public final FloatSetting petalRadius = add(new FloatSetting("petals.spawnRadius", PARTICLES, "Cherry petals", "Spawn area radius",
            "How far around you petals appear (also capped by the quality preset).", PROFILE, 24f, 8f, 48f, 1f, Format.BLOCKS));
    public final FloatSetting petalHeight = add(new FloatSetting("petals.spawnHeight", PARTICLES, "Cherry petals", "Spawn height",
            "How high above you petals start falling.", PROFILE, 14f, 4f, 32f, 1f, Format.BLOCKS));
    public final FloatSetting petalFalloff = add(new FloatSetting("petals.distanceFalloff", PARTICLES, "Cherry petals", "Distance reduction",
            "Draw fewer petals far away (saves frame time).", PROFILE, 0.5f, 0f, 1f, 0.01f, Format.PERCENT));

    public final BoolSetting leaves = add(new BoolSetting("ambient.leaves", PARTICLES, "Ambient", "Drifting leaves",
            "Occasional leaves carried by the wind.", PROFILE, false));
    public final FloatSetting leafDensity = add(new FloatSetting("ambient.leafDensity", PARTICLES, "Ambient", "Leaf density",
            "", PROFILE, 0.3f, 0.02f, 1f, 0.01f, Format.PERCENT));
    public final BoolSetting fireflies = add(new BoolSetting("ambient.fireflies", PARTICLES, "Ambient", "Fireflies at night",
            "Softly glowing lights near the ground after dusk.", PROFILE, true));
    public final FloatSetting fireflyDensity = add(new FloatSetting("ambient.fireflyDensity", PARTICLES, "Ambient", "Firefly density",
            "", PROFILE, 0.35f, 0.02f, 1f, 0.01f, Format.PERCENT));
    public final BoolSetting motes = add(new BoolSetting("ambient.motes", PARTICLES, "Ambient", "Light motes",
            "Faint floating dust catching the daylight.", PROFILE, false));

    // ---------------------------------------------------------------- Visual effects
    public final EnumSetting<EffectsLevel> effectsLevel = add(new EnumSetting<>("effects.level", EFFECTS, "Effects", "Visual effects level",
            "Full, Reduced (half the particles) or Performance (decorative particles off).", GLOBAL, EffectsLevel.FULL));
    public final BoolSetting reduceMotion = add(new BoolSetting("effects.reduceMotion", EFFECTS, "Motion", "Reduce motion",
            "Turns off menu animations and sky drift.", GLOBAL, false));
    public final BoolSetting menuAnimations = add(new BoolSetting("effects.menuAnimations", EFFECTS, "Motion", "Menu animations",
            "Smooth opening, hover and toggle animations.", GLOBAL, true));
    public final FloatSetting skyCrossfade = add(new FloatSetting("effects.skyCrossfade", EFFECTS, "Transitions", "Sky crossfade time",
            "Seconds to fade when the selected sky changes.", GLOBAL, 1.5f, 0f, 5f, 0.1f, Format.DECIMAL));
    public final FloatSetting particleFade = add(new FloatSetting("effects.particleFade", EFFECTS, "Transitions", "Particle fade-in",
            "Seconds for particles to fade in and out when switched.", GLOBAL, 1f, 0f, 4f, 0.1f, Format.DECIMAL));

    // ---------------------------------------------------------------- World appearance
    public final EnumSetting<FogTint> fogTint = add(new EnumSetting<>("world.fogTint", WORLD, "Fog", "Fog colour",
            "Tint distant fog to match the sky or a custom colour.", PROFILE, FogTint.MATCH_SKY));
    public final ColorSetting fogColor = add(new ColorSetting("world.fogColor", WORLD, "Fog", "Custom fog colour",
            "", PROFILE, 0xFFF4C7D9, false));
    public final FloatSetting fogTintStrength = add(new FloatSetting("world.fogTintStrength", WORLD, "Fog", "Fog tint strength",
            "", PROFILE, 0.5f, 0f, 1f, 0.01f, Format.PERCENT));
    public final BoolSetting clouds = add(new BoolSetting("world.clouds", WORLD, "Clouds", "Vanilla clouds",
            "Show Minecraft's clouds. This only changes rendering; your video settings are untouched.", PROFILE, true));
    public final BoolSetting hideVanillaWeather = add(new BoolSetting("world.hideVanillaWeather", WORLD, "Weather", "Hide vanilla rain and snow",
            "Hides Minecraft's own rain/snow sheets (visual only — it still rains in the world).", PROFILE, false));

    // ---------------------------------------------------------------- Performance
    public final EnumSetting<Quality> quality = add(new EnumSetting<>("fx.quality", PERFORMANCE, "Quality", "Effects quality",
            "Low, Medium and High set the limits below; changing a limit switches to Custom.", PROFILE, Quality.MEDIUM));
    public final IntSetting maxParticles = add(new IntSetting("fx.maxParticles", PERFORMANCE, "Quality", "Particle limit",
            "Maximum decorative particles alive at once (all effects combined).", PROFILE, 1400, 100, 8000, 50, ""));
    public final IntSetting effectRadius = add(new IntSetting("fx.radius", PERFORMANCE, "Quality", "Effect radius",
            "Particles are only simulated and drawn within this distance.", PROFILE, 24, 8, 64, 1, " blocks"));
    public final BoolSetting adaptive = add(new BoolSetting("fx.adaptive", PERFORMANCE, "Quality", "Reduce effects when FPS drops",
            "Temporarily draws fewer particles when frame rate falls below the target. Your settings are not changed.", GLOBAL, true));
    public final IntSetting targetFps = add(new IntSetting("perf.targetFps", PERFORMANCE, "Monitoring", "Target FPS",
            "Used by adaptive effects and the low-FPS recommendation.", GLOBAL, 60, 20, 240, 5, " FPS"));
    public final BoolSetting lowFpsHint = add(new BoolSetting("perf.lowFpsHint", PERFORMANCE, "Monitoring", "Low-FPS recommendation",
            "Suggest lighter settings if FPS stays below the target. Never changes anything by itself.", GLOBAL, true));

    // ---------------------------------------------------------------- Profiles
    public final BoolSetting biomeProfiles = add(new BoolSetting("profiles.biomeSwitching", PROFILES, "Automatic switching", "Switch profile by biome",
            "Use the biome rules below to change profile when you enter a biome.", GLOBAL, false));

    public BloomSettings() {
        // Child options are shown dimmed when their parent toggle is off.
        for (Setting<?> s : List.of(skyDay, skyNight, skyTransition, skyBrightness, skySaturation, skyTint, skyTintStrength, skyRotation,
                skyRotationSpeed, skyHorizonBlend, skyHaze, skyStars, skySunMoon, skyFollowWeather, skyResolution)) {
            s.dependsOn(skyEnabled);
        }
        skyStarBrightness.dependsOn(skyStars);
        for (Setting<?> s : List.of(petalArea, petalBiomes, petalDensity, petalFallSpeed, petalSize, petalSpin, petalWind, petalShape,
                petalColor, petalColorVariation, petalRadius, petalHeight, petalFalloff)) {
            s.dependsOn(petals);
        }
        for (Setting<?> s : List.of(windStrength, windDirection, windVariation, windGusts, windDrift)) s.dependsOn(windEnabled);
        leafDensity.dependsOn(leaves);
        fireflyDensity.dependsOn(fireflies);
        hudInDebug.dependsOn(hudEnabled);

        petals.keywords("sakura", "cherry", "blossom", "flowers", "pink");
        snowMode.keywords("winter", "flakes", "snowfall");
        rainMode.keywords("storm", "drops", "precipitation");
        windEnabled.keywords("breeze", "gust");
        skyEnabled.keywords("skybox", "custom sky");
        clouds.keywords("cloud");
        quality.keywords("fps", "lag", "particles", "preset");
        reduceMotion.keywords("accessibility", "animation");

        // Quality presets fill in the limits; editing a limit makes the preset "Custom".
        quality.onChange(q -> {
            if (q != Quality.CUSTOM) {
                applyingPreset = true;
                try {
                    maxParticles.set(q.maxParticles);
                    effectRadius.set(q.radius);
                } finally {
                    applyingPreset = false;
                }
            }
        });
        maxParticles.onChange(v -> markCustom());
        effectRadius.onChange(v -> markCustom());
    }

    private boolean applyingPreset;

    private void markCustom() {
        if (applyingPreset) return;
        Quality q = quality.get();
        if (q != Quality.CUSTOM && (maxParticles.get() != q.maxParticles || effectRadius.get() != q.radius)) {
            quality.set(Quality.CUSTOM);
        }
    }
}
