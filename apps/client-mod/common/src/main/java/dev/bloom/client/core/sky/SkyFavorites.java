package dev.bloom.client.core.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.bloom.client.core.settings.SettingsStore;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Favourite skyboxes, saved as the "skyFavorites" block of settings.json. */
public final class SkyFavorites implements SettingsStore.Section {
    public static final String SECTION = "skyFavorites";
    private static final int MAX = 200;
    private final Set<String> ids = new LinkedHashSet<>();
    private final SettingsStore store;

    public SkyFavorites(SettingsStore store) {
        this.store = store;
    }

    public boolean contains(String id) {
        return ids.contains(id);
    }

    public Set<String> all() {
        return Collections.unmodifiableSet(ids);
    }

    /** Adds or removes; returns whether it's now a favourite. */
    public boolean toggle(String id) {
        boolean now;
        if (ids.remove(id)) now = false;
        else {
            if (ids.size() >= MAX) return false;
            ids.add(id);
            now = true;
        }
        if (store != null) store.markSectionDirty(SECTION);
        return now;
    }

    @Override
    public String name() {
        return SECTION;
    }

    @Override
    public JsonElement save() {
        JsonArray a = new JsonArray();
        for (String id : ids) a.add(id);
        return a;
    }

    @Override
    public void load(JsonElement json, boolean external) {
        ids.clear();
        if (json == null || !json.isJsonArray()) return;
        for (JsonElement e : json.getAsJsonArray()) {
            if (ids.size() >= MAX) break;
            if (e.isJsonPrimitive()) {
                String id = e.getAsString();
                if (id.length() <= 128 && id.matches("[a-z0-9_:.\\-]+")) ids.add(id);
            }
        }
    }
}
