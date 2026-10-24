package dev.bloom.client.mixin;

import dev.bloom.client.game.MenuBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MOTION menu backgrounds on Minecraft's own screens: replaces the title-screen panorama, the
 * blurred world and the menu background texture with the player's chosen background.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Shadow @Final protected Minecraft minecraft;
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void bloom$panorama(GuiGraphics g, float partialTick, CallbackInfo ci) {
        if (!MenuBackground.active()) return;
        try {
            MenuBackground.draw(g, 0, 0, width, height, 1f);
            ci.cancel();
        } catch (RuntimeException ignored) {
        }
    }

    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void bloom$blur(GuiGraphics g, CallbackInfo ci) {
        // An opaque background hides the world anyway: skip the (costly) blur.
        if (MenuBackground.active() && (minecraft.level == null || MenuBackground.inWorldAlpha() >= 0.99f)) ci.cancel();
    }

    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true)
    private void bloom$menuBackground(GuiGraphics g, int x, int y, int w, int h, CallbackInfo ci) {
        if (!MenuBackground.active()) return;
        try {
            MenuBackground.draw(g, x, y, w, h, minecraft.level == null ? 1f : MenuBackground.inWorldAlpha());
            ci.cancel();
        } catch (RuntimeException ignored) {
        }
    }
}
