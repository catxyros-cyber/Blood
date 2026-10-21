package dev.bloom.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bloom.client.BloomClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drawing helpers for BLOOD's menus: tinted nine-slice panels, icons, text and gradients. */
public final class Ui {
    private static final Map<String, ResourceLocation> SPRITES = new HashMap<>();

    private Ui() {
    }

    public static ResourceLocation sprite(String name) {
        return SPRITES.computeIfAbsent(name, n -> ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, n));
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    private static void tint(GuiGraphics g, int argb) {
        g.setColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private static void untint(GuiGraphics g) {
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** A blended sprite draw with a colour tint. */
    public static void spriteTinted(GuiGraphics g, String name, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        RenderSystem.enableBlend();
        tint(g, argb);
        g.blitSprite(sprite(name), x, y, w, h);
        untint(g);
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

    /** Four-corner colour gradient (for the colour picker). */
    public static void gradientQuad(GuiGraphics g, int x0, int y0, int x1, int y1, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(m, x0, y0, 0).setColor(topLeft);
        b.addVertex(m, x0, y1, 0).setColor(bottomLeft);
        b.addVertex(m, x1, y1, 0).setColor(bottomRight);
        b.addVertex(m, x1, y0, 0).setColor(topRight);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
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
