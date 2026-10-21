package dev.bloom.client.gui;

import com.mojang.math.Axis;
import dev.bloom.client.BloomClient;
import dev.bloom.client.BloomKeys;
import dev.bloom.client.core.settings.BloomSettings;
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
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The Right Shift menu. A sidebar of categories, a searchable, scrolling page of settings and
 * popups for dropdowns, colours and dialogs. Every change applies immediately and is saved
 * automatically; the game keeps running behind the menu (unless "Pause singleplayer" is on).
 */
public class BloomMenuScreen extends Screen implements Host {
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/effects/atlas.png");
    private static Category lastCategory = Category.GENERAL;

    private final Screen parent;
    private Category category;
    private final ScrollList list = new ScrollList();
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
    private final float[] navHover = new float[Category.values().length];
    private String lastQuery = "";

    public BloomMenuScreen(Screen parent) {
        this(parent, lastCategory);
    }

    public BloomMenuScreen(Screen parent, Category start) {
        super(Component.literal("BLOOD"));
        this.parent = parent;
        this.category = start;
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
        ww = Math.min(width - 16, 480);
        wh = Math.min(height - 16, 320);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        sidebar = ww >= 400 ? 116 : 30;
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
            page = Pages.create(category, this);
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
        list.setBounds(contentX() + 4, wy + 32, ww - sidebar - 8, wh - 36);
    }

    public void switchTo(Category c) {
        if (c == category && search.value().isEmpty()) return;
        category = c;
        lastCategory = c;
        search.setValue("");
        lastQuery = "\u0000";
        rebuild();
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
        if (needsRebuild) rebuild();
        if (closing && openProgress() <= 0.001f) {
            finishClose();
            return;
        }
        popups.removeIf(p -> p.closed);
        Theme.Palette p = Theme.palette();
        float t = openProgress();

        // Backdrop
        float dim = BloomSettings.MENU_DIM.value();
        if (minecraft != null && minecraft.level == null) dim = 1f;
        g.fillGradient(0, 0, width, height, Theme.alpha(0xFF0C080B, dim * t), Theme.alpha(0xFF1A0E15, dim * t));
        if (Anim.decorations()) drawPetals(g, dt, t);

        boolean blockedByModal = !popups.isEmpty() && popups.get(popups.size() - 1).modal();
        boolean overPopup = false;
        for (Popup pop : popups) if (pop.isOver(mouseX, mouseY)) overPopup = true;
        int mx = blockedByModal || overPopup ? -10000 : mouseX, my = blockedByModal || overPopup ? -10000 : mouseY;

        g.pose().pushPose();
        if (Anim.motion()) {
            float s = 0.96f + 0.04f * t;
            g.pose().translate(width / 2f, height / 2f + (1 - t) * 10f, 0);
            g.pose().scale(s, s, 1);
            g.pose().translate(-width / 2f, -height / 2f, 0);
        }
        int alphaMul = Math.round(255 * t);
        Ui.shadow(g, wx, wy, ww, wh, t);
        Ui.panel(g, wx, wy, ww, wh, Theme.alpha(p.window(), t));
        // Sidebar
        Ui.panel(g, wx, wy, sidebar, wh, Theme.alpha(p.sidebar(), t));
        g.fill(wx + sidebar - 1, wy + 6, wx + sidebar, wy + wh - 6, Theme.alpha(p.border(), t));
        renderSidebar(g, mx, my, dt, t);
        // Header
        String title = search.value().isBlank() ? category.label() : "Search";
        Ui.text(g, title, contentX() + 10, wy + 12, Theme.alpha(p.text(), t));
        int sw = Math.min(140, ww - sidebar - 120);
        search.layout(wx + ww - sw - 10, wy + 7, sw);
        search.render(g, mx, my, dt);
        g.fill(contentX() + 6, wy + 30, wx + ww - 6, wy + 31, Theme.alpha(p.border(), 0.6f * t));
        list.render(g, mx, my, dt);
        g.pose().popPose();
        if (alphaMul < 8) return;

        // Popups
        for (Popup pop : popups) {
            if (pop.modal()) g.fill(0, 0, width, height, 0x88000000);
            pop.fit(width, height);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            pop.render(g, mouseX, mouseY, dt);
            g.pose().popPose();
        }
        if (popups.isEmpty()) renderTooltip(g, mouseX, mouseY);
    }

    private void renderSidebar(GuiGraphics g, int mx, int my, float dt, float t) {
        Theme.Palette p = Theme.palette();
        boolean compact = sidebar < 60;
        int y = wy + 8;
        Ui.spriteTinted(g, "logo", wx + (compact ? 7 : 9), y, 16, 16, 0xFFFFFFFF);
        if (!compact) {
            Ui.text(g, "BLOOD", wx + 29, y + 4, Theme.alpha(p.text(), t));
            Ui.text(g, "Client", wx + 29 + Ui.font().width("BLOOD "), y + 4, Theme.alpha(Theme.accentArgb(), t));
        }
        y += 26;
        Category[] cats = Category.values();
        int itemH = Math.min(20, (wh - 70) / cats.length);
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            int ix = wx + 4, iw = sidebar - 9;
            boolean sel = c == category && search.value().isBlank();
            boolean over = Ui.inside(mx, my, ix, y, iw, itemH);
            navHover[i] = Anim.approach(navHover[i], sel ? 1f : over ? 0.5f : 0f, 16f, dt);
            if (navHover[i] > 0.01f) Ui.smallPanel(g, ix, y, iw, itemH, Theme.alpha(sel ? p.surfaceActive() : p.surfaceHover(), navHover[i] * t));
            if (sel) g.fill(ix, y + 3, ix + 2, y + itemH - 3, Theme.accentArgb());
            int col = sel ? Theme.accentArgb() : over ? p.text() : p.textDim();
            Ui.icon(g, c.icon(), ix + (compact ? 3 : 6), y + (itemH - 14) / 2, 14, Theme.alpha(col, t));
            if (!compact) Ui.text(g, c.label(), ix + 25, y + (itemH - 8) / 2, Theme.alpha(col, t));
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
            g.pose().pushPose();
            g.pose().translate(px, py, 0);
            g.pose().mulPose(Axis.ZP.rotation(p[3] + p[1] * 9f));
            g.setColor(((pink >> 16) & 255) / 255f, ((pink >> 8) & 255) / 255f, (pink & 255) / 255f, 0.35f * t);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(ATLAS, -s / 2, -s / 2, s, s, (int) p[5] * 32f, 0f, 32, 32, 128, 128);
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }
    }

    private void renderTooltip(GuiGraphics g, int mx, int my) {
        List<String> lines = list.tooltip(mx, my);
        if (lines == null && search.isOver(mx, my)) lines = List.of("Search every BLOOD setting", "Tip: try \"petal\", \"fog\" or \"fps\"");
        if (lines == null || lines.isEmpty()) return;
        Theme.Palette p = Theme.palette();
        int w = 0;
        List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
        for (String s : lines) wrapped.addAll(Ui.wrap(s, 200));
        for (var l : wrapped) w = Math.max(w, Ui.font().width(l));
        int h = wrapped.size() * 10 + 6;
        int x = Math.min(width - w - 12, mx + 10), y = Math.min(height - h - 4, my + 12);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Ui.panel(g, x, y, w + 10, h, 0xF0101010);
        Ui.panelBorder(g, x, y, w + 10, h, p.border());
        for (int i = 0; i < wrapped.size(); i++) g.drawString(Ui.font(), wrapped.get(i), x + 5, y + 4 + i * 10, p.text(), false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ input

    private Popup topPopup() {
        return popups.isEmpty() ? null : popups.get(popups.size() - 1);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Popup top = topPopup();
        if (top != null) {
            boolean handled = top.mouseClicked(mx, my, button);
            if (handled || top.modal()) return true;
            // a closed non-modal popup lets the click fall through to the page
        }
        if (focused != null && focused.capturesClicks() && focused.mouseClicked(mx, my, button)) return true;
        focus(null);
        if (search.mouseClicked(mx, my, button)) return true;
        // sidebar
        Category[] cats = Category.values();
        int y = wy + 34;
        int itemH = Math.min(20, (wh - 70) / cats.length);
        for (Category c : cats) {
            if (Ui.inside(mx, my, wx + 4, y, sidebar - 9, itemH)) {
                Ui.click();
                switchTo(c);
                return true;
            }
            y += itemH + 1;
        }
        if (sidebar >= 60 && Ui.inside(mx, my, wx + 4, wy + wh - 22, sidebar - 9, 16)) {
            Ui.click();
            switchTo(Category.PROFILES);
            return true;
        }
        if (list.mouseClicked(mx, my, button)) return true;
        if (!Ui.inside(mx, my, wx, wy, ww, wh) && button == 0) {
            onClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        Popup top = topPopup();
        if (top != null && top.mouseReleased(mx, my, button)) return true;
        return list.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        Popup top = topPopup();
        if (top != null && top.mouseDragged(mx, my, button, dx, dy)) return true;
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
        return list.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
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
        if (BloomKeys.MENU.matches(key, scan)) {
            onClose();
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_F) {
            focus(search);
            search.selectAll();
            return true;
        }
        if (focused == null && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z && !hasControlDown()) {
            // Start typing anywhere to search.
            focus(search);
            return false;
        }
        return list.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        Popup top = topPopup();
        if (top != null) return top.charTyped(c, mods);
        if (focused == search || focused == null) {
            if (focused == null) focus(search);
            return search.charTyped(c, mods);
        }
        return focused.charTyped(c, mods);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (page != null) page.onFilesDrop(paths);
        else message("Drop files on a page", "Open the Skyboxes page to import skybox images or OptiFine packs, or Profiles to import a profile.");
    }

    @Override
    public void tick() {
        if (page != null) page.tick();
    }

    @Override
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
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        BloomClient.get().saveNow();
    }

    public Screen parentScreen() {
        return parent;
    }
}
