package dev.bloom.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Base for MOTION's menu elements. Coordinates are GUI pixels; bounds are set by {@link #layout}. */
public abstract class Element {
    public int x, y, w, h;

    /** Places the element at (x, y) with the given width; returns its height. */
    public int layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.w = width;
        this.h = preferredHeight(width);
        return h;
    }

    public abstract int preferredHeight(int width);

    public abstract void render(GuiGraphics g, int mx, int my, float dt);

    public boolean isOver(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        return false;
    }

    public boolean charTyped(char c, int mods) {
        return false;
    }

    /** Tooltip lines when hovered, or null. */
    public List<String> tooltip(int mx, int my) {
        return null;
    }

    /** True while the element wants every click (e.g. a key bind waiting for input). */
    public boolean capturesClicks() {
        return false;
    }

    /** Called when the element loses keyboard focus. */
    public void blur() {
    }
}
