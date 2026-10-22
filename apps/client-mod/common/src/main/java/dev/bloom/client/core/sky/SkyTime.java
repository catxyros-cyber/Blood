package dev.bloom.client.core.sky;

/**
 * Minecraft's celestial maths, version-independent. Newer Minecraft versions moved these into
 * "environment attributes"; MOTION only needs the classic Overworld curve, so it computes it from
 * the day time itself.
 */
public final class SkyTime {
    private SkyTime() {
    }

    /** Fraction of the day for the sun's angle: 0 = noon, 0.5 = midnight (vanilla's eased curve). */
    public static float timeOfDay(long dayTime, float partialTick) {
        double d = ((Math.floorMod(dayTime, 24000L) + partialTick) / 24000.0) - 0.25;
        d = d - Math.floor(d);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (float) ((d * 2.0 + e) / 3.0);
    }

    /** 0..7, the same order as vanilla's moon phase textures. */
    public static int moonPhase(long dayTime) {
        return (int) Math.floorMod(Math.floorDiv(dayTime, 24000L), 8L);
    }

    /** Vanilla's star brightness for a time of day (0 by day, up to 0.5 at night). */
    public static float starBrightness(float timeOfDay) {
        float f = 1f - ((float) Math.cos(timeOfDay * Math.PI * 2) * 2f + 0.25f);
        f = Math.max(0f, Math.min(1f, f));
        return f * f * 0.5f;
    }

    /** Vanilla's daylight curve (1 by day, 0 at night). */
    public static float daylight(float timeOfDay) {
        float v = (float) Math.cos(timeOfDay * Math.PI * 2) * 2f + 0.5f;
        return Math.max(0f, Math.min(1f, v));
    }
}
