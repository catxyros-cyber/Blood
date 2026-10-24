package dev.bloom.client.game;

import net.minecraft.client.Minecraft;

/** Client bits whose API moves between Minecraft versions, looked up reflectively (never throws). */
public final class ClientCompat {
    private ClientCompat() {
    }

    /** True while the F3 debug screen is open. */
    public static boolean debugScreenShown() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Object overlay = mc.getClass().getMethod("getDebugOverlay").invoke(mc);
            Object v = overlay.getClass().getMethod("showDebugScreen").invoke(overlay);
            return v instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
