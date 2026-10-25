package dev.bloom.client.gui.widget;

import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** A rounded button with optional icon. Styles: primary (accent), normal, danger, ghost. */
public class Button extends Element {
    public enum Style { PRIMARY, NORMAL, DANGER, GHOST }

    private String label;
    private final String icon;
    private final Style style;
    private final Runnable action;
    private String tip;
    public boolean enabled = true;
    private float hover;

    public Button(String label, String icon, Style style, Runnable action) {
        this.label = label;
        this.icon = icon;
        this.style = style;
        this.action = action;
    }

    public Button tooltip(String t) {
        this.tip = t;
        return this;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int naturalWidth() {
        int tw = label == null || label.isEmpty() ? 0 : Ui.width(label);
        int iw = icon != null ? 12 : 0;
        return Math.max(20, tw + iw + (tw > 0 && iw > 0 ? 4 : 0) + 14);
    }

    @Override
    public int preferredHeight(int width) {
        return 18;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        Theme.Palette p = Theme.palette();
        boolean over = enabled && isOver(mx, my);
        hover = dev.bloom.client.gui.Anim.approach(hover, over ? 1f : 0f, 18f, dt);
        int bg, fg;
        switch (style) {
            case PRIMARY -> {
                bg = Theme.lerp(Theme.accentArgb(), 0xFFFFFFFF, hover * 0.12f);
                fg = Theme.onAccent();
            }
            case DANGER -> {
                bg = Theme.lerp(Theme.alpha(p.danger(), 0.18f), Theme.alpha(p.danger(), 0.32f), hover);
                fg = p.danger();
            }
            case GHOST -> {
                bg = Theme.alpha(p.surfaceHover(), hover);
                fg = p.text();
            }
            default -> {
                bg = Theme.lerp(p.surface(), p.surfaceActive(), hover);
                fg = p.text();
            }
        }
        if (!enabled) {
            bg = Theme.alpha(bg, 0.45f);
            fg = p.textFaint();
        }
        Ui.smallPanel(g, x, y, w, h, bg);
        int tw = label == null || label.isEmpty() ? 0 : Ui.width(label);
        int iw = icon != null ? 12 : 0;
        int total = tw + iw + (tw > 0 && iw > 0 ? 4 : 0);
        int cx = x + (w - total) / 2;
        if (icon != null) {
            Ui.icon(g, icon, cx, y + (h - 12) / 2, 12, fg);
            cx += 16;
        }
        if (tw > 0) Ui.text(g, Ui.ellipsize(label, w - 8 - iw), cx, y + (h - 8) / 2, fg);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || button != 0 || !isOver(mx, my)) return false;
        Ui.click();
        action.run();
        return true;
    }

    @Override
    public List<String> tooltip(int mx, int my) {
        return tip != null ? List.of(tip) : null;
    }
}
