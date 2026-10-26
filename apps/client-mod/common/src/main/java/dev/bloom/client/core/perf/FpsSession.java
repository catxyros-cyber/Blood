package dev.bloom.client.core.perf;

/**
 * Frame times of one stretch of gameplay, kept as a histogram so a session of any length costs
 * the same memory and each frame costs one array increment. Gives the numbers players compare:
 * average FPS and the "1% low" (the frame rate of the slowest 1% of frames, which is what
 * stutter feels like).
 */
public final class FpsSession {
    /** Bucket width: 0.05 ms resolves 1000 FPS to within 5%. */
    static final double BUCKET_MS = 0.05;
    /** Frames slower than 100 ms share one bucket, whose exact total is kept separately. */
    static final int BUCKETS = 2000;

    private final int[] hist = new int[BUCKETS];
    private long slowFrames;
    private double slowMs;
    private long frames;
    private double totalMs;

    /** Adds one frame that took {@code ms} milliseconds. */
    public void frame(double ms) {
        if (!(ms > 0)) return;
        frames++;
        totalMs += ms;
        int b = (int) (ms / BUCKET_MS);
        if (b < BUCKETS) hist[b]++;
        else {
            slowFrames++;
            slowMs += ms;
        }
    }

    public long frames() {
        return frames;
    }

    /** Gameplay time covered, in seconds. */
    public double seconds() {
        return totalMs / 1000.0;
    }

    /** Frames per second over the whole session (0 without data). */
    public double averageFps() {
        return frames == 0 ? 0 : frames * 1000.0 / totalMs;
    }

    /**
     * The frame rate of the slowest {@code fraction} of frames (0.01 for the "1% low"): the
     * average time of those frames, as FPS. 0 without data.
     */
    public double lowFps(double fraction) {
        if (frames == 0) return 0;
        long want = Math.max(1, Math.round(frames * fraction));
        long n = 0;
        double ms = 0;
        if (slowFrames > 0) {
            long take = Math.min(slowFrames, want);
            ms += slowMs / slowFrames * take;
            n += take;
        }
        for (int b = BUCKETS - 1; b >= 0 && n < want; b--) {
            if (hist[b] == 0) continue;
            long take = Math.min(hist[b], want - n);
            ms += take * (b + 0.5) * BUCKET_MS;
            n += take;
        }
        return n == 0 ? 0 : 1000.0 / (ms / n);
    }
}
