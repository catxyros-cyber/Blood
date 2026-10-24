package dev.bloom.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
//#if MC < 1.21.11
import com.mojang.blaze3d.textures.AddressMode;
//#endif
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bloom.client.BloomClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Immediate-mode world drawing for Minecraft 1.21.11's GPU API: build a batch of quads, then draw
 * it with one of MOTION's render pipelines straight into the main render target. Blending, depth
 * and culling live in the pipeline (there is no global GL state any more), so callers pick a
 * {@link Blend} and whether the batch is depth-tested.
 *
 * <p>Usage: {@code Batch b = Draw.texColor(); b.v().addVertex(...)...; Draw.draw(b, texture, modelView, Blend.ALPHA, false);}
 */
public final class Draw {
    public enum Blend {ALPHA, ADDITIVE}

    private static final Identifier TEX_COLOR_SHADER = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "core/bloom_tex_color");
    private static final Identifier COLOR_SHADER = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "core/bloom_color");

    private static final RenderPipeline.Snippet MATRICES = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    private static final BlendFunction ALPHA_BLEND = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
    private static final BlendFunction ADDITIVE_BLEND = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE);

    private static final Map<Blend, RenderPipeline> TEX_SKY = new EnumMap<>(Blend.class);
    private static final Map<Blend, RenderPipeline> TEX_WORLD = new EnumMap<>(Blend.class);
    private static final Map<Blend, RenderPipeline> COLOR_SKY = new EnumMap<>(Blend.class);
    private static final Map<Blend, RenderPipeline> COLOR_WORLD = new EnumMap<>(Blend.class);

    private Draw() {
    }

    /** A batch of quads being built. Always pass it to {@link #draw} (or {@link #discard}). */
    public static final class Batch {
        final ByteBufferBuilder memory;
        final BufferBuilder builder;
        final boolean textured;
        int vertices;

        Batch(boolean textured) {
            this.textured = textured;
            VertexFormat format = textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR;
            this.memory = new ByteBufferBuilder(format.getVertexSize() * 256);
            this.builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS, format);
        }

        public BufferBuilder v() {
            return builder;
        }

        /** Call after adding each quad (4 vertices). */
        public void quad() {
            vertices += 4;
        }

        public void add(int n) {
            vertices += n;
        }

        public int vertices() {
            return vertices;
        }
    }

    public static Batch texColor() {
        return new Batch(true);
    }

    public static Batch color() {
        return new Batch(false);
    }

    public static void discard(Batch b) {
        try {
            MeshData mesh = b.builder.build();
            if (mesh != null) mesh.close();
        } finally {
            b.memory.close();
        }
    }

    private static RenderPipeline pipeline(boolean textured, Blend blend, boolean depthTest) {
        Map<Blend, RenderPipeline> cache = textured ? (depthTest ? TEX_WORLD : TEX_SKY) : (depthTest ? COLOR_WORLD : COLOR_SKY);
        return cache.computeIfAbsent(blend, bl -> {
            RenderPipeline.Builder p = RenderPipeline.builder(MATRICES);
            String name = (textured ? "tex_color" : "color") + "/" + bl.name().toLowerCase(java.util.Locale.ROOT) + (depthTest ? "_depth" : "_sky");
            p.withLocation(Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "pipeline/" + name));
            Identifier shader = textured ? TEX_COLOR_SHADER : COLOR_SHADER;
            p.withVertexShader(shader);
            p.withFragmentShader(shader);
            if (textured) p.withSampler("Sampler0");
            p.withVertexFormat(textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS);
            p.withBlend(bl == Blend.ADDITIVE ? ADDITIVE_BLEND : ALPHA_BLEND);
            p.withDepthWrite(false);
            p.withCull(false);
            if (!depthTest) p.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
            return p.build();
        });
    }

    /** Binds a texture with linear filtering, clamped at the edges (skyboxes, effect sprites, post effects). */
    public static void bindLinearClamp(RenderPass pass, String name, GpuTextureView view) {
//#if MC >= 1.21.11
        pass.bindTexture(name, view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
//#else
        // Before 1.21.11 filtering lived on the texture itself.
        view.texture().setTextureFilter(FilterMode.LINEAR, false);
        view.texture().setAddressMode(AddressMode.CLAMP_TO_EDGE);
        pass.bindSampler(name, view);
//#endif
    }

    /** Minecraft's per-draw transform uniforms (model-view, colour, offset, texture matrix). */
    public static GpuBufferSlice transforms(Matrix4f modelView, Vector4f color, Vector3f offset, Matrix4f texture) {
        return RenderSystem.getDynamicUniforms().writeTransform(modelView, color, offset, texture
//#if MC < 1.21.11
                , 1f
//#endif
        );
    }

    /**
     * Draws a textured batch. {@code modelView} transforms the vertices into view space (the
     * projection is Minecraft's current one). Nothing is drawn for an empty batch.
     */
    public static void draw(Batch b, Identifier texture, Matrix4f modelView, Blend blend, boolean depthTest) {
        AbstractTexture tex;
        try {
            tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
        } catch (RuntimeException e) {
            discard(b);
            throw e;
        }
        draw(b, tex.getTextureView(), modelView, blend, depthTest);
    }

    public static void draw(Batch b, GpuTextureView view, Matrix4f modelView, Blend blend, boolean depthTest) {
        submit(b, pipeline(true, blend, depthTest), modelView, pass -> bindLinearClamp(pass, "Sampler0", view), depthTest);
    }

    /** Draws an untextured (position + colour) batch. */
    public static void draw(Batch b, Matrix4f modelView, Blend blend, boolean depthTest) {
        submit(b, pipeline(false, blend, depthTest), modelView, pass -> {
        }, depthTest);
    }

    private static void submit(Batch b, RenderPipeline pipeline, Matrix4f modelView, Consumer<RenderPass> bind, boolean depthTest) {
        if (b.vertices <= 0) {
            discard(b);
            return;
        }
        try (MeshData mesh = b.builder.buildOrThrow()) {
            GpuBufferSlice transforms = transforms(modelView, new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
            GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
            GpuBuffer indices = seq.getBuffer(mesh.drawState().indexCount());
            RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
            GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
            GpuTextureView depth = null;
            if (depthTest && target.useDepth) {
                depth = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
            }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "MOTION " + pipeline, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, seq.type());
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                bind.accept(pass);
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
            }
        } finally {
            b.memory.close();
        }
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
