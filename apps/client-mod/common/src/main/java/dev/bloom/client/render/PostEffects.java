package dev.bloom.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.visual.PostParams;
import dev.bloom.client.core.visual.Smoother;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * BLOOD's post-processing: real GLSL passes (assets/minecraft/shaders/program/bloodclient_*) run
 * through Minecraft's own {@link PostChain} right after the world is drawn and before the HUD.
 *
 * <ul>
 *   <li>Nothing runs (and no GPU memory is held) when the effects are off or every value is neutral.</li>
 *   <li>The chain is built once and only rebuilt when the quality level changes; it follows the window
 *       size; it's released after a while unused, on resource reloads and when the world closes.</li>
 *   <li>If a shader fails to compile (old driver, resource pack conflict), the error is logged once
 *       and shown under the setting in the menu, and the game carries on without it.</li>
 * </ul>
 */
public final class PostEffects {
    private static final long RELEASE_AFTER_NANOS = 10_000_000_000L;
    private static final float SMOOTH_SECONDS = 0.35f;

    private static PostChain chain;
    private static PostParams.Chain loaded;
    private static int width = -1, height = -1;
    private static long unusedSince = -1;
    private static String failure;
    private static PostParams.Chain failedChain;
    private static long lastHookNanos;
    private static long wantedSince = -1;

    private static final float[] TARGET = new float[PostParams.UNIFORMS.length];
    private static final Smoother SMOOTH = new Smoother(PostParams.UNIFORMS.length);
    private static PostParams.Chain smoothedFor;

    private PostEffects() {
    }

    /** Called from GameRendererMixin after the world (and hand) are drawn, before the GUI. */
    public static void render(float partialTick) {
        lastHookNanos = System.nanoTime();
        BloomClient bc = BloomClient.get();
        Minecraft mc = Minecraft.getInstance();
        PostParams.Input in = bc == null ? null : input(bc, mc, partialTick);
        PostParams.Chain want = in == null ? null : PostParams.chain(in);
        if (want == null) {
            wantedSince = -1;
            idle();
            return;
        }
        if (wantedSince < 0) wantedSince = System.nanoTime();
        unusedSince = -1;
        if (want == failedChain) return;
        if (chain == null || loaded != want) load(want, mc);
        if (chain == null) return;

        RenderTarget main = mc.getMainRenderTarget();
        if (main.width != width || main.height != height) {
            width = main.width;
            height = main.height;
            chain.resize(width, height);
        }
        PostParams.uniforms(in, want, TARGET);
        if (smoothedFor != want) {
            // Glow on/off swaps the chain: start the new one at its target instead of fading from 0.
            SMOOTH.reset();
            smoothedFor = want;
        }
        boolean smooth = BloomSettings.SMOOTH_TRANSITIONS.on() && !BloomSettings.REDUCE_MOTION.on();
        float[] v = SMOOTH.update(TARGET, smooth ? SMOOTH_SECONDS : 0f, System.nanoTime());
        for (int i = 0; i < v.length; i++) chain.setUniform(PostParams.UNIFORMS[i], v[i]);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(partialTick);
        main.bindWrite(true);
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
        return BloomSettings.POST_DIMENSIONS.contains("overworld"); // modded dimensions behave like the Overworld
    }

    private static void load(PostParams.Chain want, Minecraft mc) {
        release();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, "shaders/post/" + want.file + ".json");
        try {
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), id);
            loaded = want;
            width = height = -1;
            failure = null;
            Log.info("Shader effects ready (" + want.file + ")");
        } catch (Exception | LinkageError e) {
            chain = null;
            loaded = null;
            failedChain = want;
            failure = "Couldn't start shader effects: " + e.getMessage();
            Log.warn("BLOOD shader effects failed to load (" + want.file + "); they stay off until the setting changes", e);
        }
    }

    private static void idle() {
        if (chain == null) return;
        long now = System.nanoTime();
        if (unusedSince < 0) unusedSince = now;
        else if (now - unusedSince > RELEASE_AFTER_NANOS) release();
    }

    /** Frees the GPU buffers (render thread). Safe to call any time. */
    public static void release() {
        if (chain != null) {
            try {
                chain.close();
            } catch (RuntimeException e) {
                Log.warn("Closing shader effects failed", e);
            }
        }
        chain = null;
        loaded = null;
        unusedSince = -1;
    }

    /** Resource packs reloaded: rebuild from the new resources and retry a chain that failed. */
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
        // Wanted for a few seconds of rendering but the GameRenderer hook never ran: the mixin
        // didn't apply (another mod rewrote GameRenderer#render).
        long now = System.nanoTime();
        if (lastHookNanos == 0 && Minecraft.getInstance().level != null && BloomSettings.POST.on()
                && BloomClient.get() != null && BloomClient.get().frames().fps(now) > 0) {
            return "Shader effects couldn't hook into the renderer (another mod may replace it). Nothing else is affected.";
        }
        return null;
    }

    /** Which chain is running this frame, for the menu ("Off", "Colour grading", "Grading + glow"). */
    public static String status() {
        if (chain == null || unusedSince >= 0) return "Off";
        return loaded != null && loaded.glow() ? (loaded == PostParams.Chain.GLOW_HIGH ? "Grading + glow (high)" : "Grading + glow")
                : "Colour grading";
    }
}
