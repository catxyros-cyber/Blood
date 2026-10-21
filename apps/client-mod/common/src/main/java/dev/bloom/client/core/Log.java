package dev.bloom.client.core;

/**
 * Minimal logging facade so the core has no game dependencies. The loader entrypoint
 * plugs in Minecraft's SLF4J logger; tests use the default (stderr, warnings only).
 * Never log tokens, file contents or anything personal.
 */
public final class Log {
    public interface Sink {
        void info(String msg);

        void warn(String msg, Throwable t);
    }

    private static volatile Sink sink = new Sink() {
        @Override
        public void info(String msg) {
        }

        @Override
        public void warn(String msg, Throwable t) {
            System.err.println("[BLOOD] " + msg + (t != null ? " (" + t + ")" : ""));
        }
    };

    private Log() {
    }

    public static void setSink(Sink s) {
        if (s != null) sink = s;
    }

    public static void info(String msg) {
        sink.info(msg);
    }

    public static void warn(String msg) {
        sink.warn(msg, null);
    }

    public static void warn(String msg, Throwable t) {
        sink.warn(msg, t);
    }
}
