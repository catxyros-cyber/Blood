package dev.bloom.client.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Modal dialog: a message, an optional text input, and up to two buttons. */
public class Dialog extends Popup {
    private final String title;
    private final String message;
    private final TextField input;
    private final List<Button> buttons = new ArrayList<>();
    private final Runnable onConfirm;
    private final Consumer<String> onText;

    private Dialog(Host host, String title, String message, String initial, String confirm, boolean danger, boolean cancel,
                   Runnable onConfirm, Consumer<String> onText) {
        this.title = title;
        this.message = message;
        this.onConfirm = onConfirm;
        this.onText = onText;
        if (onText != null) {
            input = new TextField(host, "", 64);
            input.setValue(initial);
            input.selectAll();
            host.focus(input);
        } else {
            input = null;
        }
        if (cancel) buttons.add(new Button("Cancel", null, Button.Style.NORMAL, this::close));
        buttons.add(new Button(confirm, null, danger ? Button.Style.DANGER : Button.Style.PRIMARY, this::confirm));
        w = 220;
    }

    public static Dialog confirm(Host host, String title, String message, String confirm, boolean danger, Runnable onConfirm) {
        return new Dialog(host, title, message, null, confirm, danger, true, onConfirm, null);
    }

    public static Dialog prompt(Host host, String title, String initial, String confirm, Consumer<String> onText) {
        return new Dialog(host, title, null, initial, confirm, false, true, null, onText);
    }

    public static Dialog message(Host host, String title, String message) {
        return new Dialog(host, title, message, null, "OK", false, false, null, null);
    }

    private void confirm() {
        close();
        if (onConfirm != null) onConfirm.run();
        if (onText != null) onText.accept(input.value());
    }

    @Override
    public boolean modal() {
        return true;
    }

    @Override
    public void fit(int screenW, int screenH) {
        int msgH = message != null ? Ui.wrappedHeight(message, w - 20, 14) + 6 : 0;
        h = 26 + msgH + (input != null ? 24 : 0) + 26;
        x = (screenW - w) / 2;
        y = (screenH - h) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        Theme.Palette p = Theme.palette();
        Ui.shadow(g, x, y, w, h, 1.2f);
        Ui.panel(g, x, y, w, h, p.surfaceHover());
        Ui.panelBorder(g, x, y, w, h, p.border());
        Ui.text(g, title, x + 10, y + 10, p.text());
        int cy = y + 26;
        if (message != null) {
            cy += Ui.textWrapped(g, message, x + 10, cy, w - 20, p.textDim(), 14) + 6;
        }
        if (input != null) {
            input.layout(x + 10, cy, w - 20);
            input.render(g, mx, my, dt);
            cy += 24;
        }
        int bx = x + w - 10;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            int bw = Math.max(60, b.naturalWidth());
            bx -= bw;
            b.layout(bx, y + h - 24, bw);
            bx -= 6;
            b.render(g, mx, my, dt);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (input != null && input.mouseClicked(mx, my, button)) return true;
        for (Button b : buttons) if (b.mouseClicked(mx, my, button)) return true;
        return true; // modal: swallow clicks
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            confirm();
            return true;
        }
        if (input != null) input.keyPressed(key, scan, mods);
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (input != null) input.charTyped(c, mods);
        return true;
    }
}
