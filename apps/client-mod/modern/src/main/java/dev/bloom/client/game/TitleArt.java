package dev.bloom.client.game;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The title screen's landscape (the same art as the launcher's themes). The JPEG is decoded once,
 * then scaled on a worker thread to the window's exact pixel size, so it's drawn one texel per
 * screen pixel: sharp at any resolution, with no texture filtering involved.
 */
public final class TitleArt {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "title/landscape");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MOTION title art");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static String sourceName;
    private static BufferedImage source;
    private static String wanted = "";
    private static volatile boolean busy;
    private static boolean loaded;
    private static boolean failed;
    private static int texW, texH;

    private TitleArt() {
    }

    /** The landscape that suits the MOTION menu theme. */
    public static String artFor(String theme) {
        return switch (theme) {
            case "crimson", "blood_moon", "crimson_eclipse" -> "crimson";
            case "obsidian", "spiderweb", "midnight_halloween" -> "obsidian";
            case "ash", "haunted_forest" -> "ash";
            case "ember", "pumpkin_night", "ghostfire" -> "ember";
            case "mono" -> "mono";
            default -> "motion";
        };
    }

    /**
     * Draws the art over the whole screen ({@code w} x {@code h} GUI units). Returns false until the
     * first image is ready, so the caller can draw a plain backdrop meanwhile.
     */
    public static boolean draw(GuiGraphics g, int w, int h) {
        if (failed) return false;
        var target = dev.bloom.client.render.Draw.mainTarget();
        int fbW = Math.max(1, target.width), fbH = Math.max(1, target.height);
        String name = artFor(BloomSettings.THEME.id());
        String key = name + ":" + fbW + "x" + fbH;
        if (!key.equals(wanted) && !busy) {
            wanted = key;
            busy = true;
            WORKER.submit(() -> prepare(name, fbW, fbH));
        }
        if (!loaded) return false;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, w, h, texW, texH, texW, texH, 0xFFFFFFFF);
        return true;
    }

    private static void prepare(String name, int fbW, int fbH) {
        try {
            if (!name.equals(sourceName)) {
                try (InputStream in = TitleArt.class.getResourceAsStream("/assets/bloomclient/textures/gui/title/" + name + ".jpg")) {
                    if (in == null) throw new java.io.IOException("missing title art " + name);
                    BufferedImage img = javax.imageio.ImageIO.read(in);
                    if (img == null) throw new java.io.IOException("unreadable title art " + name);
                    source = img;
                    sourceName = name;
                }
            }
            BufferedImage scaled = cover(source, fbW, fbH);
            int[] px = scaled.getRGB(0, 0, fbW, fbH, null, 0, fbW);
            NativeImage img = new NativeImage(fbW, fbH, false);
            for (int y = 0; y < fbH; y++) {
                int row = y * fbW;
                for (int x = 0; x < fbW; x++) img.setPixel(x, y, px[row + x] | 0xFF000000); // ARGB in these versions
            }
            Minecraft.getInstance().execute(() -> {
                var tm = Minecraft.getInstance().getTextureManager();
                if (loaded) tm.release(TEXTURE);
                tm.register(TEXTURE, new DynamicTexture(() -> "MOTION title art", img));
                texW = fbW;
                texH = fbH;
                loaded = true;
                busy = false;
            });
        } catch (Throwable e) {
            Log.warn("MOTION title screen: couldn't load the landscape (" + e + ")");
            failed = true;
            busy = false;
        }
    }

    /** Scales to cover {@code w} x {@code h}, keeping the upper part (sky and horizon) in view. */
    private static BufferedImage cover(BufferedImage src, int w, int h) {
        double s = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
        int cw = (int) Math.ceil(w / s), ch = (int) Math.ceil(h / s);
        int cx = (src.getWidth() - cw) / 2, cy = (int) ((src.getHeight() - ch) * 0.3);
        BufferedImage cur = src.getSubimage(Math.max(0, cx), Math.max(0, cy), Math.min(cw, src.getWidth()), Math.min(ch, src.getHeight()));
        // Halve in steps first: one big bicubic step down shimmers on block edges.
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        return resize(cur, w, h);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
