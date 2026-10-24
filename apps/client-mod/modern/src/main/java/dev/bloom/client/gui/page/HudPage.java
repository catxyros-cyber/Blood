package dev.bloom.client.gui.page;

import dev.bloom.client.Mc;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.hud.HudLayout;
import dev.bloom.client.core.hud.HudLayouts;
import dev.bloom.client.core.hud.HudWidgetType;
import dev.bloom.client.core.hud.WidgetState;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.HudEditorScreen;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import dev.bloom.client.gui.widget.Button;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class HudPage extends GenericPage {
    public HudPage(BloomMenuScreen screen) {
        super(screen, Category.HUD);
    }

    @Override
    public List<Element> build() {
        HudLayouts layouts = BloomClient.get().hudLayouts();
        List<Element> rows = new ArrayList<>();
        rows.add(new Rows.Buttons(List.of(
                new Button("Open HUD editor", "move", Button.Style.PRIMARY, () -> Mc.setScreen(new HudEditorScreen(screen)))
                        .tooltip("Drag widgets, resize with the corner or mouse wheel, snap to guides"),
                resetPageButton(screen, category))));
        rows.add(new Rows.Header("Layout"));
        rows.add(new LayoutRow(screen, layouts));
        rows.add(new Rows.Header("Widgets"));
        HudLayout active = layouts.active();
        for (HudWidgetType t : HudWidgetType.values()) rows.add(new WidgetRow(screen, layouts, active.get(t)));
        rows.addAll(Pages.settings(category, screen));
        return rows;
    }

    /** Pick, create, rename, duplicate, reset or delete layouts. */
    static final class LayoutRow extends Element {
        private final BloomMenuScreen screen;
        private final HudLayouts layouts;
        private final Rows.Buttons buttons;

        LayoutRow(BloomMenuScreen screen, HudLayouts layouts) {
            this.screen = screen;
            this.layouts = layouts;
            HudLayout cur = layouts.active();
            buttons = new Rows.Buttons(List.of(
                    new Button("New", "plus", Button.Style.NORMAL, () -> screen.prompt("New layout", "My layout", "Create", name -> {
                        HudLayout l = layouts.create(name, null);
                        if (l == null) screen.message("Too many layouts", "Delete a layout first (limit " + HudLayouts.MAX_LAYOUTS + ").");
                        else layouts.setActive(l.id());
                        screen.requestRebuild();
                    })),
                    new Button("Duplicate", "copy", Button.Style.NORMAL, () -> {
                        HudLayout l = layouts.create(cur.name() + " copy", cur);
                        if (l != null) layouts.setActive(l.id());
                        screen.requestRebuild();
                    }),
                    new Button("Rename", "edit", Button.Style.NORMAL, () -> screen.prompt("Rename layout", cur.name(), "Rename", name -> {
                        layouts.rename(cur.id(), name);
                        screen.requestRebuild();
                    })),
                    new Button("Reset layout", "reset", Button.Style.NORMAL, () -> screen.confirm("Reset \"" + cur.name() + "\"?",
                            "All widgets in this layout return to their default positions, sizes and colours.", "Reset", true, () -> {
                                layouts.reset(cur.id());
                                screen.requestRebuild();
                            })),
                    new Button("Delete", "trash", Button.Style.DANGER, () -> {
                        if (layouts.all().size() <= 1) {
                            screen.message("Can't delete", "You need at least one layout.");
                            return;
                        }
                        screen.confirm("Delete \"" + cur.name() + "\"?", "This layout will be removed. Your other layouts are not affected.", "Delete", true, () -> {
                            layouts.delete(cur.id());
                            screen.requestRebuild();
                        });
                    })));
        }

        @Override
        public int preferredHeight(int width) {
            return 24 + buttons.preferredHeight(width - 16);
        }

        @Override
        public int layout(int x, int y, int width) {
            int h = super.layout(x, y, width);
            buttons.layout(x + 8, y + 22, width - 16);
            return h;
        }

        private int ddX() {
            return x + 8 + Ui.font().width("Active layout") + 10;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            Ui.text(g, "Active layout", x + 8, y + 7, p.text());
            int dx = ddX();
            boolean over = Ui.inside(mx, my, dx, y + 2, 130, 16);
            Ui.smallPanel(g, dx, y + 2, 130, 16, over ? p.surfaceActive() : p.surface());
            Ui.text(g, Ui.ellipsize(layouts.active().name(), 110), dx + 6, y + 6, p.text());
            Ui.icon(g, "chevron_down", dx + 116, y + 5, 10, p.textDim());
            buttons.render(g, mx, my, dt);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int dx = ddX();
            if (Ui.inside(mx, my, dx, y + 2, 130, 16)) {
                Ui.click();
                List<ChoiceSetting.Option> opts = new ArrayList<>();
                for (HudLayout l : layouts.all()) opts.add(new ChoiceSetting.Option(l.id(), l.name()));
                screen.openDropdown(dx, y + 20, 130, opts, layouts.active().id(), id -> {
                    layouts.setActive(id);
                    screen.requestRebuild();
                });
                return true;
            }
            return buttons.mouseClicked(mx, my, button);
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            return buttons.tooltip(mx, my);
        }
    }

    /** Visibility, per-widget options and reset for one widget. */
    static final class WidgetRow extends Element {
        private final BloomMenuScreen screen;
        private final HudLayouts layouts;
        private final WidgetState state;

        WidgetRow(BloomMenuScreen screen, HudLayouts layouts, WidgetState state) {
            this.screen = screen;
            this.layouts = layouts;
            this.state = state;
        }

        private int chipsHeight(int width) {
            int cx = 0, rows = 1;
            for (HudWidgetType.Option o : state.type.options()) {
                int cw = Ui.font().width(o.label()) + 14;
                if (cx > 0 && cx + cw > width) {
                    rows++;
                    cx = 0;
                }
                cx += cw + 4;
            }
            return rows * 18;
        }

        @Override
        public int preferredHeight(int width) {
            return 30 + (state.visible ? chipsHeight(width - 44) : 0);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            boolean broken = BloomClient.get() != null && isBroken();
            Ui.smallPanel(g, x, y, w, h - 2, isOver(mx, my) ? p.surfaceHover() : Theme.alpha(p.surface(), 0.6f));
            boolean eyeOver = Ui.inside(mx, my, x + 6, y + 6, 16, 16);
            Ui.icon(g, state.visible ? "eye" : "eye_off", x + 8, y + 7, 14, state.visible ? (eyeOver ? 0xFFFFFFFF : Theme.accentArgb()) : p.textFaint());
            Ui.text(g, state.type.label(), x + 30, y + 6, state.visible ? p.text() : p.textDim());
            String sub = broken ? "Turned off after repeated errors (restart to retry)." : state.type.description();
            Ui.text(g, Ui.ellipsize(sub, w - 70), x + 30, y + 17, broken ? p.warn() : p.textFaint());
            boolean resetOver = Ui.inside(mx, my, x + w - 20, y + 7, 12, 12);
            Ui.icon(g, "reset", x + w - 20, y + 7, 12, resetOver ? Theme.accentArgb() : Theme.alpha(p.textDim(), 0.5f));
            if (!state.visible) return;
            int cx = x + 30, cy = y + 28;
            for (HudWidgetType.Option o : state.type.options()) {
                int cw = Ui.font().width(o.label()) + 14;
                if (cx > x + 30 && cx + cw > x + w - 14) {
                    cx = x + 30;
                    cy += 18;
                }
                boolean on = state.option(o.id());
                boolean over = Ui.inside(mx, my, cx, cy, cw, 14);
                Ui.pill(g, cx, cy, cw, 14, on ? Theme.accentArgb() : over ? p.surfaceActive() : p.surface());
                Ui.textCentered(g, o.label(), cx + cw / 2, cy + 3, on ? Theme.onAccent() : p.textDim());
                cx += cw + 4;
            }
        }

        private boolean isBroken() {
            return BloomClient.get().hudRenderer().broken(state.type);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!isOver(mx, my) || button != 0) return false;
            if (Ui.inside(mx, my, x + 6, y + 6, 18, 18)) {
                state.visible = !state.visible;
                layouts.changed();
                Ui.click();
                screen.requestRebuild();
                return true;
            }
            if (Ui.inside(mx, my, x + w - 20, y + 7, 12, 12)) {
                layouts.resetWidget(layouts.active().id(), state.type);
                Ui.click();
                screen.requestRebuild();
                return true;
            }
            if (state.visible) {
                int cx = x + 30, cy = y + 28;
                for (HudWidgetType.Option o : state.type.options()) {
                    int cw = Ui.font().width(o.label()) + 14;
                    if (cx > x + 30 && cx + cw > x + w - 14) {
                        cx = x + 30;
                        cy += 18;
                    }
                    if (Ui.inside(mx, my, cx, cy, cw, 14)) {
                        state.setOption(o.id(), !state.option(o.id()));
                        layouts.changed();
                        Ui.click();
                        return true;
                    }
                    cx += cw + 4;
                }
            }
            if (my < y + 26) {
                state.visible = !state.visible;
                layouts.changed();
                Ui.click();
                screen.requestRebuild();
            }
            return true;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            if (Ui.inside(mx, my, x + w - 20, y + 7, 12, 12)) return List.of("Reset this widget's position, size and colours");
            if (Ui.inside(mx, my, x + 6, y + 6, 18, 18)) return List.of(state.visible ? "Hide widget" : "Show widget");
            return null;
        }
    }
}
