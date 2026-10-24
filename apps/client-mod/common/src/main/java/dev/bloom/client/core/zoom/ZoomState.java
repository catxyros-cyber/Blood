package dev.bloom.client.core.zoom;

/**
 * Zoom without any Minecraft types, so it can be unit tested: whether zoom is wanted, the target
 * magnification (with scroll adjustments) and the eased value the field of view is divided by.
 */
public final class ZoomState {
    public static final float MIN = 1.5f;
    public static final float MAX = 50f;

    private boolean wanted;
    private boolean toggledOn;
    private boolean wasDown;
    /** Magnification the player scrolled to while zoomed; 0 = use the configured amount. */
    private float scrolled;
    /** Current eased magnification (1 = no zoom). */
    private float current = 1f;
    private long lastNanos = -1;

    /**
     * Feed the key state once per tick.
     *
     * @param down   is the Zoom key held right now
     * @param toggle "toggle instead of hold"
     * @param allowed false while a screen is open or there's no player: zoom ends
     */
    public void key(boolean down, boolean toggle, boolean allowed) {
        if (!allowed) {
            wanted = false;
            toggledOn = false;
            wasDown = down;
            scrolled = 0;
            return;
        }
        if (toggle) {
            if (down && !wasDown) toggledOn = !toggledOn;
            wanted = toggledOn;
        } else {
            toggledOn = false;
            wanted = down;
        }
        wasDown = down;
        if (!wanted) scrolled = 0;
    }

    public boolean wanted() {
        return wanted;
    }

    /** True while zoomed in at all (including easing out). */
    public boolean active() {
        return wanted || current > 1.001f;
    }

    /** Mouse wheel while zoomed: each notch multiplies or divides the magnification by 1.25. */
    public void scroll(double notches, float configured) {
        if (!wanted || notches == 0) return;
        float base = scrolled > 0 ? scrolled : configured;
        float next = (float) (base * Math.pow(1.25, notches));
        scrolled = clamp(next, MIN, MAX);
    }

    public float target(float configured) {
        return wanted ? (scrolled > 0 ? scrolled : clamp(configured, MIN, MAX)) : 1f;
    }

    /**
     * Advance the easing and return the field-of-view multiplier (1 = unchanged, 0.25 = 4× zoom).
     *
     * @param smooth ease in/out; otherwise snap
     */
    public double fovMultiplier(long nanos, float configured, boolean smooth) {
        float target = target(configured);
        if (!smooth || lastNanos < 0) {
            current = smooth && lastNanos < 0 ? current : target;
        } else {
            double dt = Math.min(0.1, Math.max(0, (nanos - lastNanos) / 1e9));
            // Exponential approach in log space so 2× → 8× feels as quick as 1× → 4×.
            double k = 1 - Math.exp(-dt * 14);
            double logC = Math.log(current), logT = Math.log(target);
            current = (float) Math.exp(logC + (logT - logC) * k);
            if (Math.abs(current - target) < 0.002f) current = target;
        }
        lastNanos = nanos;
        return 1.0 / current;
    }

    /** Current magnification, for the on-screen indicator. */
    public float magnification() {
        return current;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
