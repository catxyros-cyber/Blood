package dev.bloom.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.bloom.client.game.MenuBackground;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** MOTION menu background colour on Minecraft's resource-loading screen (the Mojang logo screen). */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int bloom$backgroundColor(int original) {
        try {
            return MenuBackground.loadingColor(original);
        } catch (RuntimeException e) {
            return original;
        }
    }
}
