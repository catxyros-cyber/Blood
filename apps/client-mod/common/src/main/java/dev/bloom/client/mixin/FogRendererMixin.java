package dev.bloom.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.bloom.client.render.FogTweaks;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void bloom$fogDistance(Camera camera, FogRenderer.FogMode fogMode, float farPlaneDistance, boolean shouldCreateFog,
                                          float partialTick, CallbackInfo ci) {
        if (fogMode == FogRenderer.FogMode.FOG_TERRAIN) FogTweaks.adjustFogDistance(camera, farPlaneDistance, shouldCreateFog);
    }

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void bloom$fogColor(Camera camera, float partialTick, ClientLevel level, int renderDistanceChunks,
                                       float bossColorModifier, CallbackInfo ci) {
        float[] rgb = {fogRed, fogGreen, fogBlue};
        if (FogTweaks.tintFogColor(camera, rgb)) {
            fogRed = rgb[0];
            fogGreen = rgb[1];
            fogBlue = rgb[2];
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
        }
    }
}
