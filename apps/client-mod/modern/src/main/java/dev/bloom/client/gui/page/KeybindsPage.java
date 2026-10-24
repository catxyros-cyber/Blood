package dev.bloom.client.gui.page;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bloom.client.BloomKeys;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class KeybindsPage extends GenericPage {
    public KeybindsPage(BloomMenuScreen screen) {
        super(screen, Category.KEYBINDS);
    }

    @Override
    public List<Element> build() {
        List<Element> rows = new ArrayList<>();
        rows.add(new Rows.Info("info", "Click a key to change it, then press the new key (or a mouse side button). Backspace clears it, Esc cancels. "
                + "These are normal Minecraft key binds, so they also appear under Options → Controls → Key Binds.", Theme.palette().textDim()));
        for (KeyMapping k : BloomKeys.ALL) rows.add(new KeyRow(screen, k));
        rows.addAll(Pages.settings(Category.KEYBINDS, screen));
        return rows;
    }

    /** All other key mappings bound to the same key. */
    static List<KeyMapping> conflicts(KeyMapping k) {
        List<KeyMapping> out = new ArrayList<>();
        if (k.isUnbound()) return out;
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other != k && !other.isUnbound() && other.same(k)) out.add(other);
        }
        return out;
    }

    static final class KeyRow extends Element {
        private final BloomMenuScreen screen;
        private final KeyMapping key;
        private boolean listening;

        KeyRow(BloomMenuScreen screen, KeyMapping key) {
            this.screen = screen;
            this.key = key;
        }

        private List<KeyMapping> conflicts() {
            return BloomSettings.KEY_CONFLICT_WARNINGS.on() ? KeybindsPage.conflicts(key) : List.of();
        }

        @Override
        public int preferredHeight(int width) {
            return 32 + (conflicts().isEmpty() ? 0 : 14);
        }

        private int btnX() {
            return x + w - 8 - 110;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            if (isOver(mx, my)) Ui.smallPanel(g, x, y, w, h - 2, Theme.alpha(p.surface(), 0.6f));
            Ui.text(g, BloomKeys.label(key), x + 8, y + 7, p.text());
            Ui.text(g, Ui.ellipsize(BloomKeys.description(key), w - 150), x + 8, y + 18, p.textFaint());
            boolean over = Ui.inside(mx, my, btnX(), y + 4, 110, 18);
            int bg = listening ? Theme.accentArgb() : over ? p.surfaceActive() : p.surface();
            Ui.smallPanel(g, btnX(), y + 4, 110, 18, bg);
            String label = listening ? "Press a key…" : key.isUnbound() ? "Not bound" : key.getTranslatedKeyMessage().getString();
            int col = listening ? Theme.onAccent() : key.isUnbound() ? p.textFaint() : conflicts().isEmpty() ? p.text() : p.warn();
            Ui.textCentered(g, Ui.ellipsize(label, 100), btnX() + 55, y + 9, col);
            if (!key.isDefault()) {
                boolean ro = Ui.inside(mx, my, btnX() - 18, y + 7, 12, 12);
                Ui.icon(g, "reset", btnX() - 18, y + 7, 12, ro ? Theme.accentArgb() : Theme.alpha(p.textDim(), 0.5f));
            }
            List<KeyMapping> c = conflicts();
            if (!c.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (KeyMapping k : c) names.add(Component.translatable(k.getName()).getString());
                String text = "Also used by: " + String.join(", ", names);
                Ui.icon(g, "warning", x + 8, y + 30, 10, p.warn());
                Ui.text(g, Ui.ellipsize(text, w - 120), x + 22, y + 31, p.warn());
                boolean fo = Ui.inside(mx, my, x + w - 96, y + 28, 88, 13);
                Ui.smallPanel(g, x + w - 96, y + 28, 88, 13, fo ? p.surfaceActive() : p.surface());
                Ui.textCentered(g, "Unbind others", x + w - 52, y + 31, p.text());
            }
        }

        private void apply(InputConstants.Key k) {
            key.setKey(k);
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
            listening = false;
            screen.focus(null);
            screen.requestRebuild();
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (listening) {
                if (button >= 2) {
                    apply(InputConstants.Type.MOUSE.getOrCreate(button));
                } else {
                    listening = false;
                    screen.focus(null);
                }
                return true;
            }
            if (button != 0) return false;
            if (Ui.inside(mx, my, btnX(), y + 4, 110, 18)) {
                listening = true;
                screen.focus(this);
                Ui.click();
                return true;
            }
            if (!key.isDefault() && Ui.inside(mx, my, btnX() - 18, y + 7, 12, 12)) {
                Ui.click();
                apply(key.getDefaultKey());
                return true;
            }
            if (!conflicts().isEmpty() && Ui.inside(mx, my, x + w - 96, y + 28, 88, 13)) {
                Ui.click();
                for (KeyMapping other : conflicts()) other.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
                screen.requestRebuild();
                return true;
            }
            return false;
        }

        @Override
        public boolean keyPressed(int k, int scan, int mods) {
            if (!listening || !screen.isFocused(this)) return false;
            if (k == InputConstants.KEY_ESCAPE) {
                listening = false;
                screen.focus(null);
            } else if (k == InputConstants.KEY_BACKSPACE || k == InputConstants.KEY_DELETE) {
                apply(InputConstants.UNKNOWN);
            } else {
//#if MC >= 26.3
                apply(InputConstants.Type.KEYBOARD.getOrCreate(k));
//#else
                apply(k != -1 ? InputConstants.Type.KEYSYM.getOrCreate(k) : InputConstants.Type.SCANCODE.getOrCreate(scan));
//#endif
            }
            return true;
        }

        @Override
        public void blur() {
            listening = false;
        }

        @Override
        public boolean capturesClicks() {
            return listening;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            if (Ui.inside(mx, my, btnX() - 18, y + 7, 12, 12) && !key.isDefault()) {
                return List.of("Reset to " + key.getDefaultKey().getDisplayName().getString());
            }
            return null;
        }
    }
}
