package dev.bloom.client.core.particles;

/** Small deterministic value noise (smooth, cheap, no allocation). */
public final class Noise {
    private Noise() {
    }

    private static float hash(int x, int y, int z, int seed) {
        int h = seed;
        h ^= x * 0x27D4EB2D;
        h = Integer.rotateLeft(h, 13) * 0x165667B1;
        h ^= y * 0x61C88647;
        h = Integer.rotateLeft(h, 11) * 0x27D4EB2F;
        h ^= z * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF; // 0..1
    }

    private static float fade(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** 1D noise in [-1, 1]. */
    public static float noise1(float x, int seed) {
        int xi = (int) Math.floor(x);
        float t = fade(x - xi);
        return lerp(hash(xi, 0, 0, seed), hash(xi + 1, 0, 0, seed), t) * 2f - 1f;
    }

    /** 3D noise in [-1, 1]. */
    public static float noise3(float x, float y, float z, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float tx = fade(x - xi), ty = fade(y - yi), tz = fade(z - zi);
        float c000 = hash(xi, yi, zi, seed), c100 = hash(xi + 1, yi, zi, seed);
        float c010 = hash(xi, yi + 1, zi, seed), c110 = hash(xi + 1, yi + 1, zi, seed);
        float c001 = hash(xi, yi, zi + 1, seed), c101 = hash(xi + 1, yi, zi + 1, seed);
        float c011 = hash(xi, yi + 1, zi + 1, seed), c111 = hash(xi + 1, yi + 1, zi + 1, seed);
        float x00 = lerp(c000, c100, tx), x10 = lerp(c010, c110, tx);
        float x01 = lerp(c001, c101, tx), x11 = lerp(c011, c111, tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz) * 2f - 1f;
    }
}
