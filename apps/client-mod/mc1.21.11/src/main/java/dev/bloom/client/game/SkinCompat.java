package dev.bloom.client.game;

import dev.bloom.client.core.Log;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small reflective helpers for the parts of Minecraft's player-skin and profile API that change
 * between versions (PlayerSkin's cape is a texture location in some versions and a "client
 * asset" in others; GameProfile became a record). Everything is looked up once and cached; if
 * nothing fits, MOTION simply leaves the skin alone.
 */
public final class SkinCompat {
    private static final Map<Class<?>, Plan> PLANS = new ConcurrentHashMap<>();
    private static volatile boolean warned;

    private SkinCompat() {
    }

    private record Plan(Constructor<?> skinCtor, Method[] getters, int capeIndex, Constructor<?> assetCtor) {
    }

    /** The UUID of a GameProfile (works with both getId() and the record accessor id()). */
    public static UUID profileId(Object profile) {
        if (profile == null) return null;
        for (String name : new String[]{"id", "getId"}) {
            try {
                Object v = profile.getClass().getMethod(name).invoke(profile);
                if (v instanceof UUID u) return u;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    /** A copy of {@code skin} wearing {@code cape}, or {@code skin} itself if that isn't possible. */
    public static Object withCape(Object skin, Identifier cape) {
        if (skin == null || cape == null) return skin;
        Plan plan = PLANS.computeIfAbsent(skin.getClass(), SkinCompat::plan);
        if (plan.skinCtor() == null) return skin;
        try {
            Object[] args = new Object[plan.getters().length];
            for (int i = 0; i < args.length; i++) args[i] = plan.getters()[i].invoke(skin);
            Object capeValue = plan.assetCtor() == null ? cape : plan.assetCtor().newInstance(cape, cape);
            if (capeValue.equals(args[plan.capeIndex()])) return skin;
            args[plan.capeIndex()] = capeValue;
            return plan.skinCtor().newInstance(args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
            return skin;
        }
    }

    private static Plan plan(Class<?> skinClass) {
        try {
            if (!skinClass.isRecord()) return new Plan(null, null, -1, null);
            RecordComponent[] comps = skinClass.getRecordComponents();
            Class<?>[] types = new Class<?>[comps.length];
            Method[] getters = new Method[comps.length];
            int cape = -1;
            for (int i = 0; i < comps.length; i++) {
                types[i] = comps[i].getType();
                getters[i] = comps[i].getAccessor();
                getters[i].setAccessible(true);
                String n = comps[i].getName();
                if (cape < 0 && (n.equals("cape") || n.equals("capeTexture"))) cape = i;
            }
            // In a real (non-development) game Minecraft's record components are obfuscated
            // (comp_1234), so the names above never match. PlayerSkin always lists the body
            // texture first and the cape as the next component of that same type
            // (1.21.1: texture, textureUrl, capeTexture, elytraTexture, …;
            //  1.21.9+: body, cape, elytra, …), so find it by type instead.
            if (cape < 0) cape = capeIndexByType(types);
            if (cape < 0) {
                warnOnce(new IllegalStateException("Couldn't find the cape in " + skinClass.getName()));
                return new Plan(null, null, -1, null);
            }
            Constructor<?> ctor = skinClass.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            Class<?> capeType = types[cape];
            Constructor<?> asset = null;
            if (!capeType.isAssignableFrom(Identifier.class)) {
                asset = assetConstructor(capeType);
                if (asset == null) {
                    warnOnce(new IllegalStateException("No way to make a " + capeType.getName() + " from a texture id"));
                    return new Plan(null, null, -1, null);
                }
            }
            return new Plan(ctor, getters, cape, asset);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
            return new Plan(null, null, -1, null);
        }
    }

    /** Index of the second component with the same type as the first (the cape), or -1. */
    static int capeIndexByType(Class<?>[] types) {
        if (types.length < 2) return -1;
        Class<?> texture = types[0];
        if (texture.isPrimitive() || texture == String.class) return -1;
        for (int i = 1; i < types.length; i++) if (types[i] == texture) return i;
        return -1;
    }

    /** A record implementing {@code type} built from (Identifier id, Identifier texturePath). */
    private static Constructor<?> assetConstructor(Class<?> type) {
        java.util.List<Class<?>> candidates = new java.util.ArrayList<>();
        candidates.add(type);
        if (type.getDeclaringClass() != null) {
            candidates.add(type.getDeclaringClass());
            java.util.Collections.addAll(candidates, type.getDeclaringClass().getDeclaredClasses());
        }
        java.util.Collections.addAll(candidates, type.getDeclaredClasses());
        for (Class<?> c : candidates) {
            if (!type.isAssignableFrom(c) || c.isInterface() || java.lang.reflect.Modifier.isAbstract(c.getModifiers())) continue;
            try {
                Constructor<?> k = c.getDeclaredConstructor(Identifier.class, Identifier.class);
                k.setAccessible(true);
                return k;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static void warnOnce(Exception e) {
        if (warned) return;
        warned = true;
        Log.warn("MOTION capes: this Minecraft version's skins can't take a custom cape; capes stay hidden", e);
    }
}
