package dev.bloom.client.mixin;

import dev.bloom.client.Mc;
import dev.bloom.client.BloomClient;
import dev.bloom.client.FreeLook;
import dev.bloom.client.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts clicks for the CPS widget. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void bloom$countClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && Mc.screen() == null && BloomClient.ready()) BloomClient.get().onMouseClick(info.button());
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void motion$freeLookTurn(CallbackInfo ci) {
        if (minecraft.player != null) FreeLook.afterMouseTurn(minecraft.player);
    }

    /** Zoom: while zoomed, the mouse wheel changes the zoom instead of the hotbar slot. */
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void motion$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Mc.screen() == null && minecraft.player != null && Zoom.onScroll(vertical)) ci.cancel();
    }
}
