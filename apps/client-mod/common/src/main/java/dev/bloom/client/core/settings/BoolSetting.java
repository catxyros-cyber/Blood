package dev.bloom.client.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting {
    private final boolean defaultValue;
    private boolean value;

    public BoolSetting(String key, Category category, String group, String label, String description, boolean defaultValue, String... keywords) {
        super(key, category, group, label, description, keywords);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    /** Primitive getter for render code. */
    public boolean on() {
        return value;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    public void set(boolean v) {
        if (v != value) {
            value = v;
            fireChanged();
        }
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public boolean isDefault() {
        return value == defaultValue;
    }

    @Override
    public void reset() {
        set(defaultValue);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public boolean accepts(JsonElement json) {
        return json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean();
    }

    @Override
    public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isBoolean()) return false;
        set(json.getAsBoolean());
        return true;
    }

    @Override
    public String displayValue() {
        return value ? "On" : "Off";
    }
}
