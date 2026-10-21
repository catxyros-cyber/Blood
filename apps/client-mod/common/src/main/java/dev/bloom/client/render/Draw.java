package dev.bloom.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/** Small helpers for immediate-mode drawing with Minecraft 1.21.1's render system. */
public final class Draw {
    private Draw() {
    }

    public static BufferBuilder beginTexColor() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    public static BufferBuilder beginColor() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    /** Draws a textured batch with the position/texture/colour shader. {@code vertices} 0 = nothing to draw. */
    public static void drawTexColor(BufferBuilder b, int vertices, ResourceLocation texture) {
        if (vertices <= 0) {
            discard(b);
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    public static void drawTexColor(BufferBuilder b, int vertices, int glTextureId) {
        if (vertices <= 0) {
            discard(b);
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, glTextureId);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    public static void drawColor(BufferBuilder b, int vertices) {
        if (vertices <= 0) {
            discard(b);
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** A builder with no vertices must still be finished; build() returns null in that case. */
    private static void discard(BufferBuilder b) {
        var mesh = b.build();
        if (mesh != null) mesh.close();
    }

    public static void blendAlpha() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    public static void blendAdditive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    public static void restoreBlend() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static float r(int rgb) {
        return ((rgb >> 16) & 255) / 255f;
    }

    public static float g(int rgb) {
        return ((rgb >> 8) & 255) / 255f;
    }

    public static float b(int rgb) {
        return (rgb & 255) / 255f;
    }
}
