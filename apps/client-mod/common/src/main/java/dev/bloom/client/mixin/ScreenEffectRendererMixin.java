package dev.bloom.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Fire overlay height": moves the burning overlay down so it covers less of the screen. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void bloom$lowerFire(Minecraft minecraft, PoseStack poseStack, CallbackInfo ci) {
        poseStack.pushPose();
        if (BloomClient.ready() && BloomSettings.ENABLED.on()) {
            poseStack.translate(0f, -BloomSettings.FIRE_OFFSET.value(), 0f);
        }
    }

    @Inject(method = "renderFire", at = @At("TAIL"))
    private static void bloom$lowerFireEnd(Minecraft minecraft, PoseStack poseStack, CallbackInfo ci) {
        poseStack.popPose();
    }
}
