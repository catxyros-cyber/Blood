package dev.bloom.client.core.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/** The visual profiles that ship with MOTION. Values not listed use the setting's default. */
public final class BuiltInProfiles {
    public static final String DEFAULT_ID = "blood_moon";

    private BuiltInProfiles() {
    }

    public static List<Profile> create() {
        return List.of(
                p("blood_moon", "Blood Red", "MOTION's signature look: crimson dusk, a blood-red moon, red petals and haze, and a dark, dramatic crimson grade with glow.",
                        v().n("post.contrast", 1.12).n("post.saturation", 1.05).n("post.warmth", 0.1).c("post.tint", "#E3192F")
                                .n("post.tintStrength", 0.12).n("post.vignette", 0.35).b("post.glow", true).n("post.glowStrength", 0.35)
                                .n("post.glowThreshold", 0.72).n("post.brightness", -0.1).n("world.netherFogStrength", 0.25)),
                p("cherry_blossom", "Cherry Blossom", "Pink skies, drifting cherry petals and soft pink haze.",
                        v().s("sky.day", "fs_002_sunset").s("sky.night", "fs_classic_night_2").mc("petals.colorA", "#FFC3D9").mc("petals.colorB", "#FF8FB8")
                                .c("env.hazeColor", "#FFC2D4").c("outline.color", "#FF8FB8").c("world.fogTint", "#FFD6E2").c("world.skyTint", "#FFB3CC")),
                p("competitive", "Clean PvP", "Clear visibility for PvP: crisp, slightly punchier colours, thinner fog, no decorative particles or skybox, a clear block outline and low fire.",
                        v().n("post.contrast", 1.06).n("post.saturation", 1.1).n("post.sharpen", 0.35).b("post.weatherMood", false)
                                .n("world.fogDistance", 1.3).b("sky.enabled", false).b("sky.clouds", false).b("petals.enabled", false).b("leaves.enabled", false)
                                .b("fireflies.enabled", false).b("dust.enabled", false).b("rain.enabled", false).b("snow.enabled", false)
                                .n("env.haze", 0).b("glow.sun", false).b("outline.enabled", true).c("outline.color", "#FFFFFF")
                                .n("outline.width", 2.5).n("fire.offset", 0.3).s("perf.quality", "low").s("ui.theme", "mono")
                                .b("sky.stars", false)),
                p("minimal", "Minimal", "Vanilla-like visuals with a light, tidy HUD.",
                        v().b("sky.enabled", false).b("petals.enabled", false).b("leaves.enabled", false).b("fireflies.enabled", false)
                                .b("rain.enabled", false).n("env.haze", 0).b("glow.sun", false).b("hud.background", false)
                                .b("sky.stars", false).s("ui.theme", "ash").s("perf.quality", "low")),
                p("snowfall", "Snowfall", "Gentle decorative snow everywhere under a cool, pale sky.",
                        v().b("snow.enabled", true).s("snow.where", "everywhere").s("snow.intensity", "medium").b("petals.enabled", false)
                                .s("sky.day", "fs_classic_day_1").s("sky.night", "fs_002_night").n("sky.saturation", 0.7)
                                .n("env.haze", 0.35).c("env.hazeColor", "#DDE8F5").c("world.fogTint", "#E6EEF7").n("world.fogTintStrength", 0.35)
                                .n("wind.strength", 0.25).s("ui.theme", "ash").b("fireflies.enabled", false)),
                p("rainy_day", "Rainy Day", "Steady decorative rain, grey-blue sky and closer fog.",
                        v().b("sky.enabled", false).b("rain.enabled", true).s("rain.mode", "always").n("rain.density", 0.8)
                                .c("world.skyTint", "#8A96A8").n("world.skyTintStrength", 0.55).n("world.fogDistance", 0.75)
                                .c("world.fogTint", "#A9B4C2").n("world.fogTintStrength", 0.4).n("env.haze", 0.45).c("env.hazeColor", "#A9B4C2")
                                .b("petals.enabled", false).b("fireflies.enabled", false).n("wind.strength", 0.55).s("ui.theme", "ash")),
                p("fantasy", "Fantasy", "Pastel clouds, aurora nights, fireflies and petals everywhere.",
                        v().s("sky.day", "fs_classic_day_1").s("sky.night", "fs_017_night").n("sky.saturation", 1.2).b("petals.everywhere", true)
                                .n("fireflies.density", 0.6).b("dust.enabled", true).n("glow.strength", 0.7).s("ui.theme", "obsidian")
                                .mc("petals.colorA", "#FFD1E8").mc("petals.colorB", "#D9B8FF")),
                p("night_sky", "Night Sky", "Always night (client-side only): a galaxy sky, dense stars and fireflies.",
                        v().b("world.timeLock", true).n("world.time", 18000).s("sky.night", "fs_classic_night_3").n("sky.starDensity", 1)
                                .n("fireflies.density", 0.7).b("petals.enabled", false).s("ui.theme", "obsidian")),
                p("cinematic", "Cinematic", "Soft golden-hour colours, atmospheric haze, film-like glow and vignette for screenshots and videos.",
                        v().s("sky.day", "sbs_amber_dusk").s("sky.night", "sbs_violet_night").n("post.contrast", 1.04).n("post.saturation", 0.92)
                                .n("post.warmth", 0.2).n("post.vignette", 0.45).b("post.glow", true).n("post.glowStrength", 0.5)
                                .n("post.glowThreshold", 0.62).s("perf.postQuality", "high").n("env.haze", 0.45).n("world.fogTintStrength", 0.25).n("sky.rotationSpeed", 0.3)
                                .s("perf.quality", "high").n("petals.density", 0.7).b("dust.enabled", true).b("hud.background", false)
                                .n("leaves.density", 0.6).n("glow.strength", 0.8)),
                p("performance", "Performance", "Lightweight: plain sky, no particles or glow, half-size textures, colour grading off. For low-end PCs.",
                        v().b("post.enabled", false).b("perf.lite", true).s("perf.postQuality", "low").s("perf.skyQuality", "performance")
                                .s("perf.quality", "low").b("sky.enabled", false).b("sky.stars", false).b("sky.clouds", false)
                                .b("petals.enabled", false).b("leaves.enabled", false).b("fireflies.enabled", false).b("dust.enabled", false)
                                .b("rain.enabled", false).b("snow.enabled", false).b("glow.sun", false).n("env.haze", 0).n("sky.horizonBlend", 0)),
                p("vanilla", "Vanilla", "Plain Minecraft visuals: MOTION's sky, shaders, fog changes, particles and outline are all off. The HUD still works.",
                        v().b("post.enabled", false).b("sky.enabled", false).b("world.fogEnabled", false).b("sky.stars", false)
                                .b("petals.enabled", false).b("leaves.enabled", false).b("fireflies.enabled", false).b("dust.enabled", false)
                                .b("rain.enabled", false).b("snow.enabled", false).b("glow.sun", false).b("outline.enabled", false)
                                .n("env.haze", 0).n("sky.horizonBlend", 0).n("fire.offset", 0))
        );
    }

    private static Profile p(String id, String name, String desc, V values) {
        return new Profile(id, name, desc, true, values.o, null);
    }

    private static V v() {
        return new V();
    }

    /** Tiny builder for readable definitions. */
    private static final class V {
        final JsonObject o = new JsonObject();

        V b(String k, boolean v) {
            o.addProperty(k, v);
            return this;
        }

        V n(String k, double v) {
            o.addProperty(k, v);
            return this;
        }

        V s(String k, String v) {
            o.addProperty(k, v);
            return this;
        }

        V c(String k, String v) {
            o.addProperty(k, v);
            return this;
        }

        V mc(String k, String v) {
            return c(k, v);
        }

        @SuppressWarnings("unused")
        V list(String k, String... items) {
            JsonArray a = new JsonArray();
            for (String i : items) a.add(i);
            o.add(k, a);
            return this;
        }
    }
}
