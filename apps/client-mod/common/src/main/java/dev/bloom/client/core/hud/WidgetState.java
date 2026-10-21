package dev.bloom.client.core.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bloom.client.core.settings.ColorSetting;

import java.util.LinkedHashMap;
import java.util.Map;

/** Position, size and look of one widget in one layout. Colours of -1 mean "use the HUD style". */
public final class WidgetState {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 3f;
    public static final int MAX_OFFSET = 4096;

    public final HudWidgetType type;
    public boolean visible;
    public Anchor anchor;
    public float x;
    public float y;
    public float scale = 1f;
    public int textColor = -1;
    public int backgroundColor = -1;
    /** -1 = use the HUD style's opacity. */
    public float backgroundOpacity = -1f;
    private final Map<String, Boolean> options = new LinkedHashMap<>();

    public WidgetState(HudWidgetType type) {
        this.type = type;
        reset();
    }

    public void reset() {
        visible = type.visibleByDefault();
        anchor = type.defaultAnchor();
        x = type.defaultX();
        y = type.defaultY();
        scale = 1f;
        textColor = -1;
        backgroundColor = -1;
        backgroundOpacity = -1f;
        options.clear();
    }

    public void resetPosition() {
        anchor = type.defaultAnchor();
        x = type.defaultX();
        y = type.defaultY();
        scale = 1f;
    }

    public boolean option(String id) {
        Boolean b = options.get(id);
        return b != null ? b : type.optionDefault(id);
    }

    public void setOption(String id, boolean value) {
        if (!type.hasOption(id)) return;
        if (value == type.optionDefault(id)) options.remove(id);
        else options.put(id, value);
    }

    public void setScale(float s) {
        if (!Float.isFinite(s)) return;
        scale = Math.round(Math.max(MIN_SCALE, Math.min(MAX_SCALE, s)) * 20f) / 20f;
    }

    public void setOffset(float nx, float ny) {
        if (!Float.isFinite(nx) || !Float.isFinite(ny)) return;
        x = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, Math.round(nx)));
        y = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, Math.round(ny)));
    }

    public WidgetState copy() {
        WidgetState w = new WidgetState(type);
        w.copyFrom(this);
        return w;
    }

    public void copyFrom(WidgetState o) {
        visible = o.visible;
        anchor = o.anchor;
        x = o.x;
        y = o.y;
        scale = o.scale;
        textColor = o.textColor;
        backgroundColor = o.backgroundColor;
        backgroundOpacity = o.backgroundOpacity;
        options.clear();
        options.putAll(o.options);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("visible", visible);
        o.addProperty("anchor", anchor.name().toLowerCase(java.util.Locale.ROOT));
        o.addProperty("x", Math.round(x));
        o.addProperty("y", Math.round(y));
        o.addProperty("scale", scale);
        if (textColor >= 0) o.addProperty("textColor", ColorSetting.hex(textColor));
        if (backgroundColor >= 0) o.addProperty("backgroundColor", ColorSetting.hex(backgroundColor));
        if (backgroundOpacity >= 0) o.addProperty("backgroundOpacity", backgroundOpacity);
        if (!options.isEmpty()) {
            JsonObject opts = new JsonObject();
            for (Map.Entry<String, Boolean> e : options.entrySet()) opts.addProperty(e.getKey(), e.getValue());
            o.add("options", opts);
        }
        return o;
    }

    /** Lenient: every field is optional and invalid fields keep their defaults. */
    public void fromJson(JsonObject o) {
        reset();
        visible = bool(o, "visible", visible);
        String a = str(o, "anchor");
        if (a != null) {
            try {
                anchor = Anchor.valueOf(a.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        setOffset(num(o, "x", x), num(o, "y", y));
        setScale(num(o, "scale", 1f));
        textColor = color(o, "textColor");
        backgroundColor = color(o, "backgroundColor");
        float op = num(o, "backgroundOpacity", -1f);
        backgroundOpacity = op < 0 ? -1f : Math.min(1f, op);
        JsonElement opts = o.get("options");
        if (opts != null && opts.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : opts.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isBoolean()) {
                    setOption(e.getKey(), e.getValue().getAsBoolean());
                }
            }
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
    }

    private static float num(JsonObject o, String k, float def) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return def;
        float f = e.getAsFloat();
        return Float.isFinite(f) ? f : def;
    }

    private static int color(JsonObject o, String k) {
        Integer c = ColorSetting.parseHex(str(o, k));
        return c == null ? -1 : c;
    }
}
