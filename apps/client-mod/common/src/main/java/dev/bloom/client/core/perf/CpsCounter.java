package dev.bloom.client.core.perf;

/** Counts clicks during the last second, per button. */
public final class CpsCounter {
    private static final int MAX = 128;
    private final long[][] clicks = new long[2][MAX];
    private final int[] heads = new int[2];
    private final int[] sizes = new int[2];

    /** button 0 = left, 1 = right. */
    public void click(int button, long nowMs) {
        if (button < 0 || button > 1) return;
        clicks[button][heads[button]] = nowMs;
        heads[button] = (heads[button] + 1) % MAX;
        if (sizes[button] < MAX) sizes[button]++;
    }

    public int cps(int button, long nowMs) {
        if (button < 0 || button > 1) return 0;
        int n = 0;
        for (int i = 0; i < sizes[button]; i++) {
            int idx = Math.floorMod(heads[button] - 1 - i, MAX);
            if (nowMs - clicks[button][idx] >= 1000) break;
            n++;
        }
        return n;
    }
}
