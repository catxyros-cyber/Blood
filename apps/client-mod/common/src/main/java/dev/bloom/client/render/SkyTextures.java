package dev.bloom.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.io.SafeFiles;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.sky.CubeLayout;
import dev.bloom.client.core.sky.PngInfo;
import dev.bloom.client.core.sky.SkyboxCatalog;
import dev.bloom.client.core.sky.SkyboxLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

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
    private static final int MAX_CACHED = 3;
    private static final long REPROCESS_INTERVAL_MS = 120;

    private record Look(float brightness, float saturation, int tint, float tintStrength) {
        boolean neutral() {
            return Math.abs(brightness - 1f) < 1e-3 && Math.abs(saturation - 1f) < 1e-3 && tintStrength < 1e-3;
        }

        static Look current() {
            return new Look(BloomSettings.SKY_BRIGHTNESS.value(), BloomSettings.SKY_SATURATION.value(),
                    BloomSettings.SKY_TINT.rgb(), BloomSettings.SKY_TINT_STRENGTH.value());
        }
    }

    private static final class Entry {
        final String skyboxId;
        final ResourceLocation location;
        DynamicTexture texture;
        Look look;
        CompletableFuture<NativeImage> pending;
        Look pendingLook;
        long lastRequest;
        int faceSize;
        boolean failed;

        Entry(String id, int n) {
            skyboxId = id;
            location = ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, "dynamic/sky_" + n);
        }
    }

    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(8, 0.75f, true);
    private static final Map<String, int[]> SOURCE = new HashMap<>(); // id -> {w, h, argb...} kept for reprocessing
    private static final Map<String, ResourceLocation> PREVIEWS = new HashMap<>();
    private static int counter;

    private SkyTextures() {
    }

    /** Resource location of the built-in texture for an id. */
    public static ResourceLocation builtIn(String id) {
        return ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/skybox/" + id + ".png");
    }

    public static ResourceLocation builtInPreview(String id) {
        return ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/gui/sky_preview/" + id + ".png");
    }

    /**
     * Texture to bind for a skybox, or null while it isn't ready (or failed to load). Must be
     * called on the render thread.
     */
    public static ResourceLocation get(String id) {
        SkyboxCatalog.Entry e = SkyboxCatalog.find(id);
        if (e == null) return null;
        Look look = Look.current();
        if (e.builtIn() && look.neutral()) return builtIn(id);
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
        return entry.texture != null ? entry.location : null;
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
                try (NativeImage img = NativeImage.read(bytes)) {
                    int w = img.getWidth(), h = img.getHeight();
                    if (CubeLayout.checkAtlasSize(w, h) != null) throw new IllegalStateException("bad skybox size " + w + "x" + h);
                    src = new int[2 + w * h];
                    src[0] = w;
                    src[1] = h;
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) src[2 + y * w + x] = img.getPixelRGBA(x, y);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                synchronized (SOURCE) {
                    if (SOURCE.size() >= MAX_CACHED) SOURCE.clear();
                    SOURCE.put(id, src);
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

    /** Applies brightness, saturation and tint. Input/output pixels are NativeImage ABGR. */
    private static NativeImage process(int[] src, Look look) {
        int w = src[0], h = src[1];
        NativeImage out = new NativeImage(w, h, false);
        float tr = ((look.tint() >> 16) & 255) / 255f, tg = ((look.tint() >> 8) & 255) / 255f, tb = (look.tint() & 255) / 255f;
        float s = look.saturation(), br = look.brightness(), ts = look.tintStrength();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src[2 + y * w + x];
                float r = (c & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = ((c >> 16) & 255) / 255f;
                int a = (c >>> 24);
                float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                r = luma + (r - luma) * s;
                g = luma + (g - luma) * s;
                b = luma + (b - luma) * s;
                if (ts > 0) {
                    float k = 0.35f + 0.65f * luma;
                    r += (tr * k - r) * ts;
                    g += (tg * k - g) * ts;
                    b += (tb * k - b) * ts;
                }
                r *= br;
                g *= br;
                b *= br;
                int ir = clamp(r), ig = clamp(g), ib = clamp(b);
                out.setPixelRGBA(x, y, (a << 24) | (ib << 16) | (ig << 8) | ir);
            }
        }
        return out;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    private static void poll(Entry e) {
        if (e.pending == null || !e.pending.isDone()) return;
        CompletableFuture<NativeImage> f = e.pending;
        e.pending = null;
        try {
            NativeImage img = f.join();
            if (e.texture == null) {
                e.texture = new DynamicTexture(img);
                Minecraft.getInstance().getTextureManager().register(e.location, e.texture);
            } else {
                e.texture.setPixels(img);
            }
            e.texture.upload();
            e.texture.setFilter(true, false);
            e.texture.bind();
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            e.faceSize = img.getWidth() / 3;
            e.look = e.pendingLook;
        } catch (Exception ex) {
            Log.warn("Could not prepare skybox " + e.skyboxId, ex);
            e.failed = true;
        }
    }

    private static void trim() {
        while (CACHE.size() > MAX_CACHED) {
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

    /** Forget everything (resource reload, skybox deleted or replaced). Render thread only. */
    public static void invalidateAll() {
        for (Entry e : CACHE.values()) release(e);
        CACHE.clear();
        synchronized (SOURCE) {
            SOURCE.clear();
        }
        for (ResourceLocation rl : PREVIEWS.values()) Minecraft.getInstance().getTextureManager().release(rl);
        PREVIEWS.clear();
    }

    public static void invalidate(String id) {
        Entry e = CACHE.remove(id);
        if (e != null) release(e);
        synchronized (SOURCE) {
            SOURCE.remove(id);
        }
        ResourceLocation p = PREVIEWS.remove(id);
        if (p != null) Minecraft.getInstance().getTextureManager().release(p);
    }

    /**
     * Menu thumbnail (128×72) for a skybox: the bundled preview for built-ins, or a crop of the
     * north face for imported ones. Null if it can't be made.
     */
    public static ResourceLocation preview(String id) {
        if (!id.startsWith(SkyboxCatalog.CUSTOM_PREFIX)) return builtInPreview(id);
        ResourceLocation have = PREVIEWS.get(id);
        if (have != null) return have;
        byte[] bytes = readBytes(id, false);
        if (bytes == null) return null;
        try (NativeImage atlas = NativeImage.read(bytes)) {
            int f = atlas.getWidth() / 3;
            NativeImage thumb = new NativeImage(128, 72, false);
            // North face: column 1, row 1. Take the upper 56% (sky above the horizon).
            for (int y = 0; y < 72; y++) {
                for (int x = 0; x < 128; x++) {
                    int sx = f + Math.min(f - 1, x * f / 128);
                    int sy = f + Math.min(f - 1, (int) (y * (f * 0.56f) / 72f));
                    thumb.setPixelRGBA(x, y, atlas.getPixelRGBA(sx, sy) | 0xFF000000);
                }
            }
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, "dynamic/sky_preview_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(thumb));
            PREVIEWS.put(id, rl);
            return rl;
        } catch (Exception e) {
            Log.warn("Could not make a preview for " + id, e);
            return null;
        }
    }
}
