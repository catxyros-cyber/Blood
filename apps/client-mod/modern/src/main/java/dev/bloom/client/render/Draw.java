package dev.bloom.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
//#if MC >= 26.2
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
//#else
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
//#endif
//#if MC >= 26
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
//#else
import com.mojang.blaze3d.platform.DepthTestFunction;
//#endif
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
import dev.bloom.client.Mc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Immediate-mode world drawing on Minecraft's GPU API: build a batch of quads, then draw it with
 * one of MOTION's render pipelines straight into the main render target. Blending, depth and
 * culling live in the pipeline (there is no global GL state), so callers pick a {@link Blend} and
 * whether the batch is depth-tested.
 *
 * <p>The GPU API changes a lot between Minecraft versions (26.1 split pipeline state into colour
 * and depth records, 26.2 added Vulkan with bind group layouts, primitive topologies and transient
 * uploads), so every version-specific call lives in the small helpers at the bottom of this class;
 * {@link PostEffects} uses them too.
 *
 * <p>Usage: {@code Batch b = Draw.texColor(); b.v().addVertex(...)...; Draw.draw(b, texture, modelView, Blend.ALPHA, false);}
 */
public final class Draw {
    public enum Blend {ALPHA, ADDITIVE}

    private static final Identifier TEX_COLOR_SHADER = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "core/bloom_tex_color");
    private static final Identifier COLOR_SHADER = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "core/bloom_color");

//#if MC >= 26.2
    private static final BlendFunction ALPHA_BLEND = new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA);
    private static final BlendFunction ADDITIVE_BLEND = new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ONE);
//#else
    private static final BlendFunction ALPHA_BLEND = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
    private static final BlendFunction ADDITIVE_BLEND = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE);
//#endif

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
            this.builder = quadBuilder(memory, format);
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
            String name = (textured ? "tex_color" : "color") + "/" + bl.name().toLowerCase(java.util.Locale.ROOT) + (depthTest ? "_depth" : "_sky");
            Identifier shader = textured ? TEX_COLOR_SHADER : COLOR_SHADER;
            RenderPipeline.Builder p = newPipeline(textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR,
                    true, textured ? new String[]{"Sampler0"} : new String[0]);
            p.withLocation(Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "pipeline/" + name));
            p.withVertexShader(shader);
            p.withFragmentShader(shader);
            renderState(p, bl == Blend.ADDITIVE ? ADDITIVE_BLEND : ALPHA_BLEND, depthTest);
            p.withCull(false);
            return p.build();
        });
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
            RenderTarget target = mainTarget();
            GpuTextureView color = colorOutput(target);
            GpuTextureView depth = depthTest && hasDepth(target) ? depthOutput(target) : null;
            try (Quads quads = Quads.upload(mesh); RenderPass pass = openPass("MOTION " + pipeline, color, depth)) {
                setPipeline(pass, pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                bind.accept(pass);
                quads.draw(pass);
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

    // ------------------------------------------------------------------ version-specific GPU calls

    /** A buffer builder for quads in {@code format}. */
    static BufferBuilder quadBuilder(ByteBufferBuilder memory, VertexFormat format) {
//#if MC >= 26.2
        return new BufferBuilder(memory, PrimitiveTopology.QUADS, format);
//#else
        return new BufferBuilder(memory, VertexFormat.Mode.QUADS, format);
//#endif
    }

    /**
     * A pipeline builder for quads in {@code format}, with Minecraft's transform uniforms (and the
     * projection, for world drawing) and the given samplers declared.
     */
    static RenderPipeline.Builder newPipeline(VertexFormat format, boolean projection, String... samplers) {
//#if MC >= 26.2
        RenderPipeline.Builder p = RenderPipeline.builder();
        BindGroupLayout.Builder layout = BindGroupLayout.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER);
        if (projection) layout.withUniform("Projection", UniformType.UNIFORM_BUFFER);
        for (String s : samplers) layout.withSampler(s);
        p.withBindGroupLayout(layout.build());
        p.withVertexBinding(0, format);
        p.withPrimitiveTopology(PrimitiveTopology.QUADS);
        return p;
//#else
        RenderPipeline.Builder p = RenderPipeline.builder();
        p.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER);
        if (projection) p.withUniform("Projection", UniformType.UNIFORM_BUFFER);
        for (String s : samplers) p.withSampler(s);
        p.withVertexFormat(format, VertexFormat.Mode.QUADS);
        return p;
//#endif
    }

    /**
     * The render state every MOTION pipeline uses: optional blending (null for none), an optional
     * depth test, and never writing depth.
     */
    static void renderState(RenderPipeline.Builder p, BlendFunction blend, boolean depthTest) {
//#if MC >= 26.2
        p.withColorTargetState(new ColorTargetState(Optional.ofNullable(blend), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL));
        p.withDepthStencilState(depthTest ? Optional.of(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)) : Optional.empty());
//#elif MC >= 26
        // RGBA write mask: red 1, green 2, blue 4, alpha 8.
        p.withColorTargetState(new ColorTargetState(Optional.ofNullable(blend), 15));
        p.withDepthStencilState(new DepthStencilState(depthTest ? CompareOp.LESS_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS, false, 0f, 0f));
//#else
        if (blend != null) p.withBlend(blend);
        else p.withoutBlend();
        p.withDepthWrite(false);
        if (!depthTest) p.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
//#endif
    }

    /** Minecraft's main render target (what the world and HUD are drawn into). */
    static RenderTarget mainTarget() {
//#if MC >= 26.2
        return Mc.get().gameRenderer.mainRenderTarget();
//#else
        return Mc.get().getMainRenderTarget();
//#endif
    }

    /** Where world drawing goes this frame (before 26.3 Minecraft could redirect it, e.g. for Fabulous transparency). */
    static GpuTextureView colorOutput(RenderTarget target) {
//#if MC >= 26.3
        return target.getColorTextureView();
//#else
        return RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
//#endif
    }

    static GpuTextureView depthOutput(RenderTarget target) {
//#if MC >= 26.3
        return target.getDepthTextureView();
//#else
        return RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
//#endif
    }

    static boolean hasDepth(RenderTarget target) {
//#if MC >= 26.3
        return target.hasDepth();
//#else
        return target.useDepth;
//#endif
    }

    /** 26.3 draws with compiled pipelines (Minecraft compiles and caches them). */
    static void setPipeline(RenderPass pass, RenderPipeline pipeline) {
//#if MC >= 26.3
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
//#else
        pass.setPipeline(pipeline);
//#endif
    }

    /** Opens a render pass that draws into {@code color} (and tests against {@code depth}, if given), clearing nothing. */
    static RenderPass openPass(String label, GpuTextureView color, GpuTextureView depth) {
//#if MC >= 26.2
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, color, Optional.empty(), depth, OptionalDouble.empty());
//#else
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, color, OptionalInt.empty(), depth, OptionalDouble.empty());
//#endif
    }

    /** Quads uploaded for one draw: the vertices and Minecraft's shared quad index buffer. */
    static final class Quads implements AutoCloseable {
//#if MC >= 26.2
        private final GpuBufferSlice vertices;
//#else
        private final GpuBuffer vertices;
//#endif
        private final GpuBuffer indices;
        private final RenderSystem.AutoStorageIndexBuffer sequence;
        private final int indexCount;

//#if MC >= 26.2
        private Quads(GpuBufferSlice vertices, RenderSystem.AutoStorageIndexBuffer sequence, int indexCount) {
//#else
        private Quads(GpuBuffer vertices, RenderSystem.AutoStorageIndexBuffer sequence, int indexCount) {
//#endif
            this.vertices = vertices;
            this.sequence = sequence;
            this.indexCount = indexCount;
            this.indices = sequence.getBuffer(indexCount);
        }

        static Quads upload(MeshData mesh) {
            int count = mesh.drawState().indexCount();
//#if MC >= 26.2
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuBufferSlice vertices = encoder.transientMemory().uploadGpu(mesh.vertexBuffer(), 16, GpuBuffer.USAGE_VERTEX);
            return new Quads(vertices, RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS), count);
//#else
            GpuBuffer vertices = mesh.drawState().format().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            return new Quads(vertices, RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS), count);
//#endif
        }

        void draw(RenderPass pass) {
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, sequence.type());
//#if MC >= 26.2
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
//#else
            pass.drawIndexed(0, 0, indexCount, 1);
//#endif
        }

        @Override
        public void close() {
            // Vertices live in Minecraft's per-frame upload memory; the index buffer is shared.
        }
    }

    /** Binds a texture with linear filtering, clamped at the edges (skyboxes, effect sprites, post effects). */
    public static void bindLinearClamp(RenderPass pass, String name, GpuTextureView view) {
//#if MC >= 26.3
        pass.setUniform(name, view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
//#elif MC >= 1.21.11
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
}
