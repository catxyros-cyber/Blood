package dev.bloom.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** BLOOD's notification, styled like the menu. Shown for about five seconds. */
public final class BloomToast implements Toast {
    private static final int WIDTH = 200;
    private final String title;
    private final List<FormattedCharSequence> lines;

    public BloomToast(String title, String message) {
        this.title = title;
        this.lines = Ui.wrap(message, WIDTH - 34);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return 20 + Math.min(4, lines.size()) * 10;
    }

    private Visibility visibility = Visibility.SHOW;

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleFor) {
        visibility = fullyVisibleFor < 5000L + lines.size() * 600L ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void render(GuiGraphics g, Font font, long fullyVisibleFor) {
        Theme.Palette p = Theme.palette();
        Ui.panel(g, 0, 0, width(), height(), p.window() | 0xFF000000);
        Ui.panelBorder(g, 0, 0, width(), height(), Theme.alpha(Theme.accentArgb(), 0.5f));
        Ui.spriteTinted(g, "logo", 6, 6, 16, 16, 0xFFFFFFFF);
        g.drawString(font, title, 28, 6, Theme.accentArgb(), false);
        for (int i = 0; i < Math.min(4, lines.size()); i++) g.drawString(font, lines.get(i), 28, 17 + i * 10, p.textDim(), false);
    }
}
