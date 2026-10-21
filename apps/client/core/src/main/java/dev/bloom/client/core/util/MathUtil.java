package dev.bloom.client.core.util;

public final class MathUtil {
    private MathUtil() {}

    public static final float PI = (float) Math.PI;
    public static final float TAU = (float) (Math.PI * 2);
    public static final float DEG = (float) (Math.PI / 180.0);

    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(max, v);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(max, v);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(max, v);
    }

    public static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) return x < edge0 ? 0 : 1;
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }

    /** Wraps degrees into [0, 360). */
    public static float wrapDegrees360(float deg) {
        float d = deg % 360f;
        return d < 0 ? d + 360f : d;
    }

    /** Frame-rate independent exponential approach: move current toward target. */
    public static float approach(float current, float target, float ratePerSecond, float dt) {
        float k = 1f - (float) Math.exp(-ratePerSecond * dt);
        return current + (target - current) * k;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }
}
