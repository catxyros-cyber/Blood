package dev.bloom.client.gui.widget;

import dev.bloom.client.gui.Anim;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** A vertical list of elements inside a clipped, smoothly scrolling viewport. */
public class ScrollList extends Element {
    private final List<Element> children = new ArrayList<>();
    private float scroll, target;
    private int contentH;
    private boolean draggingBar;
    private Element pressed;

    public void set(List<Element> elements) {
        children.clear();
        children.addAll(elements);
        scroll = target = 0;
    }

    public void setKeepScroll(List<Element> elements) {
        children.clear();
        children.addAll(elements);
    }

    public List<Element> children() {
        return children;
    }

    @Override
    public int preferredHeight(int width) {
        return h;
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        relayout();
    }

    private void relayout() {
        int cy = 0;
        for (Element e : children) cy += e.layout(x + 4, y + 4 + cy - Math.round(scroll), w - 14) + 2;
        contentH = cy + 8;
    }

    private float maxScroll() {
        return Math.max(0, contentH - h);
    }

    public void scrollTo(float v) {
        target = Math.max(0, Math.min(maxScroll(), v));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        target = Math.max(0, Math.min(maxScroll(), target));
        scroll = Anim.approach(scroll, target, 20f, dt);
        relayout();
        boolean inside = isOver(mx, my);
        int emx = inside ? mx : -10000, emy = inside ? my : -10000;
        g.enableScissor(x, y, x + w, y + h);
        try {
            for (Element e : children) {
                if (e.y + e.h < y || e.y > y + h) continue;
                e.render(g, emx, emy, dt);
            }
        } finally {
            g.disableScissor();
        }
        if (maxScroll() > 0) {
            Theme.Palette p = Theme.palette();
            int barH = Math.max(20, Math.round(h * (h / (float) contentH)));
            int barY = y + Math.round((h - barH) * (scroll / maxScroll()));
            Ui.pill(g, x + w - 5, barY, 3, barH, Theme.alpha(p.textDim(), draggingBar ? 0.7f : 0.35f));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!isOver(mx, my)) return false;
        target = Math.max(0, Math.min(maxScroll(), target - (float) amount * 28f));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isOver(mx, my)) return false;
        if (maxScroll() > 0 && mx >= x + w - 8) {
            draggingBar = true;
            return true;
        }
        for (Element e : children) {
            if (e.y + e.h < y || e.y > y + h) continue;
            if (e.mouseClicked(mx, my, button)) {
                pressed = e;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingBar = false;
        Element p = pressed;
        pressed = null;
        return p != null && p.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingBar) {
            target = scroll = Math.max(0, Math.min(maxScroll(), scroll + (float) dy * contentH / (float) h));
            return true;
        }
        return pressed != null && pressed.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (Element e : children) if (e.keyPressed(key, scan, mods)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        for (Element e : children) if (e.charTyped(c, mods)) return true;
        return false;
    }

    @Override
    public List<String> tooltip(int mx, int my) {
        if (!isOver(mx, my)) return null;
        for (Element e : children) if (e.isOver(mx, my)) return e.tooltip(mx, my);
        return null;
    }
}
