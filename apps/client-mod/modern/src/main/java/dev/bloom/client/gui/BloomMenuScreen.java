package dev.bloom.client.gui;

import dev.bloom.client.Mc;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.Minecraft;
import dev.bloom.client.BloomClient;
import dev.bloom.client.BloomKeys;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.BoolSetting;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.core.settings.ColorSetting;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.gui.page.Page;
import dev.bloom.client.gui.page.Pages;
import dev.bloom.client.gui.widget.ColorPickerPopup;
import dev.bloom.client.gui.widget.Dialog;
import dev.bloom.client.gui.widget.DropdownPopup;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Host;
import dev.bloom.client.gui.widget.Popup;
import dev.bloom.client.gui.widget.Rows;
import dev.bloom.client.gui.widget.ScrollList;
import dev.bloom.client.gui.widget.TextField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * MOTION's menus. A sidebar of sections, a searchable, scrolling page of settings and popups for
 * dropdowns, colours and dialogs. Every change applies immediately and is saved automatically;
 * the game keeps running behind the menu (unless "Pause singleplayer" is on).
 *
 * <p>Two variants share this screen:
 * <ul>
 *   <li>the full <b>Right Shift</b> menu: one section per {@link Category};</li>
 *   <li>the compact <b>Right Ctrl</b> visuals menu ({@link #visuals}): Visuals, World, Performance and
 *       General, docked to the side of the screen so changes can be watched live in the world.</li>
 * </ul>
 */
public class BloomMenuScreen extends Screen implements Host {
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/effects/atlas.png");
    private static Category lastCategory = Category.GENERAL;
    private static int lastVisualsIndex;

    /** One sidebar entry. {@code category} is null for sections that aren't a settings category. */
    public record Nav(String label, String icon, Function<BloomMenuScreen, Page> page, Category category) {
    }

    private final Screen parent;
    private final boolean visualsMenu;
    private boolean overview;
    private final List<Nav> nav;
    private int navIndex;
    private final ScrollList list = new ScrollList();
    private final List<ScrollList> overviewColumns = new ArrayList<>();
    private final List<int[]> overviewBounds = new ArrayList<>();
    private TextField search;
    private final List<Popup> popups = new ArrayList<>();
    private Element focused;
    private Page page;
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();
    private boolean needsRebuild;
    private int wx, wy, ww, wh, sidebar;
    private final float[][] petals = new float[26][6];
    private boolean closing;
    private long closeStart;
    private final float[] navHover;
    private String lastQuery = "";
    private String layoutPos;

    public BloomMenuScreen(Screen parent) {
        this(parent, false, fullNav(), lastCategory.ordinal(), true);
    }

    public BloomMenuScreen(Screen parent, Category start) {
        this(parent, false, fullNav(), start.ordinal(), false);
    }

    /** The compact Right Ctrl visuals menu. */
    public static BloomMenuScreen visuals(Screen parent) {
        List<Nav> nav = dev.bloom.client.gui.page.VisualsPages.nav();
        return new BloomMenuScreen(parent, true, nav, Math.min(lastVisualsIndex, nav.size() - 1), false);
    }

    private static List<Nav> fullNav() {
        List<Nav> out = new ArrayList<>();
        for (Category c : Category.values()) out.add(new Nav(c.label(), c.icon(), s -> Pages.create(c, s), c));
        return out;
    }

    private BloomMenuScreen(Screen parent, boolean visualsMenu, List<Nav> nav, int start, boolean overview) {
        super(Component.literal(visualsMenu ? "MOTION Visuals" : "MOTION"));
        this.parent = parent;
        this.visualsMenu = visualsMenu;
        this.overview = overview;
        this.nav = nav;
        this.navIndex = Math.max(0, Math.min(start, nav.size() - 1));
        this.navHover = new float[nav.size()];
        Random r = new Random();
        for (float[] p : petals) {
            p[0] = r.nextFloat();
            p[1] = r.nextFloat();
            p[2] = 0.3f + r.nextFloat() * 0.7f; // speed
            p[3] = r.nextFloat() * 6.28f; // phase
            p[4] = 5 + r.nextFloat() * 6; // size
            p[5] = r.nextInt(4); // shape
        }
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        if (visualsMenu) {
            layoutPos = BloomSettings.VISUALS_POSITION.id();
            // Compact and docked to one side, so most of the world stays visible for live previews.
            ww = Math.min(width - 16, 400);
            wh = Math.min(height - 16, 360);
            String pos = BloomSettings.VISUALS_POSITION.id();
            wx = "right".equals(pos) ? width - ww - 8 : "center".equals(pos) ? (width - ww) / 2 : 8;
            wy = (height - wh) / 2;
            sidebar = ww >= 340 ? 100 : 30;
        } else {
            ww = Math.min(width - 20, 1200);
            wh = Math.min(height - 20, 720);
            wx = (width - ww) / 2;
            wy = (height - wh) / 2;
            sidebar = overview ? 0 : ww >= 400 ? 116 : 30;
        }
        if (search == null) {
            search = new TextField(this, "Search settings", 40).icon("search").onChange(s -> needsRebuild = true);
        }
        BloomClient.get().skyboxes().scan();
        rebuild();
    }

    private int contentX() {
        return wx + sidebar;
    }

    @Override
    public void rebuild() {
        needsRebuild = false;
        String q = search == null ? "" : search.value().trim();
        if (overview && q.isEmpty()) {
            page = null;
            list.set(List.of());
            rebuildOverview();
            lastQuery = q;
            return;
        }
        List<Element> rows = new ArrayList<>();
        boolean sameView = q.equals(lastQuery);
        if (!q.isEmpty()) {
            List<Setting> found = BloomSettings.search(q);
            if (found.isEmpty()) {
                rows.add(new Rows.Info("search", "No settings match \"" + q + "\".", Theme.palette().textDim()));
            } else {
                rows.addAll(Rows.settingRows(found, this, true));
            }
            page = null;
        } else {
            page = nav.get(navIndex).page().apply(this);
            rows.addAll(page.build());
        }
        lastQuery = q;
        if (sameView) list.setKeepScroll(rows);
        else list.set(rows);
        layoutList();
    }

    /** Rebuild after the current event finishes (safe to call from inside a row's click). */
    public void requestRebuild() {
        needsRebuild = true;
    }

    private void layoutList() {
        if (overview) list.setBounds(wx + 12, wy + 46, ww - 24, wh - 68);
        else list.setBounds(contentX() + 4, wy + 32, ww - sidebar - 8, wh - 36);
    }

    /** Build the multi-column click-GUI overview. Each column scrolls independently. */
    private void rebuildOverview() {
        overviewColumns.clear();
        overviewBounds.clear();
        int count = Math.max(1, Math.min(4, ww / 250));
        List<List<Category>> groups = new ArrayList<>();
        for (int i = 0; i < count; i++) groups.add(new ArrayList<>());
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) groups.get(i % count).add(categories[i]);

        int gap = 8;
        int columnW = (ww - 24 - gap * (count - 1)) / count;
        int top = wy + 46;
        int columnH = wh - 68;
        for (int i = 0; i < count; i++) {
            List<Element> rows = new ArrayList<>();
            for (Category category : groups.get(i)) {
                rows.add(new OverviewHeader(this, category));
                for (Setting setting : BloomSettings.in(category)) rows.add(new OverviewSetting(this, category, setting));
                rows.add(new Rows.Spacer(5));
            }
            ScrollList column = new ScrollList();
            column.set(rows);
            int x = wx + 12 + i * (columnW + gap);
            column.setBounds(x + 3, top + 3, columnW - 6, columnH - 6);
            overviewColumns.add(column);
            overviewBounds.add(new int[]{x, top, columnW, columnH});
        }
    }

    private void backToOverview() {
        if (visualsMenu || overview) return;
        overview = true;
        search.setValue("");
        lastQuery = "";
        sidebar = 0;
        rebuild();
    }

    public void switchTo(Category c) {
        for (int i = 0; i < nav.size(); i++) {
            if (nav.get(i).category() == c) {
                switchTo(i);
                return;
            }
        }
        // Not a section of this menu (e.g. Profiles from the visuals menu): open the full menu there.
        if (minecraft != null) Mc.setScreen(new BloomMenuScreen(parent, c));
    }

    public void switchTo(int index) {
        boolean wasOverview = overview;
        overview = false;
        sidebar = ww >= 400 ? 116 : 30;
        if (index == navIndex && search.value().isEmpty() && !wasOverview) return;
        navIndex = Math.max(0, Math.min(index, nav.size() - 1));
        if (visualsMenu) lastVisualsIndex = navIndex;
        else if (nav.get(navIndex).category() != null) lastCategory = nav.get(navIndex).category();
        search.setValue("");
        lastQuery = "\u0000";
        rebuild();
    }

    public boolean isVisualsMenu() {
        return visualsMenu;
    }

    // ------------------------------------------------------------------ Host

    @Override
    public void openDropdown(int x, int y, int w, List<ChoiceSetting.Option> options, String selected, Consumer<String> onPick) {
        popups.add(new DropdownPopup(x, y, w, options, selected, onPick));
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
    public void confirm(String title, String message, String confirmLabel, boolean danger, Runnable onConfirm) {
        popups.add(Dialog.confirm(this, title, message, confirmLabel, danger, onConfirm));
    }

    @Override
    public void prompt(String title, String initial, String confirmLabel, Consumer<String> onConfirm) {
        popups.add(Dialog.prompt(this, title, initial == null ? "" : initial, confirmLabel, onConfirm));
    }

    @Override
    public void message(String title, String message) {
        popups.add(Dialog.message(this, title, message));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Drawn in render(): no blur, so the game stays visible and the menu stays cheap.
    }

    private float openProgress() {
        if (closing) return 1f - Anim.easeOutCubic(Anim.progress(closeStart, 140));
        return Anim.easeOutCubic(Anim.progress(openedAt, 220));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        if (visualsMenu && !BloomSettings.VISUALS_POSITION.id().equals(layoutPos)) init(); // "Menu position" changed
        if (needsRebuild) rebuild();
        if (closing && openProgress() <= 0.001f) {
            finishClose();
            return;
        }
        popups.removeIf(p -> p.closed);
        Theme.Palette p = Theme.palette();
        float t = openProgress();

        // Backdrop
        float dim = visualsMenu ? BloomSettings.VISUALS_DIM.value() : BloomSettings.MENU_DIM.value();
        if (minecraft != null && minecraft.level == null) dim = 1f;
        g.fillGradient(0, 0, width, height, Theme.alpha(0xFF0C080B, dim * t), Theme.alpha(0xFF1A0E15, dim * t));
        if (ThemeDecor.active()) ThemeDecor.render(g, 0, 0, width, height, dt, visualsMenu ? t * 0.6f : t);
        else if (Anim.decorations() && !visualsMenu) drawPetals(g, dt, t);

        boolean blockedByModal = !popups.isEmpty() && popups.get(popups.size() - 1).modal();
        boolean overPopup = false;
        for (Popup pop : popups) if (pop.isOver(mouseX, mouseY)) overPopup = true;
        int mx = blockedByModal || overPopup ? -10000 : mouseX, my = blockedByModal || overPopup ? -10000 : mouseY;

        g.pose().pushMatrix();
        if (Anim.motion()) {
            float s = 0.96f + 0.04f * t;
            // The docked visuals menu slides in from its side; the full menu rises from below.
            float slide = visualsMenu && wx < width / 3 ? -(1 - t) * 16f : visualsMenu && wx > width / 3 ? (1 - t) * 16f : 0f;
            g.pose().translate(width / 2f + slide, height / 2f + (visualsMenu ? 0 : (1 - t) * 10f));
            g.pose().scale(s, s);
            g.pose().translate(-width / 2f, -height / 2f);
        }
        int alphaMul = Math.round(255 * t);
        if (overview) {
            int searchW = Math.max(84, Math.min(220, ww / 4));
            search.layout(wx + ww - searchW - 12, wy + 8, searchW);
            search.render(g, mx, my, dt);
            Ui.spriteTinted(g, "logo", wx + 14, wy + 8, 22, 22, 0xFFFFFFFF);
            Ui.text(g, "MOTION", wx + 43, wy + 8, Theme.alpha(p.text(), t));
            Ui.text(g, "CLIENT", wx + 43 + Ui.font().width("MOTION "), wy + 8, Theme.alpha(0xFFE6C35E, t));
            int subtitleX = wx + 44;
            int subtitleW = Math.max(32, search.x - subtitleX - 12);
            Ui.text(g, Ui.ellipsize("CLICK GUI  ·  Click a switch to toggle, a section to open details", subtitleW), subtitleX, wy + 21, Theme.alpha(p.textFaint(), t));
            g.fill(wx + 8, wy + 38, wx + ww - 8, wy + 39, Theme.alpha(p.border(), 0.8f * t));
            if (search.value().isBlank()) {
                for (int i = 0; i < overviewColumns.size(); i++) {
                    int[] b = overviewBounds.get(i);
                    Ui.shadow(g, b[0], b[1], b[2], b[3], t * 0.8f);
                    Ui.panel(g, b[0], b[1], b[2], b[3], Theme.alpha(p.window(), t));
                    Ui.panelBorder(g, b[0], b[1], b[2], b[3], Theme.alpha(p.border(), t));
                    g.fill(b[0] + 7, b[1], b[0] + b[2] - 7, b[1] + 2, Theme.alpha(0xFFE6C35E, t));
                    overviewColumns.get(i).render(g, mx, my, dt);
                }
            } else {
                Ui.shadow(g, wx + 12, wy + 46, ww - 24, wh - 68, t * 0.8f);
                Ui.panel(g, wx + 12, wy + 46, ww - 24, wh - 68, Theme.alpha(p.window(), t));
                list.render(g, mx, my, dt);
            }
            Ui.text(g, "Right Shift / ESC  ·  Scroll each column  ·  Click section headings for full controls", wx + 14, wy + wh - 14, Theme.alpha(p.textFaint(), t));
        } else {
            Ui.shadow(g, wx, wy, ww, wh, t);
            Ui.panel(g, wx, wy, ww, wh, Theme.alpha(p.window(), t));
            int accentEnd = Theme.lerp(p.accent(), 0xFF8AF7FF, 0.32f);
            Ui.gradientBand(g, wx + 1, wy + 1, ww - 2, 2,
                    Theme.alpha(p.accent(), 0.96f * t), Theme.alpha(accentEnd, 0.96f * t), 36);
            // Sidebar
            Ui.panel(g, wx, wy, sidebar, wh, Theme.alpha(p.sidebar(), t));
            g.fill(wx + sidebar - 1, wy + 6, wx + sidebar, wy + wh - 6, Theme.alpha(p.border(), t));
            renderSidebar(g, mx, my, dt, t);
            // Header
            String title = search.value().isBlank() ? nav.get(navIndex).label() : "Search";
            if (!visualsMenu) {
                Ui.pill(g, contentX() + 7, wy + 8, 43, 15, Theme.alpha(p.surfaceActive(), t));
                Ui.text(g, "‹ ALL", contentX() + 12, wy + 11, Theme.alpha(0xFFE6C35E, t));
            }
            Ui.text(g, title, contentX() + (visualsMenu ? 10 : 58), wy + 12, Theme.alpha(p.text(), t));
            int sw = Math.min(visualsMenu ? 110 : 180, ww - sidebar - 120);
            if (visualsMenu) renderShaderStatus(g, contentX() + 16 + Ui.font().width(title), wy + 9, wx + ww - sw - 16, t);
            search.layout(wx + ww - sw - 10, wy + 7, sw);
            search.render(g, mx, my, dt);
            g.fill(contentX() + 6, wy + 30, wx + ww - 6, wy + 31, Theme.alpha(p.border(), 0.6f * t));
            list.render(g, mx, my, dt);
        }
        g.pose().popMatrix();
        if (alphaMul < 8) return;

        // Popups
        for (Popup pop : popups) {
            // Each popup on its own layer, above the menu's text.
            g.nextStratum();
            if (pop.modal()) g.fill(0, 0, width, height, 0x88000000);
            pop.fit(width, height);
            pop.render(g, mouseX, mouseY, dt);
        }
        if (popups.isEmpty()) renderTooltip(g, mouseX, mouseY);
    }

    /** Live indicator in the visuals menu header: what the shader effects are doing right now. */
    private void renderShaderStatus(GuiGraphics g, int x, int y, int maxX, float t) {
        Theme.Palette p = Theme.palette();
        String status = dev.bloom.client.render.PostEffects.status();
        boolean on = !"Off".equals(status);
        String text = Ui.ellipsize(on ? status : "Shaders off", maxX - x - 14);
        int w = Ui.font().width(text) + 14;
        if (x + w > maxX || w < 30) return;
        Ui.pill(g, x, y, w, 13, Theme.alpha(on ? Theme.accentArgb() : p.surface(), (on ? 0.22f : 1f) * t));
        Ui.circle(g, x + 6, y + 6, 2, on ? Theme.accentArgb() : p.textFaint());
        Ui.text(g, text, x + 11, y + 3, Theme.alpha(on ? p.text() : p.textDim(), t));
    }

    private void renderSidebar(GuiGraphics g, int mx, int my, float dt, float t) {
        Theme.Palette p = Theme.palette();
        int accentEnd = Theme.lerp(p.accent(), 0xFF8AF7FF, 0.32f);
        boolean compact = sidebar < 60;
        int y = wy + 8;
        Ui.spriteTinted(g, "logo", wx + (compact ? 7 : 9), y, 16, 16, 0xFFFFFFFF);
        if (!compact) {
            Ui.text(g, "MOTION", wx + 29, y + 4, Theme.alpha(p.text(), t));
            String sub = visualsMenu ? "Visuals" : "Client";
            Ui.text(g, sub, wx + 29 + Ui.font().width("MOTION "), y + 4, Theme.alpha(Theme.accentArgb(), t));
        }
        y += 26;
        int itemH = itemHeight();
        for (int i = 0; i < nav.size(); i++) {
            Nav c = nav.get(i);
            int ix = wx + 4, iw = sidebar - 9;
            boolean sel = i == navIndex && search.value().isBlank();
            boolean over = Ui.inside(mx, my, ix, y, iw, itemH);
            navHover[i] = Anim.approach(navHover[i], sel ? 1f : over ? 0.5f : 0f, 16f, dt);
            if (navHover[i] > 0.01f) {
                Ui.smallPanel(g, ix, y, iw, itemH, Theme.alpha(sel ? p.surfaceActive() : p.surfaceHover(), navHover[i] * t));
                Ui.gradientBand(g, ix + 1, y + 1, iw - 2, itemH - 2,
                        Theme.alpha(p.surfaceActive(), navHover[i] * t),
                        Theme.alpha(p.accent(), navHover[i] * 0.18f * t), 12);
            }
            if (sel) g.fillGradient(ix, y + 3, ix + 2, y + itemH - 3,
                    Theme.alpha(p.accent(), t), Theme.alpha(accentEnd, t));
            int col = sel ? Theme.accentArgb() : over ? p.text() : p.textDim();
            Ui.icon(g, c.icon(), ix + (compact ? 3 : 6), y + (itemH - 14) / 2, 14, Theme.alpha(col, t));
            if (!compact) Ui.text(g, Ui.ellipsize(c.label(), iw - 30), ix + 25, y + (itemH - 8) / 2, Theme.alpha(col, t));
            y += itemH + 1;
        }
        // Active profile chip at the bottom
        if (!compact) {
            var prof = BloomClient.get().profiles().active();
            int by = wy + wh - 22;
            boolean over = Ui.inside(mx, my, wx + 4, by, sidebar - 9, 16);
            Ui.smallPanel(g, wx + 4, by, sidebar - 9, 16, over ? p.surfaceActive() : p.surface());
            Ui.icon(g, "profiles", wx + 8, by + 3, 10, Theme.accentArgb());
            Ui.text(g, Ui.ellipsize(prof.name(), sidebar - 32), wx + 21, by + 4, p.textDim());
        }
    }

    private int itemHeight() {
        return Math.min(visualsMenu ? 22 : 20, (wh - 70) / nav.size());
    }

    private void drawPetals(GuiGraphics g, float dt, float t) {
        int pink = Theme.accentArgb();
        for (float[] p : petals) {
            p[1] += dt * 0.035f * p[2];
            p[0] += dt * 0.012f * (float) Math.sin(p[3] + p[1] * 6);
            if (p[1] > 1.05f) {
                p[1] = -0.05f;
                p[0] = (float) Math.random();
            }
            float px = p[0] * width, py = p[1] * height;
            int s = Math.round(p[4]);
            g.pose().pushMatrix();
            g.pose().translate(px, py);
            g.pose().rotate(p[3] + p[1] * 9f);
            int col = Theme.alpha(0xFF000000 | (pink & 0xFFFFFF), 0.35f * t);
            g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, -s / 2, -s / 2, (int) p[5] * 32f, 0f, s, s, 32, 32, 128, 128, col);
            g.pose().popMatrix();
        }
    }

    private void renderTooltip(GuiGraphics g, int mx, int my) {
        List<String> lines = list.tooltip(mx, my);
        if (overview && search.value().isBlank()) {
            for (ScrollList column : overviewColumns) {
                if (lines == null) lines = column.tooltip(mx, my);
            }
        }
        if (lines == null && search.isOver(mx, my)) lines = List.of("Search every MOTION setting", "Tip: try \"petal\", \"fog\" or \"fps\"");
        if (lines == null || lines.isEmpty()) return;
        Theme.Palette p = Theme.palette();
        int w = 0;
        List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
        for (String s : lines) wrapped.addAll(Ui.wrap(s, 200));
        for (var l : wrapped) w = Math.max(w, Ui.font().width(l));
        int h = wrapped.size() * 10 + 6;
        int x = Math.min(width - w - 12, mx + 10), y = Math.min(height - h - 4, my + 12);
        g.nextStratum();
        g.pose().pushMatrix();
        Ui.panel(g, x, y, w + 10, h, 0xF0101010);
        Ui.panelBorder(g, x, y, w + 10, h, p.border());
        for (int i = 0; i < wrapped.size(); i++) g.drawString(Ui.font(), wrapped.get(i), x + 5, y + 4 + i * 10, p.text(), false);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ input

    private Popup topPopup() {
        return popups.isEmpty() ? null : popups.get(popups.size() - 1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return click(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return release(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return drag(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return key(event, event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (!Character.isBmpCodePoint(cp)) return false;
        return typed((char) cp, event.modifiers());
    }

    private boolean click(double mx, double my, int button) {
        Popup top = topPopup();
        if (top != null) {
            boolean handled = top.mouseClicked(mx, my, button);
            if (handled || top.modal()) return true;
            // a closed non-modal popup lets the click fall through to the page
        }
        if (focused != null && focused.capturesClicks() && focused.mouseClicked(mx, my, button)) return true;
        focus(null);
        if (!overview && !visualsMenu && button == 0 && Ui.inside(mx, my, contentX() + 7, wy + 8, 43, 15)) {
            backToOverview();
            Ui.click();
            return true;
        }
        if (search.mouseClicked(mx, my, button)) return true;
        if (overview) {
            if (search.value().isBlank()) {
                for (ScrollList column : overviewColumns) if (column.mouseClicked(mx, my, button)) return true;
            } else if (list.mouseClicked(mx, my, button)) return true;
            if (!Ui.inside(mx, my, wx, wy, ww, wh) && button == 0) onClose();
            return true;
        }
        // sidebar
        int y = wy + 34;
        int itemH = itemHeight();
        for (int i = 0; i < nav.size(); i++) {
            if (Ui.inside(mx, my, wx + 4, y, sidebar - 9, itemH)) {
                Ui.click();
                switchTo(i);
                return true;
            }
            y += itemH + 1;
        }
        if (sidebar >= 60 && Ui.inside(mx, my, wx + 4, wy + wh - 22, sidebar - 9, 16)) {
            Ui.click();
            // The visuals menu's first section holds the presets.
            if (visualsMenu) switchTo(0);
            else switchTo(Category.PROFILES);
            return true;
        }
        if (list.mouseClicked(mx, my, button)) return true;
        if (!Ui.inside(mx, my, wx, wy, ww, wh) && button == 0) {
            onClose();
            return true;
        }
        return true;
    }

    private boolean release(double mx, double my, int button) {
        Popup top = topPopup();
        if (top != null && top.mouseReleased(mx, my, button)) return true;
        if (overview && search.value().isBlank()) {
            for (ScrollList column : overviewColumns) if (column.mouseReleased(mx, my, button)) return true;
            return false;
        }
        return list.mouseReleased(mx, my, button);
    }

    private boolean drag(double mx, double my, int button, double dx, double dy) {
        Popup top = topPopup();
        if (top != null && top.mouseDragged(mx, my, button, dx, dy)) return true;
        if (overview && search.value().isBlank()) {
            for (ScrollList column : overviewColumns) if (column.mouseDragged(mx, my, button, dx, dy)) return true;
            return false;
        }
        return list.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        Popup top = topPopup();
        if (top != null) {
            if (top.isOver(mx, my)) return top.mouseScrolled(mx, my, scrollY);
            if (top.modal()) return true;
            top.close();
        }
        if (overview && search.value().isBlank()) {
            for (ScrollList column : overviewColumns) if (column.mouseScrolled(mx, my, scrollY)) return true;
            return false;
        }
        return list.mouseScrolled(mx, my, scrollY);
    }

    private boolean key(KeyEvent event, int key, int scan, int mods) {
        Popup top = topPopup();
        if (top != null) {
            top.keyPressed(key, scan, mods);
            return true;
        }
        if (focused != null && focused.keyPressed(key, scan, mods)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (focused != null) {
                focus(null);
                return true;
            }
            onClose();
            return true;
        }
        if ((visualsMenu ? BloomKeys.VISUALS_MENU : BloomKeys.MENU).matches(event)) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && focused == null && !overview) {
            // Tab / Shift+Tab: next / previous section.
            int n = nav.size();
            switchTo((navIndex + (Minecraft.getInstance().hasShiftDown() ? n - 1 : 1)) % n);
            Ui.click();
            return true;
        }
        if (Minecraft.getInstance().hasControlDown() && key == GLFW.GLFW_KEY_F) {
            focus(search);
            search.selectAll();
            return true;
        }
        if (focused == null && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z && !Minecraft.getInstance().hasControlDown()) {
            // Start typing anywhere to search.
            focus(search);
            return false;
        }
        return list.keyPressed(key, scan, mods);
    }

    private boolean typed(char c, int mods) {
        Popup top = topPopup();
        if (top != null) return top.charTyped(c, mods);
        if (focused == search || focused == null) {
            if (focused == null) focus(search);
            return search.charTyped(c, mods);
        }
        return focused.charTyped(c, mods);
    }

    public void onFilesDrop(List<Path> paths) {
        if (page != null) page.onFilesDrop(paths);
        else message("Drop files on a page", "Open the Skyboxes page to import skybox images or OptiFine packs, or Profiles to import a profile.");
    }

    public void tick() {
        if (page != null) page.tick();
    }

    public boolean isPauseScreen() {
        return BloomSettings.PAUSE_SINGLEPLAYER.on();
    }

    @Override
    public void onClose() {
        if (closing) return;
        BloomClient.get().saveNow();
        if (Anim.level() <= 0f) {
            finishClose();
            return;
        }
        closing = true;
        closeStart = System.nanoTime();
    }

    private void finishClose() {
        if (minecraft != null) Mc.setScreen(parent);
    }

    public void removed() {
        dev.bloom.client.gui.page.SkyBrowserPage.onMenuClosed();
        BloomClient.get().saveNow();
    }

    public Screen parentScreen() {
        return parent;
    }

    /** Compact category strip used by the multi-column in-game click GUI. */
    private static final class OverviewHeader extends Element {
        private final BloomMenuScreen screen;
        private final Category category;
        private final int settingCount;

        private OverviewHeader(BloomMenuScreen screen, Category category) {
            this.screen = screen;
            this.category = category;
            this.settingCount = BloomSettings.in(category).size();
        }

        @Override
        public int preferredHeight(int width) {
            return 25;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            boolean over = isOver(mx, my);
            Ui.smallPanel(g, x, y + 2, w, h - 3,
                    over ? Theme.alpha(p.surfaceHover(), 0.95f) : Theme.alpha(p.surface(), 0.78f));
            g.fill(x + 1, y + 6, x + 2, y + h - 5,
                    Theme.alpha(over ? 0xFFFFD878 : 0xFFE6C35E, 0.92f));
            Ui.icon(g, category.icon(), x + 7, y + 6, 13,
                    over ? 0xFFFFD878 : 0xFFE6C35E);
            int countW = Ui.font().width(Integer.toString(settingCount)) + 10;
            Ui.pill(g, x + w - countW - 7, y + 7, countW, 13, Theme.alpha(p.surfaceActive(), 0.95f));
            Ui.text(g, Integer.toString(settingCount), x + w - countW - 2, y + 10, p.textDim());
            Ui.text(g, Ui.ellipsize(category.label(), Math.max(24, w - countW - 43)),
                    x + 25, y + 9, over ? p.text() : Theme.alpha(p.text(), 0.94f));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !isOver(mx, my)) return false;
            Ui.click();
            screen.switchTo(category);
            return true;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            if (!isOver(mx, my)) return null;
            return List.of("Open " + category.label(), settingCount + " settings · full controls and descriptions");
        }
    }

    /** One-line feature row. Switches toggle in-place; other settings open their full category. */
    private static final class OverviewSetting extends Element {
        private final BloomMenuScreen screen;
        private final Category category;
        private final Setting setting;

        private OverviewSetting(BloomMenuScreen screen, Category category, Setting setting) {
            this.screen = screen;
            this.category = category;
            this.setting = setting;
        }

        @Override
        public int preferredHeight(int width) {
            return 19;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            boolean over = isOver(mx, my);
            boolean active = setting.isActive();
            if (over) Ui.smallPanel(g, x, y, w, h - 1, Theme.alpha(p.surfaceHover(), 0.88f));

            String value = setting.displayValue();
            int valueW = Math.min(Math.max(28, Ui.font().width(value) + 12), Math.max(28, w / 2));
            int chipX = x + w - valueW - 2;
            String label = Ui.ellipsize(setting.label(), Math.max(18, chipX - x - 13));
            Ui.text(g, label, x + 8, y + 5,
                    active ? (over ? p.text() : p.textDim()) : p.textFaint());

            if (setting instanceof BoolSetting toggle) {
                int chipColor = toggle.on() && active
                        ? Theme.alpha(0xFFE6C35E, 0.26f)
                        : Theme.alpha(p.surfaceActive(), 0.9f);
                Ui.pill(g, chipX, y + 3, valueW, 13, chipColor);
                if (toggle.on() && active) {
                    Ui.circle(g, chipX + 6, y + 9, 2, 0xFFFFD878);
                    Ui.text(g, "ON", chipX + 12, y + 6, p.text());
                } else {
                    Ui.text(g, active ? "OFF" : "—", chipX + 5, y + 6, p.textFaint());
                }
            } else {
                Ui.pill(g, chipX, y + 3, valueW, 13, Theme.alpha(p.surfaceActive(), 0.88f));
                Ui.text(g, Ui.ellipsize(value, valueW - 8), chipX + 4, y + 6,
                        active ? p.textDim() : p.textFaint());
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !isOver(mx, my)) return false;
            if (setting instanceof BoolSetting toggle && setting.isActive()) {
                toggle.toggle();
                Ui.click();
                return true;
            }
            Ui.click();
            screen.switchTo(category);
            return true;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            if (!isOver(mx, my)) return null;
            String detail = setting.description();
            String unavailable = setting.unavailableReason();
            if (unavailable != null && !unavailable.isBlank()) detail = unavailable;
            if (detail == null || detail.isBlank()) return List.of(setting.label() + " · " + setting.displayValue());
            return List.of(setting.label() + " · " + setting.displayValue(), detail);
        }
    }

}
