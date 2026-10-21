package dev.bloom.client.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One value out of a list, shown as a dropdown. The option list may be dynamic
 * (e.g. skyboxes include the user's imported ones), so unknown saved values are kept
 * as long as the provider might offer them later, and fall back to the default otherwise.
 */
public final class ChoiceSetting extends Setting {
    public record Option(String id, String label) {
    }

    private final String defaultId;
    private final Supplier<List<Option>> options;
    private String value;
    private int index;

    public ChoiceSetting(String key, Category category, String group, String label, String description,
                         String defaultId, List<Option> options, String... keywords) {
        this(key, category, group, label, description, defaultId, () -> options, keywords);
    }

    public ChoiceSetting(String key, Category category, String group, String label, String description,
                         String defaultId, Supplier<List<Option>> options, String... keywords) {
        super(key, category, group, label, description, keywords);
        this.defaultId = defaultId;
        this.options = options;
        this.value = defaultId;
        this.index = indexOf(defaultId);
    }

    public static List<Option> of(String... idLabelPairs) {
        if (idLabelPairs.length % 2 != 0) throw new IllegalArgumentException("pairs expected");
        List<Option> out = new ArrayList<>();
        for (int i = 0; i < idLabelPairs.length; i += 2) out.add(new Option(idLabelPairs[i], idLabelPairs[i + 1]));
        return Collections.unmodifiableList(out);
    }

    public String id() {
        return value;
    }

    /** Index in the current option list (cheap comparison for render code); -1 if not listed. */
    public int index() {
        return index;
    }

    public boolean is(String id) {
        return value.equals(id);
    }

    public List<Option> options() {
        List<Option> list = options.get();
        return list == null ? List.of() : list;
    }

    public String defaultId() {
        return defaultId;
    }

    public String labelOf(String id) {
        for (Option o : options()) if (o.id().equals(id)) return o.label();
        return id;
    }

    public boolean isValid(String id) {
        return indexOf(id) >= 0;
    }

    public void set(String id) {
        if (id == null || !isValid(id)) return;
        if (!id.equals(value)) {
            value = id;
            index = indexOf(id);
            fireChanged();
        }
    }

    /** Re-resolve after the option list changed (e.g. a custom skybox was deleted). */
    public void revalidate() {
        int i = indexOf(value);
        if (i < 0) {
            value = defaultId;
            index = indexOf(defaultId);
            fireChanged();
        } else {
            index = i;
        }
    }

    public void cycle(int dir) {
        List<Option> list = options();
        if (list.isEmpty()) return;
        int i = Math.max(0, index);
        int next = Math.floorMod(i + dir, list.size());
        set(list.get(next).id());
    }

    private int indexOf(String id) {
        List<Option> list = options();
        for (int i = 0; i < list.size(); i++) if (Objects.equals(list.get(i).id(), id)) return i;
        return -1;
    }

    @Override
    public boolean isDefault() {
        return value.equals(defaultId);
    }

    @Override
    public void reset() {
        set(defaultId);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public boolean accepts(JsonElement json) {
        return json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() && isValid(json.getAsString());
    }

    @Override
    public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) return false;
        String id = json.getAsString();
        if (!isValid(id)) return false;
        set(id);
        return true;
    }

    @Override
    public String displayValue() {
        return labelOf(value);
    }
}
