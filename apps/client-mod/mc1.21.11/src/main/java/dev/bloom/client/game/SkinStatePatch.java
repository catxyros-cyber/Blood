package dev.bloom.client.game;

import dev.bloom.client.core.Log;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;

/**
 * Puts the MOTION cape on an already-extracted player render state. The skin field is found once
 * by type (its name is obfuscated in a real game): the field whose type is a record that the
 * state's current skin value is an instance of. If none is found, this quietly does nothing and
 * the getSkin() hook remains the only path.
 */
public final class SkinStatePatch {
    private static volatile Field field;
    private static volatile boolean searched;
    private static volatile boolean warned;

    private SkinStatePatch() {
    }

    public static void apply(UUID uuid, Object state) {
        if (uuid == null || state == null) return;
        try {
            Identifier cape = CapeManager.capeFor(uuid);
            if (cape == null) return;
            Field f = field(state);
            if (f == null) return;
            Object skin = f.get(state);
            Object changed = SkinCompat.withCape(skin, cape);
            if (changed != skin) {
                f.set(state, changed);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                Log.warn("MOTION capes: unable to patch player render state", e);
            }
        }
    }

    private static Field field(Object state) throws IllegalAccessException {
        if (searched) return field;
        synchronized (SkinStatePatch.class) {
            if (searched) return field;
            for (Class<?> c = state.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || !f.getType().isRecord()) continue;
                    f.setAccessible(true);
                    Object v = f.get(state);
                    // PlayerSkin: a record whose first component's type also appears again (body, cape…).
                    if (v != null && looksLikeSkin(f.getType())) {
                        field = f;
                        searched = true;
                        return f;
                    }
                }
            }
            searched = true;
            return null;
        }
    }

    private static boolean looksLikeSkin(Class<?> type) {
        var comps = type.getRecordComponents();
        if (comps == null || comps.length < 4) return false;
        Class<?>[] types = new Class<?>[comps.length];
        for (int i = 0; i < comps.length; i++) types[i] = comps[i].getType();
        return SkinCompat.capeIndexByType(types) > 0;
    }
}
