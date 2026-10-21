package dev.bloom.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.render.OutlineRenderer;
import dev.bloom.client.render.SkyRenderer;
import dev.bloom.client.render.WorldMatrices;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void bloom$captureMatrices(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                       LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        WorldMatrices.capture(frustumMatrix, projectionMatrix);
    }

    /** F3+T / resource pack changes: drop cached sky textures and rebuild the shader chain. */
    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void bloom$reload(ResourceManager resourceManager, CallbackInfo ci) {
        if (BloomClient.ready()) BloomClient.get().onResourcesReloaded();
    }

    @Inject(method = "renderSky", at = @At("RETURN"))
    private void bloom$renderSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy,
                                 Runnable skyFogSetup, CallbackInfo ci) {
        try {
            SkyRenderer.render(frustumMatrix, projectionMatrix, partialTick, camera, isFoggy);
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("sky", e);
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void bloom$hideClouds(PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick,
                                  double camX, double camY, double camZ, CallbackInfo ci) {
        BloomClient bc = BloomClient.get();
        if (bc != null && bc.skyActive() && !BloomSettings.SKY_CLOUDS.on()) ci.cancel();
    }

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void bloom$hideVanillaWeather(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        BloomClient bc = BloomClient.get();
        if (bc != null && BloomSettings.ENABLED.on() && BloomSettings.RAIN_HIDE_VANILLA.on() && !bc.worldEffectsPaused()) ci.cancel();
    }

    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void bloom$outline(PoseStack poseStack, VertexConsumer consumer, Entity entity, double camX, double camY, double camZ,
                               BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!BloomClient.ready()) return;
        try {
            if (OutlineRenderer.render(poseStack, entity, camX, camY, camZ, pos, state)) ci.cancel();
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("block outline", e);
        }
    }
}
