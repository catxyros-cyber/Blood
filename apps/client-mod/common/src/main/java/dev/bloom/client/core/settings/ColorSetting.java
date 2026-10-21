package dev.bloom.client.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** An RGB colour (no alpha; opacity is always its own slider). Stored as "#RRGGBB". */
public final class ColorSetting extends Setting {
    private final int defaultRgb;
    private int rgb;
    // Cached float components for render code.
    private float r, g, b;

    public ColorSetting(String key, Category category, String group, String label, String description, int defaultRgb, String... keywords) {
        super(key, category, group, label, description, keywords);
        this.defaultRgb = defaultRgb & 0xFFFFFF;
        apply(this.defaultRgb);
    }

    public int rgb() {
        return rgb;
    }

    /** Colour with full alpha, as Minecraft GUI code expects (0xAARRGGBB). */
    public int argb() {
        return 0xFF000000 | rgb;
    }

    public float red() {
        return r;
    }

    public float green() {
        return g;
    }

    public float blue() {
        return b;
    }

    public int defaultRgb() {
        return defaultRgb;
    }

    public void set(int newRgb) {
        newRgb &= 0xFFFFFF;
        if (newRgb != rgb) {
            apply(newRgb);
            fireChanged();
        }
    }

    private void apply(int v) {
        rgb = v;
        r = ((v >> 16) & 0xFF) / 255f;
        g = ((v >> 8) & 0xFF) / 255f;
        b = (v & 0xFF) / 255f;
    }

    @Override
    public boolean isDefault() {
        return rgb == defaultRgb;
    }

    @Override
    public void reset() {
        set(defaultRgb);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(hex(rgb));
    }

    @Override
    public boolean accepts(JsonElement json) {
        return json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() && parseHex(json.getAsString()) != null;
    }

    @Override
    public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) return false;
        Integer parsed = parseHex(json.getAsString());
        if (parsed == null) return false;
        set(parsed);
        return true;
    }

    @Override
    public String displayValue() {
        return hex(rgb);
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Accepts "#RGB", "#RRGGBB" (with or without '#'). Returns null if invalid. */
    public static Integer parseHex(String s) {
        if (s == null) return null;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        if (h.length() != 6 || !h.matches("[0-9a-fA-F]{6}")) return null;
        return Integer.parseInt(h, 16);
    }
}
