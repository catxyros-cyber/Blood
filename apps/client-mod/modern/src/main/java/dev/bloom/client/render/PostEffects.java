package dev.bloom.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
//#if MC < 26.2
import com.mojang.blaze3d.textures.TextureFormat;
//#endif
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.visual.PostParams;
import dev.bloom.client.core.visual.Smoother;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalInt;

/**
 * MOTION's post-processing on Minecraft 1.21.11: real GLSL passes run right after the world (and
 * hand) are drawn, before the HUD. Each pass is a full-screen quad drawn with one of MOTION's
 * render pipelines; parameters travel in Minecraft's per-draw uniform block, so sliders update
 * live without rebuilding anything.
 *
 * <ul>
 *   <li>Nothing runs (and no GPU memory is held) when the effects are off or every value is neutral.</li>
 *   <li>Targets follow the window size and are released after a while unused, on resource reloads
 *       and when the world closes.</li>
 *   <li>If a shader fails, the error is logged once and shown under the setting in the menu, and
 *       the game carries on without it.</li>
 * </ul>
 */
public final class PostEffects {
    private static final long RELEASE_AFTER_NANOS = 10_000_000_000L;
    private static final float SMOOTH_SECONDS = 0.35f;

    private static RenderPipeline grade, bright, blur, copy;

    private static Target swap, glowA, glowB;
    private static long unusedSince = -1;
    private static String failure;
    private static PostParams.Chain failedChain;
    private static PostParams.Chain running;
    private static long lastHookNanos;

    private static final float[] TARGET = new float[PostParams.UNIFORMS.length];
    private static final Smoother SMOOTH = new Smoother(PostParams.UNIFORMS.length);
    private static PostParams.Chain smoothedFor;

    private PostEffects() {
    }

    /** A colour texture we render into. */
    private record Target(GpuTexture texture, GpuTextureView view, int width, int height) {
        static Target create(String label, int w, int h) {
            GpuDevice device = RenderSystem.getDevice();
            GpuTexture tex = device.createTexture(label, GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
//#if MC >= 26.2
                    com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, w, h, 1, 1);
//#else
                    TextureFormat.RGBA8, w, h, 1, 1);
//#endif
            return new Target(tex, device.createTextureView(tex), w, h);
        }

        void close() {
            view.close();
            texture.close();
        }
    }

    private static RenderPipeline pipeline(String fragment, boolean twoSamplers) {
        RenderPipeline.Builder b = Draw.newPipeline(DefaultVertexFormat.POSITION_TEX, false,
                twoSamplers ? new String[]{"InSampler", "BloomSampler"} : new String[]{"InSampler"});
        b.withLocation(Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "pipeline/post_" + fragment));
        b.withVertexShader(Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "post/bloom_post"));
        b.withFragmentShader(Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "post/" + fragment));
        Draw.renderState(b, null, false);
        b.withCull(false);
        return b.build();
    }

    private static void ensurePipelines() {
        if (grade != null) return;
        grade = pipeline("bloom_grade", true);
        bright = pipeline("bloom_bright", false);
        blur = pipeline("bloom_blur", false);
        copy = pipeline("bloom_copy", false);
    }

    /** Called from GameRendererMixin after the world (and hand) are drawn, before the GUI. */
    public static void render(float partialTick) {
        lastHookNanos = System.nanoTime();
        BloomClient bc = BloomClient.get();
        Minecraft mc = Minecraft.getInstance();
        PostParams.Input in = bc == null ? null : input(bc, mc, partialTick);
        PostParams.Chain want = in == null ? null : PostParams.chain(in);
        if (want == null) {
            running = null;
            idle();
            return;
        }
        unusedSince = -1;
        if (want == failedChain) return;
        try {
            run(mc, in, want);
            running = want;
            failure = null;
        } catch (RuntimeException | LinkageError e) {
            running = null;
            failedChain = want;
            failure = "Couldn't run shader effects: " + e.getMessage();
            Log.warn("MOTION shader effects failed (" + want.file + "); they stay off until a setting changes", e);
            release();
        }
    }

    private static void run(Minecraft mc, PostParams.Input in, PostParams.Chain want) {
        ensurePipelines();
        RenderTarget main = Draw.mainTarget();
        int w = main.width, h = main.height;
        if (w <= 0 || h <= 0) return;
        if (swap == null || swap.width() != w || swap.height() != h) {
            if (swap != null) swap.close();
            swap = Target.create("MOTION post swap", w, h);
        }
        PostParams.uniforms(in, want, TARGET);
        if (smoothedFor != want) {
            SMOOTH.reset();
            smoothedFor = want;
        }
        boolean smooth = BloomSettings.SMOOTH_TRANSITIONS.on() && !BloomSettings.REDUCE_MOTION.on();
        float[] v = SMOOTH.update(TARGET, smooth ? SMOOTH_SECONDS : 0f, System.nanoTime());

        GpuTextureView mainView = main.getColorTextureView();
        GpuTextureView bloom = mainView;
        if (want.glow()) {
            boolean high = want == PostParams.Chain.GLOW_HIGH;
            // Glow resolution follows the window (a fixed fraction), so it looks the same at 4K.
            int gw = Math.max(64, w / (high ? 3 : 4)), gh = Math.max(36, h / (high ? 3 : 4));
            if (glowA == null || glowA.width() != gw || glowA.height() != gh) {
                if (glowA != null) glowA.close();
                if (glowB != null) glowB.close();
                glowA = Target.create("MOTION glow A", gw, gh);
                glowB = Target.create("MOTION glow B", gw, gh);
            }
            Matrix4f p = params(v);
            pass(bright, glowA, mainView, null, p, new Vector4f(), sizes(w, h, gw, gh));
            float[] radii = high ? new float[]{1f, 2f, 3.5f} : new float[]{1f, 2f};
            for (float r : radii) {
                pass(blur, glowB, glowA.view(), null, p, new Vector4f(1f, 0f, r, 0f), sizes(gw, gh, gw, gh));
                pass(blur, glowA, glowB.view(), null, p, new Vector4f(0f, 1f, r, 0f), sizes(gw, gh, gw, gh));
            }
            bloom = glowA.view();
        } else if (glowA != null) {
            glowA.close();
            glowB.close();
            glowA = glowB = null;
        }
        pass(grade, swap, mainView, bloom, params(v), new Vector4f(), sizes(w, h, w, h));
        passInto(copy, main.getColorTextureView(), swap.view(), null, params(v), new Vector4f(), sizes(w, h, w, h));
    }

    private static Matrix4f params(float[] v) {
        // Column-major: each group of four is one column, read in GLSL as ModelViewMat[i].
        return new Matrix4f(
                v[PostParams.EXPOSURE], v[PostParams.CONTRAST], v[PostParams.SATURATION], v[PostParams.WARMTH],
                v[PostParams.TINT_R], v[PostParams.TINT_G], v[PostParams.TINT_B], v[PostParams.TINT_STRENGTH],
                v[PostParams.VIGNETTE], v[PostParams.SHARPEN], v[PostParams.SOFT_HIGHLIGHTS], v[PostParams.GLOW_STRENGTH],
                v[PostParams.MOOD], v[PostParams.GLOW_THRESHOLD], 0f, 0f);
    }

    private static Matrix4f sizes(int inW, int inH, int outW, int outH) {
        return new Matrix4f(inW, inH, outW, outH, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
    }

    private static void pass(RenderPipeline pipeline, Target out, GpuTextureView in, GpuTextureView second, Matrix4f params, Vector4f extra, Matrix4f sizes) {
        passInto(pipeline, out.view(), in, second, params, extra, sizes);
    }

    private static void passInto(RenderPipeline pipeline, GpuTextureView out, GpuTextureView in, GpuTextureView second,
                                 Matrix4f params, Vector4f extra, Matrix4f sizes) {
        GpuBufferSlice uniforms = Draw.transforms(params, extra, new Vector3f(), sizes);
        try (ByteBufferBuilder memory = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4)) {
            BufferBuilder b = Draw.quadBuilder(memory, DefaultVertexFormat.POSITION_TEX);
            b.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
            b.addVertex(1f, -1f, 0f).setUv(1f, 0f);
            b.addVertex(1f, 1f, 0f).setUv(1f, 1f);
            b.addVertex(-1f, 1f, 0f).setUv(0f, 1f);
            try (MeshData mesh = b.buildOrThrow();
                 Draw.Quads quads = Draw.Quads.upload(mesh);
                 RenderPass pass = Draw.openPass("MOTION post", out, null)) {
                Draw.setPipeline(pass, pipeline);
                pass.setUniform("DynamicTransforms", uniforms);
                Draw.bindLinearClamp(pass, "InSampler", in);
                if (second != null) Draw.bindLinearClamp(pass, "BloomSampler", second);
                quads.draw(pass);
            }
        }
    }

    private static PostParams.Input input(BloomClient bc, Minecraft mc, float partialTick) {
        ClientLevel level = mc.level;
        if (level == null) return null;
        boolean on = BloomSettings.ENABLED.on() && BloomSettings.POST.on() && !bc.worldEffectsPaused() && dimensionAllowed(level);
        float rain = level.getRainLevel(partialTick), thunder = level.getThunderLevel(partialTick);
        boolean downgrade = BloomSettings.ADAPTIVE.on() && bc.budget().factor() < 0.75f;
        return new PostParams.Input(on, BloomSettings.POST_BRIGHTNESS.value(), BloomSettings.POST_CONTRAST.value(),
                BloomSettings.POST_SATURATION.value(), BloomSettings.POST_WARMTH.value(), BloomSettings.POST_TINT.rgb(),
                BloomSettings.POST_TINT_STRENGTH.value(), BloomSettings.POST_VIGNETTE.value(), BloomSettings.POST_SHARPEN.value(),
                BloomSettings.POST_SOFT_HIGHLIGHTS.on(), BloomSettings.POST_GLOW.on(), BloomSettings.POST_GLOW_STRENGTH.value(),
                BloomSettings.POST_GLOW_THRESHOLD.value(), BloomSettings.POST_WEATHER.on(), rain, thunder,
                BloomSettings.POST_QUALITY.id(), BloomSettings.LITE_MODE.on(), downgrade);
    }

    private static boolean dimensionAllowed(ClientLevel level) {
        if (Level.OVERWORLD.equals(level.dimension())) return BloomSettings.POST_DIMENSIONS.contains("overworld");
        if (Level.NETHER.equals(level.dimension())) return BloomSettings.POST_DIMENSIONS.contains("nether");
        if (Level.END.equals(level.dimension())) return BloomSettings.POST_DIMENSIONS.contains("end");
        return BloomSettings.POST_DIMENSIONS.contains("overworld");
    }

    private static void idle() {
        if (swap == null) return;
        long now = System.nanoTime();
        if (unusedSince < 0) unusedSince = now;
        else if (now - unusedSince > RELEASE_AFTER_NANOS) release();
    }

    /** Frees the GPU textures (render thread). Safe to call any time. */
    public static void release() {
        try {
            if (swap != null) swap.close();
            if (glowA != null) glowA.close();
            if (glowB != null) glowB.close();
        } catch (RuntimeException e) {
            Log.warn("Closing shader effects failed", e);
        }
        swap = glowA = glowB = null;
        unusedSince = -1;
    }

    /** Resource packs reloaded: rebuild targets and retry a chain that failed. */
    public static void onResourcesReloaded() {
        release();
        failedChain = null;
        failure = null;
    }

    /** A setting changed: allow a failed chain to be tried again. */
    public static void retry() {
        failedChain = null;
    }

    /** Null when fine; otherwise why shader effects aren't running (shown in the menu). */
    public static String problem() {
        if (failure != null) return failure;
        long now = System.nanoTime();
        if (lastHookNanos == 0 && Minecraft.getInstance().level != null && BloomSettings.POST.on()
                && BloomClient.get() != null && BloomClient.get().frames().fps(now) > 0) {
            return "Shader effects couldn't hook into the renderer (another mod may replace it). Nothing else is affected.";
        }
        return null;
    }

    /** Which chain is running this frame, for the menu ("Off", "Colour grading", "Grading + glow"). */
    public static String status() {
        if (running == null) return "Off";
        return running.glow() ? (running == PostParams.Chain.GLOW_HIGH ? "Grading + glow (high)" : "Grading + glow") : "Colour grading";
    }
}
