package dev.bloom.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.sky.PngInfo;
import dev.bloom.client.core.io.SafeFiles;
import dev.bloom.client.core.sky.CubeFormats;
import dev.bloom.client.core.sky.CubeLayout;
import dev.bloom.client.core.sky.SkyLook;
import dev.bloom.client.core.sky.SkyboxCatalog;
import dev.bloom.client.core.sky.SkyboxLibrary;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.game.McImageCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Supplies skybox textures. Built-in skyboxes with default look settings are drawn straight
 * from the mod's resources. When brightness, saturation or tint are changed (or for imported
 * skyboxes), the image is processed on a background thread and uploaded when ready, so moving
 * a slider never stalls the game; the previous texture is shown until then.
 */
public final class SkyTextures {
    /** A texture ready to bind, with the face size its UVs are inset for. */
    public record Bound(GpuTextureView view, GpuSampler sampler, int faceSize) {
    }

    /** Skyboxes kept on the GPU at once ("Skybox cache"); others are released. */
    private static int maxCached() {
        return Math.max(1, Math.min(6, BloomSettings.SKY_CACHE.intValue()));
    }

    private static final long REPROCESS_INTERVAL_MS = 120;

    private record Look(float brightness, float saturation, int tint, float tintStrength, int maxFace) {
        /** True when the bundled texture can be drawn as it is (no colour change, not above the size cap). */
        boolean neutralFor(String id) {
            return SkyLook.neutral(brightness, saturation, tintStrength) && maxFace >= SkyboxCatalog.nativeFace(id);
        }

        static Look current() {
            return new Look(BloomSettings.SKY_BRIGHTNESS.value(), BloomSettings.SKY_SATURATION.value(),
                    BloomSettings.SKY_TINT.rgb(), BloomSettings.SKY_TINT_STRENGTH.value(), SkyTextures.maxFace());
        }
    }

    /** Face size cap from "Skybox quality", lowered when memory or the GPU can't take more. */
    public static int maxFace() {
        int cap = switch (BloomSettings.SKY_QUALITY.id()) {
            case "performance" -> 256;
            case "balanced" -> 512;
            case "ultra" -> 2048;
            default -> 1024;
        };
        if (memoryMb() < 2048 && cap > 512) cap = 512;
        int gl = glMaxTexture();
        while (cap > 64 && cap * 3 > gl) cap /= 2;
        return cap;
    }

    private static long memoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    private static int glMaxCache = -1;

    private static int glMaxTexture() {
        if (glMaxCache < 0) {
            try {
                int v = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
                glMaxCache = v > 0 ? v : 8192;
            } catch (RuntimeException | LinkageError e) {
                glMaxCache = 8192;
            }
        }
        return glMaxCache;
    }

    /** Why skyboxes are drawn smaller than the quality setting asks, or null. */
    public static String reductionReason() {
        String q = BloomSettings.SKY_QUALITY.id();
        int wanted = "ultra".equals(q) ? 2048 : "high".equals(q) ? 1024 : 0;
        if (wanted == 0 || maxFace() >= wanted) return null;
        if (memoryMb() < 2048) {
            return "Reduced to 512 px faces: Minecraft has only " + memoryMb() + " MB of memory. Give it at least 2 GB in the launcher for High or Ultra.";
        }
        return "Reduced to " + maxFace() + " px faces: your graphics card's largest texture is " + glMaxTexture() + " px.";
    }

    private static final class Entry {
        final String skyboxId;
        final Identifier location;
        DynamicTexture texture;
        Look look;
        CompletableFuture<NativeImage> pending;
        Look pendingLook;
        long lastRequest;
        int faceSize;
        boolean failed;

        Entry(String id, int n) {
            skyboxId = id;
            location = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "dynamic/sky_" + n);
        }
    }

    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(8, 0.75f, true);
    private static final Map<String, int[]> SOURCE = new HashMap<>(); // id -> {w, h, argb...} kept for reprocessing
    private static final Map<String, Identifier> PREVIEWS = new HashMap<>();
    private static int counter;

    private SkyTextures() {
    }

    /** Resource location of the built-in texture for an id. */
    public static Identifier builtIn(String id) {
        return Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/skybox/" + id + ".png");
    }

    public static Identifier builtInPreview(String id) {
        return Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/gui/sky_preview/" + id + ".png");
    }

    private static Bound bindable(Identifier id, int faceSize) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id);
        GpuTextureView view = tex.getTextureView();
        if (view == null) return null;
        return new Bound(view, Draw.linearClamp(), faceSize);
    }

    /**
     * Texture to bind for a skybox, or null while it isn't ready (or failed to load). Must be
     * called on the render thread.
     */
    public static Bound get(String id) {
        SkyboxCatalog.Entry e = SkyboxCatalog.find(id);
        if (e == null) return null;
        Look look = Look.current();
        if (!e.builtIn() && !SkyboxCatalog.customEnabled()) return null;
        if (e.builtIn() && look.neutralFor(id)) return bindable(builtIn(id), SkyboxCatalog.nativeFace(id));
        Entry entry = CACHE.get(id);
        if (entry == null) {
            entry = new Entry(id, counter++);
            CACHE.put(id, entry);
            trim();
        }
        if (entry.failed) return null;
        poll(entry);
        long now = System.currentTimeMillis();
        if (entry.pending == null && !look.equals(entry.look) && !look.equals(entry.pendingLook)
                && now - entry.lastRequest >= REPROCESS_INTERVAL_MS) {
            request(entry, look);
        }
        if (entry.texture != null) return new Bound(entry.texture.getTextureView(), Draw.linearClamp(), entry.faceSize);
        // Built-ins: show the bundled texture until the processed one is ready, so the sky never blinks
        // (unless it's bigger than the quality cap: an 8K texture isn't uploaded on lower settings).
        return e.builtIn() && SkyboxCatalog.nativeFace(id) <= look.maxFace() ? bindable(builtIn(id), SkyboxCatalog.nativeFace(id)) : null;
    }

    private static void request(Entry entry, Look look) {
        entry.lastRequest = System.currentTimeMillis();
        entry.pendingLook = look;
        String id = entry.skyboxId;
        int[] cached = SOURCE.get(id);
        boolean builtIn = !id.startsWith(SkyboxCatalog.CUSTOM_PREFIX);
        byte[] bytes;
        if (cached == null) {
            bytes = readBytes(id, builtIn);
            if (bytes == null) {
                entry.failed = true;
                return;
            }
        } else {
            bytes = null;
        }
        entry.pending = CompletableFuture.supplyAsync(() -> {
            int[] src = cached;
            if (src == null) {
                try (NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes))) {
                    int w = img.getWidth(), h = img.getHeight();
                    if (CubeLayout.checkAtlasSize(w, h) != null) throw new IllegalStateException("bad skybox size " + w + "x" + h);
                    src = new int[2 + w * h];
                    src[0] = w;
                    src[1] = h;
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) src[2 + y * w + x] = img.getPixel(x, y);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                // 8K sources (6144x4096 = 100 MB of pixels) aren't kept: they're decoded again if the
                // look changes, so memory stays low.
                if ((long) src[0] * src[1] <= 3072L * 2048L) {
                    synchronized (SOURCE) {
                        if (SOURCE.size() >= maxCached()) SOURCE.clear();
                        SOURCE.put(id, src);
                    }
                }
            }
            return process(src, look);
        });
    }

    private static byte[] readBytes(String id, boolean builtIn) {
        try {
            if (builtIn) {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(builtIn(id));
                if (res.isEmpty()) return null;
                try (InputStream in = res.get().open()) {
                    return SafeFiles.readLimited(in, SkyboxLibrary.MAX_PNG_BYTES);
                }
            }
            byte[] data = SafeFiles.readLimited(BloomClient.get().skyboxes().textureFile(id), SkyboxLibrary.MAX_PNG_BYTES);
            PngInfo info = PngInfo.read(data);
            if (info == null || CubeLayout.checkAtlasSize(info.width, info.height) != null) {
                Log.warn("Skybox " + id + " is not a valid 3x2 PNG; skipping");
                return null;
            }
            return data;
        } catch (Exception e) {
            Log.warn("Could not read skybox " + id, e);
            return null;
        }
    }

    /**
     * Shrinks to the quality cap (area average, face by face, so faces never bleed) and applies
     * brightness, saturation and tint (the same maths as the menu preview, see SkyLook). Runs on a
     * background thread. Pixels are ARGB (NativeImage#getPixel / #setPixel).
     */
    private static NativeImage process(int[] src, Look look) {
        int[] size = new int[2];
        int[] px = java.util.Arrays.copyOfRange(src, 2, src.length);
        px = CubeFormats.shrinkAtlas(px, src[0], src[1], look.maxFace(), size);
        int w = size[0], h = size[1];
        NativeImage out = new NativeImage(w, h, false);
        boolean colour = !SkyLook.neutral(look.brightness(), look.saturation(), look.tintStrength());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                if (colour) c = SkyLook.apply(c, false, look.brightness(), look.saturation(), look.tint(), look.tintStrength());
                out.setPixel(x, y, c);
            }
        }
        return out;
    }

    private static void poll(Entry e) {
        if (e.pending == null || !e.pending.isDone()) return;
        CompletableFuture<NativeImage> f = e.pending;
        e.pending = null;
        try {
            NativeImage img = f.join();
            int faceSize = img.getWidth() / 3;
            // A new texture each time: the GPU texture's size is fixed when it's created.
            if (e.texture != null) Minecraft.getInstance().getTextureManager().release(e.location);
            String label = "MOTION sky " + e.skyboxId;
            e.texture = new DynamicTexture(() -> label, img);
            Minecraft.getInstance().getTextureManager().register(e.location, e.texture);
            e.faceSize = faceSize;
            e.look = e.pendingLook;
        } catch (Exception ex) {
            Log.warn("Could not prepare skybox " + e.skyboxId, ex);
            e.failed = true;
        }
    }

    private static void trim() {
        while (CACHE.size() > maxCached()) {
            Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
            Entry old = it.next().getValue();
            it.remove();
            release(old);
        }
    }

    private static void release(Entry e) {
        if (e.texture != null) Minecraft.getInstance().getTextureManager().release(e.location);
        e.texture = null;
        if (e.pending != null) {
            e.pending.thenAccept(NativeImage::close);
            e.pending = null;
        }
    }

    /**
     * Pixels of a skybox for the menu preview: {w, h, abgr...}, shrunk to at most {@code maxFace}
     * px faces. File reading happens here (call on the render thread); decoding and shrinking run
     * in the background. Fails for unknown, disabled or invalid skyboxes.
     */
    public static CompletableFuture<int[]> previewPixels(String id, int maxFace) {
        SkyboxCatalog.Entry e = SkyboxCatalog.find(id);
        if (e == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown skybox " + id));
        byte[] bytes = readBytes(id, e.builtIn());
        if (bytes == null) return CompletableFuture.failedFuture(new IllegalStateException("Couldn't read " + id));
        return CompletableFuture.supplyAsync(() -> {
            try (NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes))) {
                int w = img.getWidth(), h = img.getHeight();
                if (CubeLayout.checkAtlasSize(w, h) != null) throw new IllegalStateException("bad skybox size " + w + "x" + h);
                int[] px = new int[w * h];
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = McImageCodec.argbToAbgr(img.getPixel(x, y));
                int[] size = new int[2];
                int[] small = CubeFormats.shrinkAtlas(px, w, h, maxFace, size);
                int[] out = new int[2 + small.length];
                out[0] = size[0];
                out[1] = size[1];
                System.arraycopy(small, 0, out, 2, small.length);
                return out;
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
    }

    /** Forget everything (resource reload, skybox deleted or replaced). Render thread only. */
    public static void invalidateAll() {
        for (Entry e : CACHE.values()) release(e);
        CACHE.clear();
        synchronized (SOURCE) {
            SOURCE.clear();
        }
        for (Identifier rl : PREVIEWS.values()) Minecraft.getInstance().getTextureManager().release(rl);
        PREVIEWS.clear();
    }

    public static void invalidate(String id) {
        Entry e = CACHE.remove(id);
        if (e != null) release(e);
        synchronized (SOURCE) {
            SOURCE.remove(id);
        }
        Identifier p = PREVIEWS.remove(id);
        if (p != null) Minecraft.getInstance().getTextureManager().release(p);
    }

    /**
     * Menu thumbnail (128×72) for a skybox: the bundled preview for built-ins, or a crop of the
     * north face for imported ones. Null if it can't be made.
     */
    public static Identifier preview(String id) {
        if (!id.startsWith(SkyboxCatalog.CUSTOM_PREFIX)) return builtInPreview(id);
        Identifier have = PREVIEWS.get(id);
        if (have != null) return have;
        byte[] bytes = readBytes(id, false);
        if (bytes == null) return null;
        try (NativeImage atlas = NativeImage.read(new java.io.ByteArrayInputStream(bytes))) {
            int f = atlas.getWidth() / 3;
            NativeImage thumb = new NativeImage(128, 72, false);
            // North face: column 1, row 1. Take the upper 56% (sky above the horizon).
            for (int y = 0; y < 72; y++) {
                for (int x = 0; x < 128; x++) {
                    int sx = f + Math.min(f - 1, x * f / 128);
                    int sy = f + Math.min(f - 1, (int) (y * (f * 0.56f) / 72f));
                    thumb.setPixel(x, y, atlas.getPixel(sx, sy) | 0xFF000000);
                }
            }
            Identifier rl = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "dynamic/sky_preview_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(() -> "MOTION sky preview", thumb));
            PREVIEWS.put(id, rl);
            return rl;
        } catch (Exception e) {
            Log.warn("Could not make a preview for " + id, e);
            return null;
        }
    }
}
