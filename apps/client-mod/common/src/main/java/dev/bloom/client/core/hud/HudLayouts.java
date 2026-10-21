package dev.bloom.client.core.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bloom.client.core.io.SafeFiles;
import dev.bloom.client.core.profile.ProfileCodec;
import dev.bloom.client.core.settings.SettingsStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All HUD layouts and which one is active. Stored in the "hud" block of settings.json. */
public final class HudLayouts implements SettingsStore.Section {
    public static final String SECTION = "hud";
    public static final int MAX_LAYOUTS = 32;

    private final Map<String, HudLayout> layouts = new LinkedHashMap<>();
    private final SettingsStore store;
    private String active;
    private int version;

    public HudLayouts(SettingsStore store) {
        this.store = store;
        installDefaults();
        if (store != null) store.addSection(this);
    }

    private void installDefaults() {
        layouts.clear();
        layouts.put("default", preset("default", "Default"));
        layouts.put("pvp", preset("pvp", "PvP"));
        active = "default";
    }

    /** Built-in starting points; "Reset layout" returns a layout to its preset. */
    public static HudLayout preset(String id, String name) {
        HudLayout l = new HudLayout(id, name);
        if ("pvp".equals(id)) {
            l.get(HudWidgetType.FPS).visible = true;
            l.get(HudWidgetType.COORDINATES).visible = false;
            l.get(HudWidgetType.CPS).visible = true;
            l.get(HudWidgetType.CPS).y = 18;
            l.get(HudWidgetType.KEYSTROKES).visible = true;
            l.get(HudWidgetType.ARMOR).visible = true;
            l.get(HudWidgetType.POTIONS).visible = true;
            l.get(HudWidgetType.DIRECTION).visible = false;
        }
        return l;
    }

    /** Increments on every change; renderers can cache by it. */
    public int version() {
        return version;
    }

    public HudLayout active() {
        HudLayout l = layouts.get(active);
        if (l == null) {
            l = layouts.values().iterator().next();
            active = l.id();
        }
        return l;
    }

    public List<HudLayout> all() {
        return new ArrayList<>(layouts.values());
    }

    public HudLayout get(String id) {
        return layouts.get(id);
    }

    public void setActive(String id) {
        if (layouts.containsKey(id) && !id.equals(active)) {
            active = id;
            changed();
        }
    }

    /** Call after editing a widget in the active (or any) layout. */
    public void changed() {
        version++;
        if (store != null) store.markSectionDirty(SECTION);
    }

    public HudLayout create(String name, HudLayout copyFrom) {
        if (layouts.size() >= MAX_LAYOUTS) return null;
        String clean = ProfileCodec.cleanName(name);
        if (clean.isEmpty()) clean = "Layout";
        String stem = SafeFiles.slug(clean, "layout");
        String id = stem;
        for (int i = 2; layouts.containsKey(id); i++) id = stem + "_" + i;
        HudLayout l = copyFrom != null ? copyFrom.copy(id, clean) : new HudLayout(id, clean);
        layouts.put(id, l);
        changed();
        return l;
    }

    public boolean rename(String id, String name) {
        HudLayout l = layouts.get(id);
        String clean = ProfileCodec.cleanName(name);
        if (l == null || clean.isEmpty()) return false;
        l.setName(clean);
        changed();
        return true;
    }

    /** The last layout can't be deleted. */
    public boolean delete(String id) {
        if (layouts.size() <= 1 || !layouts.containsKey(id)) return false;
        layouts.remove(id);
        if (id.equals(active)) active = layouts.keySet().iterator().next();
        changed();
        return true;
    }

    /** Resets every widget of a layout to its preset (Default unless it is the PvP layout). */
    public void reset(String id) {
        HudLayout l = layouts.get(id);
        if (l == null) return;
        HudLayout p = preset("pvp".equals(id) ? "pvp" : "default", l.name());
        for (HudWidgetType t : HudWidgetType.values()) l.get(t).copyFrom(p.get(t));
        changed();
    }

    public void resetWidget(String layoutId, HudWidgetType type) {
        HudLayout l = layouts.get(layoutId);
        if (l == null) return;
        HudLayout p = preset("pvp".equals(layoutId) ? "pvp" : "default", "");
        l.get(type).copyFrom(p.get(type));
        changed();
    }

    @Override
    public String name() {
        return SECTION;
    }

    @Override
    public JsonElement save() {
        JsonObject o = new JsonObject();
        o.addProperty("activeLayout", active);
        JsonArray arr = new JsonArray();
        for (HudLayout l : layouts.values()) arr.add(l.toJson());
        o.add("layouts", arr);
        return o;
    }

    @Override
    public void load(JsonElement json, boolean external) {
        if (json == null || !json.isJsonObject()) return;
        JsonObject o = json.getAsJsonObject();
        JsonElement arr = o.get("layouts");
        if (arr == null || !arr.isJsonArray()) return;
        Map<String, HudLayout> loaded = new LinkedHashMap<>();
        for (JsonElement e : arr.getAsJsonArray()) {
            if (!e.isJsonObject() || loaded.size() >= MAX_LAYOUTS) continue;
            JsonObject lo = e.getAsJsonObject();
            String id = lo.has("id") && lo.get("id").isJsonPrimitive() ? lo.get("id").getAsString() : null;
            if (!SafeFiles.isSafeName(id) || loaded.containsKey(id)) continue;
            String name = ProfileCodec.cleanName(lo.has("name") && lo.get("name").isJsonPrimitive() ? lo.get("name").getAsString() : "");
            HudLayout l = "pvp".equals(id) ? preset("pvp", name.isEmpty() ? "PvP" : name) : new HudLayout(id, name.isEmpty() ? id : name);
            l.loadWidgets(lo);
            loaded.put(id, l);
        }
        if (loaded.isEmpty()) return;
        layouts.clear();
        layouts.putAll(loaded);
        JsonElement act = o.get("activeLayout");
        active = act != null && act.isJsonPrimitive() && layouts.containsKey(act.getAsString()) ? act.getAsString() : layouts.keySet().iterator().next();
        version++;
    }
}
