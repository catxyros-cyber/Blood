package dev.bloom.client.gui.page;

import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.NativeImage;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.io.SafeFiles;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.sky.SkyLook;
import dev.bloom.client.core.sky.SkyView;
import dev.bloom.client.core.sky.SkyboxCatalog;
import dev.bloom.client.core.sky.SkyboxLibrary;
import dev.bloom.client.core.visual.Themes;
import dev.bloom.client.gui.Anim;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.FilePicker;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import dev.bloom.client.gui.widget.Button;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;
import dev.bloom.client.gui.widget.TextField;
import dev.bloom.client.render.SkyRenderer;
import dev.bloom.client.render.SkyTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The sky browser (Right Ctrl → Skyboxes): a large live preview that is rendered from the very
 * texture the game will draw (same atlas, same mapping, same colour settings), drag to look around,
 * search, category filters, favourites, apply by day / night, "Try in world", restore vanilla,
 * and import / rename / replace / remove for your own skyboxes.
 */
public final class SkyBrowserPage implements Page {
    // Browser state survives page rebuilds while the menu is open.
    static String selected;
    static String filter = "all";
    static String query = "";
    static float yaw = -20f, pitch = 12f;
    static boolean autoRotate = true;

    private final BloomMenuScreen screen;

    SkyBrowserPage(BloomMenuScreen screen) {
        this.screen = screen;
        if (selected == null || SkyboxCatalog.find(selected) == null && !SkyboxCatalog.NONE.equals(selected)) {
            selected = BloomSettings.SKY_ENABLED.on() ? BloomSettings.SKY_NIGHT.id() : SkyboxCatalog.NONE;
        }
    }

    /** Menu closed: end "Try in world" and free the preview texture and cached pixels. */
    public static void onMenuClosed() {
        SkyRenderer.setPreview(null);
        Preview.release();
        searchField = null;
        searchOwner = null;
    }

    @Override
    public List<Element> build() {
        List<Element> rows = new ArrayList<>();
        rows.add(new Preview());
        rows.add(new Rows.Buttons(actions()));
        rows.add(new Filters(screen));
        rows.add(new Grid(screen));
        rows.add(new Rows.Buttons(List.of(
                new Button("Import image…", "import", Button.Style.NORMAL, this::pickImage)
                        .tooltip("A 3×2 skybox, a 360° panorama (2:1) or a cubemap cross (4:3), PNG"),
                new Button("Six faces (folder)…", "folder", Button.Style.NORMAL, this::pickFaces)
                        .tooltip("A folder with px/nx/py/ny/pz/nz.png or right/left/top/bottom/front/back.png"),
                new Button("Six faces (.zip)…", "import", Button.Style.NORMAL, this::pickFacesZip))));
        rows.add(new Rows.Info("info", "The preview is drawn from the same texture and mapping as the game, with your Sky look settings applied. "
                + "Shader effects and fog are added on top in the world: use Try in world to see the final result.", Theme.palette().textDim()));
        return rows;
    }

    // ------------------------------------------------------------------ actions

    private List<Button> actions() {
        List<Button> out = new ArrayList<>();
        boolean none = SkyboxCatalog.NONE.equals(selected);
        out.add(new Button("Use day & night", "check", Button.Style.PRIMARY, () -> apply(true, true)).tooltip("Apply this sky all day"));
        out.add(new Button("By day", "star", Button.Style.NORMAL, () -> apply(true, false)));
        out.add(new Button("At night", "star", Button.Style.NORMAL, () -> apply(false, true)));
        Button tryIt = new Button(SkyRenderer.preview() != null ? "Stop trying" : "Try in world", "eye", Button.Style.NORMAL, () -> {
            SkyRenderer.setPreview(SkyRenderer.preview() != null || none ? null : selected);
            screen.requestRebuild();
        }).tooltip("Shows this sky in the world now, without saving it. Ends when you close the menu.");
        tryIt.enabled = !none;
        out.add(tryIt);
        if (!none) {
            boolean fav = BloomClient.get().favorites().contains(selected);
            out.add(new Button(fav ? "Unfavourite" : "Favourite", "star", Button.Style.GHOST, () -> {
                BloomClient.get().favorites().toggle(selected);
                screen.requestRebuild();
            }));
        }
        out.add(new Button("Restore vanilla", "reset", Button.Style.GHOST, () -> {
            SkyRenderer.setPreview(null);
            BloomSettings.SKY_ENABLED.set(false);
            selected = SkyboxCatalog.NONE;
            screen.requestRebuild();
        }).tooltip("Back to Minecraft's own sky (fades if Skybox switch fade is on)"));
        if (selected != null && selected.startsWith(SkyboxCatalog.CUSTOM_PREFIX)) {
            out.add(new Button("Rename", "edit", Button.Style.GHOST, this::rename));
            out.add(new Button("Replace image…", "import", Button.Style.GHOST, this::pickReplacement));
            out.add(new Button("Remove", "trash", Button.Style.DANGER, this::remove));
        }
        return out;
    }

    private void apply(boolean day, boolean night) {
        SkyRenderer.setPreview(null);
        if (SkyboxCatalog.NONE.equals(selected)) {
            BloomSettings.SKY_ENABLED.set(false);
        } else {
            if (day) BloomSettings.SKY_DAY.set(selected);
            if (night) BloomSettings.SKY_NIGHT.set(selected);
            BloomSettings.SKY_ENABLED.set(true);
        }
        screen.requestRebuild();
    }

    private void rename() {
        SkyboxCatalog.Entry e = SkyboxCatalog.find(selected);
        if (e == null) return;
        screen.prompt("Rename skybox", e.name(), "Rename", name -> {
            try {
                BloomClient.get().skyboxes().rename(selected, name);
            } catch (Exception ex) {
                screen.message("Couldn't rename", String.valueOf(ex.getMessage()));
            }
            screen.requestRebuild();
        });
    }

    private void remove() {
        String id = selected;
        SkyboxCatalog.Entry e = SkyboxCatalog.find(id);
        if (e == null) return;
        screen.confirm("Remove \"" + e.name() + "\"?", "The files move to the skyboxes/deleted folder, so you can still get them back.", "Remove", true, () -> {
            try {
                SkyRenderer.setPreview(null);
                SkyTextures.invalidate(id);
                BloomClient.get().skyboxes().remove(id);
                BloomSettings.SKY_DAY.revalidate();
                BloomSettings.SKY_NIGHT.revalidate();
                selected = BloomSettings.SKY_NIGHT.id();
                Preview.forget(id);
            } catch (Exception ex) {
                screen.message("Couldn't remove", String.valueOf(ex.getMessage()));
            }
            screen.requestRebuild();
        });
    }

    // ------------------------------------------------------------------ import

    private static Path home() {
        Path rp = Minecraft.getInstance().getResourcePackDirectory();
        return Files.isDirectory(rp) ? rp : Path.of(System.getProperty("user.home"));
    }

    private void pickImage() {
        FilePicker.open("Choose a skybox image", home(), "PNG images", new String[]{"*.png"}, r -> r.ifPresent(f ->
                runImport(() -> {
                    SkyboxLibrary.Imported imp = BloomClient.get().skyboxes().importPng(SafeFiles.readLimited(f, SkyboxLibrary.MAX_PNG_BYTES),
                            f.getFileName().toString());
                    return result(imp);
                })));
    }

    private void pickFaces() {
        FilePicker.folder("Choose a folder with six face images", home(), r -> r.ifPresent(dir -> runImport(() -> {
            Map<String, byte[]> files = new LinkedHashMap<>();
            try (Stream<Path> s = Files.list(dir)) {
                for (Path p : s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).limit(24).toList()) {
                    files.put(p.getFileName().toString(), SafeFiles.readLimited(p, SkyboxLibrary.MAX_PNG_BYTES));
                }
            }
            return result(BloomClient.get().skyboxes().importFaces(files, dir.getFileName().toString()));
        })));
    }

    private void pickFacesZip() {
        FilePicker.open("Choose a .zip with six face images", home(), "Zip files", new String[]{"*.zip"}, r -> r.ifPresent(zip -> runImport(() -> {
            Map<String, byte[]> files = new LinkedHashMap<>();
            try (ZipFile z = new ZipFile(zip.toFile())) {
                var en = z.entries();
                while (en.hasMoreElements() && files.size() < 24) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory() || !e.getName().toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                    if (e.getSize() > SkyboxLibrary.MAX_PNG_BYTES) throw new java.io.IOException(e.getName() + " is larger than 32 MB.");
                    try (var in = z.getInputStream(e)) {
                        files.put(e.getName(), SafeFiles.readLimited(in, SkyboxLibrary.MAX_PNG_BYTES));
                    }
                }
            }
            return result(BloomClient.get().skyboxes().importFaces(files, zip.getFileName().toString()));
        })));
    }

    private void pickReplacement() {
        String id = selected;
        FilePicker.open("Choose the new image", home(), "PNG images", new String[]{"*.png"}, r -> r.ifPresent(f -> runImport(() -> {
            List<String> notes = BloomClient.get().skyboxes().replace(id, SafeFiles.readLimited(f, SkyboxLibrary.MAX_PNG_BYTES));
            Minecraft.getInstance().execute(() -> {
                SkyTextures.invalidate(id);
                Preview.forget(id);
            });
            return new Object[]{"Replaced the image." + (notes.isEmpty() ? "" : " " + String.join(" ", notes)), id};
        })));
    }

    private static Object[] result(SkyboxLibrary.Imported imp) {
        String text = "Imported \"" + imp.entry().name() + "\" (" + SkyboxCatalog.resolutionLabel(imp.entry().id()) + ")."
                + (imp.notes().isEmpty() ? "" : "\n" + String.join("\n", imp.notes()));
        return new Object[]{text, imp.entry().id()};
    }

    private interface Job {
        Object[] run() throws Exception;
    }

    /** Runs an import off the render thread; errors become a clear message instead of a crash. */
    private void runImport(Job job) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return job.run();
            } catch (Exception | OutOfMemoryError e) {
                Log.warn("Skybox import failed", e);
                return new Object[]{String.valueOf(e.getMessage()), null};
            }
        }).thenAccept(res -> Minecraft.getInstance().execute(() -> {
            String id = (String) res[1];
            if (id != null) {
                BloomClient.get().skyboxes().scan();
                selected = id;
                filter = "all";
            }
            screen.message(id != null ? "Skybox ready" : "Couldn't import", (String) res[0]);
            screen.requestRebuild();
        }));
    }

    // ------------------------------------------------------------------ preview

    /** The live preview: CPU-rendered from the real atlas, uploaded to one reused texture. */
    static final class Preview extends Element {
        private static final int W = 384, H = 216;
        private static final Identifier TEX = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "dynamic/sky_browser");
        private static DynamicTexture texture;
        private static final Map<String, int[]> SOURCES = new LinkedHashMap<>(4, 0.75f, true);
        private static final Map<String, CompletableFuture<int[]>> LOADING = new LinkedHashMap<>();
        private static final Map<String, String> FAILED = new LinkedHashMap<>();
        private static int[] frame = new int[W * H], previous;
        private static String shown;
        private static long switchedAt;
        private static float lastYaw = Float.NaN, lastPitch, lastLookHash;
        private static long lastRender;
        private boolean dragging;

        static void forget(String id) {
            SOURCES.remove(id);
            FAILED.remove(id);
            if (id.equals(shown)) shown = null;
        }

        /** Frees the preview texture and cached pixels (menu closed). */
        public static void release() {
            if (texture != null) Minecraft.getInstance().getTextureManager().release(TEX);
            texture = null;
            SOURCES.clear();
            LOADING.clear();
            shown = null;
        }

        @Override
        public int preferredHeight(int width) {
            return width * 9 / 16 + 18;
        }

        private int[] source(String id) {
            int[] s = SOURCES.get(id);
            if (s != null) return s;
            if (FAILED.containsKey(id)) return null;
            CompletableFuture<int[]> f = LOADING.get(id);
            if (f == null) {
                try {
                    LOADING.put(id, SkyTextures.previewPixels(id, 512));
                } catch (RuntimeException e) {
                    FAILED.put(id, String.valueOf(e.getMessage()));
                }
                return null;
            }
            if (!f.isDone()) return null;
            LOADING.remove(id);
            try {
                s = f.join();
                SOURCES.put(id, s);
                while (SOURCES.size() > 3) SOURCES.remove(SOURCES.keySet().iterator().next());
                return s;
            } catch (RuntimeException e) {
                FAILED.put(id, "This skybox couldn't be loaded: " + e.getMessage());
                return null;
            }
        }

        private void renderFrame(float dt) {
            String id = selected;
            if (autoRotate && !dragging && Anim.motion()) yaw = (yaw + dt * 6f) % 360f;
            float lookHash = BloomSettings.SKY_BRIGHTNESS.value() * 31 + BloomSettings.SKY_SATURATION.value() * 17
                    + BloomSettings.SKY_TINT.rgb() * 0.000001f + BloomSettings.SKY_TINT_STRENGTH.value() * 13;
            long now = System.nanoTime();
            boolean switching = !id.equals(shown);
            boolean fading = previous != null && now - switchedAt < 300_000_000L;
            boolean changed = switching || fading || yaw != lastYaw || pitch != lastPitch || lookHash != lastLookHash;
            if (!changed || now - lastRender < 33_000_000L) return; // at most ~30 updates per second
            int[] next = new int[W * H];
            if (SkyboxCatalog.NONE.equals(id)) {
                for (int j = 0; j < H; j++) {
                    float t = j / (float) H;
                    int r = (int) (120 + 72 * t), g = (int) (167 + 49 * t);
                    int c = 0xFF000000 | (255 << 16) | (g << 8) | r; // ABGR: a sky-blue gradient (Minecraft's sky)
                    for (int i = 0; i < W; i++) next[j * W + i] = c;
                }
            } else {
                int[] src = source(id);
                if (src == null) return; // still loading (or failed: the label says so)
                int[] atlas = java.util.Arrays.copyOfRange(src, 2, src.length);
                SkyView.render(atlas, src[0], src[1], yaw, pitch, 75f, W, H, next);
                if (!SkyLook.neutral(BloomSettings.SKY_BRIGHTNESS.value(), BloomSettings.SKY_SATURATION.value(), BloomSettings.SKY_TINT_STRENGTH.value())) {
                    for (int k = 0; k < next.length; k++) {
                        next[k] = SkyLook.apply(next[k], true, BloomSettings.SKY_BRIGHTNESS.value(), BloomSettings.SKY_SATURATION.value(),
                                BloomSettings.SKY_TINT.rgb(), BloomSettings.SKY_TINT_STRENGTH.value());
                    }
                }
            }
            if (switching) {
                previous = shown == null ? null : frame.clone();
                switchedAt = now;
                shown = id;
            }
            // Smooth transition: blend from the previous sky over 0.3 s.
            float t = previous == null ? 1f : Math.min(1f, (now - switchedAt) / 300_000_000f);
            if (t < 1f && Anim.motion()) {
                for (int k = 0; k < next.length; k++) next[k] = lerpPx(previous[k], next[k], t);
            } else {
                previous = null;
            }
            frame = next;
            lastYaw = yaw;
            lastPitch = pitch;
            lastLookHash = lookHash;
            lastRender = now;
            upload();
        }

        private static int lerpPx(int a, int b, float t) {
            int out = 0;
            for (int sh = 0; sh < 32; sh += 8) {
                int va = (a >>> sh) & 255, vb = (b >>> sh) & 255;
                out |= (Math.round(va + (vb - va) * t) & 255) << sh;
            }
            return out;
        }

        private void upload() {
            if (texture == null) {
                texture = new DynamicTexture(() -> "MOTION sky browser", new NativeImage(W, H, false));
                Minecraft.getInstance().getTextureManager().register(TEX, texture);
            }
            NativeImage img = texture.getPixels();
            if (img == null) return;
            for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) img.setPixel(i, j, dev.bloom.client.game.McImageCodec.abgrToArgb(frame[j * W + i]) | 0xFF000000);
            texture.upload();
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            int ph = h - 18;
            renderFrame(dt);
            Ui.smallPanel(g, x, y, w, ph, 0xFF000000);
            if (texture != null && shown != null) g.blit(RenderPipelines.GUI_TEXTURED, TEX, x + 1, y + 1, 0f, 0f, w - 2, ph - 2, W, H, W, H);
            String id = selected;
            String failed = FAILED.get(id);
            if (failed != null) Ui.textWrapped(g, failed, x + 8, y + 8, w - 16, p.warn(), 3);
            else if (!SkyboxCatalog.NONE.equals(id) && !SOURCES.containsKey(id)) Ui.text(g, "Loading preview…", x + 8, y + 8, p.textDim());
            // caption
            SkyboxCatalog.Entry e = SkyboxCatalog.find(id);
            String name = e == null ? "Minecraft's sky" : e.name();
            Ui.text(g, Ui.ellipsize(name, w - 150), x + 2, y + ph + 5, p.text());
            String res = e == null ? "vanilla" : SkyboxCatalog.resolutionLabel(id);
            Ui.text(g, res, x + w - Ui.width(res) - 2, y + ph + 5, p.textDim());
            // compatibility with the current theme
            Themes.Info theme = Themes.get(BloomSettings.THEME.id());
            if (id.equals(theme.recommendedSky()) || SkyboxCatalog.NONE.equals(id) && "none".equals(theme.recommendedSky())) {
                String badge = "Matches " + theme.name();
                int bw = Ui.width(badge) + 8;
                Ui.smallPanel(g, x + 4, y + 4, bw, 11, Theme.accentArgb());
                Ui.text(g, badge, x + 8, y + 6, Theme.onAccent());
            }
            String hint = autoRotate ? "Drag to look around · click to stop turning" : "Drag to look around · click to auto-turn";
            if (isOver(mx, my) && my < y + ph) Ui.text(g, hint, x + 6, y + ph - 12, Theme.alpha(0xFFFFFFFF, 0.75f));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!isOver(mx, my) || my > y + h - 18) return false;
            dragging = false;
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (!dragging && !isOver(mx, my)) return false;
            dragging = true;
            yaw = (float) ((yaw + dx * 0.6) % 360);
            pitch = (float) Math.max(-60, Math.min(80, pitch - dy * 0.6));
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (dragging) {
                dragging = false;
                return true;
            }
            if (isOver(mx, my) && my <= y + h - 18) {
                autoRotate = !autoRotate;
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ filters

    static final String[][] FILTERS = {{"all", "All"}, {"favorites", "Favourites"}, {"halloween", "Halloween"}, {"blood", "MOTION"},
            {"cloudy", "Cloudy"}, {"custom", "Yours"}};

    // One search field per menu, kept across page rebuilds so typing keeps its cursor and focus.
    private static TextField searchField;
    private static BloomMenuScreen searchOwner;

    static final class Filters extends Element {
        private final TextField search;

        Filters(BloomMenuScreen screen) {
            if (searchField == null || searchOwner != screen) {
                searchOwner = screen;
                searchField = new TextField(screen, "Search skies", 32).icon("search").onChange(s -> {
                    query = s;
                    screen.requestRebuild();
                });
                searchField.setValue(query);
            }
            search = searchField;
        }

        @Override
        public int preferredHeight(int width) {
            return 38;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            search.layout(x, y, w);
            search.render(g, mx, my, dt);
            int cx = x;
            for (String[] f : FILTERS) {
                int fw = Ui.width(f[1]) + 10;
                boolean on = filter.equals(f[0]);
                boolean over = Ui.inside(mx, my, cx, y + 21, fw, 14);
                Ui.pill(g, cx, y + 21, fw, 14, on ? Theme.accentArgb() : over ? p.surfaceActive() : p.surface());
                Ui.text(g, f[1], cx + 5, y + 24, on ? Theme.onAccent() : p.textDim());
                cx += fw + 4;
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (search.mouseClicked(mx, my, button)) return true;
            int cx = x;
            for (String[] f : FILTERS) {
                int fw = Ui.width(f[1]) + 10;
                if (Ui.inside(mx, my, cx, y + 21, fw, 14)) {
                    filter = f[0];
                    Ui.click();
                    return true;
                }
                cx += fw + 4;
            }
            return false;
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            return search.keyPressed(key, scan, mods);
        }

        @Override
        public boolean charTyped(char c, int mods) {
            return search.charTyped(c, mods);
        }
    }

    // ------------------------------------------------------------------ grid

    static List<SkyboxCatalog.Entry> visible() {
        List<SkyboxCatalog.Entry> out = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (SkyboxCatalog.Entry e : SkyboxCatalog.all()) {
            boolean fav = BloomClient.get().favorites().contains(e.id());
            boolean match = switch (filter) {
                case "favorites" -> fav;
                case "all" -> true;
                default -> filter.equals(SkyboxCatalog.category(e.id()));
            };
            if (!match) continue;
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.description().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(e);
        }
        // Favourites first, otherwise catalogue order.
        out.sort((a, b) -> Boolean.compare(BloomClient.get().favorites().contains(b.id()), BloomClient.get().favorites().contains(a.id())));
        return out;
    }

    static final class Grid extends Element {
        private final BloomMenuScreen screen;
        private List<SkyboxCatalog.Entry> items = List.of();

        Grid(BloomMenuScreen screen) {
            this.screen = screen;
        }

        private int cols(int width) {
            return Math.max(2, width / 92);
        }

        private int cardW(int width) {
            int c = cols(width);
            return (width - (c - 1) * 5) / c;
        }

        private int cardH(int width) {
            return cardW(width) * 72 / 128 + 24;
        }

        @Override
        public int preferredHeight(int width) {
            items = visible();
            int c = cols(width);
            int rows = Math.max(1, (items.size() + c - 1) / c);
            return rows * (cardH(width) + 5);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            if (items.isEmpty()) {
                Ui.text(g, "Nothing matches. Try another filter or search.", x + 4, y + 6, p.textDim());
                return;
            }
            int c = cols(w), cw = cardW(w), ch = cardH(w), ih = ch - 24;
            for (int i = 0; i < items.size(); i++) {
                SkyboxCatalog.Entry e = items.get(i);
                int cx = x + (i % c) * (cw + 5), cy = y + (i / c) * (ch + 5);
                boolean over = Ui.inside(mx, my, cx, cy, cw, ch);
                boolean sel = e.id().equals(selected);
                Ui.smallPanel(g, cx, cy, cw, ch, over ? p.surfaceHover() : p.surface());
                Identifier tex = SkyTextures.preview(e.id());
                if (tex != null) g.blit(RenderPipelines.GUI_TEXTURED, tex, cx + 2, cy + 2, 0f, 0f, cw - 4, ih - 2, 128, 72, 128, 72);
                else g.fill(cx + 2, cy + 2, cx + cw - 2, cy + ih, 0xFF000000 | e.previewTint());
                if (sel) Ui.panelBorder(g, cx, cy, cw, ch, Theme.accentArgb());
                int bx = cx + 4;
                if (BloomSettings.SKY_ENABLED.on() && BloomSettings.SKY_DAY.is(e.id())) bx += badge(g, bx, cy + 4, "DAY") + 3;
                if (BloomSettings.SKY_ENABLED.on() && BloomSettings.SKY_NIGHT.is(e.id())) badge(g, bx, cy + 4, "NIGHT");
                if (BloomClient.get().favorites().contains(e.id())) Ui.icon(g, "star", cx + cw - 15, cy + 3, 12, 0xFFFFD25A);
                Ui.text(g, Ui.ellipsize(e.name(), cw - 6), cx + 3, cy + ih + 4, sel ? Theme.accentArgb() : p.text());
                Ui.text(g, Ui.ellipsize(SkyboxCatalog.resolutionLabel(e.id()), cw - 6), cx + 3, cy + ih + 14, p.textFaint());
            }
        }

        private static int badge(GuiGraphics g, int x, int y, String text) {
            int w = Ui.width(text) + 6;
            Ui.smallPanel(g, x, y, w, 10, Theme.accentArgb());
            Ui.text(g, text, x + 3, y + 1, Theme.onAccent());
            return w;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int c = cols(w), cw = cardW(w), ch = cardH(w);
            for (int i = 0; i < items.size(); i++) {
                int cx = x + (i % c) * (cw + 5), cy = y + (i / c) * (ch + 5);
                if (!Ui.inside(mx, my, cx, cy, cw, ch)) continue;
                Ui.click();
                String id = items.get(i).id();
                if (Ui.inside(mx, my, cx + cw - 16, cy + 2, 14, 14)) BloomClient.get().favorites().toggle(id);
                selected = id;
                if (SkyRenderer.preview() != null) SkyRenderer.setPreview(id); // keep "Try in world" following the selection
                screen.requestRebuild();
                return true;
            }
            return false;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            int c = cols(w), cw = cardW(w), ch = cardH(w);
            for (int i = 0; i < items.size(); i++) {
                int cx = x + (i % c) * (cw + 5), cy = y + (i / c) * (ch + 5);
                if (Ui.inside(mx, my, cx, cy, cw, ch)) {
                    SkyboxCatalog.Entry e = items.get(i);
                    return List.of(e.name(), e.description(), SkyboxCatalog.resolutionLabel(e.id()), "Click to preview · star to favourite");
                }
            }
            return null;
        }
    }
}
