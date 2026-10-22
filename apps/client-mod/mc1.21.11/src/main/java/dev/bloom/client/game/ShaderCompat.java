package dev.bloom.client.game;

import java.lang.reflect.Method;

/**
 * Detects whether Iris (or Oculus) has a shader pack switched on, via Iris's public API and
 * reflection, so BLOOD never needs Iris at compile time and never breaks when it's absent.
 */
public final class ShaderCompat {
    private static final Object API;
    private static final Method IN_USE;

    static {
        Object api = null;
        Method m = null;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = c.getMethod("getInstance").invoke(null);
            m = c.getMethod("isShaderPackInUse");
        } catch (Throwable ignored) {
            // Iris not installed (or an incompatible version): treat as "no shaders".
        }
        API = api;
        IN_USE = m;
    }

    private ShaderCompat() {
    }

    public static boolean irisPresent() {
        return API != null;
    }

    public static boolean shaderPackInUse() {
        if (API == null) return false;
        try {
            return (Boolean) IN_USE.invoke(API);
        } catch (Throwable t) {
            return false;
        }
    }
}
