package dev.bloom.client.core.particles;

/**
 * Temporarily scales particle counts down while the measured FPS is under the target, and
 * back up when there's headroom. Runtime only: saved settings are never changed.
 */
public final class AdaptiveBudget {
    public static final float MIN = 0.2f;
    private float factor = 1f;

    /** Call about once a second with the FPS measured over the last second. */
    public float update(boolean enabled, int fps, int target) {
        if (!enabled || fps <= 0) {
            factor = Math.min(1f, factor + 0.1f);
            if (!enabled) factor = 1f;
            return factor;
        }
        if (fps < target * 0.95f) {
            float deficit = 1f - fps / (float) target; // 0..1
            factor = Math.max(MIN, factor * (1f - Math.min(0.3f, deficit)));
        } else if (fps > target * 1.12f) {
            factor = Math.min(1f, factor + 0.05f);
        }
        return factor;
    }

    public float factor() {
        return factor;
    }
}
