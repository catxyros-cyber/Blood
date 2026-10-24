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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The background behind Minecraft's own screens (title screen, pause and option menus, loading
 * screens): Minecraft's own, a solid colour (red, black, white, Motion green) or the player's
 * image. The launcher writes the choice to settings.json and copies the image to
 * {@code config/bloomclient/menu-background.png} (or .jpg); a changed image is picked up within
 * seconds.
 */
public final class MenuBackground {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "menu_background");
    private static final String[] FILES = {"menu-background.png", "menu-background.jpg", "menu-background.jpeg"};
    private static final long MAX_BYTES = 40L * 1024 * 1024;

    private static boolean loaded;
    private static int texW, texH;
    private static int average = 0x000000;
    private static long fileStamp = -1;
    private static long lastCheck;

    private MenuBackground() {
    }

    private static String mode() {
        if (!BloomClient.ready()) return "vanilla";
        return BloomSettings.MENU_BACKGROUND.id();
    }

    /** True when MOTION replaces Minecraft's backgrounds. */
    public static boolean active() {
        return !"vanilla".equals(mode());
    }

    public static boolean onLoadingScreens() {
        return active() && BloomSettings.MENU_BACKGROUND_LOADING.on();
    }

    /** How opaque the background is over the world (pause and option menus while playing). */
    public static float inWorldAlpha() {
        return BloomSettings.MENU_BACKGROUND_INWORLD.value();
    }

    /** Top and bottom colours (RGB) of the solid styles. */
    private static int[] colors(String mode) {
        return switch (mode) {
            case "red" -> new int[]{0xD0001C, 0x8A0012};
            case "white" -> new int[]{0xFFFFFF, 0xF1F1F1};
            case "green" -> new int[]{0x0E2A1A, 0x020604};
            default -> new int[]{0x000000, 0x000000}; // black, and custom while its image loads
        };
    }

    /** Draws the background into the rectangle. {@code alpha} 0..1. */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0f) return;
        String mode = mode();
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24;
        if ("custom".equals(mode)) {
            refresh();
            if (loaded) {
                // Cover: fill the rectangle, cropping the image's overflow evenly.
                float scale = Math.max((float) w / texW, (float) h / texH);
                int rw = Math.max(1, Math.round(w / scale)), rh = Math.max(1, Math.round(h / scale));
                float u = (texW - rw) / 2f, v = (texH - rh) / 2f;
                g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, u, v, w, h, rw, rh, texW, texH, a | 0xFFFFFF);
                return;
            }
        }
        int[] c = colors(mode);
        g.fillGradient(x, y, x + w, y + h, a | c[0], a | c[1]);
        if ("green".equals(mode)) {
            // Motion green: a soft electric-green glow rising from the bottom.
            int glowA = Math.round(alpha * 70f) << 24;
            g.fillGradient(x, y + h / 2, x + w, y + h, 0x0000E676, glowA | 0x00E676);
        }
    }

    /** The colour (RGB) for Minecraft's resource-loading screen. */
    public static int loadingColor(int original) {
        if (!onLoadingScreens()) return original;
        String mode = mode();
        if ("custom".equals(mode)) {
            refresh();
            return loaded ? average : 0x000000;
        }
        if ("green".equals(mode)) return 0x07150D;
        return colors(mode)[0];
    }

    /** Loads or reloads the custom image when the file changed (checked every 2 s). */
    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 2000 && fileStamp != -1) return;
        lastCheck = now;
        if (!BloomClient.ready()) return;
        Path file = null;
        for (String name : FILES) {
            Path f = BloomClient.get().configDir().resolve(name);
            if (Files.isRegularFile(f)) {
                file = f;
                break;
            }
        }
        long stamp;
        try {
            stamp = file == null ? 0 : (Files.getLastModifiedTime(file).toMillis() ^ Files.size(file) ^ file.getFileName().toString().hashCode());
        } catch (Exception e) {
            stamp = 0;
        }
        if (stamp == fileStamp) return;
        fileStamp = stamp;
        unload();
        if (stamp == 0) return;
        try {
            if (Files.size(file) > MAX_BYTES) {
                Log.warn("MOTION menu background: " + file + " is over 40 MB, not loading it");
                return;
            }
            NativeImage img = decode(file);
            texW = img.getWidth();
            texH = img.getHeight();
            average = averageRgb(img);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "MOTION menu background", img));
            loaded = true;
            Log.info("MOTION menu background: loaded " + texW + "x" + texH);
        } catch (Exception e) {
            Log.warn("MOTION menu background: couldn't load " + file, e);
        }
    }

    /** PNG through Minecraft's decoder; anything else (JPG) through Java's ImageIO. */
    private static NativeImage decode(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return NativeImage.read(in);
        } catch (Exception notPng) {
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(file.toFile());
            if (bi == null) throw new java.io.IOException("unsupported image format");
            int w = bi.getWidth(), h = bi.getHeight();
            NativeImage img = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) img.setPixel(x, y, bi.getRGB(x, y) | 0xFF000000); // ARGB in this version
            }
            return img;
        }
    }

    private static void unload() {
        if (loaded) Minecraft.getInstance().getTextureManager().release(TEXTURE);
        loaded = false;
    }

    /** Average colour of a sample grid (ARGB pixels in this Minecraft version). */
    private static int averageRgb(NativeImage img) {
        long r = 0, gg = 0, b = 0, n = 0;
        int sx = Math.max(1, img.getWidth() / 32), sy = Math.max(1, img.getHeight() / 32);
        for (int y = 0; y < img.getHeight(); y += sy) {
            for (int x = 0; x < img.getWidth(); x += sx) {
                int p = img.getPixel(x, y);
                r += (p >> 16) & 0xFF;
                gg += (p >> 8) & 0xFF;
                b += p & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0;
        return (int) ((r / n) << 16 | (gg / n) << 8 | (b / n));
    }
}
