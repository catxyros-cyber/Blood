package dev.bloom.client.core.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.io.SafeFiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Waypoints per world (a server address or a singleplayer world name), saved to
 * config/bloomclient/waypoints.json. Pure Java so it can be unit tested.
 */
public final class WaypointStore {
    public static final int MAX_PER_WORLD = 200;
    public static final String DEATH_NAME = "Death";
    /** Distinct, readable marker colours handed out in turn. */
    private static final int[] PALETTE = {0x39FF88, 0x4FC3F7, 0xFFD54F, 0xFF7043, 0xBA68C8, 0xF06292, 0x81C784, 0xE0E0E0};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Waypoint(String name, int x, int y, int z, String dimension, int color, boolean death, long createdAt) {
        public Waypoint {
            name = clean(name);
            dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension;
        }

        public Waypoint renamed(String newName) {
            return new Waypoint(newName, x, y, z, dimension, color, death, createdAt);
        }

        public double distanceTo(double px, double py, double pz) {
            double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private final Path file;
    private final Map<String, List<Waypoint>> worlds = new LinkedHashMap<>();
    private boolean dirty;

    public WaypointStore(Path file) {
        this.file = file;
    }

    static String clean(String name) {
        String n = name == null ? "" : name.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").trim();
        if (n.isEmpty()) n = "Waypoint";
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    public List<Waypoint> list(String world) {
        List<Waypoint> l = worlds.get(world);
        return l == null ? List.of() : Collections.unmodifiableList(l);
    }

    /** Waypoints in this world and dimension. */
    public List<Waypoint> visible(String world, String dimension) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : list(world)) if (w.dimension().equals(dimension)) out.add(w);
        return out;
    }

    /** "Waypoint 1", "Waypoint 2", … skipping names already used. */
    public String nextName(String world) {
        for (int i = 1; ; i++) {
            String n = "Waypoint " + i;
            boolean taken = false;
            for (Waypoint w : list(world)) if (w.name().equalsIgnoreCase(n)) taken = true;
            if (!taken) return n;
        }
    }

    public Waypoint add(String world, String name, int x, int y, int z, String dimension, long now) {
        List<Waypoint> l = worlds.computeIfAbsent(world, k -> new ArrayList<>());
        int normal = 0;
        for (Waypoint w : l) if (!w.death()) normal++;
        if (normal >= MAX_PER_WORLD) throw new IllegalStateException("You can have up to " + MAX_PER_WORLD + " waypoints per world.");
        Waypoint w = new Waypoint(name == null ? nextName(world) : name, x, y, z, dimension, PALETTE[normal % PALETTE.length], false, now);
        l.add(w);
        dirty = true;
        return w;
    }

    /** Replace the death waypoint for this world (there is at most one). */
    public Waypoint setDeath(String world, int x, int y, int z, String dimension, long now) {
        List<Waypoint> l = worlds.computeIfAbsent(world, k -> new ArrayList<>());
        l.removeIf(Waypoint::death);
        Waypoint w = new Waypoint(DEATH_NAME, x, y, z, dimension, 0xFF5252, true, now);
        l.add(w);
        dirty = true;
        return w;
    }

    public boolean remove(String world, Waypoint w) {
        List<Waypoint> l = worlds.get(world);
        boolean removed = l != null && l.remove(w);
        if (removed) {
            dirty = true;
            if (l.isEmpty()) worlds.remove(world);
        }
        return removed;
    }

    public boolean rename(String world, Waypoint w, String name) {
        List<Waypoint> l = worlds.get(world);
        if (l == null) return false;
        int i = l.indexOf(w);
        if (i < 0) return false;
        l.set(i, w.renamed(name));
        dirty = true;
        return true;
    }

    /** Drop the death waypoint once the player gets within {@code radius} blocks of it. */
    public boolean clearDeathIfReached(String world, double px, double py, double pz, String dimension, double radius) {
        List<Waypoint> l = worlds.get(world);
        if (l == null) return false;
        boolean changed = l.removeIf(w -> w.death() && w.dimension().equals(dimension) && w.distanceTo(px, py, pz) <= radius);
        if (changed) dirty = true;
        return changed;
    }

    public boolean dirty() {
        return dirty;
    }

    // ------------------------------------------------------------------ persistence

    public void load() {
        worlds.clear();
        dirty = false;
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            String text = new String(SafeFiles.readLimited(file, 4L * 1024 * 1024), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonObject ws = root.has("worlds") && root.get("worlds").isJsonObject() ? root.getAsJsonObject("worlds") : new JsonObject();
            for (Map.Entry<String, JsonElement> e : ws.entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                List<Waypoint> l = new ArrayList<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    try {
                        JsonObject o = el.getAsJsonObject();
                        l.add(new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
                                o.has("dimension") ? o.get("dimension").getAsString() : null,
                                o.has("color") ? o.get("color").getAsInt() & 0xFFFFFF : PALETTE[0],
                                o.has("death") && o.get("death").getAsBoolean(),
                                o.has("createdAt") ? o.get("createdAt").getAsLong() : 0L));
                    } catch (RuntimeException bad) {
                        // Skip a damaged entry, keep the rest.
                    }
                    if (l.size() >= MAX_PER_WORLD + 1) break;
                }
                if (!l.isEmpty()) worlds.put(e.getKey(), l);
            }
        } catch (Exception e) {
            Log.warn("Could not read waypoints.json (" + e + ")");
        }
    }

    public void save() {
        if (file == null) return;
        JsonObject ws = new JsonObject();
        for (Map.Entry<String, List<Waypoint>> e : worlds.entrySet()) {
            JsonArray arr = new JsonArray();
            for (Waypoint w : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name());
                o.addProperty("x", w.x());
                o.addProperty("y", w.y());
                o.addProperty("z", w.z());
                o.addProperty("dimension", w.dimension());
                o.addProperty("color", w.color());
                if (w.death()) o.addProperty("death", true);
                o.addProperty("createdAt", w.createdAt());
                arr.add(o);
            }
            ws.add(e.getKey(), arr);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("worlds", ws);
        try {
            Files.createDirectories(file.getParent());
            SafeFiles.writeAtomic(file, GSON.toJson(root));
            dirty = false;
        } catch (Exception e) {
            Log.warn("Could not save waypoints.json (" + e + ")");
        }
    }
}
