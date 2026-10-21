package dev.bloom.client.gui;

import com.google.gson.JsonObject;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.hud.Anchor;
import dev.bloom.client.core.hud.HudLayout;
import dev.bloom.client.core.hud.HudLayouts;
import dev.bloom.client.core.hud.HudSnap;
import dev.bloom.client.core.hud.HudWidgetType;
import dev.bloom.client.core.hud.WidgetState;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.core.settings.ColorSetting;
import dev.bloom.client.core.settings.MultiChoiceSetting;
import dev.bloom.client.core.settings.NumberSetting;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.gui.widget.Button;
import dev.bloom.client.gui.widget.ColorPickerPopup;
import dev.bloom.client.gui.widget.Dialog;
import dev.bloom.client.gui.widget.DropdownPopup;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Host;
import dev.bloom.client.gui.widget.Popup;
import dev.bloom.client.gui.widget.Rows;
import dev.bloom.client.gui.widget.ScrollList;
import dev.bloom.client.hud.HudRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * HUD editor: drag widgets (they snap to edges, centre lines and each other — hold Shift to
 * move freely), resize with the corner handle or mouse wheel, and change colours and options
 * in the side panel. Ctrl+Z undoes; "Cancel" restores the layout as it was when you opened it.
 */
public class HudEditorScreen extends Screen implements Host {
    private static final int GRID = 8;
    private final Screen parent;
    private final HudLayouts layouts;
    private final HudRenderer hud;
    private final JsonObject original;
    private final Deque<JsonObject> undo = new ArrayDeque<>();
    private WidgetState selected;
    private WidgetState dragging;
    private boolean resizing;
    private double grabDX, grabDY;
    private float resizeStartScale, resizeStartW;
    private List<HudSnap.Guide> guides = List.of();
    private final List<Popup> popups = new ArrayList<>();
    private Element focused;
    private final ScrollList panel = new ScrollList();
    private final List<Button> toolbar = new ArrayList<>();
    private boolean showGrid = true;
    private boolean snap = true;
    private long lastFrame = System.nanoTime();
    private boolean dirtyPanel;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("BLOOD HUD editor"));
        this.parent = parent;
        this.layouts = BloomClient.get().hudLayouts();
        this.hud = BloomClient.get().hudRenderer();
        this.original = layouts.active().toJson();
    }

    private HudLayout layout() {
        return layouts.active();
    }

    @Override
    protected void init() {
        toolbar.clear();
        toolbar.add(new Button("Layout: " + Ui.ellipsize(layout().name(), 70), "chevron_down", Button.Style.NORMAL, this::pickLayout));
        toolbar.add(new Button("Add widget", "plus", Button.Style.NORMAL, this::addWidget).tooltip("Show a hidden widget"));
        toolbar.add(new Button("Grid", "grid", showGrid ? Button.Style.PRIMARY : Button.Style.NORMAL, () -> {
            showGrid = !showGrid;
            rebuildToolbar();
        }).tooltip("Snap to an " + GRID + "px grid"));
        toolbar.add(new Button("Snap", "move", snap ? Button.Style.PRIMARY : Button.Style.NORMAL, () -> {
            snap = !snap;
            rebuildToolbar();
        }).tooltip("Snap to edges and other widgets (hold Shift to move freely)"));
        toolbar.add(new Button("Reset layout", "reset", Button.Style.GHOST, () -> confirm("Reset layout?",
                "Every widget goes back to its default place, size and colours.", "Reset", true, () -> {
                    pushUndo();
                    layouts.reset(layout().id());
                    select(null);
                })));
        toolbar.add(new Button("Cancel", "close", Button.Style.GHOST, this::cancel).tooltip("Undo everything since you opened the editor"));
        toolbar.add(new Button("Done", "check", Button.Style.PRIMARY, this::onClose));
        int total = 0;
        for (Button b : toolbar) total += b.naturalWidth() + 4;
        int x = Math.max(4, (width - total) / 2);
        for (Button b : toolbar) {
            int bw = b.naturalWidth();
            b.layout(x, 6, bw);
            x += bw + 4;
        }
        rebuildPanel();
    }

    private void rebuildToolbar() {
        init();
    }

    // ------------------------------------------------------------------ side panel

    private void select(WidgetState s) {
        selected = s;
        rebuildPanel();
    }

    private void rebuildPanel() {
        dirtyPanel = false;
        List<Element> rows = new ArrayList<>();
        if (selected == null) {
            rows.add(new Rows.Info("info", "Click a widget to edit it. Drag to move, drag the corner or scroll to resize. "
                    + "Arrows nudge (Shift ×10), Delete hides, Ctrl+Z undoes.", Theme.palette().textDim()));
            List<Setting> style = new ArrayList<>();
            for (Setting s : BloomSettings.in(Category.HUD)) if (s != BloomSettings.HUD_ENABLED) style.add(s);
            rows.addAll(Rows.settingRows(style, this, false));
        } else {
            WidgetState w = selected;
            rows.add(new Rows.Header(w.type.label()));
            NumberSetting scale = new NumberSetting("hudedit.scale", Category.HUD, "Widget", "Size", "Scale of this widget.",
                    1f, WidgetState.MIN_SCALE, WidgetState.MAX_SCALE, 0.05f, NumberSetting.Format.MULTIPLIER, "");
            scale.set(w.scale);
            scale.addListener(s -> {
                w.setScale(scale.value());
                layouts.changed();
            });
            rows.add(new Rows.SettingRow(scale, this, null));
            int textDefault = BloomSettings.HUD_TEXT.rgb();
            ColorSetting text = new ColorSetting("hudedit.text", Category.HUD, "Widget", "Text colour",
                    "Overrides the HUD text colour for this widget.", textDefault);
            text.set(w.textColor >= 0 ? w.textColor : textDefault);
            text.addListener(s -> {
                w.textColor = text.rgb() == textDefault ? -1 : text.rgb();
                layouts.changed();
            });
            rows.add(new Rows.SettingRow(text, this, null));
            int bgDefault = BloomSettings.HUD_BG_COLOR.rgb();
            ColorSetting bg = new ColorSetting("hudedit.bg", Category.HUD, "Widget", "Background colour",
                    "Overrides the HUD background colour for this widget.", bgDefault);
            bg.set(w.backgroundColor >= 0 ? w.backgroundColor : bgDefault);
            bg.addListener(s -> {
                w.backgroundColor = bg.rgb() == bgDefault ? -1 : bg.rgb();
                layouts.changed();
            });
            rows.add(new Rows.SettingRow(bg, this, null));
            float opDefault = BloomSettings.HUD_BG_OPACITY.value();
            NumberSetting op = new NumberSetting("hudedit.opacity", Category.HUD, "Widget", "Background opacity",
                    "Overrides the HUD background opacity for this widget.", opDefault, 0f, 1f, 0.05f, NumberSetting.Format.PERCENT, "");
            op.set(w.backgroundOpacity >= 0 ? w.backgroundOpacity : opDefault);
            op.addListener(s -> {
                w.backgroundOpacity = Math.abs(op.value() - opDefault) < 1e-3 ? -1f : op.value();
                layouts.changed();
            });
            rows.add(new Rows.SettingRow(op, this, null));
            if (!w.type.options().isEmpty()) {
                List<ChoiceSetting.Option> opts = new ArrayList<>();
                Set<String> on = new LinkedHashSet<>();
                for (HudWidgetType.Option o : w.type.options()) {
                    opts.add(new ChoiceSetting.Option(o.id(), o.label()));
                    if (w.option(o.id())) on.add(o.id());
                }
                Set<String> defaults = new LinkedHashSet<>();
                for (HudWidgetType.Option o : w.type.options()) if (o.defaultValue()) defaults.add(o.id());
                MultiChoiceSetting options = new MultiChoiceSetting("hudedit.options", Category.HUD, "Widget", "Show",
                        "What this widget displays.", opts, defaults);
                long mask = 0;
                for (int i = 0; i < opts.size(); i++) if (on.contains(opts.get(i).id())) mask |= 1L << i;
                options.setMask(mask);
                options.addListener(s -> {
                    for (ChoiceSetting.Option o : opts) w.setOption(o.id(), options.contains(o.id()));
                    layouts.changed();
                });
                rows.add(new Rows.SettingRow(options, this, null));
            }
            rows.add(new Rows.Buttons(List.of(
                    new Button("Hide", "eye_off", Button.Style.NORMAL, () -> {
                        pushUndo();
                        w.visible = false;
                        layouts.changed();
                        select(null);
                    }),
                    new Button("Reset widget", "reset", Button.Style.NORMAL, () -> {
                        pushUndo();
                        layouts.resetWidget(layout().id(), w.type);
                        w.visible = true;
                        layouts.changed();
                        dirtyPanel = true;
                    }),
                    new Button("Deselect", "close", Button.Style.GHOST, () -> select(null)))));
        }
        panel.set(rows);
    }

    private int panelW() {
        return Math.min(190, Math.max(150, width / 3));
    }

    private int panelX() {
        // Dock on the side away from the selected widget.
        if (selected != null) {
            HudSnap.Rect r = hud.bounds(selected, true, width, height);
            if (r != null && r.cx() > width / 2f) return 6;
        }
        return width - panelW() - 6;
    }

    // ------------------------------------------------------------------ Host

    @Override
    public void openDropdown(int x, int y, int w, List<ChoiceSetting.Option> options, String sel, Consumer<String> onPick) {
        popups.add(new DropdownPopup(x, y, w, options, sel, onPick));
    }

    @Override
    public void openColorPicker(int x, int y, ColorSetting setting) {
        popups.add(new ColorPickerPopup(this, x, y, setting));
    }

    @Override
    public void focus(Element e) {
        if (focused != null && focused != e) focused.blur();
        focused = e;
    }

    @Override
    public boolean isFocused(Element e) {
        return focused == e;
    }

    @Override
    public void rebuild() {
        dirtyPanel = true;
    }

    @Override
    public void confirm(String title, String message, String label, boolean danger, Runnable onConfirm) {
        popups.add(Dialog.confirm(this, title, message, label, danger, onConfirm));
    }

    @Override
    public void prompt(String title, String initial, String label, Consumer<String> onConfirm) {
        popups.add(Dialog.prompt(this, title, initial, label, onConfirm));
    }

    @Override
    public void message(String title, String message) {
        popups.add(Dialog.message(this, title, message));
    }

    // ------------------------------------------------------------------ actions

    private void pushUndo() {
        undo.push(layout().toJson());
        while (undo.size() > 40) undo.removeLast();
    }

    private void undoLast() {
        if (undo.isEmpty()) return;
        JsonObject prev = undo.pop();
        layout().loadWidgets(prev);
        layouts.changed();
        select(null);
    }

    private void cancel() {
        layout().loadWidgets(original);
        layouts.changed();
        close();
    }

    private void pickLayout() {
        List<ChoiceSetting.Option> opts = new ArrayList<>();
        for (HudLayout l : layouts.all()) opts.add(new ChoiceSetting.Option(l.id(), l.name()));
        Button b = toolbar.get(0);
        openDropdown(b.x, b.y + b.h + 2, 140, opts, layout().id(), id -> {
            layouts.setActive(id);
            undo.clear();
            select(null);
            init();
        });
    }

    private void addWidget() {
        List<ChoiceSetting.Option> hidden = new ArrayList<>();
        for (WidgetState w : layout().widgets()) if (!w.visible) hidden.add(new ChoiceSetting.Option(w.type.id(), w.type.label()));
        if (hidden.isEmpty()) {
            message("All widgets are shown", "Every widget is already on screen. Select one and press Hide to remove it.");
            return;
        }
        Button b = toolbar.get(1);
        openDropdown(b.x, b.y + b.h + 2, 140, hidden, "", id -> {
            HudWidgetType t = HudWidgetType.byId(id);
            if (t == null) return;
            pushUndo();
            WidgetState w = layout().get(t);
            w.visible = true;
            layouts.changed();
            select(w);
        });
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (dirtyPanel) rebuildPanel();
        popups.removeIf(p -> p.closed);
        Theme.Palette p = Theme.palette();
        if (minecraft != null && minecraft.level == null) g.fill(0, 0, width, height, 0xFF202830);
        g.fill(0, 0, width, height, 0x30000000);
        if (showGrid) {
            for (int x = 0; x < width; x += GRID) g.fill(x, 0, x + 1, height, 0x14FFFFFF);
            for (int y = 0; y < height; y += GRID) g.fill(0, y, width, y + 1, 0x14FFFFFF);
        }
        g.fill(width / 2, 0, width / 2 + 1, height, 0x30FFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, 0x30FFFFFF);

        boolean popupOpen = !popups.isEmpty();
        int mx = popupOpen ? -10000 : mouseX, my = popupOpen ? -10000 : mouseY;
        WidgetState hover = null;
        for (WidgetState w : layout().widgets()) {
            if (!w.visible) continue;
            HudSnap.Rect r = hud.drawWidget(g, w, true, partialTick, width, height);
            if (r == null) {
                // Hidden content (e.g. nothing to show): still give it a box to grab.
                r = hud.bounds(w, true, width, height);
                if (r == null) continue;
            }
            boolean over = Ui.inside(mx, my, (int) r.x(), (int) r.y(), (int) r.w(), (int) r.h()) && !overPanel(mx, my);
            if (over && dragging == null) hover = w;
            int col = w == selected ? Theme.accentArgb() : over ? 0xAAFFFFFF : 0x40FFFFFF;
            outline(g, r, col);
            if (w == selected) {
                int hx = (int) (r.x() + r.w()) - 3, hy = (int) (r.y() + r.h()) - 3;
                g.fill(hx, hy, hx + 6, hy + 6, Theme.accentArgb());
                String label = w.type.label() + "  " + Math.round(w.scale * 100) + "%";
                int ly = r.y() > 14 ? (int) r.y() - 11 : (int) (r.y() + r.h()) + 3;
                Ui.text(g, label, (int) r.x(), ly, Theme.accentArgb());
            }
        }
        for (HudSnap.Guide guide : guides) {
            if (guide.vertical()) g.fill(Math.round(guide.pos()), 0, Math.round(guide.pos()) + 1, height, Theme.accentArgb());
            else g.fill(0, Math.round(guide.pos()), width, Math.round(guide.pos()) + 1, Theme.accentArgb());
        }

        // Toolbar
        int tx0 = toolbar.get(0).x - 4, tx1 = toolbar.get(toolbar.size() - 1).x + toolbar.get(toolbar.size() - 1).w + 4;
        Ui.panel(g, tx0, 2, tx1 - tx0, 26, p.window());
        for (Button b : toolbar) b.render(g, mx, my, dt);
        // Side panel
        int pw = panelW(), px = panelX(), py = 34, ph = Math.min(height - 40, 260);
        Ui.shadow(g, px, py, pw, ph, 0.8f);
        Ui.panel(g, px, py, pw, ph, p.window());
        panel.setBounds(px + 2, py + 2, pw - 4, ph - 4);
        panel.render(g, mx, my, dt);

        for (Popup pop : popups) {
            if (pop.modal()) g.fill(0, 0, width, height, 0x88000000);
            pop.fit(width, height);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            pop.render(g, mouseX, mouseY, dt);
            g.pose().popPose();
        }
        if (popups.isEmpty()) {
            List<String> tip = null;
            for (Button b : toolbar) if (b.isOver(mouseX, mouseY)) tip = b.tooltip(mouseX, mouseY);
            if (tip == null) tip = panel.tooltip(mouseX, mouseY);
            if (tip != null) drawTip(g, tip, mouseX, mouseY);
        }
    }

    private void drawTip(GuiGraphics g, List<String> lines, int mx, int my) {
        int w = 0;
        for (String s : lines) w = Math.max(w, Ui.font().width(s));
        int h = lines.size() * 10 + 6;
        int x = Math.min(width - w - 12, mx + 10), y = Math.min(height - h - 4, my + 12);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Ui.panel(g, x, y, w + 10, h, 0xF0101010);
        for (int i = 0; i < lines.size(); i++) Ui.text(g, lines.get(i), x + 5, y + 4 + i * 10, Theme.palette().text());
        g.pose().popPose();
    }

    private static void outline(GuiGraphics g, HudSnap.Rect r, int col) {
        int x0 = Math.round(r.x()), y0 = Math.round(r.y()), x1 = Math.round(r.x() + r.w()), y1 = Math.round(r.y() + r.h());
        g.fill(x0, y0, x1, y0 + 1, col);
        g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0, x0 + 1, y1, col);
        g.fill(x1 - 1, y0, x1, y1, col);
    }

    private boolean overPanel(double mx, double my) {
        int pw = panelW(), px = panelX();
        if (Ui.inside(mx, my, px, 34, pw, Math.min(height - 40, 260))) return true;
        int tx0 = toolbar.get(0).x - 4, tx1 = toolbar.get(toolbar.size() - 1).x + toolbar.get(toolbar.size() - 1).w + 4;
        return Ui.inside(mx, my, tx0, 2, tx1 - tx0, 26);
    }

    private WidgetState widgetAt(double mx, double my) {
        WidgetState found = null;
        for (WidgetState w : layout().widgets()) {
            if (!w.visible) continue;
            HudSnap.Rect r = hud.bounds(w, true, width, height);
            if (r != null && Ui.inside(mx, my, (int) r.x(), (int) r.y(), (int) r.w() + 1, (int) r.h() + 1)) found = w;
        }
        return found;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!popups.isEmpty()) {
            Popup top = popups.get(popups.size() - 1);
            if (top.mouseClicked(mx, my, button) || top.modal()) return true;
        }
        focus(null);
        for (Button b : toolbar) if (b.mouseClicked(mx, my, button)) return true;
        if (panel.mouseClicked(mx, my, button)) return true;
        if (overPanel(mx, my)) return true;
        if (selected != null) {
            HudSnap.Rect r = hud.bounds(selected, true, width, height);
            if (r != null && Ui.inside(mx, my, (int) (r.x() + r.w()) - 5, (int) (r.y() + r.h()) - 5, 10, 10)) {
                pushUndo();
                resizing = true;
                dragging = selected;
                resizeStartScale = selected.scale;
                resizeStartW = r.w();
                grabDX = mx - r.x();
                return true;
            }
        }
        WidgetState w = widgetAt(mx, my);
        if (w != null) {
            if (w != selected) select(w);
            if (button == 0) {
                pushUndo();
                HudSnap.Rect r = hud.bounds(w, true, width, height);
                dragging = w;
                resizing = false;
                grabDX = mx - r.x();
                grabDY = my - r.y();
            }
            return true;
        }
        select(null);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!popups.isEmpty()) return popups.get(popups.size() - 1).mouseDragged(mx, my, button, dx, dy);
        if (dragging == null) return panel.mouseDragged(mx, my, button, dx, dy);
        HudSnap.Rect r = hud.bounds(dragging, true, width, height);
        if (r == null) return true;
        if (resizing) {
            float newW = (float) Math.max(10, mx - r.x());
            dragging.setScale(resizeStartScale * newW / Math.max(1f, resizeStartW));
            HudSnap.Rect nr = hud.bounds(dragging, true, width, height);
            if (nr != null) place(dragging, r.x(), r.y(), nr.w(), nr.h());
        } else {
            float nx = (float) (mx - grabDX), ny = (float) (my - grabDY);
            HudSnap.Rect proposed = new HudSnap.Rect(nx, ny, r.w(), r.h());
            if (snap && !hasShiftDown()) {
                List<HudSnap.Rect> others = new ArrayList<>();
                for (WidgetState o : layout().widgets()) {
                    if (o == dragging || !o.visible) continue;
                    HudSnap.Rect orr = hud.bounds(o, true, width, height);
                    if (orr != null) others.add(orr);
                }
                HudSnap.Result res = HudSnap.snap(proposed, others, width, height, showGrid ? GRID : 0);
                guides = res.guides();
                place(dragging, res.x(), res.y(), r.w(), r.h());
            } else {
                guides = List.of();
                place(dragging, Math.max(0, Math.min(width - r.w(), nx)), Math.max(0, Math.min(height - r.h(), ny)), r.w(), r.h());
            }
        }
        layouts.changed();
        return true;
    }

    /** Stores a screen position as anchor + offset so it survives window resizes. */
    private void place(WidgetState w, float left, float top, float bw, float bh) {
        Anchor a = Anchor.nearest(left + bw / 2, top + bh / 2, width, height);
        w.anchor = a;
        w.setOffset(a.offsetX(left, bw, width), a.offsetY(top, bh, height));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!popups.isEmpty()) return popups.get(popups.size() - 1).mouseReleased(mx, my, button);
        if (dragging != null) {
            dragging = null;
            resizing = false;
            guides = List.of();
            dirtyPanel = true;
            return true;
        }
        return panel.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!popups.isEmpty()) return popups.get(popups.size() - 1).mouseScrolled(mx, my, sy);
        if (panel.mouseScrolled(mx, my, sy)) return true;
        WidgetState w = widgetAt(mx, my);
        if (w != null) {
            if (w != selected) select(w);
            pushUndo();
            w.setScale(w.scale + (float) Math.signum(sy) * 0.05f);
            layouts.changed();
            dirtyPanel = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!popups.isEmpty()) {
            popups.get(popups.size() - 1).keyPressed(key, scan, mods);
            return true;
        }
        if (focused != null && focused.keyPressed(key, scan, mods)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Z) {
            undoLast();
            return true;
        }
        if (selected != null) {
            int step = hasShiftDown() ? 10 : 1;
            int dx = key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0;
            int dy = key == GLFW.GLFW_KEY_UP ? -step : key == GLFW.GLFW_KEY_DOWN ? step : 0;
            if (dx != 0 || dy != 0) {
                pushUndo();
                selected.setOffset(selected.x + dx, selected.y + dy);
                layouts.changed();
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                pushUndo();
                selected.visible = false;
                layouts.changed();
                select(null);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!popups.isEmpty()) return popups.get(popups.size() - 1).charTyped(c, mods);
        return focused != null && focused.charTyped(c, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        close();
    }

    private void close() {
        BloomClient.get().saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
