package dev.bloom.client.gui.widget;

import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Single-line text input with cursor, select-all, copy and paste. */
public class TextField extends Element {
    private final Host host;
    private String value = "";
    private int cursor;
    private boolean allSelected;
    private final String placeholder;
    private final int maxLength;
    private Consumer<String> onChange = s -> {
    };
    private Predicate<Character> filter = c -> c >= 32 && c != 127 && c != '§';
    private String icon;
    private int scroll;

    public TextField(Host host, String placeholder, int maxLength) {
        this.host = host;
        this.placeholder = placeholder;
        this.maxLength = maxLength;
    }

    public TextField icon(String icon) {
        this.icon = icon;
        return this;
    }

    public TextField onChange(Consumer<String> c) {
        this.onChange = c;
        return this;
    }

    public TextField filter(Predicate<Character> f) {
        this.filter = f;
        return this;
    }

    public String value() {
        return value;
    }

    public void setValue(String v) {
        value = v == null ? "" : (v.length() > maxLength ? v.substring(0, maxLength) : v);
        cursor = value.length();
        allSelected = false;
    }

    public void selectAll() {
        allSelected = true;
        cursor = value.length();
    }

    @Override
    public int preferredHeight(int width) {
        return 18;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        Theme.Palette p = Theme.palette();
        boolean focused = host.isFocused(this);
        Ui.smallPanel(g, x, y, w, h, focused ? p.surfaceActive() : p.surface());
        if (focused) Ui.panelBorder(g, x, y, w, h, Theme.alpha(Theme.accentArgb(), 0.8f));
        int tx = x + 5;
        if (icon != null) {
            Ui.icon(g, icon, x + 4, y + 3, 12, p.textDim());
            tx = x + 19;
        }
        int avail = x + w - 5 - tx;
        g.enableScissor(tx, y, tx + avail, y + h);
        try {
            if (value.isEmpty() && !focused) {
                Ui.text(g, placeholder, tx, y + 5, p.textFaint());
            } else {
                int cx = Ui.font().width(value.substring(0, cursor));
                if (cx - scroll > avail - 2) scroll = cx - avail + 2;
                if (cx - scroll < 0) scroll = cx;
                if (allSelected && !value.isEmpty()) {
                    g.fill(tx - scroll, y + 4, tx - scroll + Ui.font().width(value), y + 14, Theme.alpha(Theme.accentArgb(), 0.4f));
                }
                Ui.text(g, value, tx - scroll, y + 5, p.text());
                if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
                    g.fill(tx - scroll + cx, y + 4, tx - scroll + cx + 1, y + 14, p.text());
                }
            }
        } finally {
            g.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isOver(mx, my)) return false;
        host.focus(this);
        int tx = x + (icon != null ? 19 : 5) - scroll;
        int rel = (int) mx - tx;
        cursor = Ui.font().plainSubstrByWidth(value, Math.max(0, rel)).length();
        allSelected = false;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!host.isFocused(this)) return false;
        boolean ctrl = net.minecraft.client.Minecraft.getInstance().hasControlDown();
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (allSelected) {
                    change("");
                } else if (cursor > 0) {
                    int from = ctrl ? wordStart(cursor) : cursor - 1;
                    String v = value.substring(0, from) + value.substring(cursor);
                    cursor = from;
                    changeKeepCursor(v);
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (allSelected) change("");
                else if (cursor < value.length()) changeKeepCursor(value.substring(0, cursor) + value.substring(cursor + 1));
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                allSelected = false;
                cursor = ctrl ? wordStart(cursor) : Math.max(0, cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                allSelected = false;
                cursor = Math.min(value.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                allSelected = false;
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                allSelected = false;
                cursor = value.length();
                return true;
            }
            default -> {
            }
        }
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectAll();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        return key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER
                && key != GLFW.GLFW_KEY_TAB && key != GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!host.isFocused(this)) return false;
        insert(String.valueOf(c));
        return true;
    }

    private void insert(String s) {
        StringBuilder clean = new StringBuilder();
        for (char c : s.toCharArray()) if (filter.test(c)) clean.append(c);
        if (clean.length() == 0) return;
        String base = allSelected ? "" : value;
        int at = allSelected ? 0 : cursor;
        String v = base.substring(0, at) + clean + base.substring(at);
        if (v.length() > maxLength) return;
        allSelected = false;
        cursor = at + clean.length();
        value = v;
        onChange.accept(value);
    }

    private void change(String v) {
        value = v;
        cursor = v.length();
        allSelected = false;
        onChange.accept(value);
    }

    private void changeKeepCursor(String v) {
        value = v;
        cursor = Math.min(cursor, v.length());
        allSelected = false;
        onChange.accept(value);
    }

    private int wordStart(int from) {
        int i = from - 1;
        while (i > 0 && value.charAt(i - 1) == ' ') i--;
        while (i > 0 && value.charAt(i - 1) != ' ') i--;
        return Math.max(0, i);
    }
}
