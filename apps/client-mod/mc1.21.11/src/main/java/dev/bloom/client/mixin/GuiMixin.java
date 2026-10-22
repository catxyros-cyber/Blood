package dev.bloom.client.mixin;

import dev.bloom.client.render.CrosshairRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom crosshair: when it's enabled MOTION draws its own and skips the vanilla one. The
 * injection is optional ("defaultRequire": 0), so if another mod replaces this method the
 * game still starts and simply keeps the vanilla crosshair.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void bloom$customCrosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (CrosshairRenderer.render(graphics)) ci.cancel();
    }
}
