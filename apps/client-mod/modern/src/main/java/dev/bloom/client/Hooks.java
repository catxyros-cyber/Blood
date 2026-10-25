package dev.bloom.client;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which of MOTION's mixin hooks have actually run. Only tracked in the CI smoke test
 * ({@code -Dmotion.smoke=true}): most hooks are optional so a game update can't crash players,
 * which also means a hook that stopped matching fails silently. The smoke test exercises the game
 * and reports any hook that never fired.
 */
public final class Hooks {
    public static final boolean TRACK = Boolean.getBoolean("motion.smoke");

    /** Every hook, by the name its mixin reports. */
    public static final List<String> ALL = List.of(
            "skin", "cape", "avatarState", "freeLook", "zoomFov", "timeOfDay", "clouds", "fog",
            "frame", "postEffects", "crosshair", "swing", "loadingScreen", "matrices", "sky",
            "loadingOverlay", "titleScreen", "reload", "mouseButton", "turnPlayer", "scroll",
            "attack", "displayName", "tabName", "screenBackground", "weather");

    private static final Set<String> FIRED = ConcurrentHashMap.newKeySet();

    private Hooks() {
    }

    public static void fired(String hook) {
        if (TRACK) FIRED.add(hook);
    }

    public static Set<String> notFired() {
        Set<String> out = new TreeSet<>(ALL);
        out.removeAll(FIRED);
        return out;
    }

    public static Set<String> firedSet() {
        return new TreeSet<>(FIRED);
    }
}
