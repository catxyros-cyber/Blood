package dev.bloom.client.core.settings;

import java.util.Map;

public final class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String id, Category category, String group, String label, String description, Scope scope, boolean def) {
        super(id, category, group, label, description, scope, def);
    }

    public boolean on() {
        return get();
    }

    public void toggle() {
        set(!get());
    }

    @Override
    protected Boolean sanitize(Boolean candidate) {
        return candidate;
    }

    @Override
    protected Boolean fromJson(Object json) {
        return json instanceof Boolean b ? b : null;
    }

    @Override
    protected Object toJson(Boolean value) {
        return value;
    }

    @Override
    public String type() {
        return "bool";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of();
    }

    @Override
    public String display() {
        return get() ? "On" : "Off";
    }
}
