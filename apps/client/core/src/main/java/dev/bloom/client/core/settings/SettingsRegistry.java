package dev.bloom.client.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** All settings in declaration order, with bulk load/save and change notification. */
public class SettingsRegistry {
    private final Map<String, Setting<?>> byId = new LinkedHashMap<>();
    private final List<Consumer<Setting<?>>> changeHooks = new CopyOnWriteArrayList<>();
    private BiConsumer<Setting<?>, RuntimeException> errorHandler = (s, e) -> {};
    private int batchDepth;
    private boolean batchChanged;

    protected <S extends Setting<?>> S add(S setting) {
        if (byId.containsKey(setting.id)) throw new IllegalStateException("Duplicate setting id " + setting.id);
        setting.registry = this;
        byId.put(setting.id, setting);
        return setting;
    }

    public List<Setting<?>> all() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public Setting<?> get(String id) {
        return byId.get(id);
    }

    public List<Setting<?>> inCategory(Category c) {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> s : byId.values()) if (s.category == c) out.add(s);
        return out;
    }

    public List<Setting<?>> inScope(Setting.Scope scope) {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> s : byId.values()) if (s.scope == scope) out.add(s);
        return out;
    }

    /** Called (on the thread that changed it) after any setting changes. */
    public void onAnyChange(Consumer<Setting<?>> hook) {
        changeHooks.add(hook);
    }

    public void onListenerError(BiConsumer<Setting<?>, RuntimeException> handler) {
        this.errorHandler = handler;
    }

    void reportListenerError(Setting<?> s, RuntimeException e) {
        errorHandler.accept(s, e);
    }

    void fireChanged(Setting<?> s) {
        if (batchDepth > 0) {
            batchChanged = true;
            return;
        }
        for (Consumer<Setting<?>> h : changeHooks) h.accept(s);
    }

    /** Apply many changes and fire a single change notification (with a null setting) at the end. */
    public void batch(Runnable r) {
        batchDepth++;
        try {
            r.run();
        } finally {
            batchDepth--;
            if (batchDepth == 0 && batchChanged) {
                batchChanged = false;
                for (Consumer<Setting<?>> h : changeHooks) h.accept(null);
            }
        }
    }

    /** Values of one scope as plain JSON values, keyed by id. Only non-default values when {@code onlyChanged}. */
    public Map<String, Object> snapshot(Setting.Scope scope, boolean onlyChanged) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Setting<?> s : byId.values()) {
            if (s.scope != scope) continue;
            if (onlyChanged && s.isDefault()) continue;
            m.put(s.id, s.toJson());
        }
        return m;
    }

    /** Result of applying untrusted values. */
    public record ApplyReport(int applied, List<String> unknown, List<String> invalid) {
        public boolean clean() {
            return unknown.isEmpty() && invalid.isEmpty();
        }
    }

    /**
     * Apply values for one scope. Settings of that scope missing from the map are
     * reset to defaults when {@code resetMissing}. Unknown ids and invalid values
     * are reported and ignored — never applied.
     */
    public ApplyReport apply(Map<String, Object> values, Setting.Scope scope, boolean resetMissing) {
        List<String> unknown = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        int[] applied = {0};
        batch(() -> {
            if (values != null) {
                for (Map.Entry<String, Object> e : values.entrySet()) {
                    Setting<?> s = byId.get(e.getKey());
                    if (s == null || s.scope != scope) {
                        unknown.add(e.getKey());
                        continue;
                    }
                    if (s.loadJson(e.getValue())) applied[0]++;
                    else invalid.add(e.getKey());
                }
            }
            if (resetMissing) {
                for (Setting<?> s : byId.values()) {
                    if (s.scope == scope && (values == null || !values.containsKey(s.id) || invalid.contains(s.id))) s.reset();
                }
            }
        });
        return new ApplyReport(applied[0], unknown, invalid);
    }

    public void resetAll(Setting.Scope scope) {
        batch(() -> {
            for (Setting<?> s : byId.values()) if (s.scope == scope) s.reset();
        });
    }

    public void resetCategory(Category c) {
        batch(() -> {
            for (Setting<?> s : byId.values()) if (s.category == c) s.reset();
        });
    }
}
