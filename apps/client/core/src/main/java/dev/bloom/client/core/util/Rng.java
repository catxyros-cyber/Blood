package dev.bloom.client.core.util;

/** Small, fast, allocation-free deterministic RNG (SplitMix64). Not thread-safe. */
public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform in [0, 1). */
    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    public float range(float min, float max) {
        return min + (max - min) * nextFloat();
    }

    public int nextInt(int bound) {
        return (int) ((nextLong() >>> 33) % bound);
    }

    /** Stateless hash of an integer to [0, 1). */
    public static float hash01(long x) {
        long z = x * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 40) * 0x1.0p-24f;
    }
}
