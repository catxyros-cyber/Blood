package dev.bloom.client.core.perf;

/**
 * Measures real frame times (no estimates). The game layer calls {@link #frame(long)} once
 * per rendered frame with {@code System.nanoTime()}.
 */
public final class FrameStats {
    /** About 10 seconds at 240 FPS. */
    public static final int CAPACITY = 2400;
    private final long[] times = new long[CAPACITY]; // frame durations (ns)
    private final long[] stamps = new long[CAPACITY]; // frame end times (ns)
    private int head; // next write index
    private int size;
    private long last = -1;

    public void frame(long nowNs) {
        if (last >= 0) {
            long dt = nowNs - last;
            if (dt > 0 && dt < 5_000_000_000L) { // ignore pauses longer than 5 s (e.g. window dragged)
                times[head] = dt;
                stamps[head] = nowNs;
                head = (head + 1) % CAPACITY;
                if (size < CAPACITY) size++;
            }
        }
        last = nowNs;
    }

    public void reset() {
        head = 0;
        size = 0;
        last = -1;
    }

    public int samples() {
        return size;
    }

    /** Frames completed during the last second (Minecraft's own definition of FPS). */
    public int fps(long nowNs) {
        int n = 0;
        for (int i = 0; i < size; i++) {
            int idx = Math.floorMod(head - 1 - i, CAPACITY);
            if (nowNs - stamps[idx] > 1_000_000_000L) break;
            n++;
        }
        return n;
    }

    /** Mean frame time over the last {@code windowNs}, in milliseconds (0 when no data). */
    public double averageMs(long nowNs, long windowNs) {
        long sum = 0;
        int n = 0;
        for (int i = 0; i < size; i++) {
            int idx = Math.floorMod(head - 1 - i, CAPACITY);
            if (nowNs - stamps[idx] > windowNs) break;
            sum += times[idx];
            n++;
        }
        return n == 0 ? 0 : sum / (double) n / 1_000_000.0;
    }

    /** Slowest frame in the window, in milliseconds. */
    public double worstMs(long nowNs, long windowNs) {
        long worst = 0;
        for (int i = 0; i < size; i++) {
            int idx = Math.floorMod(head - 1 - i, CAPACITY);
            if (nowNs - stamps[idx] > windowNs) break;
            worst = Math.max(worst, times[idx]);
        }
        return worst / 1_000_000.0;
    }

    /**
     * "1% low" FPS: the frame rate implied by the slowest 1% of frames in the window
     * (0 when fewer than 20 frames were measured).
     */
    public double onePercentLowFps(long nowNs, long windowNs) {
        int n = 0;
        long[] buf = new long[size];
        for (int i = 0; i < size; i++) {
            int idx = Math.floorMod(head - 1 - i, CAPACITY);
            if (nowNs - stamps[idx] > windowNs) break;
            buf[n++] = times[idx];
        }
        if (n < 20) return 0;
        java.util.Arrays.sort(buf, 0, n);
        int count = Math.max(1, n / 100);
        long sum = 0;
        for (int i = n - count; i < n; i++) sum += buf[i];
        double avgNs = sum / (double) count;
        return 1_000_000_000.0 / avgNs;
    }

    /** Copies the most recent {@code out.length} frame times (ms, oldest first) for a graph. */
    public int recentMs(float[] out) {
        int n = Math.min(out.length, size);
        for (int i = 0; i < n; i++) {
            int idx = Math.floorMod(head - n + i, CAPACITY);
            out[i] = times[idx] / 1_000_000f;
        }
        return n;
    }
}
