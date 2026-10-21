package dev.bloom.client.core.settings;

import java.util.LinkedHashMap;
import java.util.Map;

public final class IntSetting extends Setting<Integer> {
    public final int min;
    public final int max;
    public final int step;
    public final String unit;

    public IntSetting(String id, Category category, String group, String label, String description, Scope scope, int def, int min, int max, int step, String unit) {
        super(id, category, group, label, description, scope, Math.max(min, Math.min(max, def)));
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.unit = unit == null ? "" : unit;
    }

    public int value() {
        return get();
    }

    @Override
    protected Integer sanitize(Integer candidate) {
        if (candidate == null) return null;
        return Math.max(min, Math.min(max, candidate));
    }

    @Override
    protected Integer fromJson(Object json) {
        if (!(json instanceof Number n)) return null;
        double d = n.doubleValue();
        if (Double.isNaN(d)) return null;
        return (int) Math.round(Math.max(min, Math.min(max, d)));
    }

    @Override
    protected Object toJson(Integer value) {
        return value;
    }

    @Override
    public String type() {
        return "int";
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("min", min);
        m.put("max", max);
        m.put("step", step);
        if (!unit.isEmpty()) m.put("unit", unit);
        return m;
    }

    @Override
    public String display() {
        return get() + (unit.isEmpty() ? "" : unit);
    }
}
