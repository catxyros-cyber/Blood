package dev.bloom.client.core.settings;

import dev.bloom.client.core.util.Colors;
import java.util.Map;

/** An ARGB colour. Stored in JSON as "#AARRGGBB" (or "#RRGGBB" when alpha is not editable). */
public final class ColorSetting extends Setting<Integer> {
    public final boolean alpha;

    public ColorSetting(String id, Category category, String group, String label, String description, Scope scope, int def, boolean alpha) {
        super(id, category, group, label, description, scope, alpha ? def : (def | 0xFF000000));
        this.alpha = alpha;
    }

    public int argb() {
        return get();
    }

    @Override
    protected Integer sanitize(Integer candidate) {
        if (candidate == null) return null;
        return alpha ? candidate : (candidate | 0xFF000000);
    }

    @Override
    protected Integer fromJson(Object json) {
        if (!(json instanceof String s)) return null;
        Integer c = Colors.parseHex(s);
        if (c == null) return null;
        return alpha ? c : (c | 0xFF000000);
    }

    @Override
    protected Object toJson(Integer value) {
        return Colors.toHex(value, alpha);
    }

    @Override
    public String type() {
        return "color";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of("alpha", alpha);
    }

    @Override
    public String display() {
        return Colors.toHex(get(), alpha);
    }
}
