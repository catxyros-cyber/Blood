package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
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
        if (action == GLFW.GLFW_PRESS && minecraft.screen == null && BloomClient.ready()) BloomClient.get().onMouseClick(info.button());
    }
}
