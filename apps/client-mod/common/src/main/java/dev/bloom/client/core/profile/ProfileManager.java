package dev.bloom.client.core.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.io.SafeFiles;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.core.settings.SettingsStore;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Visual profiles: the eight built-ins plus the player's own, stored as JSON files in
 * {@code config/bloomclient/profiles/}. Editing a profile-scoped setting updates the active
 * profile automatically. Deleted profiles are moved to {@code profiles/deleted/}, never erased.
 */
public final class ProfileManager implements SettingsStore.Section {
    public static final String SECTION = "profiles";
    public static final int MAX_PROFILES = 200;
    static final long SAVE_DELAY_MS = 1000;
    static final long BIOME_SWITCH_DELAY_MS = 2500;

    public enum Reason { MANUAL, BIOME, EXTERNAL, RESET, STARTUP }

    public interface Listener {
        void switched(Profile profile, Reason reason);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path dir;
    private final List<Setting> settings;
    private final SettingsStore store;
    private final LongSupplier clock;
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Map<String, Profile> definitions = new LinkedHashMap<>();
    private final Map<String, String> biomeRules = new LinkedHashMap<>();
    private final Set<String> dirtyProfiles = new HashSet<>();
    private final List<Listener> listeners = new ArrayList<>();

    private String selected = BuiltInProfiles.DEFAULT_ID;
    private String active = BuiltInProfiles.DEFAULT_ID;
    private boolean applyingProfile;
    private boolean ready;
    private long saveDueAt = -1;
    private String pendingBiomeTarget;
    private long pendingBiomeSince;

    public ProfileManager(Path dir, List<Setting> settings, SettingsStore store, LongSupplier clockMs) {
        this.dir = dir;
        this.settings = settings;
        this.store = store;
        this.clock = clockMs;
        for (Profile p : BuiltInProfiles.create()) definitions.put(p.id(), p);
        for (Setting s : settings) s.addListener(this::onSettingChanged);
        store.addSection(this);
        scan();
    }

    /** Call after the settings store finished its initial load. */
    public void ready() {
        adoptNewSettings();
        ready = true;
    }

    /**
     * Settings that weren't in settings.json yet (a new install, or options added by an update)
     * take their value from the active profile, so e.g. the default "Blood Red" look includes its
     * shader effects from the first start. Settings the player already has are never touched.
     */
    private void adoptNewSettings() {
        Profile p = profiles.get(active);
        if (p == null) return;
        List<String> keys = new ArrayList<>();
        applyingProfile = true;
        try {
            for (Setting s : settings) {
                if (!s.profileScoped() || !store.keysMissingAtLoad().contains(s.key())) continue;
                JsonElement v = p.values().get(s.key());
                if (v != null && s.fromJson(v)) keys.add(s.key());
            }
        } finally {
            applyingProfile = false;
        }
        if (!keys.isEmpty()) store.markDirty(keys);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    // ------------------------------------------------------------------ queries

    public List<Profile> all() {
        return List.copyOf(profiles.values());
    }

    public Profile get(String id) {
        return profiles.get(id);
    }

    public Profile active() {
        Profile p = profiles.get(active);
        return p != null ? p : profiles.get(BuiltInProfiles.DEFAULT_ID);
    }

    public String selectedId() {
        return selected;
    }

    public Map<String, String> biomeRules() {
        return Map.copyOf(biomeRules);
    }

    // ------------------------------------------------------------------ files

    /** Loads built-in definitions and every profile file. */
    public void scan() {
        profiles.clear();
        for (Profile def : definitions.values()) {
            profiles.put(def.id(), copyOf(def));
        }
        List<Profile> user = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            int count = 0;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
                for (Path f : ds) {
                    if (++count > MAX_PROFILES) {
                        Log.warn("Too many profile files; only the first " + MAX_PROFILES + " were loaded");
                        break;
                    }
                    String name = f.getFileName().toString();
                    String id = name.substring(0, name.length() - 5);
                    if (!SafeFiles.isSafeName(id)) continue;
                    readProfileFile(f, id, user);
                }
            } catch (IOException e) {
                Log.warn("Could not list profiles", e);
            }
        }
        user.sort(Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT)));
        for (Profile p : user) profiles.put(p.id(), p);
        if (!profiles.containsKey(selected)) selected = BuiltInProfiles.DEFAULT_ID;
        if (!profiles.containsKey(active)) active = selected;
    }

    private void readProfileFile(Path f, String id, List<Profile> user) {
        try {
            JsonObject root = JsonParser.parseString(SafeFiles.readText(f, ProfileCodec.MAX_IMPORT_BYTES)).getAsJsonObject();
            JsonElement basedOn = root.get("basedOn");
            JsonObject values = sanitize(root.get("values"));
            if (definitions.containsKey(id)) {
                // Player's edits to a built-in profile.
                if (basedOn == null || !id.equals(basedOn.getAsString())) return;
                Profile def = definitions.get(id);
                Profile p = new Profile(id, def.name(), def.description(), true, values, str(root, "hudLayout"));
                p.setModified(true);
                profiles.put(id, p);
                return;
            }
            String name = ProfileCodec.cleanName(str(root, "name"));
            if (name.isEmpty()) name = id;
            user.add(new Profile(id, name, ProfileCodec.cleanText(str(root, "description"), 160), false, values, str(root, "hudLayout")));
        } catch (Exception e) {
            Log.warn("Skipping unreadable profile " + f.getFileName(), e);
        }
    }

    private JsonObject sanitize(JsonElement el) {
        JsonObject out = new JsonObject();
        if (el == null || !el.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            Setting s = BloomSettings.get(e.getKey());
            if (s != null && s.profileScoped() && s.accepts(e.getValue())) out.add(e.getKey(), e.getValue());
        }
        return out;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static Profile copyOf(Profile p) {
        return new Profile(p.id(), p.name(), p.description(), p.builtIn(), p.copyValues(), p.hudLayout());
    }

    private void writeProfile(Profile p) throws IOException {
        JsonObject o = ProfileCodec.toJson(p);
        if (p.builtIn()) o.addProperty("basedOn", p.id());
        SafeFiles.writeAtomic(SafeFiles.resolveInside(dir, p.id() + ".json"), GSON.toJson(o));
    }

    /** Moves a profile file into profiles/deleted/ instead of erasing it. */
    private void retireFile(String id) throws IOException {
        Path f = SafeFiles.resolveInside(dir, id + ".json");
        if (!Files.exists(f)) return;
        Path trash = dir.resolve("deleted");
        Files.createDirectories(trash);
        Files.move(f, SafeFiles.uniqueIn(trash, id + "-" + clock.getAsLong(), ".json"));
    }

    public void tick() {
        if (saveDueAt >= 0 && clock.getAsLong() >= saveDueAt) flush();
    }

    public void flush() {
        saveDueAt = -1;
        for (String id : List.copyOf(dirtyProfiles)) {
            Profile p = profiles.get(id);
            if (p == null) {
                dirtyProfiles.remove(id);
                continue;
            }
            try {
                writeProfile(p);
                dirtyProfiles.remove(id);
            } catch (IOException e) {
                Log.warn("Could not save profile " + id, e);
                saveDueAt = clock.getAsLong() + 5000;
            }
        }
    }

    // ------------------------------------------------------------------ switching

    /** Player picked a profile in the menu. */
    public void select(String id) {
        Profile p = profiles.get(id);
        if (p == null) return;
        selected = id;
        pendingBiomeTarget = null;
        switchTo(p, Reason.MANUAL);
        store.markSectionDirty(SECTION);
    }

    private void switchTo(Profile p, Reason reason) {
        active = p.id();
        applyValues(p);
        for (Listener l : listeners) {
            try {
                l.switched(p, reason);
            } catch (RuntimeException e) {
                Log.warn("Profile listener failed", e);
            }
        }
    }

    private void applyValues(Profile p) {
        applyingProfile = true;
        List<String> keys = new ArrayList<>();
        try {
            // Two passes so presets (e.g. quality) can't be undone by a later reset.
            for (Setting s : settings) {
                if (!s.profileScoped()) continue;
                s.reset();
                keys.add(s.key());
            }
            for (Setting s : settings) {
                if (!s.profileScoped()) continue;
                JsonElement v = p.values().get(s.key());
                if (v != null) s.fromJson(v);
            }
        } finally {
            applyingProfile = false;
        }
        store.markDirty(keys);
    }

    private void onSettingChanged(Setting s) {
        if (applyingProfile || !ready || !s.profileScoped()) return;
        Profile p = profiles.get(active);
        if (p == null) return;
        p.values().add(s.key(), s.toJson());
        if (p.builtIn()) p.setModified(true);
        dirtyProfiles.add(p.id());
        saveDueAt = clock.getAsLong() + SAVE_DELAY_MS;
    }

    /**
     * Biome-based switching. Call every second or so with the biome group under the player
     * (null outside the Overworld). Switches only after the player has stayed for a moment,
     * so walking along a border doesn't flicker.
     */
    public void onBiomeGroup(String group) {
        String target = selected;
        if (BloomSettings.PROFILE_BY_BIOME.on() && group != null) {
            String rule = biomeRules.get(group);
            if (rule != null && profiles.containsKey(rule)) target = rule;
        }
        if (target.equals(active)) {
            pendingBiomeTarget = null;
            return;
        }
        long now = clock.getAsLong();
        if (!target.equals(pendingBiomeTarget)) {
            pendingBiomeTarget = target;
            pendingBiomeSince = now;
            return;
        }
        if (now - pendingBiomeSince >= BIOME_SWITCH_DELAY_MS) {
            pendingBiomeTarget = null;
            switchTo(profiles.get(target), Reason.BIOME);
        }
    }

    public void setBiomeRule(String group, String profileId) {
        if (profileId == null || !profiles.containsKey(profileId)) biomeRules.remove(group);
        else biomeRules.put(group, profileId);
        store.markSectionDirty(SECTION);
    }

    // ------------------------------------------------------------------ editing

    public Profile create(String name) throws IOException {
        return addUserProfile(name, "", captureCurrent(), null);
    }

    public Profile duplicate(String id) throws IOException {
        Profile src = profiles.get(id);
        if (src == null) throw new IOException("Unknown profile");
        String base = src.name();
        String name = base.length() > ProfileCodec.MAX_NAME_LENGTH - 5 ? base.substring(0, ProfileCodec.MAX_NAME_LENGTH - 5) : base;
        return addUserProfile(name + " copy", src.description(), src.copyValues(), src.hudLayout());
    }

    public Profile importProfile(ProfileCodec.ImportResult r) throws IOException {
        return addUserProfile(r.name(), r.description(), r.values(), r.hudLayout());
    }

    private Profile addUserProfile(String name, String description, JsonObject values, String hud) throws IOException {
        if (profiles.size() >= MAX_PROFILES) throw new IOException("You have the maximum number of profiles.");
        String clean = ProfileCodec.cleanName(name);
        if (clean.isEmpty()) clean = "My profile";
        String stem = SafeFiles.slug(clean, "profile");
        if (definitions.containsKey(stem)) stem = stem + "_custom";
        String id = stem;
        for (int i = 2; profiles.containsKey(id) || Files.exists(dir.resolve(id + ".json")); i++) id = stem + "_" + i;
        Profile p = new Profile(id, uniqueName(clean), description, false, values, hud);
        writeProfile(p);
        profiles.put(id, p);
        return p;
    }

    private String uniqueName(String name) {
        Set<String> names = new HashSet<>();
        for (Profile p : profiles.values()) names.add(p.name().toLowerCase(Locale.ROOT));
        if (!names.contains(name.toLowerCase(Locale.ROOT))) return name;
        for (int i = 2; ; i++) {
            String n = name + " " + i;
            if (n.length() > ProfileCodec.MAX_NAME_LENGTH) n = name.substring(0, ProfileCodec.MAX_NAME_LENGTH - 3) + " " + i;
            if (!names.contains(n.toLowerCase(Locale.ROOT))) return n;
        }
    }

    public void rename(String id, String newName) throws IOException {
        Profile p = profiles.get(id);
        if (p == null || p.builtIn()) throw new IOException("Built-in profiles can't be renamed. Duplicate it first.");
        String clean = ProfileCodec.cleanName(newName);
        if (clean.isEmpty()) throw new IOException("Enter a name.");
        if (clean.equalsIgnoreCase(p.name())) {
            p.setName(clean);
        } else {
            p.setName(uniqueName(clean));
        }
        writeProfile(p);
    }

    /** Deletes a player profile (the file is moved to profiles/deleted/). */
    public void delete(String id) throws IOException {
        Profile p = profiles.get(id);
        if (p == null || p.builtIn()) throw new IOException("Built-in profiles can't be deleted.");
        retireFile(id);
        profiles.remove(id);
        dirtyProfiles.remove(id);
        biomeRules.values().removeIf(id::equals);
        if (id.equals(selected) || id.equals(active)) select(BuiltInProfiles.DEFAULT_ID);
        store.markSectionDirty(SECTION);
    }

    /** Restores a built-in profile's original values (your edits are kept in profiles/deleted/). */
    public void resetBuiltIn(String id) throws IOException {
        Profile def = definitions.get(id);
        if (def == null) throw new IOException("Not a built-in profile.");
        retireFile(id);
        dirtyProfiles.remove(id);
        Profile fresh = copyOf(def);
        profiles.put(id, fresh);
        if (id.equals(active)) switchTo(fresh, Reason.RESET);
    }

    public void setHudLayout(String id, String layoutId) throws IOException {
        Profile p = profiles.get(id);
        if (p == null) return;
        p.setHudLayout(layoutId);
        if (p.builtIn()) p.setModified(true);
        writeProfile(p);
    }

    /** Writes a pretty JSON copy to {@code exportDir} without overwriting anything; returns the file. */
    public Path exportToFile(String id, Path exportDir) throws IOException {
        Profile p = profiles.get(id);
        if (p == null) throw new IOException("Unknown profile");
        Path out = SafeFiles.uniqueIn(exportDir, SafeFiles.slug(p.name(), "profile"), ".bloomprofile.json");
        SafeFiles.writeAtomic(out, ProfileCodec.toPrettyJson(p));
        return out;
    }

    private JsonObject captureCurrent() {
        JsonObject o = new JsonObject();
        for (Setting s : settings) if (s.profileScoped()) o.add(s.key(), s.toJson());
        return o;
    }

    // ------------------------------------------------------------------ settings.json section

    @Override
    public String name() {
        return SECTION;
    }

    @Override
    public JsonElement save() {
        JsonObject o = new JsonObject();
        o.addProperty("active", selected);
        JsonObject rules = new JsonObject();
        for (Map.Entry<String, String> e : biomeRules.entrySet()) rules.addProperty(e.getKey(), e.getValue());
        o.add("biomeRules", rules);
        return o;
    }

    @Override
    public void load(JsonElement json, boolean external) {
        if (json == null || !json.isJsonObject()) return;
        JsonObject o = json.getAsJsonObject();
        if (external) scan(); // the launcher may have added or edited profile files
        biomeRules.clear();
        JsonElement rules = o.get("biomeRules");
        if (rules != null && rules.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : rules.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonPrimitive()) continue;
                String pid = e.getValue().getAsString();
                if (profiles.containsKey(pid) && e.getKey().matches("[a-z_]{1,32}")) biomeRules.put(e.getKey(), pid);
            }
        }
        String wanted = str(o, "active");
        boolean applyFlag = o.has("apply") && o.get("apply").isJsonPrimitive() && o.get("apply").getAsBoolean();
        if (wanted == null || !profiles.containsKey(wanted)) return;
        boolean changed = !wanted.equals(selected);
        selected = wanted;
        if (applyFlag || (external && changed)) {
            // The launcher chose a profile: apply its values and clear the flag.
            pendingBiomeTarget = null;
            switchTo(profiles.get(wanted), external ? Reason.EXTERNAL : Reason.STARTUP);
            store.requestSave(SECTION);
        } else if (!external) {
            active = wanted;
        }
    }
}
