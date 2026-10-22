package dev.bloom.client.gui.page;

import net.minecraft.client.renderer.RenderPipelines;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.io.SafeFiles;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.core.sky.OptiFineSky;
import dev.bloom.client.core.sky.SkyboxCatalog;
import dev.bloom.client.core.sky.SkyboxLibrary;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.FilePicker;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import dev.bloom.client.gui.widget.Button;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;
import dev.bloom.client.render.SkyTextures;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class SkyboxPage extends GenericPage {
    private static boolean choosingNight;
    private static final int MAX_LAYERS_PER_IMPORT = 16;

    public SkyboxPage(BloomMenuScreen screen) {
        super(screen, Category.SKYBOX);
    }

    @Override
    public List<Element> build() {
        List<Element> rows = new ArrayList<>();
        rows.add(new ModeRow());
        rows.add(new Grid(screen));
        rows.add(new Rows.Buttons(List.of(
                new Button("Import image…", "import", Button.Style.PRIMARY, this::pickImage)
                        .tooltip("A 3×2 skybox image (like OptiFine's) or a 360° panorama (2:1), PNG"),
                new Button("OptiFine pack (.zip)…", "import", Button.Style.NORMAL, this::pickPack)
                        .tooltip("Reads optifine/sky/world0 layers from a resource pack"),
                new Button("Pack folder…", "folder", Button.Style.NORMAL, this::pickPackFolder),
                new Button("Open skybox folder", "folder", Button.Style.GHOST, this::openFolder),
                resetPageButton(screen, category))));
        rows.add(new Rows.Info("info", "Tip: drag PNG images or OptiFine resource packs (.zip) onto the game window to import them. "
                + "Imports are copied into MOTION's skybox folder; the originals are never changed.", Theme.palette().textDim()));
        List<Setting> settings = new ArrayList<>();
        for (Setting s : BloomSettings.in(Category.SKYBOX)) {
            if (s != BloomSettings.SKY_DAY && s != BloomSettings.SKY_NIGHT) settings.add(s);
        }
        rows.addAll(Rows.settingRows(settings, screen, false));
        return rows;
    }

    // ------------------------------------------------------------------ import

    private void pickImage() {
        FilePicker.open("Choose a skybox image", home(), "PNG images", new String[]{"*.png"}, r -> r.ifPresent(this::importFile));
    }

    private void pickPack() {
        FilePicker.open("Choose a resource pack", home(), "Resource packs (.zip)", new String[]{"*.zip"}, r -> r.ifPresent(this::importFile));
    }

    private void pickPackFolder() {
        FilePicker.folder("Choose an unzipped resource pack folder", home(), r -> r.ifPresent(this::importFile));
    }

    private static Path home() {
        Path rp = Minecraft.getInstance().getResourcePackDirectory();
        return Files.isDirectory(rp) ? rp : Path.of(System.getProperty("user.home"));
    }

    private void openFolder() {
        Path dir = BloomClient.get().skyboxes().dir();
        try {
            Files.createDirectories(dir);
            dev.bloom.client.game.OsOpen.open(dir);
        } catch (Exception e) {
            screen.message("Couldn't open the folder", dir.toString());
        }
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        for (Path f : files) importFile(f);
    }

    private void importFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        SkyboxLibrary lib = BloomClient.get().skyboxes();
        CompletableFuture.supplyAsync(() -> {
            List<String> report = new ArrayList<>();
            String firstId = null;
            try {
                if (name.endsWith(".png")) {
                    SkyboxLibrary.Imported imp = lib.importPng(SafeFiles.readLimited(file, SkyboxLibrary.MAX_PNG_BYTES), file.getFileName().toString());
                    report.add("Imported \"" + imp.entry().name() + "\".");
                    report.addAll(imp.notes());
                    firstId = imp.entry().id();
                } else if (name.endsWith(".zip") || Files.isDirectory(file)) {
                    try (OptiFineSky.Pack pack = OptiFineSky.open(file)) {
                        OptiFineSky.ScanResult scan = OptiFineSky.scan(pack);
                        int n = 0;
                        for (OptiFineSky.Layer layer : scan.layers()) {
                            if (n >= MAX_LAYERS_PER_IMPORT) {
                                report.add("Only the first " + MAX_LAYERS_PER_IMPORT + " layers were imported.");
                                break;
                            }
                            try {
                                SkyboxLibrary.Imported imp = lib.importOptiFineLayer(pack, layer, file.getFileName().toString());
                                n++;
                                String when = switch (imp.meta().suggested()) {
                                    case "night" -> " (best as a night sky)";
                                    case "day" -> " (best as a day sky)";
                                    default -> "";
                                };
                                report.add("Imported \"" + imp.entry().name() + "\"" + when + ".");
                                for (String note : imp.notes()) report.add("  " + note);
                                if (firstId == null) firstId = imp.entry().id();
                            } catch (Exception e) {
                                report.add(layer.name() + ": " + e.getMessage());
                            }
                        }
                        report.addAll(scan.problems());
                        if (n > 0) report.add("OptiFine weather/biome conditions aren't used; pick these skies as Day or Night above.");
                    }
                } else {
                    report.add(file.getFileName() + " isn't a PNG image or a resource pack.");
                }
            } catch (Exception e) {
                Log.warn("Skybox import failed", e);
                report.add("Couldn't import " + file.getFileName() + ": " + e.getMessage());
            }
            return new Object[]{report, firstId};
        }).thenAccept(res -> Minecraft.getInstance().execute(() -> {
            @SuppressWarnings("unchecked") List<String> report = (List<String>) res[0];
            String first = (String) res[1];
            lib.scan();
            if (first != null) {
                (choosingNight ? BloomSettings.SKY_NIGHT : BloomSettings.SKY_DAY).set(first);
                BloomSettings.SKY_ENABLED.set(true);
            }
            screen.message(first != null ? "Skybox imported" : "Nothing imported", String.join("\n", report));
            screen.requestRebuild();
        }));
    }

    // ------------------------------------------------------------------ rows

    /** "Choosing for: Day / Night". */
    static final class ModeRow extends Element {
        @Override
        public int preferredHeight(int width) {
            return 22;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            Ui.text(g, "Click a sky to use it for", x + 8, y + 7, p.textDim());
            int sx = x + 14 + Ui.font().width("Click a sky to use it for");
            seg(g, sx, "Day", !choosingNight, mx, my, p);
            seg(g, sx + 52, "Night", choosingNight, mx, my, p);
        }

        private void seg(GuiGraphics g, int sx, String label, boolean on, int mx, int my, Theme.Palette p) {
            boolean over = Ui.inside(mx, my, sx, y + 2, 50, 16);
            Ui.smallPanel(g, sx, y + 2, 50, 16, on ? Theme.accentArgb() : over ? p.surfaceActive() : p.surface());
            Ui.textCentered(g, label, sx + 25, y + 6, on ? Theme.onAccent() : p.textDim());
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int sx = x + 14 + Ui.font().width("Click a sky to use it for");
            if (Ui.inside(mx, my, sx, y + 2, 50, 16)) {
                choosingNight = false;
                Ui.click();
                return true;
            }
            if (Ui.inside(mx, my, sx + 52, y + 2, 50, 16)) {
                choosingNight = true;
                Ui.click();
                return true;
            }
            return false;
        }
    }

    /** Cards for every skybox: vanilla, built-ins, imported. */
    static final class Grid extends Element {
        private final BloomMenuScreen screen;
        private List<ChoiceSetting.Option> options;

        Grid(BloomMenuScreen screen) {
            this.screen = screen;
            this.options = SkyboxCatalog.options();
        }

        private int cols(int width) {
            return Math.max(2, width / 100);
        }

        private int cardW(int width) {
            int c = cols(width);
            return (width - (c - 1) * 6) / c;
        }

        private int cardH(int width) {
            return cardW(width) * 72 / 128 + 18;
        }

        @Override
        public int preferredHeight(int width) {
            options = SkyboxCatalog.options();
            int c = cols(width);
            int rows = (options.size() + c - 1) / c;
            return rows * (cardH(width) + 6);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            int c = cols(w), cw = cardW(w), ch = cardH(w), ih = ch - 18;
            for (int i = 0; i < options.size(); i++) {
                ChoiceSetting.Option o = options.get(i);
                int cx = x + (i % c) * (cw + 6), cy = y + (i / c) * (ch + 6);
                boolean over = Ui.inside(mx, my, cx, cy, cw, ch);
                boolean isDay = BloomSettings.SKY_DAY.is(o.id()), isNight = BloomSettings.SKY_NIGHT.is(o.id());
                boolean current = choosingNight ? isNight : isDay;
                Ui.smallPanel(g, cx, cy, cw, ch, over ? p.surfaceHover() : p.surface());
                // picture
                if (SkyboxCatalog.NONE.equals(o.id())) {
                    g.fillGradient(cx + 2, cy + 2, cx + cw - 2, cy + ih, 0xFF78A7FF, 0xFFC0D8FF);
                    Ui.textCentered(g, "Minecraft", cx + cw / 2, cy + ih / 2 - 4, 0xFF20304A);
                } else {
                    Identifier tex = SkyTextures.preview(o.id());
                    if (tex != null) {
                        g.blit(RenderPipelines.GUI_TEXTURED, tex, cx + 2, cy + 2, 0f, 0f, cw - 4, ih - 2, 128, 72, 128, 72);
                    } else {
                        SkyboxCatalog.Entry e = SkyboxCatalog.find(o.id());
                        g.fill(cx + 2, cy + 2, cx + cw - 2, cy + ih, 0xFF000000 | (e != null ? e.previewTint() : 0x777777));
                    }
                }
                if (current) Ui.panelBorder(g, cx, cy, cw, ch, Theme.accentArgb());
                int bx = cx + 4;
                if (isDay) bx += badge(g, bx, cy + 4, "DAY") + 3;
                if (isNight) badge(g, bx, cy + 4, "NIGHT");
                String name = SkyboxCatalog.NONE.equals(o.id()) ? "Vanilla sky" : stripCustom(o.label());
                Ui.text(g, Ui.ellipsize(name, cw - 8), cx + 4, cy + ih + 5, current ? Theme.accentArgb() : p.text());
                if (o.id().startsWith(SkyboxCatalog.CUSTOM_PREFIX) && over) {
                    boolean eo = Ui.inside(mx, my, cx + cw - 30, cy + 4, 12, 12), to = Ui.inside(mx, my, cx + cw - 16, cy + 4, 12, 12);
                    Ui.smallPanel(g, cx + cw - 32, cy + 2, 30, 16, 0xC0000000);
                    Ui.icon(g, "edit", cx + cw - 30, cy + 4, 12, eo ? Theme.accentArgb() : 0xFFFFFFFF);
                    Ui.icon(g, "trash", cx + cw - 16, cy + 4, 12, to ? p.danger() : 0xFFFFFFFF);
                }
            }
        }

        private static String stripCustom(String label) {
            return label.endsWith(" (custom)") ? label.substring(0, label.length() - 9) : label;
        }

        private static int badge(GuiGraphics g, int x, int y, String text) {
            int w = Ui.font().width(text) + 6;
            Ui.smallPanel(g, x, y, w, 10, Theme.accentArgb());
            Ui.text(g, text, x + 3, y + 1, Theme.onAccent());
            return w;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int c = cols(w), cw = cardW(w), ch = cardH(w);
            for (int i = 0; i < options.size(); i++) {
                ChoiceSetting.Option o = options.get(i);
                int cx = x + (i % c) * (cw + 6), cy = y + (i / c) * (ch + 6);
                if (!Ui.inside(mx, my, cx, cy, cw, ch)) continue;
                Ui.click();
                SkyboxLibrary lib = BloomClient.get().skyboxes();
                if (o.id().startsWith(SkyboxCatalog.CUSTOM_PREFIX)) {
                    if (Ui.inside(mx, my, cx + cw - 30, cy + 4, 12, 12)) {
                        screen.prompt("Rename skybox", stripCustom(o.label()), "Rename", name -> {
                            try {
                                lib.rename(o.id(), name);
                            } catch (Exception e) {
                                screen.message("Couldn't rename", e.getMessage());
                            }
                            screen.requestRebuild();
                        });
                        return true;
                    }
                    if (Ui.inside(mx, my, cx + cw - 16, cy + 4, 12, 12)) {
                        screen.confirm("Remove \"" + stripCustom(o.label()) + "\"?",
                                "The skybox is moved to the skyboxes/deleted folder, so you can still get it back.", "Remove", true, () -> {
                                    try {
                                        SkyTextures.invalidate(o.id());
                                        lib.remove(o.id());
                                        BloomSettings.SKY_DAY.revalidate();
                                        BloomSettings.SKY_NIGHT.revalidate();
                                    } catch (Exception e) {
                                        screen.message("Couldn't remove", e.getMessage());
                                    }
                                    screen.requestRebuild();
                                });
                        return true;
                    }
                }
                boolean night = choosingNight || button == 1;
                (night ? BloomSettings.SKY_NIGHT : BloomSettings.SKY_DAY).set(o.id());
                if (!SkyboxCatalog.NONE.equals(o.id())) BloomSettings.SKY_ENABLED.set(true);
                return true;
            }
            return false;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            int c = cols(w), cw = cardW(w), ch = cardH(w);
            for (int i = 0; i < options.size(); i++) {
                int cx = x + (i % c) * (cw + 6), cy = y + (i / c) * (ch + 6);
                if (Ui.inside(mx, my, cx, cy, cw, ch)) {
                    ChoiceSetting.Option o = options.get(i);
                    SkyboxCatalog.Entry e = SkyboxCatalog.find(o.id());
                    String desc = e != null ? e.description() : "Minecraft's own sky (MOTION's other sky options still apply).";
                    return List.of(desc, "Click: use " + (choosingNight ? "at night" : "by day") + " · Right-click: use at night");
                }
            }
            return null;
        }
    }
}
