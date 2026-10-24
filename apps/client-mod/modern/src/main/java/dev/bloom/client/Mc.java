package dev.bloom.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The few things every part of MOTION asks of Minecraft whose home moved between versions (26.2
 * moved screens, toasts and the HUD into {@code Gui}, and the camera getter into a record-style
 * accessor). Call these instead of reaching into {@link Minecraft} directly.
 */
public final class Mc {
    private Mc() {
    }

    public static Minecraft get() {
        return Minecraft.getInstance();
    }

    /** The open screen, or null in game. */
    public static Screen screen() {
//#if MC >= 26.2
        return get().gui.screen();
//#else
        return get().screen;
//#endif
    }

    public static void setScreen(Screen screen) {
//#if MC >= 26.2
        get().gui.setScreen(screen);
//#else
        get().setScreen(screen);
//#endif
    }

    /** The action bar message above the hotbar. */
    public static void overlayMessage(Component message, boolean animateColor) {
//#if MC >= 26.2
        get().gui.hud.setOverlayMessage(message, animateColor);
//#else
        get().gui.setOverlayMessage(message, animateColor);
//#endif
    }

    /** True while the player has hidden the HUD (F1). */
    public static boolean hudHidden() {
//#if MC >= 26.2
        return get().gui.hud.isHidden();
//#else
        return get().options.hideGui;
//#endif
    }

    public static Camera camera() {
//#if MC >= 26.2
        return get().gameRenderer.mainCamera();
//#else
        return get().gameRenderer.getMainCamera();
//#endif
    }

    public static ToastManager toasts() {
//#if MC >= 26.2
        return get().gui.toastManager();
//#else
        return get().getToastManager();
//#endif
    }
}
