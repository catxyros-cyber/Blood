package dev.bloom.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.bloom.client.BloomClient;
import dev.bloom.client.render.FogTweaks;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BLOOD fog: distance, haze and tint, applied just before Minecraft uploads this frame's fog. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void bloom$fog(Camera camera, int renderDistance, net.minecraft.client.DeltaTracker deltaTracker, float darken,
                           net.minecraft.client.multiplayer.ClientLevel level, CallbackInfoReturnable<Vector4f> cir,
                           @Local FogData data, @Local Vector4f fogColor) {
        if (!BloomClient.ready()) return;
        try {
            FogTweaks.apply(camera, data, fogColor);
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("fog", e);
        }
    }
}
