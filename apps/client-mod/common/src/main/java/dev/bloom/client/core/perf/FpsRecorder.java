package dev.bloom.client.core.perf;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bloom.client.core.io.SafeFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Real FPS per play session, for the launcher's before/after comparison. Only gameplay counts:
 * frames in a world with no menu open, not paused and with the window focused. A session splits
 * whenever what it runs under changes (render distance, FPS limit, VSync, shaders, the mod list,
 * window size, singleplayer or a server), so every saved number is comparable with its context.
 * Sessions shorter than {@link #MIN_SECONDS} of gameplay aren't saved. Pure Java, unit tested.
 */
public final class FpsRecorder {
    public static final double MIN_SECONDS = 30;
    /** The minimum actually applied (the CI smoke test lowers it with -Dmotion.fps.minSeconds). */
    private static final double MIN_APPLIED = Math.max(0.5, parseOr(System.getProperty("motion.fps.minSeconds"), MIN_SECONDS));

    private static double parseOr(String s, double fallback) {
        try {
            return s == null ? fallback : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
    /** Sessions kept in the log file. */
    public static final int KEEP = 40;

    private FpsSession session;
    private Map<String, Object> context;
    private long startedAt;
    private long lastNs = -1;
    private boolean lastGameplay;

    /** Every rendered frame. {@code gameplay}: in a world, no menu, not paused, window focused. */
    public void frame(long nowNs, boolean gameplay) {
        long last = lastNs;
        lastNs = nowNs;
        boolean counted = gameplay && lastGameplay && session != null && last >= 0;
        lastGameplay = gameplay;
        if (!counted) return; // the first frame back from a menu has no meaningful duration
        double ms = (nowNs - last) / 1_000_000.0;
        if (ms < 1000) session.frame(ms); // a frozen window isn't a frame rate
    }

    /**
     * About once a second: {@code ctx} describes what the game runs under (null when not in a
     * world). Starts, splits or ends the session; returns a finished session to save, or null.
     */
    public JsonObject update(Map<String, Object> ctx, long nowMs) {
        if (ctx == null) return finish(nowMs);
        if (session != null && Objects.equals(ctx, context)) return null;
        JsonObject done = finish(nowMs);
        session = new FpsSession();
        context = Map.copyOf(ctx);
        startedAt = nowMs;
        return done;
    }

    /** Ends the current session (leaving a world, quitting). Returns it if long enough to keep. */
    public JsonObject finish(long nowMs) {
        FpsSession s = session;
        Map<String, Object> ctx = context;
        session = null;
        context = null;
        if (s == null || s.seconds() < MIN_APPLIED) return null;
        JsonObject o = new JsonObject();
        o.addProperty("startedAt", startedAt);
        o.addProperty("endedAt", nowMs);
        o.addProperty("seconds", Math.round(s.seconds()));
        o.addProperty("frames", s.frames());
        o.addProperty("avgFps", round1(s.averageFps()));
        o.addProperty("low1Fps", round1(s.lowFps(0.01)));
        o.addProperty("low01Fps", round1(s.lowFps(0.001)));
        JsonElement c = new Gson().toJsonTree(ctx);
        if (c.isJsonObject()) for (Map.Entry<String, JsonElement> e : c.getAsJsonObject().entrySet()) o.add(e.getKey(), e.getValue());
        return o;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** Appends a session to the log ({"version":1,"sessions":[…]}), keeping the last {@link #KEEP}. */
    public static void append(Path file, JsonObject entry) throws IOException {
        JsonArray sessions = new JsonArray();
        if (Files.isRegularFile(file)) {
            try {
                JsonElement root = JsonParser.parseString(SafeFiles.readText(file, 2_000_000));
                if (root.isJsonObject() && root.getAsJsonObject().get("sessions") instanceof JsonArray a) sessions = a;
            } catch (RuntimeException | IOException e) {
                // A damaged log starts over rather than blocking new sessions.
            }
        }
        sessions.add(entry);
        JsonArray kept = new JsonArray();
        for (int i = Math.max(0, sessions.size() - KEEP); i < sessions.size(); i++) kept.add(sessions.get(i));
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("sessions", kept);
        Files.createDirectories(file.getParent());
        SafeFiles.writeAtomic(file, root.toString().getBytes(StandardCharsets.UTF_8));
    }
}
