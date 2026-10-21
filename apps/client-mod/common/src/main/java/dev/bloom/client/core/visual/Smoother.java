package dev.bloom.client.core.visual;

/**
 * Frame-rate independent exponential smoothing for a group of values. Used so fog and shader
 * parameters glide to new values (preset switches, slider drags, weather) instead of jumping.
 * Pure Java: no Minecraft classes, unit tested.
 */
public final class Smoother {
    private final float[] value;
    private boolean primed;
    private long lastNanos;

    public Smoother(int size) {
        value = new float[size];
    }

    /**
     * Moves the values towards {@code target}. {@code seconds} is the time constant: after that
     * long about 63% of the gap is closed, after three times as long 95%. A time constant of 0
     * (or the first call) snaps straight to the target. Returns the smoothed values (live array).
     */
    public float[] update(float[] target, float seconds, long nowNanos) {
        if (target.length != value.length) throw new IllegalArgumentException("size");
        float dt = primed ? Math.min(0.25f, Math.max(0f, (nowNanos - lastNanos) / 1_000_000_000f)) : 0f;
        lastNanos = nowNanos;
        if (!primed || seconds <= 0f) {
            System.arraycopy(target, 0, value, 0, value.length);
            primed = true;
            return value;
        }
        float k = 1f - (float) Math.exp(-dt / seconds);
        for (int i = 0; i < value.length; i++) {
            float d = target[i] - value[i];
            value[i] = Math.abs(d) < 1e-4f ? target[i] : value[i] + d * k;
        }
        return value;
    }

    /** True when every value has reached the target given to the last update. */
    public boolean settled(float[] target) {
        for (int i = 0; i < value.length; i++) if (Math.abs(value[i] - target[i]) > 1e-4f) return false;
        return true;
    }

    /** The next update snaps to its target. */
    public void reset() {
        primed = false;
    }

    public float get(int i) {
        return value[i];
    }
}
