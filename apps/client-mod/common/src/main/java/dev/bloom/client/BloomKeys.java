package dev.bloom.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** MOTION's key mappings. They're normal Minecraft key mappings, so they're saved in options.txt. */
public final class BloomKeys {
    public static final String CATEGORY = "key.categories.bloomclient";

    public static final KeyMapping MENU = new KeyMapping("key.bloomclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY);
    public static final KeyMapping VISUALS_MENU = new KeyMapping("key.bloomclient.visuals_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY);
    public static final KeyMapping HUD_EDITOR = new KeyMapping("key.bloomclient.hud_editor", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping TOGGLE_VISUALS = new KeyMapping("key.bloomclient.toggle_visuals", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping NEXT_PROFILE = new KeyMapping("key.bloomclient.next_profile", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.bloomclient.toggle_hud", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final List<KeyMapping> ALL = List.of(MENU, VISUALS_MENU, HUD_EDITOR, TOGGLE_VISUALS, NEXT_PROFILE, TOGGLE_HUD);

    private BloomKeys() {
    }

    public static String label(KeyMapping k) {
        if (k == MENU) return "Open MOTION menu";
        if (k == VISUALS_MENU) return "Open visuals menu";
        if (k == HUD_EDITOR) return "Open HUD editor";
        if (k == TOGGLE_VISUALS) return "Turn MOTION visuals on/off";
        if (k == NEXT_PROFILE) return "Next visual profile";
        if (k == TOGGLE_HUD) return "Show/hide MOTION HUD";
        return k.getName();
    }

    public static String description(KeyMapping k) {
        if (k == MENU) return "Opens this menu from anywhere in game. Press it again or Esc to close.";
        if (k == VISUALS_MENU) return "Skyboxes, shader effects, fog, presets and performance in a compact menu. Press it again to close.";
        if (k == HUD_EDITOR) return "Jumps straight into the HUD editor.";
        if (k == TOGGLE_VISUALS) return "Quickly compare MOTION's visuals with plain Minecraft.";
        if (k == NEXT_PROFILE) return "Cycles through your visual profiles.";
        if (k == TOGGLE_HUD) return "Hides or shows all MOTION HUD widgets.";
        return "";
    }
}
