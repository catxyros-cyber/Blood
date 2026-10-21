package dev.bloom.client.core.settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** A short list of ids (e.g. biome ids like "minecraft:cherry_grove"). */
public final class StringListSetting extends Setting<List<String>> {
    public static final Pattern ITEM = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public final int maxItems;

    public StringListSetting(String id, Category category, String group, String label, String description, Scope scope, List<String> def, int maxItems) {
        super(id, category, group, label, description, scope, List.copyOf(def));
        this.maxItems = maxItems;
    }

    @Override
    protected List<String> sanitize(List<String> candidate) {
        if (candidate == null) return null;
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : candidate) {
            if (s == null) continue;
            String t = s.trim().toLowerCase(java.util.Locale.ROOT);
            if (!t.contains(":")) t = "minecraft:" + t;
            if (ITEM.matcher(t).matches() && t.length() <= 96) out.add(t);
            if (out.size() >= maxItems) break;
        }
        return List.copyOf(out);
    }

    @Override
    protected List<String> fromJson(Object json) {
        if (!(json instanceof List<?> l)) return null;
        List<String> out = new ArrayList<>();
        for (Object o : l) if (o instanceof String s) out.add(s);
        return sanitize(out);
    }

    @Override
    protected Object toJson(List<String> value) {
        return new ArrayList<>(value);
    }

    public void add(String item) {
        List<String> l = new ArrayList<>(get());
        l.add(item);
        set(l);
    }

    public void remove(String item) {
        List<String> l = new ArrayList<>(get());
        l.remove(item);
        set(l);
    }

    @Override
    public String type() {
        return "list";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of("maxItems", maxItems);
    }

    @Override
    public String display() {
        List<String> v = get();
        return v.isEmpty() ? "None" : v.size() == 1 ? v.get(0) : v.size() + " selected";
    }
}
