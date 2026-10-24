package dev.bloom.client;

import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.zoom.ZoomState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * OptiFine-style zoom: hold (or toggle) the Zoom key, scroll to zoom further. Works by scaling the
 * world field of view in {@code GameRendererMixin}; the hand and the HUD are left alone.
 */
public final class Zoom {
    private static final ZoomState STATE = new ZoomState();
    private static boolean cinematicOn;
    private static boolean cinematicBefore;
    /** Set once the field-of-view hook runs, so we can tell players if this version lacks it. */
    private static volatile boolean hookSeen;
    private static boolean warned;

    private Zoom() {
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        boolean allowed = mc.player != null && Mc.screen() == null;
        STATE.key(BloomKeys.ZOOM.isDown(), BloomSettings.ZOOM_TOGGLE.on(), allowed);

        boolean cinematic = STATE.wanted() && BloomSettings.ZOOM_CINEMATIC.on();
        if (cinematic && !cinematicOn) {
            cinematicBefore = mc.options.smoothCamera;
            mc.options.smoothCamera = true;
            cinematicOn = true;
        } else if (!cinematic && cinematicOn) {
            mc.options.smoothCamera = cinematicBefore;
            cinematicOn = false;
        }

        if (STATE.wanted() && !hookSeen && !warned && mc.player != null) {
            warned = true;
            Mc.overlayMessage(Component.literal("MOTION zoom isn't available on this Minecraft version"), false);
        }
    }

    /** Multiplier for the world's field of view this frame (1 = no zoom). */
    public static double fovMultiplier() {
        hookSeen = true;
        boolean smooth = BloomSettings.ZOOM_SMOOTH.on() && !BloomSettings.REDUCE_MOTION.on();
        return STATE.fovMultiplier(System.nanoTime(), BloomSettings.ZOOM_LEVEL.value(), smooth);
    }

    /** Mouse wheel. Returns true when zoom used it (so the hotbar doesn't scroll). */
    public static boolean onScroll(double vertical) {
        if (!STATE.wanted() || !BloomSettings.ZOOM_SCROLL.on() || vertical == 0) return false;
        STATE.scroll(Math.signum(vertical), BloomSettings.ZOOM_LEVEL.value());
        Mc.overlayMessage(
                Component.literal(String.format(java.util.Locale.ROOT, "Zoom %.1f×", STATE.target(BloomSettings.ZOOM_LEVEL.value()))), false);
        return true;
    }

    public static boolean active() {
        return STATE.active();
    }
}
