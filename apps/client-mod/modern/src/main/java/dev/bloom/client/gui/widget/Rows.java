package dev.bloom.client.gui.widget;

import dev.bloom.client.core.settings.BoolSetting;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.core.settings.ColorSetting;
import dev.bloom.client.core.settings.MultiChoiceSetting;
import dev.bloom.client.core.settings.NumberSetting;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.gui.Anim;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Row types used by menu pages. */
public final class Rows {
    private Rows() {
    }

    // ---------------------------------------------------------------- header

    public static final class Header extends Element {
        private final String text;

        public Header(String text) {
            this.text = text;
        }

        @Override
        public int preferredHeight(int width) {
            return 20;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            Ui.text(g, text.toUpperCase(java.util.Locale.ROOT), x + 2, y + 9, Theme.alpha(Theme.accentArgb(), 0.9f));
        }
    }

    // ---------------------------------------------------------------- information / warning text

    public static final class Info extends Element {
        private final String icon;
        private final String text;
        private final int color;

        public Info(String icon, String text, int color) {
            this.icon = icon;
            this.text = text;
            this.color = color;
        }

        @Override
        public int preferredHeight(int width) {
            return Math.max(16, Ui.wrappedHeight(text, width - 30, 8) + 8);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Ui.smallPanel(g, x, y, w, h - 2, Theme.alpha(color, 0.12f));
            if (icon != null) Ui.icon(g, icon, x + 6, y + 4, 12, color);
            Ui.textWrapped(g, text, x + 24, y + 5, w - 30, Theme.palette().text(), 8);
        }
    }

    // ---------------------------------------------------------------- a row of buttons

    public static final class Buttons extends Element {
        private final List<Button> buttons;

        public Buttons(List<Button> buttons) {
            this.buttons = buttons;
        }

        @Override
        public int preferredHeight(int width) {
            int cx = 0, rows = 1;
            for (Button b : buttons) {
                int bw = b.naturalWidth();
                if (cx > 0 && cx + bw > width) {
                    rows++;
                    cx = 0;
                }
                cx += bw + 4;
            }
            return rows * 22;
        }

        @Override
        public int layout(int x, int y, int width) {
            int h = super.layout(x, y, width);
            int cx = x, cy = y + 2;
            for (Button b : buttons) {
                int bw = b.naturalWidth();
                if (cx > x && cx + bw > x + width) {
                    cx = x;
                    cy += 22;
                }
                b.layout(cx, cy, bw);
                cx += bw + 4;
            }
            return h;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            for (Button b : buttons) b.render(g, mx, my, dt);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            for (Button b : buttons) if (b.mouseClicked(mx, my, button)) return true;
            return false;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            for (Button b : buttons) if (b.isOver(mx, my)) return b.tooltip(mx, my);
            return null;
        }
    }

    // ---------------------------------------------------------------- spacer

    public static final class Spacer extends Element {
        private final int size;

        public Spacer(int size) {
            this.size = size;
        }

        @Override
        public int preferredHeight(int width) {
            return size;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
        }
    }

    // ---------------------------------------------------------------- setting row

    /** Label + description on the left, the right control on the right, per-setting reset. */
    public static final class SettingRow extends Element {
        public final Setting setting;
        private final Element control;
        private final boolean wide; // control below the text (chips)
        private final String categoryTag;
        private int controlW;
        private float hover;
        private int resetX, resetY;

        public SettingRow(Setting s, Host host, String categoryTag) {
            this.setting = s;
            this.categoryTag = categoryTag;
            if (s instanceof BoolSetting b) {
                control = new Controls.Toggle(b);
                controlW = 24;
                wide = false;
            } else if (s instanceof NumberSetting n) {
                control = new Controls.Slider(n, host);
                controlW = 96;
                wide = false;
            } else if (s instanceof ColorSetting c) {
                control = new Controls.Swatch(c, host);
                controlW = 72;
                wide = false;
            } else if (s instanceof ChoiceSetting c) {
                int segW = Controls.Segmented.widthFor(c);
                if (c.options().size() <= 3 && segW <= 150) {
                    control = new Controls.Segmented(c);
                    controlW = segW;
                } else {
                    control = new Controls.Dropdown(c, host);
                    controlW = 124;
                }
                wide = false;
            } else if (s instanceof MultiChoiceSetting m) {
                control = new Controls.Chips(m);
                controlW = 0;
                wide = true;
            } else {
                throw new IllegalArgumentException(s.key());
            }
        }

        private int valueW() {
            return setting instanceof NumberSetting ? 44 : 0;
        }

        private int textWidth(int width) {
            return wide ? width - 34 : width - controlW - valueW() - 40;
        }

        private String subtitle() {
            String reason = setting.unavailableReason();
            if (reason != null) return reason;
            if (setting.restartReason() != null) return setting.description() + " (Restart required: " + setting.restartReason() + ")";
            return setting.description();
        }

        @Override
        public int preferredHeight(int width) {
            int tw = textWidth(width);
            int textH = 10 + Ui.wrappedHeight(subtitle(), tw, 3) + (categoryTag != null ? 0 : 0);
            int h = Math.max(24, textH + 10);
            if (wide) h += control.preferredHeight(width - 16) + 4;
            return h;
        }

        @Override
        public int layout(int x, int y, int width) {
            int h = super.layout(x, y, width);
            if (wide) {
                int ch = control.preferredHeight(width - 16);
                control.layout(x + 8, y + h - ch - 6, width - 16);
            } else {
                int ch = control.preferredHeight(controlW);
                control.layout(x + width - 8 - controlW, y + 6 + Math.max(0, (Math.min(h, 30) - 12 - ch) / 2), controlW);
            }
            resetX = wide ? x + width - 22 : x + width - 8 - controlW - valueW() - 18;
            resetY = y + 6;
            return h;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            boolean active = setting.isActive();
            setEnabled(active);
            boolean over = isOver(mx, my);
            hover = Anim.approach(hover, over ? 1f : 0f, 14f, dt);
            if (hover > 0.01f) Ui.smallPanel(g, x, y, w, h - 2, Theme.alpha(p.surface(), 0.6f * hover));
            int tw = textWidth(w);
            int titleCol = active ? p.text() : p.textFaint();
            String title = setting.label();
            if (categoryTag != null) {
                int tagW = Ui.width(categoryTag) + 8;
                Ui.pill(g, x + 8, y + 5, tagW, 11, Theme.alpha(Theme.accentArgb(), 0.18f));
                Ui.text(g, categoryTag, x + 12, y + 7, Theme.accentArgb());
                Ui.text(g, Ui.ellipsize(title, tw - tagW - 4), x + 12 + tagW, y + 7, titleCol);
            } else {
                Ui.text(g, Ui.ellipsize(title, tw), x + 8, y + 7, titleCol);
            }
            String sub = subtitle();
            boolean warn = setting.unavailableReason() != null;
            Ui.textWrapped(g, sub, x + 8, y + 18, tw, warn ? p.warn() : p.textFaint(), 3);
            if (setting instanceof NumberSetting n) {
                String v = n.displayValue();
                Ui.text(g, v, control.x - 6 - Ui.width(v), control.y + 2, active ? p.textDim() : p.textFaint());
            }
            control.render(g, mx, my, dt);
            if (!setting.isDefault()) {
                boolean ro = Ui.inside(mx, my, resetX, resetY, 12, 12);
                Ui.icon(g, "reset", resetX, resetY, 12, ro ? Theme.accentArgb() : Theme.alpha(p.textDim(), over ? 0.9f : 0.35f));
            }
        }

        private void setEnabled(boolean on) {
            if (control instanceof Controls.Toggle t) t.enabled = on;
            else if (control instanceof Controls.Slider s) s.enabled = on;
            else if (control instanceof Controls.Swatch s) s.enabled = on;
            else if (control instanceof Controls.Dropdown d) d.enabled = on;
            else if (control instanceof Controls.Segmented s) s.enabled = on;
            else if (control instanceof Controls.Chips c) c.enabled = on;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!setting.isDefault() && Ui.inside(mx, my, resetX, resetY, 12, 12) && button == 0) {
                setting.reset();
                Ui.click();
                return true;
            }
            if (control.mouseClicked(mx, my, button)) return true;
            // Clicking the label of a toggle flips it.
            if (button == 0 && isOver(mx, my) && setting instanceof BoolSetting b && setting.isActive()) {
                b.toggle();
                Ui.click();
                return true;
            }
            return isOver(mx, my);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            return control.mouseReleased(mx, my, button);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            return control.mouseDragged(mx, my, button, dx, dy);
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            return control.keyPressed(key, scan, mods);
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            if (!setting.isDefault() && Ui.inside(mx, my, resetX, resetY, 12, 12)) return List.of("Reset to default");
            if (control.isOver(mx, my)) return control.tooltip(mx, my);
            return null;
        }
    }

    /** Builds rows for a list of settings, inserting group headers. */
    public static List<Element> settingRows(List<Setting> settings, Host host, boolean tagCategory) {
        List<Element> out = new ArrayList<>();
        String group = null;
        for (Setting s : settings) {
            if (!tagCategory && (group == null || !group.equals(s.group()))) {
                group = s.group();
                out.add(new Header(group));
            }
            out.add(new SettingRow(s, host, tagCategory ? s.category().label() : null));
        }
        return out;
    }
}
