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
//#if MC >= 26.2
// 26.2 moved the in-game HUD (crosshair included) out of Gui into Hud.
@Mixin(net.minecraft.client.gui.Hud.class)
//#else
@Mixin(Gui.class)
//#endif
public abstract class GuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void bloom$customCrosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        dev.bloom.client.Hooks.fired("crosshair");
        if (CrosshairRenderer.render(graphics)) ci.cancel();
    }
}
