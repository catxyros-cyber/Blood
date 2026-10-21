package dev.bloom.client.core.settings;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * One user-facing option. Values live in the setting object itself so renderers can
 * read them every frame without map lookups or boxing (see the typed getters on each
 * subclass). Every setting knows how to validate, serialize and reset itself.
 */
public abstract class Setting {
    public interface Listener {
        void changed(Setting setting);
    }

    private final String key;
    private final Category category;
    private final String group;
    private final String label;
    private final String description;
    private final String[] keywords;
    private final List<Listener> listeners = new ArrayList<>(2);
    /** Settings that belong to a visual profile (sky, effects, colours, HUD style, quality). */
    private boolean profileScoped;
    /** Non-null when a change only takes effect after restarting the game; says why. */
    private String restartReason;
    /** Shown greyed out unless this toggle is on. */
    private BoolSetting parent;
    /** Extra categories this setting is also listed under. */
    private Category[] alsoIn = new Category[0];
    /** Returns a reason when the feature can't work right now (e.g. shader pack active), else null. */
    private Supplier<String> availability = () -> null;

    protected Setting(String key, Category category, String group, String label, String description, String... keywords) {
        if (!key.matches("[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9]+)+")) {
            throw new IllegalArgumentException("Bad setting key: " + key);
        }
        this.key = key;
        this.category = category;
        this.group = group;
        this.label = label;
        this.description = description;
        this.keywords = keywords;
    }

    public final String key() {
        return key;
    }

    public final Category category() {
        return category;
    }

    public final String group() {
        return group;
    }

    public final String label() {
        return label;
    }

    public final String description() {
        return description;
    }

    public final boolean profileScoped() {
        return profileScoped;
    }

    public final String restartReason() {
        return restartReason;
    }

    public final BoolSetting parent() {
        return parent;
    }

    public final Category[] alsoIn() {
        return alsoIn;
    }

    /** True when this setting should appear in the given category. */
    public final boolean listedIn(Category c) {
        if (c == category) return true;
        for (Category other : alsoIn) if (other == c) return true;
        return false;
    }

    /** Null when usable; otherwise a short explanation shown next to the control. */
    public final String unavailableReason() {
        try {
            return availability.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Enabled = parent toggle on (if any) and feature available. */
    public final boolean isActive() {
        return (parent == null || parent.on()) && unavailableReason() == null;
    }

    // ---- builder-style configuration (used once while registering) ----

    @SuppressWarnings("unchecked")
    public <S extends Setting> S profile() {
        this.profileScoped = true;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting> S requiresRestart(String reason) {
        this.restartReason = reason;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting> S dependsOn(BoolSetting toggle) {
        this.parent = toggle;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting> S alsoIn(Category... categories) {
        this.alsoIn = categories;
        return (S) this;
    }

    public void setAvailability(Supplier<String> availability) {
        this.availability = availability == null ? () -> null : availability;
    }

    // ---- values ----

    public final void addListener(Listener l) {
        listeners.add(l);
    }

    protected final void fireChanged() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).changed(this);
        }
    }

    public abstract boolean isDefault();

    public abstract void reset();

    public abstract JsonElement toJson();

    /** True when {@link #fromJson} would accept this value. Never changes anything. */
    public abstract boolean accepts(JsonElement json);

    /** Applies a JSON value. Returns false (and changes nothing) when the value is invalid. */
    public abstract boolean fromJson(JsonElement json);

    /** Short human-readable value, used by search results and tooltips. */
    public abstract String displayValue();

    /** Search: matches the label, key, group, description or keywords. */
    public final boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (String token : q.split("\\s+")) {
            if (!matchesToken(token)) return false;
        }
        return true;
    }

    private boolean matchesToken(String t) {
        if (label.toLowerCase(Locale.ROOT).contains(t)) return true;
        if (key.toLowerCase(Locale.ROOT).contains(t)) return true;
        if (group != null && group.toLowerCase(Locale.ROOT).contains(t)) return true;
        if (description != null && description.toLowerCase(Locale.ROOT).contains(t)) return true;
        if (category.label().toLowerCase(Locale.ROOT).contains(t)) return true;
        for (String k : keywords) if (k.toLowerCase(Locale.ROOT).contains(t)) return true;
        return false;
    }

    @Override
    public String toString() {
        return key + "=" + displayValue();
    }
}
