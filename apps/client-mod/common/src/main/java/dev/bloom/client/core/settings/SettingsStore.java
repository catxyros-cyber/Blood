package dev.bloom.client.core.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.io.SafeFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Persists {@link BloomSettings} to {@code config/bloomclient/settings.json}, which the
 * BLOOD launcher also reads and edits.
 *
 * <ul>
 *   <li>Changes are saved automatically shortly after they happen (debounced) and on exit.</li>
 *   <li>Writes are atomic (temp file + rename), so a crash never leaves half a file.</li>
 *   <li>Every write bumps {@code revision}. If the launcher edits the file while the game runs,
 *       the change is picked up within a couple of seconds. Keys changed in-game since the last
 *       save are kept; everything else takes the file's (newer) value. Nothing newer is
 *       overwritten by an older copy.</li>
 *   <li>Unknown keys (from a newer BLOOD version) are preserved, never dropped.</li>
 *   <li>Invalid values are ignored one by one; a corrupt file is backed up, never deleted.</li>
 * </ul>
 */
public final class SettingsStore {
    public static final int FORMAT = 1;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    static final long SAVE_DELAY_MS = 750;
    static final long CHECK_INTERVAL_MS = 2000;

    /** An extra top-level block in settings.json (HUD layouts, profile selection…). */
    public interface Section {
        String name();

        JsonElement save();

        /** Load from file. {@code external} is true when the launcher changed the file. */
        void load(JsonElement json, boolean external);
    }

    public interface ExternalChangeListener {
        void externalChange(Set<String> changedKeys, Set<String> changedSections);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> RESERVED = Set.of("format", "revision", "updatedAt", "updatedBy", "settings");

    private final Path file;
    private final List<Setting> settings;
    private final LongSupplier clock;
    private final Map<String, Setting> byKey = new LinkedHashMap<>();
    private final Map<String, Section> sections = new LinkedHashMap<>();
    private final Map<String, JsonElement> unknownSettings = new LinkedHashMap<>();
    private final Map<String, JsonElement> unknownTop = new LinkedHashMap<>();
    private final Set<String> dirtyKeys = new HashSet<>();
    private final Set<String> dirtySections = new HashSet<>();
    private final List<ExternalChangeListener> externalListeners = new ArrayList<>();

    private long revision;
    private long knownMtime = -1;
    private long knownSize = -1;
    private boolean applying;
    private long saveDueAt = -1;
    private long nextCheckAt;
    private String loadProblem;

    public SettingsStore(Path file, List<Setting> settings, LongSupplier clockMs) {
        this.file = file;
        this.settings = settings;
        this.clock = clockMs;
        for (Setting s : settings) {
            byKey.put(s.key(), s);
            s.addListener(this::onSettingChanged);
        }
    }

    public Path file() {
        return file;
    }

    public long revision() {
        return revision;
    }

    /** A message to show once if the file could not be read (null when fine). */
    public String takeLoadProblem() {
        String p = loadProblem;
        loadProblem = null;
        return p;
    }

    public void addSection(Section section) {
        sections.put(section.name(), section);
    }

    public void addExternalChangeListener(ExternalChangeListener l) {
        externalListeners.add(l);
    }

    /** Call when a section's data changed so it gets saved. */
    public void markSectionDirty(String name) {
        if (applying) return;
        dirtySections.add(name);
        scheduleSave();
    }

    /** Saves a section soon even while applying file data (e.g. to clear a one-shot flag). */
    public void requestSave(String section) {
        dirtySections.add(section);
        scheduleSave();
    }

    /** Marks keys for saving even while applying external data (e.g. a profile the launcher selected). */
    public void markDirty(Iterable<String> keys) {
        for (String k : keys) if (byKey.containsKey(k)) dirtyKeys.add(k);
        scheduleSave();
    }

    public boolean isApplying() {
        return applying;
    }

    private void onSettingChanged(Setting s) {
        if (applying) return;
        dirtyKeys.add(s.key());
        scheduleSave();
    }

    private void scheduleSave() {
        saveDueAt = clock.getAsLong() + SAVE_DELAY_MS;
    }

    public boolean hasPendingChanges() {
        return !dirtyKeys.isEmpty() || !dirtySections.isEmpty();
    }

    // ------------------------------------------------------------------ load

    /** Keys that weren't in settings.json at startup (new install, or settings added by an update). */
    private final Set<String> missingAtLoad = new java.util.HashSet<>();

    public Set<String> keysMissingAtLoad() {
        return java.util.Collections.unmodifiableSet(missingAtLoad);
    }

    /** Initial load at startup. Creates the file with defaults if it's missing. */
    public void load() {
        missingAtLoad.clear();
        missingAtLoad.addAll(byKey.keySet());
        if (!Files.exists(file)) {
            for (Section sec : sections.values()) sec.load(null, false);
            forceSave();
            return;
        }
        JsonObject root = readFile(true);
        if (root == null) {
            backupCorrupt();
            for (Section sec : sections.values()) sec.load(null, false);
            forceSave();
            return;
        }
        apply(root, false, null, null);
        JsonElement present = root.get("settings");
        if (present != null && present.isJsonObject()) missingAtLoad.removeAll(present.getAsJsonObject().keySet());
        // Write back if anything was missing or invalid, so the launcher sees full values.
        nextCheckAt = clock.getAsLong() + CHECK_INTERVAL_MS;
    }

    private JsonObject readFile(boolean report) {
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            String text = SafeFiles.readText(file, MAX_FILE_BYTES);
            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject()) throw new IOException("Top level is not an object");
            knownMtime = mtime;
            knownSize = size;
            return el.getAsJsonObject();
        } catch (Exception e) {
            Log.warn("Could not read " + file.getFileName(), e);
            if (report) loadProblem = "BLOOD settings could not be read (" + e.getClass().getSimpleName() + "). Defaults are in use; the old file was kept as a backup.";
            return null;
        }
    }

    private void backupCorrupt() {
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + clock.getAsLong());
            Files.copy(file, backup);
        } catch (IOException e) {
            Log.warn("Could not back up unreadable settings", e);
        }
    }

    /**
     * Applies a parsed file. When {@code external}, keys/sections changed locally since the
     * last save are kept, and the names of everything that changed are collected.
     */
    private void apply(JsonObject root, boolean external, Set<String> changedKeys, Set<String> changedSections) {
        applying = true;
        try {
            if (root.has("revision") && root.get("revision").isJsonPrimitive()) {
                try {
                    revision = Math.max(revision, root.get("revision").getAsLong());
                } catch (NumberFormatException ignored) {
                }
            }
            JsonElement settingsEl = root.get("settings");
            if (settingsEl != null && settingsEl.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : settingsEl.getAsJsonObject().entrySet()) {
                    String key = e.getKey();
                    Setting s = byKey.get(key);
                    if (s == null) {
                        unknownSettings.put(key, e.getValue());
                        continue;
                    }
                    if (external && dirtyKeys.contains(key)) continue;
                    JsonElement before = s.toJson();
                    if (!s.fromJson(e.getValue())) {
                        Log.warn("Ignoring invalid value for " + key);
                        continue;
                    }
                    if (changedKeys != null && !before.equals(s.toJson())) changedKeys.add(key);
                }
            }
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                String name = e.getKey();
                if (RESERVED.contains(name)) continue;
                Section sec = sections.get(name);
                if (sec == null) {
                    unknownTop.put(name, e.getValue());
                    continue;
                }
                if (external && dirtySections.contains(name)) continue;
                JsonElement before = external ? sec.save() : null;
                try {
                    sec.load(e.getValue(), external);
                } catch (RuntimeException ex) {
                    Log.warn("Ignoring invalid '" + name + "' block in settings", ex);
                }
                if (changedSections != null && before != null && !before.equals(sec.save())) changedSections.add(name);
            }
            if (!external) {
                for (Map.Entry<String, Section> e : sections.entrySet()) {
                    if (!root.has(e.getKey())) e.getValue().load(null, false);
                }
            }
        } finally {
            applying = false;
        }
    }

    // ------------------------------------------------------------------ tick

    /** Call once per client tick (or more often); cheap when nothing changed. */
    public void tick() {
        long now = clock.getAsLong();
        if (now >= nextCheckAt) {
            nextCheckAt = now + CHECK_INTERVAL_MS;
            checkExternal();
        }
        if (saveDueAt >= 0 && now >= saveDueAt) save();
    }

    /** Re-reads the file if something else (the launcher) changed it. */
    public void checkExternal() {
        try {
            if (!Files.exists(file)) return;
            long mtime = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            if (mtime == knownMtime && size == knownSize) return;
            JsonObject root = readFile(false);
            if (root == null) {
                // Probably caught mid-write by another program; try again next time.
                knownMtime = -1;
                return;
            }
            long fileRev = 0;
            try {
                if (root.has("revision")) fileRev = root.get("revision").getAsLong();
            } catch (RuntimeException ignored) {
            }
            if (fileRev <= revision && !"launcher".equals(str(root, "updatedBy"))) return;
            Set<String> keys = new HashSet<>();
            Set<String> secs = new HashSet<>();
            apply(root, true, keys, secs);
            if (!keys.isEmpty() || !secs.isEmpty()) {
                Log.info("Applied " + keys.size() + " setting(s) changed outside the game");
                for (ExternalChangeListener l : externalListeners) {
                    try {
                        l.externalChange(keys, secs);
                    } catch (RuntimeException e) {
                        Log.warn("Settings listener failed", e);
                    }
                }
            }
        } catch (IOException e) {
            Log.warn("Could not check settings file", e);
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    // ------------------------------------------------------------------ save

    /** Saves now if anything changed (e.g. when the menu closes or the game stops). */
    public void flush() {
        if (hasPendingChanges()) save();
    }

    private void forceSave() {
        dirtySections.addAll(sections.keySet());
        save();
    }

    private void save() {
        saveDueAt = -1;
        // Pick up anything the launcher wrote since our last look, so it isn't overwritten.
        checkExternal();
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("revision", revision + 1);
        root.addProperty("updatedAt", Instant.ofEpochMilli(clock.getAsLong()).toString());
        root.addProperty("updatedBy", "game");
        JsonObject values = new JsonObject();
        for (Setting s : settings) values.add(s.key(), s.toJson());
        for (Map.Entry<String, JsonElement> e : unknownSettings.entrySet()) {
            if (!values.has(e.getKey())) values.add(e.getKey(), e.getValue());
        }
        root.add("settings", values);
        for (Section sec : sections.values()) {
            try {
                JsonElement el = sec.save();
                if (el != null) root.add(sec.name(), el);
            } catch (RuntimeException e) {
                Log.warn("Could not save '" + sec.name() + "'", e);
            }
        }
        for (Map.Entry<String, JsonElement> e : unknownTop.entrySet()) {
            if (!root.has(e.getKey())) root.add(e.getKey(), e.getValue());
        }
        try {
            SafeFiles.writeAtomic(file, GSON.toJson(root));
            revision = revision + 1;
            knownMtime = Files.getLastModifiedTime(file).toMillis();
            knownSize = Files.size(file);
            dirtyKeys.clear();
            dirtySections.clear();
        } catch (IOException e) {
            Log.warn("Could not save BLOOD settings; will retry", e);
            saveDueAt = clock.getAsLong() + 5000;
        }
    }

    /** Runs {@code r} without marking anything dirty (used when applying external data). */
    public void applyingQuietly(Runnable r) {
        boolean was = applying;
        applying = true;
        try {
            r.run();
        } finally {
            applying = was;
        }
    }
}
