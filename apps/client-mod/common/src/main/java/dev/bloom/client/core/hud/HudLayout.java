package dev.bloom.client.core.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.Map;

/** A named arrangement of all HUD widgets. */
public final class HudLayout {
    private final String id;
    private String name;
    private final EnumMap<HudWidgetType, WidgetState> widgets = new EnumMap<>(HudWidgetType.class);

    public HudLayout(String id, String name) {
        this.id = id;
        this.name = name;
        for (HudWidgetType t : HudWidgetType.values()) widgets.put(t, new WidgetState(t));
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public WidgetState get(HudWidgetType t) {
        return widgets.get(t);
    }

    public Iterable<WidgetState> widgets() {
        return widgets.values();
    }

    public void resetAll() {
        for (WidgetState w : widgets.values()) w.reset();
    }

    public HudLayout copy(String newId, String newName) {
        HudLayout l = new HudLayout(newId, newName);
        for (Map.Entry<HudWidgetType, WidgetState> e : widgets.entrySet()) l.widgets.get(e.getKey()).copyFrom(e.getValue());
        return l;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        JsonObject ws = new JsonObject();
        for (WidgetState w : widgets.values()) ws.add(w.type.id(), w.toJson());
        o.add("widgets", ws);
        return o;
    }

    /** Widgets missing from the JSON keep their defaults; unknown widget ids are ignored. */
    public void loadWidgets(JsonObject o) {
        JsonElement ws = o.get("widgets");
        if (ws == null || !ws.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : ws.getAsJsonObject().entrySet()) {
            HudWidgetType t = HudWidgetType.byId(e.getKey());
            if (t != null && e.getValue().isJsonObject()) widgets.get(t).fromJson(e.getValue().getAsJsonObject());
        }
    }
}
