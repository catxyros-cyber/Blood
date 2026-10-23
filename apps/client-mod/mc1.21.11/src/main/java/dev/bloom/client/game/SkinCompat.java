package dev.bloom.client.game;

import dev.bloom.client.core.Log;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Puts a MOTION cape into Minecraft's player skin on 1.21.9+ (PlayerSkin: body, cape, elytra,
 * model, secure — where cape is a {@code ClientAsset.Texture}).
 *
 * <p><b>Why capes didn't show.</b> The old code built the cape value by looking for a record
 * nested in the cape's declaring class with an (Identifier, Identifier) constructor. In a real
 * (obfuscated, remapped) game that lookup depends on inner-class metadata that isn't reliably
 * present, so it found nothing, logged one warning and every cape stayed hidden for the session.
 * It also built a new PlayerSkin for every player on every frame.
 *
 * <p>Now: (1) the cape value is made by the first strategy that works: the Identifier itself
 * (older versions), a record constructor found anywhere it can be found, or, when the cape type is
 * an interface, a tiny {@link Proxy} that answers every Identifier-returning method (id(),
 * texturePath()) with the MOTION texture; (2) every result is checked by reading it back;
 * (3) patched skins are cached per original skin, so the per-frame cost is one map lookup;
 */
public final class SkinCompat {
    private static final Map<Class<?>, Plan> PLANS = new ConcurrentHashMap<>();
    /** Original skin -> (cape, patched skin). Weak: skins dropped by Minecraft disappear here too. */
    private static final Map<Object, Patched> PATCHED = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean warned;

    private SkinCompat() {
    }

    private record Patched(Identifier cape, Object skin) {
    }

    private record Plan(Constructor<?> skinCtor, Method[] getters, int capeIndex, Function<Identifier, Object> capeFactory, Map<Identifier, Object> capeValues) {
        static final Plan NONE = new Plan(null, null, -1, null, Map.of());
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
        Patched hit = PATCHED.get(skin);
        if (hit != null && hit.cape().equals(cape)) return hit.skin();
        Plan plan = PLANS.computeIfAbsent(skin.getClass(), c -> plan(skin));
        if (plan.skinCtor() == null) return skin;
        try {
            Object[] args = new Object[plan.getters().length];
            for (int i = 0; i < args.length; i++) args[i] = plan.getters()[i].invoke(skin);
            Object capeValue = plan.capeValues().computeIfAbsent(cape, plan.capeFactory());
            Object out = plan.skinCtor().newInstance(withArg(args, plan.capeIndex(), capeValue));
            PATCHED.put(skin, new Patched(cape, out));
            // The patched skin is itself returned by later calls in some paths: map it to itself.
            PATCHED.put(out, new Patched(cape, out));
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(skin.getClass(), "building the skin failed: " + e);
            PLANS.put(skin.getClass(), Plan.NONE);
            return skin;
        }
    }

    /** Leaving a world / textures released: forget cached skins and cape values. */
    public static void clearCache() {
        PATCHED.clear();
        for (Plan p : PLANS.values()) if (p.skinCtor() != null) p.capeValues().clear();
    }

    private static Object[] withArg(Object[] args, int i, Object v) {
        args[i] = v;
        return args;
    }

    private static Plan plan(Object sample) {
        Class<?> skinClass = sample.getClass();
        try {
            if (!skinClass.isRecord()) return fail(skinClass, skinClass.getName() + " isn't a record");
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
            // In a real game the component names are obfuscated (comp_1234). PlayerSkin always lists
            // the body texture first and the cape as the next component of that same type.
            if (cape < 0) cape = capeIndexByType(types);
            if (cape < 0) return fail(skinClass, "no cape slot in " + skinClass.getName());
            Constructor<?> ctor = skinClass.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            Class<?> capeType = types[cape];

            Function<Identifier, Object> factory;
            if (capeType.isAssignableFrom(Identifier.class)) {
                factory = id -> id;
            } else {
                Constructor<?> rec = recordConstructor(capeType, sample, getters);
                if (rec != null && verify(rec::newInstance, capeType)) {
                    factory = id -> {
                        try {
                            return rec.newInstance(id, id);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    };
                } else if (capeType.isInterface() && !capeType.isSealed()) {
                    factory = id -> proxy(capeType, id);
                } else {
                    return fail(skinClass, "can't make a " + capeType.getName() + " from a texture id");
                }
            }
            Log.info("MOTION capes: skin patch ready for " + skinClass.getName() + " (cape slot " + cape + ")");
            return new Plan(ctor, getters, cape, factory, new ConcurrentHashMap<>());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fail(skinClass, e.toString());
        }
    }

    private interface Maker {
        Object make(Identifier a, Identifier b) throws ReflectiveOperationException;
    }

    /** True when a value built by {@code m} implements {@code type} and hands back our texture. */
    private static boolean verify(Maker m, Class<?> type) {
        try {
            Identifier probe = Identifier.fromNamespaceAndPath("bloomclient", "capes/probe");
            Object v = m.make(probe, probe);
            if (!type.isInstance(v)) return false;
            for (Method k : type.getMethods()) {
                if (k.getParameterCount() == 0 && k.getReturnType() == Identifier.class && !probe.equals(k.invoke(v))) return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
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

    /**
     * A concrete class implementing {@code type} with an (Identifier, Identifier) constructor, if one
     * can be found: among the classes of the skin's own values (a default skin's body texture is
     * exactly such a record), the sealed type's permitted subclasses, and the nested classes.
     */
    private static Constructor<?> recordConstructor(Class<?> type, Object sample, Method[] getters) {
        List<Class<?>> candidates = new ArrayList<>();
        for (Method g : getters) {
            try {
                Object v = g.invoke(sample);
                if (v != null && type.isInstance(v)) candidates.add(v.getClass());
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        try {
            Class<?>[] permitted = type.getPermittedSubclasses();
            if (permitted != null) Collections.addAll(candidates, permitted);
            Class<?> outer = type.getDeclaringClass();
            if (outer != null) Collections.addAll(candidates, outer.getDeclaredClasses());
            Collections.addAll(candidates, type.getDeclaredClasses());
        } catch (RuntimeException | LinkageError ignored) {
        }
        for (Class<?> c : candidates) {
            if (!type.isAssignableFrom(c) || c.isInterface() || Modifier.isAbstract(c.getModifiers())) continue;
            try {
                Constructor<?> k = c.getDeclaredConstructor(Identifier.class, Identifier.class);
                k.setAccessible(true);
                return k;
            } catch (NoSuchMethodException | RuntimeException ignored) {
            }
        }
        return null;
    }

    /** An object implementing {@code type} whose Identifier-returning methods all return {@code id}. */
    static Object proxy(Class<?> type, Identifier id) {
        InvocationHandler h = (self, method, args) -> {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "equals" -> self == args[0];
                    case "hashCode" -> System.identityHashCode(self);
                    default -> "MOTION cape " + id;
                };
            }
            if (method.getReturnType() == Identifier.class) return id;
            if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, args);
            Class<?> r = method.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive() && r != void.class) return 0;
            return null;
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }

    private static Plan fail(Class<?> skinClass, String why) {
        if (!warned) {
            warned = true;
            Log.warn("MOTION capes: can't attach capes to " + skinClass.getName() + " on this Minecraft version: " + why);
        }
        return Plan.NONE;
    }
}
