package dev.bloom.client.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Consumer;

/** The list that opens under a dropdown. Scrolls when long; arrow keys and Enter work. */
public class DropdownPopup extends Popup {
    private static final int ROW = 14;
    private final List<ChoiceSetting.Option> options;
    private final String selected;
    private final Consumer<String> onPick;
    private int scroll;
    private int keyIndex = -1;

    public DropdownPopup(int x, int y, int w, List<ChoiceSetting.Option> options, String selected, Consumer<String> onPick) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.options = options;
        this.selected = selected;
        this.onPick = onPick;
        this.h = Math.min(options.size(), 10) * ROW + 6;
        for (int i = 0; i < options.size(); i++) if (options.get(i).id().equals(selected)) {
            keyIndex = i;
            scroll = Math.max(0, Math.min(options.size() - 10, i - 4));
        }
    }

    @Override
    public boolean modal() {
        return false;
    }

    @Override
    public void fit(int screenW, int screenH) {
        if (y + h > screenH - 4) y = Math.max(4, y - h - 22);
        super.fit(screenW, screenH);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        Theme.Palette p = Theme.palette();
        Ui.shadow(g, x, y, w, h, 1f);
        Ui.panel(g, x, y, w, h, p.surfaceHover());
        Ui.panelBorder(g, x, y, w, h, p.border());
        int visible = Math.min(options.size(), 10);
        for (int i = 0; i < visible; i++) {
            int idx = i + scroll;
            ChoiceSetting.Option o = options.get(idx);
            int ry = y + 3 + i * ROW;
            boolean over = Ui.inside(mx, my, x + 2, ry, w - 4, ROW);
            boolean sel = o.id().equals(selected);
            if (over || idx == keyIndex) Ui.smallPanel(g, x + 2, ry, w - 4, ROW, p.surfaceActive());
            if (sel) Ui.icon(g, "check", x + 5, ry + 2, 10, Theme.accentArgb());
            Ui.text(g, Ui.ellipsize(o.label(), w - 24), x + 18, ry + 3, sel ? Theme.accentArgb() : p.text());
        }
        if (options.size() > 10) {
            int barH = Math.max(10, h * 10 / options.size());
            int barY = y + (h - barH) * scroll / Math.max(1, options.size() - 10);
            Ui.pill(g, x + w - 4, barY, 2, barH, Theme.alpha(p.textDim(), 0.5f));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isOver(mx, my)) {
            close();
            return false;
        }
        int i = (int) ((my - y - 3) / ROW);
        if (i >= 0 && i < Math.min(options.size(), 10)) {
            onPick.accept(options.get(i + scroll).id());
            Ui.click();
            close();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scroll = Math.max(0, Math.min(Math.max(0, options.size() - 10), scroll - (int) Math.signum(amount)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case InputConstants.KEY_ESCAPE -> close();
            case InputConstants.KEY_DOWN -> keyIndex = Math.min(options.size() - 1, keyIndex + 1);
            case InputConstants.KEY_UP -> keyIndex = Math.max(0, keyIndex - 1);
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (keyIndex >= 0) onPick.accept(options.get(keyIndex).id());
                close();
            }
            default -> {
                return false;
            }
        }
        if (keyIndex >= 0) {
            if (keyIndex < scroll) scroll = keyIndex;
            if (keyIndex >= scroll + 10) scroll = keyIndex - 9;
        }
        return true;
    }
}
