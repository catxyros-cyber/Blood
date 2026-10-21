package dev.bloom.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.game.GameEnv;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/** Overworld fog distance/tint, atmospheric haze and the vanilla sky colour tint. Visual only. */
public final class FogTweaks {
    private FogTweaks() {
    }

    private static boolean applies(Camera camera) {
        BloomClient bc = BloomClient.get();
        if (bc == null || !bc.skyActive()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!GameEnv.normalSky(mc.level)) return false;
        if (camera.getFluidInCamera() != FogType.NONE) return false;
        return !(camera.getEntity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS)));
    }

    /** After FogRenderer#setupFog for terrain fog. */
    public static void adjustFogDistance(Camera camera, float farPlane, boolean thickFog) {
        if (thickFog || !applies(camera)) return;
        float mult = BloomSettings.FOG_DISTANCE.value();
        float haze = BloomSettings.HAZE.value();
        if (Math.abs(mult - 1f) < 1e-3 && haze < 1e-3) return;
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        if (mult < 1f) {
            end *= mult;
            start *= mult;
        } else {
            // Further than vanilla can't go past render distance; make the fog band thinner instead.
            start = end - (end - start) / mult;
        }
        if (haze > 0f) start = start * (1f - 0.65f * haze);
        end = Math.min(end, farPlane);
        start = Math.max(0f, Math.min(start, end - 1f));
        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
    }

    /** Returns the fog colour after tint and haze (r, g, b written into {@code rgb}). */
    public static boolean tintFogColor(Camera camera, float[] rgb) {
        if (!applies(camera)) return false;
        float ts = BloomSettings.FOG_TINT_STRENGTH.value();
        float haze = BloomSettings.HAZE.value() * 0.35f;
        if (ts < 1e-3 && haze < 1e-3) return false;
        mix(rgb, BloomSettings.FOG_TINT.rgb(), ts * 0.8f);
        mix(rgb, BloomSettings.HAZE_COLOR.rgb(), haze);
        return true;
    }

    /** Tint for ClientLevel#getSkyColor. */
    public static Vec3 tintSky(Vec3 original) {
        BloomClient bc = BloomClient.get();
        if (bc == null || !bc.skyActive()) return original;
        float s = BloomSettings.SKY_COLOR_TINT_STRENGTH.value();
        if (s < 1e-3) return original;
        int c = BloomSettings.SKY_COLOR_TINT.rgb();
        // Keep the brightness of the original (so nights stay dark) and move the hue.
        double lum = 0.2126 * original.x + 0.7152 * original.y + 0.0722 * original.z;
        double tr = Draw.r(c) * lum * 1.6, tg = Draw.g(c) * lum * 1.6, tb = Draw.b(c) * lum * 1.6;
        return new Vec3(original.x + (Math.min(1, tr) - original.x) * s, original.y + (Math.min(1, tg) - original.y) * s,
                original.z + (Math.min(1, tb) - original.z) * s);
    }

    private static void mix(float[] rgb, int c, float t) {
        if (t <= 0) return;
        rgb[0] += (Draw.r(c) - rgb[0]) * t;
        rgb[1] += (Draw.g(c) - rgb[1]) * t;
        rgb[2] += (Draw.b(c) - rgb[2]) * t;
    }
}
