package dev.bloom.client.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Your own Minecraft face (skin front plus hat layer), for MOTION's menus. */
public final class PlayerFace {
    private PlayerFace() {
    }

    /** Your skin from Mojang once it has loaded; Minecraft's default skin for your UUID until then. */
    public static Identifier texture() {
        UUID id = Minecraft.getInstance().getUser().getProfileId();
        ProfileSkin.fetch(id);
        ProfileSkin.Skin own = ProfileSkin.get();
        if (own != null) return own.texture();
        return DefaultPlayerSkin.get(id).body().texturePath();
    }

    /** Draws the 8×8 face and its hat overlay scaled to {@code size}. */
    public static void draw(GuiGraphics g, int x, int y, int size, int argb) {
        Identifier tex = texture();
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 8f, 8f, size, size, 8, 8, 64, 64, argb);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 40f, 8f, size, size, 8, 8, 64, 64, argb);
    }
}
