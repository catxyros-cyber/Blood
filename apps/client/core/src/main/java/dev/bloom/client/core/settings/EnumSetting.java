package dev.bloom.client.core.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EnumSetting<E extends Enum<E> & Labeled> extends Setting<E> {
    public final Class<E> type;

    public EnumSetting(String id, Category category, String group, String label, String description, Scope scope, E def) {
        super(id, category, group, label, description, scope, def);
        this.type = def.getDeclaringClass();
    }

    public E[] options() {
        return type.getEnumConstants();
    }

    public void cycle(int direction) {
        E[] all = options();
        int i = (get().ordinal() + direction + all.length) % all.length;
        set(all[i]);
    }

    @Override
    protected E sanitize(E candidate) {
        return candidate;
    }

    @Override
    protected E fromJson(Object json) {
        if (!(json instanceof String s)) return null;
        for (E e : options()) if (e.name().equalsIgnoreCase(s)) return e;
        return null;
    }

    @Override
    protected Object toJson(E value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String type() {
        return "enum";
    }

    @Override
    public Map<String, Object> schema() {
        List<Object> opts = new ArrayList<>();
        for (E e : options()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("value", e.name().toLowerCase(Locale.ROOT));
            o.put("label", e.label());
            opts.add(o);
        }
        return Map.of("options", opts);
    }

    @Override
    public String display() {
        return get().label();
    }
}
