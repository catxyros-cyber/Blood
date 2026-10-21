package dev.bloom.client.core.particles;

/**
 * Decorative wind. Direction uses Minecraft's yaw convention (0° = towards +Z/south,
 * 90° = towards -X/west). Strength wanders slowly, gusts sweep through now and then, and a
 * little spatial turbulence keeps particles from moving in lockstep. Affects only BLOOD's
 * own particles.
 */
public final class WindField {
    /** Blocks per tick at 100% strength (before gusts). */
    public static final float MAX_SPEED = 0.22f;

    private float baseX;
    private float baseZ;
    private float gust = 1f;
    private float speed;
    private float variation;

    /**
     * Recomputes the wind for this tick.
     *
     * @param timeTicks  continuous game-independent time in ticks
     */
    public void update(boolean enabled, float strength, float directionDeg, float variation, float gustiness, float timeTicks) {
        if (!enabled || strength <= 0) {
            baseX = baseZ = 0;
            speed = 0;
            gust = 1;
            this.variation = 0;
            return;
        }
        this.variation = variation;
        float t = timeTicks / 20f; // seconds
        float wander = Noise.noise1(t * 0.05f, 911) * 60f * variation; // up to ±60°
        float yaw = (float) Math.toRadians(directionDeg + wander);
        float strengthWander = 1f + Noise.noise1(t * 0.11f, 313) * 0.3f * variation;
        // Gusts: a slow noise above a threshold that drops as gustiness rises.
        float g = (Noise.noise1(t * 0.18f, 777) + 1f) * 0.5f; // 0..1
        float threshold = 1f - gustiness * 0.55f;
        float gustAmount = g > threshold ? (g - threshold) / (1f - threshold + 1e-4f) : 0f;
        gust = 1f + gustAmount * 1.6f;
        speed = MAX_SPEED * strength * strengthWander;
        baseX = (float) -Math.sin(yaw) * speed;
        baseZ = (float) Math.cos(yaw) * speed;
    }

    public float speed() {
        return speed * gust;
    }

    public float gust() {
        return gust;
    }

    public float baseX() {
        return baseX * gust;
    }

    public float baseZ() {
        return baseZ * gust;
    }

    /** Wind at a point (blocks/tick) into out[0], out[1] (x, z) and a small vertical lift out[2]. */
    public void sample(float x, float y, float z, float timeTicks, float[] out) {
        if (speed == 0) {
            out[0] = out[1] = out[2] = 0;
            return;
        }
        float s = 0.06f;
        float t = timeTicks * 0.01f;
        float turb = 0.35f + 0.4f * variation;
        out[0] = baseX * gust + Noise.noise3(x * s, y * s + t, z * s, 17) * speed * turb;
        out[1] = baseZ * gust + Noise.noise3(x * s + 31.7f, y * s, z * s + t, 23) * speed * turb;
        out[2] = Noise.noise3(x * s, y * s - t, z * s + 11.3f, 29) * speed * 0.25f;
    }
}
