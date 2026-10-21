package dev.bloom.client.core.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Any number of options from a fixed list (e.g. which biomes petals appear in). Shown as chips. */
public final class MultiChoiceSetting extends Setting {
    private final List<ChoiceSetting.Option> options;
    private final long defaultMask;
    private long mask;

    public MultiChoiceSetting(String key, Category category, String group, String label, String description,
                              List<ChoiceSetting.Option> options, Set<String> defaults, String... keywords) {
        super(key, category, group, label, description, keywords);
        if (options.size() > 63) throw new IllegalArgumentException("too many options");
        this.options = List.copyOf(options);
        long m = 0;
        for (int i = 0; i < options.size(); i++) if (defaults.contains(options.get(i).id())) m |= 1L << i;
        this.defaultMask = m;
        this.mask = m;
    }

    public List<ChoiceSetting.Option> options() {
        return options;
    }

    /** Bit i set = option i selected. Cheap to test in render/tick code. */
    public long mask() {
        return mask;
    }

    public boolean contains(String id) {
        int i = indexOf(id);
        return i >= 0 && (mask & (1L << i)) != 0;
    }

    public boolean containsIndex(int i) {
        return (mask & (1L << i)) != 0;
    }

    public int indexOf(String id) {
        for (int i = 0; i < options.size(); i++) if (options.get(i).id().equals(id)) return i;
        return -1;
    }

    public void toggle(String id) {
        int i = indexOf(id);
        if (i >= 0) setMask(mask ^ (1L << i));
    }

    public void setMask(long m) {
        long valid = (1L << options.size()) - 1;
        m &= valid;
        if (m != mask) {
            mask = m;
            fireChanged();
        }
    }

    public Set<String> selected() {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < options.size(); i++) if ((mask & (1L << i)) != 0) out.add(options.get(i).id());
        return out;
    }

    @Override
    public boolean isDefault() {
        return mask == defaultMask;
    }

    @Override
    public void reset() {
        setMask(defaultMask);
    }

    @Override
    public JsonElement toJson() {
        JsonArray arr = new JsonArray();
        for (String s : selected()) arr.add(s);
        return arr;
    }

    @Override
    public boolean accepts(JsonElement json) {
        if (json == null || !json.isJsonArray() || json.getAsJsonArray().size() > 64) return false;
        for (JsonElement e : json.getAsJsonArray()) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return false;
        }
        return true;
    }

    @Override
    public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonArray()) return false;
        long m = 0;
        for (JsonElement e : json.getAsJsonArray()) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return false;
            int i = indexOf(e.getAsString());
            if (i >= 0) m |= 1L << i; // unknown ids from newer versions are ignored
        }
        setMask(m);
        return true;
    }

    @Override
    public String displayValue() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) if ((mask & (1L << i)) != 0) labels.add(options.get(i).label());
        return labels.isEmpty() ? "None" : String.join(", ", labels);
    }
}
