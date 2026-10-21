package dev.bloom.client.core.settings;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class FloatSetting extends Setting<Float> {
    /** How the value is shown to the user. */
    public enum Format {
        PERCENT,
        MULTIPLIER,
        DEGREES,
        DEGREES_PER_MINUTE,
        BLOCKS,
        DECIMAL
    }

    public final float min;
    public final float max;
    public final float step;
    public final Format format;

    public FloatSetting(String id, Category category, String group, String label, String description, Scope scope, float def, float min, float max, float step, Format format) {
        super(id, category, group, label, description, scope, Math.max(min, Math.min(max, def)));
        this.min = min;
        this.max = max;
        this.step = step <= 0 ? (max - min) / 100f : step;
        this.format = format;
    }

    public float value() {
        return get();
    }

    @Override
    protected Float sanitize(Float candidate) {
        if (candidate == null || candidate.isNaN() || candidate.isInfinite()) return null;
        return Math.max(min, Math.min(max, candidate));
    }

    @Override
    protected Float fromJson(Object json) {
        if (!(json instanceof Number n)) return null;
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) return null;
        return (float) Math.max(min, Math.min(max, d));
    }

    @Override
    protected Object toJson(Float value) {
        // Round to the step's precision so files stay readable.
        double rounded = Math.round(value / (double) step) * (double) step;
        return Math.round(rounded * 10000.0) / 10000.0;
    }

    @Override
    protected boolean equalsValue(Float a, Float b) {
        return Math.abs(a - b) < 1e-6f;
    }

    @Override
    public String type() {
        return "float";
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("min", (double) min);
        m.put("max", (double) max);
        m.put("step", (double) step);
        m.put("format", format.name().toLowerCase(Locale.ROOT));
        return m;
    }

    public String format(float v) {
        return switch (format) {
            case PERCENT -> Math.round(v * 100) + "%";
            case MULTIPLIER -> String.format(Locale.ROOT, "%.2f×", v);
            case DEGREES -> Math.round(v) + "°";
            case DEGREES_PER_MINUTE -> String.format(Locale.ROOT, "%.1f°/min", v);
            case BLOCKS -> Math.round(v) + " blocks";
            case DECIMAL -> String.format(Locale.ROOT, "%.2f", v);
        };
    }

    @Override
    public String display() {
        return format(get());
    }
}
