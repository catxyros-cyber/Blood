package dev.bloom.client.mixin;

import dev.bloom.client.Mc;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.bloom.client.BloomClient;
import dev.bloom.client.render.SkyRenderer;
import dev.bloom.client.render.WorldMatrices;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void bloom$capture(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                               Matrix4f viewMatrix, Matrix4f projectionMatrix, Matrix4f cullProjectionMatrix, GpuBufferSlice fogBuffer,
                               Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        WorldMatrices.capture(viewMatrix, projectionMatrix, fogColor, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    /** F3+T / resource pack changes: drop cached sky textures and shader targets. */
    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void bloom$reload(ResourceManager resourceManager, CallbackInfo ci) {
        if (BloomClient.ready()) BloomClient.get().onResourcesReloaded();
    }

    /** End of the sky pass (Minecraft's sky is drawn): MOTION's skyboxes, stars and glow on top. */
    // The sky pass is a lambda inside addSkyPass; its name, signature and static-ness change
    // between versions, so each one is spelled out.
//#if MC >= 26.2
    @Inject(method = "lambda$addSkyPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V",
            at = @At("TAIL"))
    private void bloom$sky(CallbackInfo ci) {
//#elif MC >= 26
    @Inject(method = "lambda$addSkyPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;Lnet/minecraft/client/renderer/SkyRenderer;)V",
            at = @At("TAIL"))
    private static void bloom$sky(CallbackInfo ci) {
//#elif MC >= 1.21.11
    @Inject(method = "method_62215(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/SkyRenderState;Lnet/minecraft/client/renderer/SkyRenderer;)V",
            at = @At("TAIL"))
    private static void bloom$sky(CallbackInfo ci) {
//#else
    @Inject(method = "method_62215(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/SkyRenderState;)V",
            at = @At("TAIL"))
    private void bloom$sky(CallbackInfo ci) {
//#endif
        if (!BloomClient.ready()) return;
        try {
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            modelView.setTranslation(0f, 0f, 0f);
            Camera camera = Mc.camera();
            SkyRenderer.render(modelView, WorldMatrices.partialTick, camera);
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("sky", e);
        }
    }
}
