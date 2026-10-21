package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts clicks for the CPS and keystrokes widgets (only while playing, not in menus). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onPress", at = @At("HEAD"))
    private void bloom$countClick(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && minecraft.screen == null && BloomClient.ready()) BloomClient.get().onMouseClick(button);
    }
}
