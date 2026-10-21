package dev.bloom.client.gui.widget;

import dev.bloom.client.core.settings.BoolSetting;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.core.settings.ColorSetting;
import dev.bloom.client.core.settings.MultiChoiceSetting;
import dev.bloom.client.core.settings.NumberSetting;
import dev.bloom.client.gui.Anim;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/** The value editors used in setting rows. Each edits its setting directly (changes apply live). */
public final class Controls {
    private Controls() {
    }

    // ---------------------------------------------------------------- toggle

    public static final class Toggle extends Element {
        final BoolSetting setting;
        private float knob = -1;
        public boolean enabled = true;

        public Toggle(BoolSetting s) {
            this.setting = s;
        }

        @Override
        public int preferredHeight(int width) {
            return 12;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            float target = setting.on() ? 1f : 0f;
            knob = knob < 0 ? target : Anim.approach(knob, target, 16f, dt);
            int track = Theme.lerp(p.surfaceActive(), Theme.accentArgb(), knob);
            if (!enabled) track = Theme.alpha(track, 0.4f);
            Ui.pill(g, x, y, w, h, track);
            int r = h / 2 - 2;
            int cx = x + 2 + r + Math.round((w - 4 - 2 * r) * knob);
            Ui.circle(g, cx, y + h / 2, r, enabled ? 0xFFFFFFFF : 0xFF9A9A9A);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != 0 || !isOver(mx, my)) return false;
            setting.toggle();
            Ui.click();
            return true;
        }
    }

    // ---------------------------------------------------------------- slider

    public static final class Slider extends Element {
        final NumberSetting setting;
        private final Host host;
        private boolean dragging;
        public boolean enabled = true;

        public Slider(NumberSetting s, Host host) {
            this.setting = s;
            this.host = host;
        }

        @Override
        public int preferredHeight(int width) {
            return 12;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            int ty = y + h / 2 - 2;
            Ui.pill(g, x, ty, w, 4, enabled ? p.surfaceActive() : Theme.alpha(p.surfaceActive(), 0.4f));
            int fx = x + Math.round(w * setting.fraction());
            if (fx - x >= 2) Ui.pill(g, x, ty, Math.max(4, fx - x), 4, enabled ? Theme.accentArgb() : Theme.alpha(Theme.accentArgb(), 0.4f));
            boolean over = isOver(mx, my) || dragging;
            int r = over && enabled ? 5 : 4;
            Ui.circle(g, Math.max(x + r, Math.min(x + w - r, fx)), y + h / 2, r, enabled ? 0xFFFFFFFF : 0xFF9A9A9A);
            if (host.isFocused(this)) Ui.pillBorder(g, x - 2, y - 1, w + 4, h + 2, Theme.alpha(Theme.accentArgb(), 0.5f));
        }

        private void setFrom(double mx) {
            setting.setFraction((float) ((mx - x) / Math.max(1, w)));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != 0 || !Ui.inside(mx, my, x - 4, y - 3, w + 8, h + 6)) return false;
            host.focus(this);
            if (Screen.hasControlDown()) {
                host.prompt("Set " + setting.label(), trim(setting.value()), "Set", text -> {
                    try {
                        float v = Float.parseFloat(text.replace("%", "").replace(",", ".").trim());
                        if (setting.format() == NumberSetting.Format.PERCENT && text.contains("%")) v /= 100f;
                        else if (setting.format() == NumberSetting.Format.PERCENT && v > 1.5f) v /= 100f;
                        setting.set(v);
                    } catch (NumberFormatException ignored) {
                    }
                });
                return true;
            }
            dragging = true;
            setFrom(mx);
            return true;
        }

        private static String trim(float v) {
            String s = String.format(Locale.ROOT, "%.3f", v);
            return s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (!dragging) return false;
            setFrom(mx);
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean was = dragging;
            dragging = false;
            return was;
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            if (!host.isFocused(this) || !enabled) return false;
            float step = setting.step() * (Screen.hasShiftDown() ? 10 : 1);
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN) {
                setting.set(setting.value() - step);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP) {
                setting.set(setting.value() + step);
                return true;
            }
            return false;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            return List.of("Drag to adjust · arrow keys for small steps · Ctrl+click to type a value");
        }
    }

    // ---------------------------------------------------------------- colour swatch

    public static final class Swatch extends Element {
        final ColorSetting setting;
        private final Host host;
        public boolean enabled = true;

        public Swatch(ColorSetting s, Host host) {
            this.setting = s;
            this.host = host;
        }

        @Override
        public int preferredHeight(int width) {
            return 14;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            boolean over = isOver(mx, my) && enabled;
            Ui.smallPanel(g, x, y, w, h, over ? p.surfaceActive() : p.surface());
            Ui.smallPanel(g, x + 2, y + 2, 14, h - 4, enabled ? (0xFF000000 | setting.rgb()) : Theme.alpha(0xFF000000 | setting.rgb(), 0.4f));
            Ui.text(g, ColorSetting.hex(setting.rgb()), x + 20, y + (h - 8) / 2, enabled ? p.textDim() : p.textFaint());
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != 0 || !isOver(mx, my)) return false;
            Ui.click();
            host.openColorPicker(x, y + h + 2, setting);
            return true;
        }
    }

    // ---------------------------------------------------------------- dropdown

    public static final class Dropdown extends Element {
        final ChoiceSetting setting;
        private final Host host;
        public boolean enabled = true;

        public Dropdown(ChoiceSetting s, Host host) {
            this.setting = s;
            this.host = host;
        }

        @Override
        public int preferredHeight(int width) {
            return 16;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            boolean over = isOver(mx, my) && enabled;
            Ui.smallPanel(g, x, y, w, h, over ? p.surfaceActive() : p.surface());
            Ui.text(g, Ui.ellipsize(setting.displayValue(), w - 22), x + 6, y + (h - 8) / 2, enabled ? p.text() : p.textFaint());
            Ui.icon(g, "chevron_down", x + w - 14, y + (h - 10) / 2, 10, p.textDim());
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != 0 || !isOver(mx, my)) return false;
            Ui.click();
            host.openDropdown(x, y + h + 2, Math.max(w, 120), setting.options(), setting.id(), setting::set);
            return true;
        }
    }

    // ---------------------------------------------------------------- segmented (≤ 3 options)

    public static final class Segmented extends Element {
        final ChoiceSetting setting;
        public boolean enabled = true;

        public Segmented(ChoiceSetting s) {
            this.setting = s;
        }

        public static int widthFor(ChoiceSetting s) {
            int w = 0;
            for (ChoiceSetting.Option o : s.options()) w += Ui.font().width(o.label()) + 12;
            return w + 4;
        }

        @Override
        public int preferredHeight(int width) {
            return 16;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            Ui.smallPanel(g, x, y, w, h, p.surface());
            List<ChoiceSetting.Option> opts = setting.options();
            int cx = x + 2;
            int total = 0;
            for (ChoiceSetting.Option o : opts) total += Ui.font().width(o.label()) + 12;
            float k = (w - 4) / (float) Math.max(1, total);
            for (ChoiceSetting.Option o : opts) {
                int sw = Math.round((Ui.font().width(o.label()) + 12) * k);
                boolean sel = setting.is(o.id());
                boolean over = enabled && Ui.inside(mx, my, cx, y, sw, h);
                if (sel) Ui.smallPanel(g, cx, y + 2, sw, h - 4, enabled ? Theme.accentArgb() : Theme.alpha(Theme.accentArgb(), 0.4f));
                else if (over) Ui.smallPanel(g, cx, y + 2, sw, h - 4, p.surfaceHover());
                int col = sel ? Theme.onAccent() : enabled ? p.textDim() : p.textFaint();
                Ui.textCentered(g, o.label(), cx + sw / 2, y + (h - 8) / 2, col);
                cx += sw;
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != 0 || !isOver(mx, my)) return false;
            List<ChoiceSetting.Option> opts = setting.options();
            int total = 0;
            for (ChoiceSetting.Option o : opts) total += Ui.font().width(o.label()) + 12;
            float k = (w - 4) / (float) Math.max(1, total);
            int cx = x + 2;
            for (ChoiceSetting.Option o : opts) {
                int sw = Math.round((Ui.font().width(o.label()) + 12) * k);
                if (mx >= cx && mx < cx + sw) {
                    setting.set(o.id());
                    Ui.click();
                    return true;
                }
                cx += sw;
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- chips (multi-choice)

    public static final class Chips extends Element {
        final MultiChoiceSetting setting;
        public boolean enabled = true;

        public Chips(MultiChoiceSetting s) {
            this.setting = s;
        }

        @Override
        public int preferredHeight(int width) {
            int cx = 0, rows = 1;
            for (var o : setting.options()) {
                int cw = Ui.font().width(o.label()) + 14;
                if (cx > 0 && cx + cw > width) {
                    rows++;
                    cx = 0;
                }
                cx += cw + 4;
            }
            return rows * 18 - 4;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            int cx = x, cy = y;
            var opts = setting.options();
            for (int i = 0; i < opts.size(); i++) {
                var o = opts.get(i);
                int cw = Ui.font().width(o.label()) + 14;
                if (cx > x && cx + cw > x + w) {
                    cx = x;
                    cy += 18;
                }
                boolean sel = setting.containsIndex(i);
                boolean over = enabled && Ui.inside(mx, my, cx, cy, cw, 14);
                int bg = sel ? Theme.accentArgb() : over ? p.surfaceHover() : p.surface();
                if (!enabled) bg = Theme.alpha(bg, 0.4f);
                Ui.pill(g, cx, cy, cw, 14, bg);
                Ui.textCentered(g, o.label(), cx + cw / 2, cy + 3, sel ? Theme.onAccent() : enabled ? p.textDim() : p.textFaint());
                cx += cw + 4;
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != 0 || !isOver(mx, my)) return false;
            int cx = x, cy = y;
            var opts = setting.options();
            for (int i = 0; i < opts.size(); i++) {
                var o = opts.get(i);
                int cw = Ui.font().width(o.label()) + 14;
                if (cx > x && cx + cw > x + w) {
                    cx = x;
                    cy += 18;
                }
                if (Ui.inside(mx, my, cx, cy, cw, 14)) {
                    setting.toggle(o.id());
                    Ui.click();
                    return true;
                }
                cx += cw + 4;
            }
            return true;
        }
    }
}
