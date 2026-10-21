package dev.bloom.client.core.settings;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A string id chosen from a list that can change at runtime (e.g. skyboxes,
 * including imported ones). Unknown ids are kept so a profile created on a PC
 * with a custom sky still loads elsewhere; the game falls back to the default
 * when the id can't be resolved.
 */
public final class ChoiceSetting extends Setting<String> {
    public static final Pattern ID = Pattern.compile("[a-z0-9_./:-]{1,64}");

    /** One selectable entry. */
    public record Option(String id, String label) {}

    private final Supplier<List<Option>> options;

    public ChoiceSetting(String id, Category category, String group, String label, String description, Scope scope, String def, Supplier<List<Option>> options) {
        super(id, category, group, label, description, scope, def);
        this.options = options;
    }

    public List<Option> options() {
        return options.get();
    }

    public boolean isKnown(String id) {
        for (Option o : options()) if (o.id().equals(id)) return true;
        return false;
    }

    /** The current value if it is a known option, else the default. */
    public String resolved() {
        String v = get();
        return isKnown(v) ? v : defaultValue;
    }

    @Override
    protected String sanitize(String candidate) {
        if (candidate == null || !ID.matcher(candidate).matches()) return null;
        return candidate;
    }

    @Override
    protected String fromJson(Object json) {
        return json instanceof String s ? sanitize(s) : null;
    }

    @Override
    protected Object toJson(String value) {
        return value;
    }

    @Override
    public String type() {
        return "choice";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of("source", "dynamic");
    }

    @Override
    public String display() {
        String v = get();
        for (Option o : options()) if (o.id().equals(v)) return o.label();
        return v + " (missing)";
    }
}
