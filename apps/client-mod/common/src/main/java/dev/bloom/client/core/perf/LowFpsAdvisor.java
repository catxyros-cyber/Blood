package dev.bloom.client.core.perf;

import java.util.ArrayList;
import java.util.List;

/**
 * Watches the measured FPS and, when it stays below the threshold for a while, produces a
 * suggestion listing the heaviest MOTION effects that are on. It only suggests: the player
 * decides, and nothing is changed automatically. It won't nag: after a suggestion it waits
 * 10 minutes, and "Don't show again this session" silences it.
 */
public final class LowFpsAdvisor {
    public static final long SUSTAIN_MS = 15_000;
    public static final long COOLDOWN_MS = 10 * 60_000;

    /** An effect that could be lowered, with a rough relative cost (for ordering only). */
    public record Candidate(String settingKey, String label, int weight) {
    }

    private long belowSince = -1;
    private long lastShown = Long.MIN_VALUE / 2;
    private boolean silenced;

    public void silence() {
        silenced = true;
    }

    /**
     * @param fps        measured FPS this second
     * @param threshold  the player's threshold
     * @param active     effects currently on (only these can be suggested)
     * @param inGame     false while paused or in menus (FPS there isn't representative)
     * @return a suggestion (up to three effects) or null
     */
    public List<Candidate> update(long nowMs, int fps, int threshold, List<Candidate> active, boolean inGame) {
        if (silenced || !inGame || fps <= 0) {
            belowSince = -1;
            return null;
        }
        if (fps >= threshold) {
            belowSince = -1;
            return null;
        }
        if (belowSince < 0) belowSince = nowMs;
        if (nowMs - belowSince < SUSTAIN_MS || nowMs - lastShown < COOLDOWN_MS) return null;
        if (active.isEmpty()) return null;
        List<Candidate> sorted = new ArrayList<>(active);
        sorted.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        lastShown = nowMs;
        belowSince = -1;
        return sorted.subList(0, Math.min(3, sorted.size()));
    }
}
