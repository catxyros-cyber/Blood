package dev.bloom.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;

/** Something drawn above the page: dropdown lists, the colour picker and dialogs. */
public abstract class Popup extends Element {
    /** Modal popups dim the screen and only close via their buttons or Esc. */
    public abstract boolean modal();

    public boolean closed;

    public void close() {
        closed = true;
    }

    @Override
    public int preferredHeight(int width) {
        return h;
    }

    /** Called before rendering so the popup can clamp itself to the screen. */
    public void fit(int screenW, int screenH) {
        x = Math.max(4, Math.min(screenW - w - 4, x));
        y = Math.max(4, Math.min(screenH - h - 4, y));
    }

    public abstract void render(GuiGraphics g, int mx, int my, float dt);
}
