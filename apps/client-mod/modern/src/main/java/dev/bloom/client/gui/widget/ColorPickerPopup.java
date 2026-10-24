package dev.bloom.client.gui.widget;

import net.minecraft.client.renderer.RenderPipelines;
import dev.bloom.client.core.settings.ColorSetting;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * HSV colour picker: saturation/value square, hue bar, hex input, a few swatches and
 * "Reset". Changes apply live; Esc or clicking outside closes it.
 */
public class ColorPickerPopup extends Popup {
    private static final int[] SWATCHES = {0xE3192F, 0xFF1F3D, 0xA3172B, 0x7A0C1C, 0xFF4A1F, 0xFFB088, 0xFF8FB8, 0x7CC2FF, 0x7FD69A, 0xFFE37A, 0xFFFFFF, 0x0B0809};
    private final ColorSetting setting;
    private final TextField hex;
    private float hue, sat, val;
    private int dragging; // 0 none, 1 square, 2 hue
    private final Host host;

    public ColorPickerPopup(Host host, int x, int y, ColorSetting setting) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = 150;
        this.h = 148;
        this.setting = setting;
        fromRgb(setting.rgb());
        hex = new TextField(host, "#RRGGBB", 7).filter(c -> c == '#' || Character.digit(c, 16) >= 0).onChange(s -> {
            Integer v = ColorSetting.parseHex(s);
            if (v != null && (s.replace("#", "").length() == 6)) {
                setting.set(v);
                fromRgb(v);
            }
        });
        hex.setValue(ColorSetting.hex(setting.rgb()));
    }

    private void fromRgb(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        val = max;
        sat = max == 0 ? 0 : (max - min) / max;
        if (max == min) return; // keep hue
        float hh;
        if (max == r) hh = (g - b) / (max - min) + (g < b ? 6 : 0);
        else if (max == g) hh = (b - r) / (max - min) + 2;
        else hh = (r - g) / (max - min) + 4;
        hue = hh / 6f;
    }

    static int hsv(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private void apply() {
        int rgb = hsv(hue, sat, val);
        setting.set(rgb);
        hex.setValue(ColorSetting.hex(rgb));
    }

    @Override
    public boolean modal() {
        return false;
    }

    private int sqX() {
        return x + 6;
    }

    private int sqY() {
        return y + 6;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        Theme.Palette p = Theme.palette();
        Ui.shadow(g, x, y, w, h, 1f);
        Ui.panel(g, x, y, w, h, p.surfaceHover());
        Ui.panelBorder(g, x, y, w, h, p.border());
        int sx = sqX(), sy = sqY(), size = 100;
        int pure = 0xFF000000 | hsv(hue, 1, 1);
        Ui.gradientQuad(g, sx, sy, sx + size, sy + size, 0xFFFFFFFF, pure, 0xFF000000, 0xFF000000);
        int kx = sx + Math.round(sat * size), ky = sy + Math.round((1 - val) * size);
        Ui.spriteTinted(g, "ring", kx - 4, ky - 4, 8, 8, 0xFFFFFFFF);
        // hue bar
        int hx = sx + size + 6;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Ui.sprite("hue_bar"), hx, sy, 10, size);
        int hy = sy + Math.round(hue * size);
        g.fill(hx - 2, hy - 1, hx + 12, hy + 1, 0xFFFFFFFF);
        // preview + hex
        int py = sy + size + 6;
        Ui.smallPanel(g, sx, py, 16, 18, 0xFF000000 | setting.rgb());
        hex.layout(sx + 20, py, 60);
        hex.render(g, mx, my, dt);
        boolean overReset = Ui.inside(mx, my, sx + 84, py, 54, 18);
        Ui.smallPanel(g, sx + 84, py, 54, 18, overReset ? p.surfaceActive() : p.surface());
        Ui.textCentered(g, "Default", sx + 111, py + 5, p.textDim());
        // swatches
        int swY = py + 22;
        for (int i = 0; i < SWATCHES.length; i++) {
            int cx = sx + i * 11;
            Ui.smallPanel(g, cx, swY, 9, 9, 0xFF000000 | SWATCHES[i]);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isOver(mx, my)) {
            close();
            return false;
        }
        int sx = sqX(), sy = sqY();
        if (Ui.inside(mx, my, sx, sy, 101, 101)) {
            dragging = 1;
            drag(mx, my);
            return true;
        }
        if (Ui.inside(mx, my, sx + 104, sy, 14, 101)) {
            dragging = 2;
            drag(mx, my);
            return true;
        }
        int py = sy + 106;
        if (hex.mouseClicked(mx, my, button)) return true;
        if (Ui.inside(mx, my, sx + 84, py, 54, 18)) {
            setting.reset();
            fromRgb(setting.rgb());
            hex.setValue(ColorSetting.hex(setting.rgb()));
            Ui.click();
            return true;
        }
        int swY = py + 22;
        for (int i = 0; i < SWATCHES.length; i++) {
            if (Ui.inside(mx, my, sx + i * 11, swY, 9, 9)) {
                setting.set(SWATCHES[i]);
                fromRgb(SWATCHES[i]);
                hex.setValue(ColorSetting.hex(SWATCHES[i]));
                Ui.click();
                return true;
            }
        }
        return true;
    }

    private void drag(double mx, double my) {
        int sx = sqX(), sy = sqY();
        if (dragging == 1) {
            sat = (float) Math.max(0, Math.min(1, (mx - sx) / 100.0));
            val = 1f - (float) Math.max(0, Math.min(1, (my - sy) / 100.0));
            apply();
        } else if (dragging == 2) {
            hue = (float) Math.max(0, Math.min(0.999, (my - sy) / 100.0));
            apply();
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging == 0) return false;
        drag(mx, my);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            close();
            return true;
        }
        return hex.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return hex.charTyped(c, mods);
    }
}
