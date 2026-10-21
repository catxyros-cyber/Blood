package dev.bloom.client.core.settings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One user-visible option. Every control in the Right Shift menu is backed by
 * a Setting, and every Setting is read by real code — there are no decorative
 * options. Values are validated on every write (including imports), so code
 * that reads them can trust ranges and types.
 */
public abstract class Setting<T> {
    /** Where a setting is stored: in the global config, or per visual profile. */
    public enum Scope {
        GLOBAL,
        PROFILE
    }

    public final String id;
    public final Category category;
    /** Sub-heading inside the category (e.g. "Rain", "Snow"). */
    public final String group;
    public final String label;
    public final String description;
    public final Scope scope;
    public final T defaultValue;

    private volatile T value;
    private String[] keywords = new String[0];
    private boolean restartRequired;
    private String restartReason;
    private BoolSetting parent;
    private Supplier<String> unavailableReason = () -> null;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    SettingsRegistry registry;

    protected Setting(String id, Category category, String group, String label, String description, Scope scope, T defaultValue) {
        this.id = id;
        this.category = category;
        this.group = group;
        this.label = label;
        this.description = description;
        this.scope = scope;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public final T get() {
        return value;
    }

    /** Sets a new value (validated/clamped). Returns true if it changed. */
    public final boolean set(T newValue) {
        T coerced = sanitize(newValue);
        if (coerced == null || equalsValue(coerced, value)) return false;
        value = coerced;
        for (Consumer<T> l : listeners) {
            try {
                l.accept(coerced);
            } catch (RuntimeException e) {
                // A broken listener must never stop the setting from applying.
                if (registry != null) registry.reportListenerError(this, e);
            }
        }
        if (registry != null) registry.fireChanged(this);
        return true;
    }

    public final void reset() {
        set(defaultValue);
    }

    public final boolean isDefault() {
        return equalsValue(value, defaultValue);
    }

    public final void onChange(Consumer<T> listener) {
        listeners.add(listener);
    }

    /** Read a JSON value; returns false (and leaves the value unchanged) if it is invalid. */
    public final boolean loadJson(Object json) {
        T v = fromJson(json);
        if (v == null) return false;
        set(v);
        return true;
    }

    public final Object toJson() {
        return toJson(value);
    }

    // -- metadata ---------------------------------------------------------

    public final Setting<T> keywords(String... words) {
        this.keywords = words;
        return this;
    }

    public final String[] keywords() {
        return keywords;
    }

    public final Setting<T> requiresRestart(String reason) {
        this.restartRequired = true;
        this.restartReason = reason;
        return this;
    }

    public final boolean restartRequired() {
        return restartRequired;
    }

    public final String restartReason() {
        return restartReason;
    }

    /** Only meaningful while the parent toggle is on (shown dimmed otherwise). */
    public final Setting<T> dependsOn(BoolSetting parent) {
        this.parent = parent;
        return this;
    }

    public final BoolSetting parent() {
        return parent;
    }

    public final boolean isActive() {
        return parent == null || (parent.get() && parent.isActive());
    }

    /** Set by the game layer: a non-null reason means this feature can't work right now. */
    public final void setAvailability(Supplier<String> reason) {
        this.unavailableReason = reason == null ? () -> null : reason;
    }

    public final String unavailableReason() {
        try {
            return unavailableReason.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // -- type-specific ------------------------------------------------------

    /** Clamp/validate a candidate value; null means "reject". */
    protected abstract T sanitize(T candidate);

    protected abstract T fromJson(Object json);

    protected abstract Object toJson(T value);

    /** Short machine-readable type name used by the exported schema. */
    public abstract String type();

    /** Extra schema fields (ranges, options). */
    public abstract Map<String, Object> schema();

    /** Display text for the current value. */
    public abstract String display();

    protected boolean equalsValue(T a, T b) {
        return a == null ? b == null : a.equals(b);
    }
}
