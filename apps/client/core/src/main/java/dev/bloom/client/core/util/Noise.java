package dev.bloom.client.core.util;

/**
 * Deterministic gradient noise (Perlin-style) in 1-3 dimensions plus fractal
 * helpers. Used for wind gusts and procedural skies. Output roughly in [-1, 1].
 */
public final class Noise {
    private final int[] perm = new int[512];

    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Rng rng = new Rng(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float grad1(int h, float x) {
        return (h & 1) == 0 ? x : -x;
    }

    private static float grad3(int hash, float x, float y, float z) {
        int h = hash & 15;
        float u = h < 8 ? x : y;
        float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    public float noise1(float x) {
        int xi = (int) Math.floor(x);
        float xf = x - xi;
        int X = xi & 255;
        float u = fade(xf);
        return MathUtil.lerp(grad1(perm[X], xf), grad1(perm[X + 1], xf - 1), u) * 2f;
    }

    public float noise3(float x, float y, float z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float xf = x - xi, yf = y - yi, zf = z - zi;
        int X = xi & 255, Y = yi & 255, Z = zi & 255;
        float u = fade(xf), v = fade(yf), w = fade(zf);
        int a = perm[X] + Y, aa = perm[a] + Z, ab = perm[a + 1] + Z;
        int b = perm[X + 1] + Y, ba = perm[b] + Z, bb = perm[b + 1] + Z;
        float x1 = MathUtil.lerp(grad3(perm[aa], xf, yf, zf), grad3(perm[ba], xf - 1, yf, zf), u);
        float x2 = MathUtil.lerp(grad3(perm[ab], xf, yf - 1, zf), grad3(perm[bb], xf - 1, yf - 1, zf), u);
        float y1 = MathUtil.lerp(x1, x2, v);
        x1 = MathUtil.lerp(grad3(perm[aa + 1], xf, yf, zf - 1), grad3(perm[ba + 1], xf - 1, yf, zf - 1), u);
        x2 = MathUtil.lerp(grad3(perm[ab + 1], xf, yf - 1, zf - 1), grad3(perm[bb + 1], xf - 1, yf - 1, zf - 1), u);
        float y2 = MathUtil.lerp(x1, x2, v);
        return MathUtil.lerp(y1, y2, w);
    }

    /** Fractal Brownian motion: several octaves of noise3. */
    public float fbm3(float x, float y, float z, int octaves) {
        float sum = 0, amp = 0.5f, freq = 1f, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise3(x * freq, y * freq, z * freq);
            norm += amp;
            amp *= 0.5f;
            freq *= 2.03f;
        }
        return sum / norm;
    }
}
