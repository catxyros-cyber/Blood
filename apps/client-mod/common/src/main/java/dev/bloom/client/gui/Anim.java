package dev.bloom.client.gui;

import dev.bloom.client.core.settings.BloomSettings;

/** Animation helpers that respect "Interface effects" and "Reduce motion". */
public final class Anim {
    private Anim() {
    }

    /** 1 = normal speed, 0 = no animation at all. */
    public static float level() {
        if (BloomSettings.UI_EFFECTS.is("performance")) return 0f;
        if (BloomSettings.REDUCE_MOTION.on() || BloomSettings.UI_EFFECTS.is("reduced")) return 0.5f;
        return 1f;
    }

    /** Whether things may slide/scale (fades are still allowed when reduced). */
    public static boolean motion() {
        return level() >= 1f;
    }

    public static boolean decorations() {
        return BloomSettings.UI_EFFECTS.is("full") && !BloomSettings.REDUCE_MOTION.on();
    }

    /** Frame-rate independent approach of {@code current} towards {@code target}. */
    public static float approach(float current, float target, float speedPerSecond, float dtSeconds) {
        float lv = level();
        if (lv <= 0f) return target;
        float k = 1f - (float) Math.exp(-speedPerSecond * (lv < 1f ? 2f : 1f) * dtSeconds);
        float v = current + (target - current) * k;
        return Math.abs(target - v) < 0.001f ? target : v;
    }

    public static float easeOutCubic(float t) {
        t = Math.max(0, Math.min(1, t));
        float u = 1 - t;
        return 1 - u * u * u;
    }

    /** Progress 0..1 of an animation that started at {@code startNanos} and lasts {@code ms}. */
    public static float progress(long startNanos, float ms) {
        float lv = level();
        if (lv <= 0f) return 1f;
        float dur = ms * (lv < 1f ? 0.6f : 1f);
        return Math.min(1f, (System.nanoTime() - startNanos) / 1_000_000f / dur);
    }
}
