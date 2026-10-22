package dev.bloom.client.gui;

import dev.bloom.client.BloomClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drawing helpers for BLOOD's menus: tinted nine-slice panels, icons, text and gradients. */
public final class Ui {
    private static final Map<String, Identifier> SPRITES = new HashMap<>();

    private Ui() {
    }

    public static Identifier sprite(String name) {
        return SPRITES.computeIfAbsent(name, n -> Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, n));
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** A blended sprite draw with a colour tint. */
    public static void spriteTinted(GuiGraphics g, String name, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(name), x, y, w, h, argb);
    }

    // Pose helpers: GUI transforms are 2D (Matrix3x2f) from Minecraft 1.21.6 on.
    public static void push(GuiGraphics g) {
        g.pose().pushMatrix();
    }

    public static void pop(GuiGraphics g) {
        g.pose().popMatrix();
    }

    public static void translate(GuiGraphics g, float x, float y) {
        g.pose().translate(x, y);
    }

    public static void scale(GuiGraphics g, float s) {
        g.pose().scale(s, s);
    }

    /** Draws a string and returns the x just after it (GuiGraphics#drawString returns nothing any more). */
    public static int string(GuiGraphics g, Font font, String s, int x, int y, int argb, boolean shadow) {
        g.drawString(font, s, x, y, argb, shadow);
        return x + font.width(s) + (shadow ? 1 : 0);
    }

    /** Rounded panel (radius ~4px). */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int argb) {
        spriteTinted(g, w < 10 || h < 10 ? "panel_small" : "panel", x, y, w, h, argb);
    }

    public static void panelBorder(GuiGraphics g, int x, int y, int w, int h, int argb) {
        spriteTinted(g, w < 10 || h < 10 ? "panel_small_border" : "panel_border", x, y, w, h, argb);
    }

    public static void smallPanel(GuiGraphics g, int x, int y, int w, int h, int argb) {
        spriteTinted(g, "panel_small", x, y, w, h, argb);
    }

    public static void pill(GuiGraphics g, int x, int y, int w, int h, int argb) {
        spriteTinted(g, "pill", x, y, w, h, argb);
    }

    public static void pillBorder(GuiGraphics g, int x, int y, int w, int h, int argb) {
        spriteTinted(g, "pill_border", x, y, w, h, argb);
    }

    public static void circle(GuiGraphics g, int cx, int cy, int r, int argb) {
        spriteTinted(g, "circle", cx - r, cy - r, r * 2, r * 2, argb);
    }

    public static void shadow(GuiGraphics g, int x, int y, int w, int h, float strength) {
        spriteTinted(g, "shadow", x - 8, y - 6, w + 16, h + 16, Theme.withAlpha(0, (int) (110 * strength)));
    }

    public static void icon(GuiGraphics g, String name, int x, int y, int argb) {
        spriteTinted(g, "icons/" + name, x, y, 16, 16, argb);
    }

    public static void icon(GuiGraphics g, String name, int x, int y, int size, int argb) {
        spriteTinted(g, "icons/" + name, x, y, size, size, argb);
    }

    public static void text(GuiGraphics g, String s, int x, int y, int argb) {
        g.drawString(font(), s, x, y, argb, false);
    }

    public static void textCentered(GuiGraphics g, String s, int cx, int y, int argb) {
        g.drawString(font(), s, cx - font().width(s) / 2, y, argb, false);
    }

    /** Text cut to {@code maxWidth} with an ellipsis. */
    public static String ellipsize(String s, int maxWidth) {
        Font f = font();
        if (f.width(s) <= maxWidth) return s;
        String dots = "…";
        int w = maxWidth - f.width(dots);
        if (w <= 0) return dots;
        return f.plainSubstrByWidth(s, w) + dots;
    }

    public static List<FormattedCharSequence> wrap(String s, int width) {
        if (s == null || s.isEmpty()) return new ArrayList<>();
        return font().split(Component.literal(s), Math.max(10, width));
    }

    /** Draws wrapped text, at most {@code maxLines}; returns the height used. */
    public static int textWrapped(GuiGraphics g, String s, int x, int y, int width, int argb, int maxLines) {
        List<FormattedCharSequence> lines = wrap(s, width);
        int n = Math.min(lines.size(), maxLines);
        for (int i = 0; i < n; i++) g.drawString(font(), lines.get(i), x, y + i * 10, argb, false);
        return n * 10;
    }

    public static int wrappedHeight(String s, int width, int maxLines) {
        return Math.min(wrap(s, width).size(), maxLines) * 10;
    }

    /** Four-corner colour gradient (for the colour picker), drawn as thin vertical gradient strips. */
    public static void gradientQuad(GuiGraphics g, int x0, int y0, int x1, int y1, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        int w = x1 - x0;
        if (w <= 0 || y1 <= y0) return;
        for (int i = 0; i < w; i++) {
            float t = w == 1 ? 0f : i / (float) (w - 1);
            g.fillGradient(x0 + i, y0, x0 + i + 1, y1, lerp(topLeft, topRight, t), lerp(bottomLeft, bottomRight, t));
        }
    }

    private static int lerp(int a, int b, float t) {
        int out = 0;
        for (int sh = 0; sh < 32; sh += 8) {
            int ca = (a >>> sh) & 255, cb = (b >>> sh) & 255;
            out |= Math.round(ca + (cb - ca) * t) << sh;
        }
        return out;
    }

    public static void hLine(GuiGraphics g, int x0, int x1, int y, int argb) {
        g.fill(x0, y, x1, y + 1, argb);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
