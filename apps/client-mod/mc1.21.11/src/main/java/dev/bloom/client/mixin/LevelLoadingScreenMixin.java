package dev.bloom.client.mixin;

import dev.bloom.client.game.MenuBackground;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MOTION menu background on the world-loading screen. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    protected LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void bloom$background(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!MenuBackground.onLoadingScreens()) return;
        try {
            MenuBackground.draw(g, 0, 0, this.width, this.height, 1f);
            ci.cancel();
        } catch (RuntimeException ignored) {
        }
    }
}
