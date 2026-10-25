package dev.bloom.client.render;

import net.minecraft.client.renderer.fog.FogData;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.visual.Smoother;
import dev.bloom.client.game.GameEnv;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;

/**
 * Fog distance/tint and atmospheric haze in the Overworld, fog tints in the Nether and the End,
 * and the vanilla sky colour tint. Visual only.
 *
 * <p>With "Smooth fog transitions" on, MOTION's own fog changes glide to new values over about a
 * second (switching presets, dragging sliders, leaving water) instead of jumping. Minecraft's own
 * fog changes (weather, biomes) are left exactly as vanilla does them.
 */
public final class FogTweaks {
    private static final float SMOOTH_SECONDS = 0.45f;
    // distance multiplier, haze, tint strength, tint rgb, haze rgb
    private static final Smoother OVERWORLD = new Smoother(9);
    private static final float[] TARGET = new float[9];
    // tint strength, tint rgb (Nether / End)
    private static final Smoother DIMENSION = new Smoother(4);
    private static final float[] DIM_TARGET = new float[4];
    private static Object lastDimension;

    private FogTweaks() {
    }

    private static boolean visualsOn() {
        BloomClient bc = BloomClient.get();
        return bc != null && bc.skyActive() && BloomSettings.FOG_CUSTOM.on();
    }

    private static boolean clearView(Camera camera) {
        if (camera.getFluidInCamera() != FogType.NONE) return false;
        return !(camera.entity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS)));
    }

    /** Smoothed Overworld values; heads back to neutral while MOTION's fog doesn't apply. */
    private static float[] overworld(boolean applies) {
        int tint = BloomSettings.FOG_TINT.rgb(), hz = BloomSettings.HAZE_COLOR.rgb();
        TARGET[0] = applies ? BloomSettings.FOG_DISTANCE.value() : 1f;
        TARGET[1] = applies ? BloomSettings.HAZE.value() : 0f;
        TARGET[2] = applies ? BloomSettings.FOG_TINT_STRENGTH.value() : 0f;
        TARGET[3] = Draw.r(tint);
        TARGET[4] = Draw.g(tint);
        TARGET[5] = Draw.b(tint);
        TARGET[6] = Draw.r(hz);
        TARGET[7] = Draw.g(hz);
        TARGET[8] = Draw.b(hz);
        return OVERWORLD.update(TARGET, smoothing(), System.nanoTime());
    }

    private static float smoothing() {
        return BloomSettings.FOG_SMOOTH.on() && !BloomSettings.REDUCE_MOTION.on() ? SMOOTH_SECONDS : 0f;
    }

    /**
     * Minecraft 1.21.11 fog: called from FogRenderer#setupFog just before the fog values are
     * uploaded. Scales the render-distance fog band in the Overworld and tints the fog colour.
     */
    public static void apply(Camera camera, FogData data, Vector4f color) {
        Minecraft mc = Minecraft.getInstance();
        boolean applies = visualsOn() && GameEnv.normalSky(mc.level) && clearView(camera);
        float[] v = overworld(applies);
        if (applies) {
            float mult = v[0];
            float haze = v[1];
            if (Math.abs(mult - 1f) >= 1e-3 || haze >= 1e-3) {
                float start = data.renderDistanceStart, end = data.renderDistanceEnd;
                float far = end;
                if (mult < 1f) {
                    end *= mult;
                    start *= mult;
                } else {
                    // Further than vanilla can't go past render distance; make the fog band thinner instead.
                    start = end - (end - start) / mult;
                }
                if (haze > 0f) start = start * (1f - 0.65f * haze);
                end = Math.min(end, far);
                start = Math.max(0f, Math.min(start, end - 1f));
                data.renderDistanceStart = start;
                data.renderDistanceEnd = end;
            }
        }
        if (color == null) return;
        float[] rgb = {color.x(), color.y(), color.z()};
        if (tintFogColor(camera, rgb)) color.set(rgb[0], rgb[1], rgb[2], color.w());
    }

    /** Returns true and writes the fog colour after tint and haze into {@code rgb}. */
    public static boolean tintFogColor(Camera camera, float[] rgb) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return false;
        boolean on = visualsOn() && clearView(camera);
        if (GameEnv.normalSky(level)) {
            float[] v = overworld(on);
            if (!on) return false;
            float ts = v[2];
            float haze = v[1] * 0.35f;
            if (ts < 1e-3 && haze < 1e-3) return false;
            mix(rgb, v[3], v[4], v[5], ts * 0.8f);
            mix(rgb, v[6], v[7], v[8], haze);
            return true;
        }
        // Nether / End: an optional tint on top of the dimension's own fog colour.
        Object dim = level.dimension();
        if (dim != lastDimension) {
            lastDimension = dim;
            DIMENSION.reset();
        }
        int c;
        float strength;
        if (Level.NETHER.equals(level.dimension())) {
            c = BloomSettings.NETHER_FOG_TINT.rgb();
            strength = BloomSettings.NETHER_FOG_STRENGTH.value();
        } else if (Level.END.equals(level.dimension())) {
            c = BloomSettings.END_FOG_TINT.rgb();
            strength = BloomSettings.END_FOG_STRENGTH.value();
        } else {
            return false;
        }
        DIM_TARGET[0] = on ? strength : 0f;
        DIM_TARGET[1] = Draw.r(c);
        DIM_TARGET[2] = Draw.g(c);
        DIM_TARGET[3] = Draw.b(c);
        float[] v = DIMENSION.update(DIM_TARGET, smoothing(), System.nanoTime());
        if (!on || v[0] < 1e-3) return false;
        mix(rgb, v[1], v[2], v[3], v[0]);
        return true;
    }

    private static void mix(float[] rgb, float r, float g, float b, float t) {
        if (t <= 0) return;
        rgb[0] += (r - rgb[0]) * t;
        rgb[1] += (g - rgb[1]) * t;
        rgb[2] += (b - rgb[2]) * t;
    }
}
