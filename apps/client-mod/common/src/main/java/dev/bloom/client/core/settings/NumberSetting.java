package dev.bloom.client.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** A bounded number shown as a slider. Integers are numbers with step 1 and {@link Format#INTEGER}. */
public final class NumberSetting extends Setting {
    public enum Format {
        /** 0.35 → "35%" */
        PERCENT,
        /** 42 → "42" */
        INTEGER,
        /** 1.25 → "1.25×" */
        MULTIPLIER,
        /** 90 → "90°" */
        DEGREES,
        /** plain decimal with unit */
        DECIMAL
    }

    private final float min;
    private final float max;
    private final float step;
    private final float defaultValue;
    private final Format format;
    private final String unit;
    private float value;

    public NumberSetting(String key, Category category, String group, String label, String description,
                         float defaultValue, float min, float max, float step, Format format, String unit, String... keywords) {
        super(key, category, group, label, description, keywords);
        if (!(min < max) || step <= 0) throw new IllegalArgumentException("Bad range for " + key);
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.unit = unit == null ? "" : unit;
        this.defaultValue = snap(defaultValue);
        this.value = this.defaultValue;
    }

    public float value() {
        return value;
    }

    public int intValue() {
        return Math.round(value);
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    public float step() {
        return step;
    }

    public float defaultValue() {
        return defaultValue;
    }

    public Format format() {
        return format;
    }

    /** 0..1 position within the range (for slider knobs). */
    public float fraction() {
        return (value - min) / (max - min);
    }

    public void setFraction(float f) {
        set(min + Math.max(0f, Math.min(1f, f)) * (max - min));
    }

    /** Clamps and snaps to the step. NaN/infinite values are ignored. */
    public void set(float v) {
        if (!Float.isFinite(v)) return;
        float snapped = snap(v);
        if (snapped != value) {
            value = snapped;
            fireChanged();
        }
    }

    private float snap(float v) {
        float clamped = Math.max(min, Math.min(max, v));
        float steps = Math.round((clamped - min) / step);
        float out = min + steps * step;
        // Avoid 0.30000001-style noise in saved files.
        out = Math.round(out * 10000f) / 10000f;
        return Math.max(min, Math.min(max, out));
    }

    @Override
    public boolean isDefault() {
        return Math.abs(value - defaultValue) < step / 2f;
    }

    @Override
    public void reset() {
        set(defaultValue);
    }

    @Override
    public JsonElement toJson() {
        if (format == Format.INTEGER) return new JsonPrimitive(Math.round(value));
        return new JsonPrimitive(value);
    }

    @Override
    public boolean accepts(JsonElement json) {
        return json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber() && Double.isFinite(json.getAsDouble());
    }

    @Override
    public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) return false;
        double d = json.getAsDouble();
        if (!Double.isFinite(d)) return false;
        set((float) d);
        return true;
    }

    @Override
    public String displayValue() {
        return formatValue(value);
    }

    public String formatValue(float v) {
        return switch (format) {
            case PERCENT -> Math.round(v * 100f) + "%";
            case INTEGER -> Math.round(v) + unit;
            case MULTIPLIER -> trim(v) + "×";
            case DEGREES -> Math.round(v) + "°";
            case DECIMAL -> trim(v) + unit;
        };
    }

    private static String trim(float v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }
}
