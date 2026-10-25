package dev.bloom.client.gui.page;

import dev.bloom.client.Mc;
import net.minecraft.client.renderer.RenderPipelines;
import dev.bloom.client.BloomClient;
import dev.bloom.client.BloomKeys;
import dev.bloom.client.core.profile.Profile;
import dev.bloom.client.core.profile.ProfileManager;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.core.sky.SkyboxCatalog;
import dev.bloom.client.core.visual.Themes;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import dev.bloom.client.gui.widget.Button;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;
import dev.bloom.client.render.PostEffects;
import dev.bloom.client.render.SkyTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The four sections of the Right Ctrl visuals menu. Every row is a real setting (the same ones the
 * Right Shift menu shows); these pages just gather the visual ones in one compact place.
 */
public final class VisualsPages {
    /** Presets shown as cards, in order (ids of built-in profiles; see BuiltInProfiles). */
    public static final List<String> PRESETS = List.of("blood_moon", "cinematic", "competitive", "performance", "vanilla");

    private VisualsPages() {
    }

    public static List<BloomMenuScreen.Nav> nav() {
        return List.of(
                new BloomMenuScreen.Nav("Visuals", "effects", VisualsPage::new, null),
                new BloomMenuScreen.Nav("Skyboxes", "skybox", SkyBrowserPage::new, null),
                new BloomMenuScreen.Nav("Themes", "color", ThemesPage::new, null),
                new BloomMenuScreen.Nav("World", "world", WorldPage::new, null),
                new BloomMenuScreen.Nav("Performance", "performance", PerfPage::new, null),
                new BloomMenuScreen.Nav("General", "general", GeneralVisualsPage::new, null));
    }

    // ------------------------------------------------------------------ shared helpers

    /** A section header followed by one row per setting (no automatic group headers). */
    static void section(List<Element> rows, BloomMenuScreen screen, Set<Setting> used, String header, Setting... settings) {
        rows.add(new Rows.Header(header));
        for (Setting s : settings) {
            rows.add(new Rows.SettingRow(s, screen, null));
            used.add(s);
        }
    }

    /** "Reset page": puts back the defaults of exactly the settings shown on the page. */
    static Button resetButton(BloomMenuScreen screen, String page, Set<Setting> settings) {
        return new Button("Reset page", "reset", Button.Style.GHOST, () -> screen.confirm("Reset " + page + "?",
                "Every setting on this page goes back to its default. Other pages are not affected.", "Reset", true, () -> {
                    for (Setting s : settings) s.reset();
                    screen.requestRebuild();
                })).tooltip("Reset every setting on this page");
    }

    /** All settings of all four pages (used by "Restore defaults"). */
    static Set<Setting> allVisualSettings() {
        Set<Setting> out = new LinkedHashSet<>();
        for (Setting s : VisualsPage.SETTINGS) out.add(s);
        for (Setting s : WorldPage.SETTINGS) out.add(s);
        for (Setting s : PerfPage.SETTINGS) out.add(s);
        for (Setting s : ThemesPage.SETTINGS) out.add(s);
        out.add(BloomSettings.ENABLED);
        return out;
    }

    /** An information line whose text is recomputed every frame (live status). */
    static final class LiveInfo extends Element {
        private final String icon;
        private final Supplier<String> text;

        LiveInfo(String icon, Supplier<String> text) {
            this.icon = icon;
            this.text = text;
        }

        @Override
        public int preferredHeight(int width) {
            return Math.max(16, Ui.wrappedHeight(text.get(), width - 30, 3) + 8);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            int c = Theme.accentArgb();
            Ui.smallPanel(g, x, y, w, h - 2, Theme.alpha(c, 0.12f));
            Ui.icon(g, icon, x + 6, y + 4, 12, c);
            Ui.textWrapped(g, text.get(), x + 24, y + 5, w - 30, Theme.palette().text(), 3);
        }
    }

    // ------------------------------------------------------------------ Visuals

    static final class VisualsPage implements Page {
        static final Setting[] SKY = {BloomSettings.SKY_ENABLED};
        static final Setting[] SKY_LOOK = {BloomSettings.SKY_FADE_TIME, BloomSettings.SKY_TRANSITION, BloomSettings.SKY_BRIGHTNESS, BloomSettings.SKY_SATURATION,
                BloomSettings.SKY_TINT, BloomSettings.SKY_TINT_STRENGTH, BloomSettings.SKY_ROTATION, BloomSettings.SKY_ROTATION_SPEED,
                BloomSettings.SKY_SUN_MOON, BloomSettings.SKY_STARS, BloomSettings.SKY_CLOUDS, BloomSettings.SUN_GLOW, BloomSettings.SUN_GLOW_STRENGTH};
        static final Setting[] SHADERS = {BloomSettings.POST};
        static final Setting[] GRADING = {BloomSettings.POST_BRIGHTNESS, BloomSettings.POST_CONTRAST, BloomSettings.POST_SATURATION,
                BloomSettings.POST_WARMTH, BloomSettings.POST_TINT, BloomSettings.POST_TINT_STRENGTH, BloomSettings.POST_SOFT_HIGHLIGHTS};
        static final Setting[] LENS = {BloomSettings.POST_GLOW, BloomSettings.POST_GLOW_STRENGTH, BloomSettings.POST_GLOW_THRESHOLD,
                BloomSettings.POST_SHARPEN, BloomSettings.POST_VIGNETTE};
        static final Setting[] WEATHER = {BloomSettings.POST_WEATHER, BloomSettings.RAIN, BloomSettings.RAIN_HIDE_VANILLA,
                BloomSettings.SNOW, BloomSettings.SNOW_WHERE};
        static final List<Setting> SETTINGS = concat(SKY, SKY_LOOK, SHADERS, GRADING, LENS, WEATHER);

        private final BloomMenuScreen screen;

        VisualsPage(BloomMenuScreen screen) {
            this.screen = screen;
        }

        @Override
        public List<Element> build() {
            List<Element> rows = new ArrayList<>();
            Set<Setting> used = new LinkedHashSet<>();
            rows.add(new Rows.Header("Presets"));
            rows.add(new PresetCards(screen));
            rows.add(new Rows.Buttons(List.of(
                    new Button("Save as preset…", "plus", Button.Style.NORMAL, () -> saveAsPreset(screen))
                            .tooltip("Saves everything you see now as your own preset"),
                    new Button("All presets…", "profiles", Button.Style.GHOST, () -> screen.switchTo(dev.bloom.client.core.settings.Category.PROFILES))
                            .tooltip("Your own presets, import/export and per-biome switching"))));

            section(rows, screen, used, "Skybox", SKY);
            rows.add(new Rows.Buttons(List.of(new Button("Open sky browser", "skybox", Button.Style.PRIMARY, () -> screen.switchTo(1))
                    .tooltip("Live preview, 45 skies including the 4K Halloween set, favourites and imports"))));
            section(rows, screen, used, "Sky look", SKY_LOOK);

            section(rows, screen, used, "Shader effects", SHADERS);
            rows.add(new LiveInfo("effects", () -> {
                String problem = PostEffects.problem();
                if (problem != null) return problem;
                if (!BloomSettings.POST.on() || !BloomSettings.ENABLED.on()) return "Shader effects are off.";
                return "Running now: " + PostEffects.status() + ". Real GLSL passes on your GPU; the HUD and menus are not affected.";
            }));
            section(rows, screen, used, "Colour grading", GRADING);
            section(rows, screen, used, "Glow and lens", LENS);
            section(rows, screen, used, "Weather visuals", WEATHER);
            rows.add(new Rows.Buttons(List.of(resetButton(screen, "Visuals", used))));
            return rows;
        }
    }

    static void saveAsPreset(BloomMenuScreen screen) {
        screen.prompt("Save as preset", "My preset", "Save", name -> {
            try {
                ProfileManager pm = BloomClient.get().profiles();
                Profile p = pm.create(name);
                pm.select(p.id());
                screen.message("Preset saved", "\"" + p.name() + "\" is saved and selected. Changes you make now are saved into it.");
            } catch (Exception e) {
                screen.message("Couldn't save the preset", String.valueOf(e.getMessage()));
            }
            screen.requestRebuild();
        });
    }

    /** Big cards for the five headline presets, with the active one highlighted. */
    static final class PresetCards extends Element {
        private final BloomMenuScreen screen;

        PresetCards(BloomMenuScreen screen) {
            this.screen = screen;
        }

        private List<Profile> presets() {
            List<Profile> out = new ArrayList<>();
            ProfileManager pm = BloomClient.get().profiles();
            for (String id : PRESETS) {
                Profile p = pm.get(id);
                if (p != null) out.add(p);
            }
            return out;
        }

        private int cols(int width) {
            return width >= 330 ? 5 : width >= 200 ? 3 : 2;
        }

        private int cardW(int width) {
            int c = cols(width);
            return (width - (c - 1) * 5) / c;
        }

        private int cardH(int width) {
            return cardW(width) * 9 / 16 + 16;
        }

        @Override
        public int preferredHeight(int width) {
            int n = presets().size(), c = cols(width);
            return ((n + c - 1) / c) * (cardH(width) + 5) + 2;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            ProfileManager pm = BloomClient.get().profiles();
            List<Profile> list = presets();
            int c = cols(w), cw = cardW(w), ch = cardH(w), ih = ch - 16;
            for (int i = 0; i < list.size(); i++) {
                Profile pr = list.get(i);
                int cx = x + (i % c) * (cw + 5), cy = y + (i / c) * (ch + 5);
                boolean over = Ui.inside(mx, my, cx, cy, cw, ch);
                boolean active = pr.id().equals(pm.selectedId());
                Ui.smallPanel(g, cx, cy, cw, ch, over ? p.surfaceHover() : p.surface());
                String sky = skyOf(pr);
                if (sky == null || SkyboxCatalog.NONE.equals(sky)) {
                    g.fillGradient(cx + 2, cy + 2, cx + cw - 2, cy + ih, 0xFF78A7FF, 0xFFC0D8FF);
                } else {
                    Identifier tex = SkyTextures.preview(sky);
                    if (tex != null) g.blit(RenderPipelines.GUI_TEXTURED, tex, cx + 2, cy + 2, 0f, 0f, cw - 4, ih - 2, 128, 72, 128, 72);
                    else g.fill(cx + 2, cy + 2, cx + cw - 2, cy + ih, 0xFF1F1517);
                }
                if (active) {
                    Ui.panelBorder(g, cx, cy, cw, ch, Theme.accentArgb());
                    if (pr.modified()) {
                        int bw = Ui.width("EDITED") + 6;
                        Ui.smallPanel(g, cx + 4, cy + 4, bw, 10, Theme.accentArgb());
                        Ui.text(g, "EDITED", cx + 7, cy + 5, Theme.onAccent());
                    }
                }
                Ui.text(g, Ui.ellipsize(pr.name(), cw - 6), cx + 4, cy + ih + 4, active ? Theme.accentArgb() : p.text());
            }
        }

        private static String skyOf(Profile p) {
            if (p.values().has("sky.enabled") && !p.values().get("sky.enabled").getAsBoolean()) return SkyboxCatalog.NONE;
            if (p.values().has("sky.day")) return p.values().get("sky.day").getAsString();
            return BloomSettings.SKY_DAY.defaultId();
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            List<Profile> list = presets();
            int c = cols(w), cw = cardW(w), ch = cardH(w);
            for (int i = 0; i < list.size(); i++) {
                int cx = x + (i % c) * (cw + 5), cy = y + (i / c) * (ch + 5);
                if (!Ui.inside(mx, my, cx, cy, cw, ch)) continue;
                Profile pr = list.get(i);
                Ui.click();
                ProfileManager pm = BloomClient.get().profiles();
                if (pr.id().equals(pm.selectedId()) && pr.modified()) {
                    screen.confirm("Restore " + pr.name() + "?", "Your edits to this preset are discarded and its original look comes back.",
                            "Restore", true, () -> {
                                try {
                                    pm.resetBuiltIn(pr.id());
                                    pm.select(pr.id());
                                } catch (Exception e) {
                                    screen.message("Couldn't restore", String.valueOf(e.getMessage()));
                                }
                                screen.requestRebuild();
                            });
                } else {
                    pm.select(pr.id());
                    screen.requestRebuild();
                }
                return true;
            }
            return false;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            List<Profile> list = presets();
            int c = cols(w), cw = cardW(w), ch = cardH(w);
            ProfileManager pm = BloomClient.get().profiles();
            for (int i = 0; i < list.size(); i++) {
                int cx = x + (i % c) * (cw + 5), cy = y + (i / c) * (ch + 5);
                if (!Ui.inside(mx, my, cx, cy, cw, ch)) continue;
                Profile pr = list.get(i);
                boolean active = pr.id().equals(pm.selectedId());
                return List.of(pr.name(), pr.description(), active
                        ? (pr.modified() ? "Active, with your edits. Click to restore the original." : "Active. Any change you make is saved into it.")
                        : "Click to apply. You can still change any setting afterwards.");
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ Themes

    static final class ThemesPage implements Page {
        static final Setting[] SETTINGS = {BloomSettings.THEME_DECOR, BloomSettings.ACCENT_FROM_THEME, BloomSettings.ACCENT,
                BloomSettings.UI_EFFECTS, BloomSettings.REDUCE_MOTION};
        private final BloomMenuScreen screen;

        ThemesPage(BloomMenuScreen screen) {
            this.screen = screen;
        }

        @Override
        public List<Element> build() {
            List<Element> rows = new ArrayList<>();
            Set<Setting> used = new LinkedHashSet<>();
            rows.add(new Rows.Header("Themes"));
            rows.add(new ThemeCards(screen));
            rows.add(new Rows.Info("info", "A theme changes the menu colours, accents and backdrop decoration. Each Halloween theme has a matching 4K sky: "
                    + "you're asked before it's used, and your own sky choice is never changed otherwise.", Theme.palette().textDim()));
            section(rows, screen, used, "Appearance", SETTINGS);
            rows.add(new Rows.Buttons(List.of(resetButton(screen, "Themes", used))));
            return rows;
        }
    }

    static final class ThemeCards extends Element {
        private final BloomMenuScreen screen;

        ThemeCards(BloomMenuScreen screen) {
            this.screen = screen;
        }

        private int cols(int width) {
            return width >= 300 ? 3 : 2;
        }

        private int cardW(int width) {
            int c = cols(width);
            return (width - (c - 1) * 5) / c;
        }

        @Override
        public int preferredHeight(int width) {
            int c = cols(width);
            return ((Themes.ALL.size() + c - 1) / c) * 41;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            int c = cols(w), cw = cardW(w);
            for (int i = 0; i < Themes.ALL.size(); i++) {
                Themes.Info t = Themes.ALL.get(i);
                int cx = x + (i % c) * (cw + 5), cy = y + (i / c) * 41;
                boolean sel = BloomSettings.THEME.is(t.id());
                boolean over = Ui.inside(mx, my, cx, cy, cw, 36);
                Theme.Palette tp = Theme.palette(t.id());
                Ui.smallPanel(g, cx, cy, cw, 36, tp.window() | 0xFF000000);
                g.fillGradient(cx + 2, cy + 2, cx + cw - 2, cy + 14, 0xFF000000 | t.swatchA(), Theme.alpha(0xFF000000 | t.swatchB(), 0.85f));
                g.fill(cx + 2, cy + 14, cx + cw - 2, cy + 15, 0xFF000000 | (tp.accent() & 0xFFFFFF));
                Ui.text(g, Ui.ellipsize(t.name(), cw - 8), cx + 5, cy + 19, sel ? (tp.accent() | 0xFF000000) : tp.text());
                String sky = t.recommendedSky() == null ? "" : "none".equals(t.recommendedSky()) ? "Vanilla sky" : skyName(t.recommendedSky());
                Ui.text(g, Ui.ellipsize(sky, cw - 8), cx + 5, cy + 28, tp.textFaint());
                if (sel) Ui.panelBorder(g, cx, cy, cw, 36, tp.accent() | 0xFF000000);
                else if (over) Ui.panelBorder(g, cx, cy, cw, 36, Theme.alpha(p.textDim(), 0.5f));
            }
        }

        private static String skyName(String id) {
            SkyboxCatalog.Entry e = SkyboxCatalog.find(id);
            return e == null ? "" : e.name();
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int c = cols(w), cw = cardW(w);
            for (int i = 0; i < Themes.ALL.size(); i++) {
                int cx = x + (i % c) * (cw + 5), cy = y + (i / c) * 41;
                if (!Ui.inside(mx, my, cx, cy, cw, 36)) continue;
                Themes.Info t = Themes.ALL.get(i);
                Ui.click();
                BloomSettings.THEME.set(t.id());
                offerSky(screen, t);
                screen.requestRebuild();
                return true;
            }
            return false;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            int c = cols(w), cw = cardW(w);
            for (int i = 0; i < Themes.ALL.size(); i++) {
                int cx = x + (i % c) * (cw + 5), cy = y + (i / c) * 41;
                if (Ui.inside(mx, my, cx, cy, cw, 36)) {
                    Themes.Info t = Themes.ALL.get(i);
                    return List.of(t.name(), t.description());
                }
            }
            return null;
        }
    }

    /** After a theme change: offer (never force) the theme's matching sky. */
    static void offerSky(BloomMenuScreen screen, Themes.Info t) {
        String rec = t.recommendedSky();
        if (rec == null) return;
        if ("none".equals(rec)) {
            if (!BloomSettings.SKY_ENABLED.on()) return;
            screen.confirm("Use Minecraft's sky too?", t.name() + " goes best with Minecraft's own sky. Your skybox choice is kept unless you switch.",
                    "Use vanilla sky", false, () -> BloomSettings.SKY_ENABLED.set(false));
            return;
        }
        SkyboxCatalog.Entry sky = SkyboxCatalog.find(rec);
        if (sky == null) return;
        if (BloomSettings.SKY_ENABLED.on() && BloomSettings.SKY_DAY.is(rec) && BloomSettings.SKY_NIGHT.is(rec)) return;
        screen.confirm("Use " + sky.name() + " too?", t.name() + " has a matching sky: " + sky.name() + " (" + SkyboxCatalog.resolutionLabel(rec)
                + "). Use it day and night? Your current sky is kept if you don't.", "Use this sky", false, () -> {
            BloomSettings.SKY_DAY.set(rec);
            BloomSettings.SKY_NIGHT.set(rec);
            BloomSettings.SKY_ENABLED.set(true);
        });
    }

    // ------------------------------------------------------------------ World

    static final class WorldPage implements Page {
        static final Setting[] SKY = {BloomSettings.SKY_COLOR_TINT, BloomSettings.SKY_COLOR_TINT_STRENGTH, BloomSettings.SKY_HORIZON};
        static final Setting[] FOG = {BloomSettings.FOG_CUSTOM, BloomSettings.FOG_SMOOTH, BloomSettings.FOG_DISTANCE, BloomSettings.FOG_TINT,
                BloomSettings.FOG_TINT_STRENGTH, BloomSettings.HAZE, BloomSettings.HAZE_COLOR};
        static final Setting[] DIMENSIONS = {BloomSettings.POST_DIMENSIONS, BloomSettings.NETHER_FOG_TINT, BloomSettings.NETHER_FOG_STRENGTH,
                BloomSettings.END_FOG_TINT, BloomSettings.END_FOG_STRENGTH};
        static final Setting[] TIME = {BloomSettings.TIME_LOCK, BloomSettings.TIME_VALUE};
        static final Setting[] TRANSITIONS = {BloomSettings.SMOOTH_TRANSITIONS};
        static final List<Setting> SETTINGS = concat(SKY, FOG, DIMENSIONS, TIME, TRANSITIONS);

        private final BloomMenuScreen screen;

        WorldPage(BloomMenuScreen screen) {
            this.screen = screen;
        }

        @Override
        public List<Element> build() {
            List<Element> rows = new ArrayList<>();
            Set<Setting> used = new LinkedHashSet<>();
            section(rows, screen, used, "Sky colour and horizon", SKY);
            section(rows, screen, used, "Fog and atmosphere", FOG);
            section(rows, screen, used, "Dimensions", DIMENSIONS);
            rows.add(new Rows.Info("info", "Skyboxes, haze and the Overworld fog settings apply in the Overworld (and modded dimensions with a normal sky). "
                    + "The Nether has no sky and the End keeps its own, so there you get the fog tints above and, if enabled, the shader effects.",
                    Theme.palette().textDim()));
            section(rows, screen, used, "Time of day", TIME);
            section(rows, screen, used, "Transitions", TRANSITIONS);
            rows.add(new Rows.Info("info", "Smooth transitions crossfade skyboxes and glide shader colours when you switch presets or move sliders. "
                    + "Fog has its own switch above.", Theme.palette().textDim()));
            rows.add(new Rows.Buttons(List.of(resetButton(screen, "World", used))));
            return rows;
        }
    }

    // ------------------------------------------------------------------ Performance

    static final class PerfPage implements Page {
        static final Setting[] SHADERS = {BloomSettings.POST_QUALITY, BloomSettings.LITE_MODE};
        static final Setting[] SKIES = {BloomSettings.SKY_QUALITY, BloomSettings.SKY_CACHE, BloomSettings.SKY_LOAD_CUSTOM, BloomSettings.SKY_FADE_TIME};
        static final Setting[] PARTICLES = {BloomSettings.QUALITY, BloomSettings.MAX_PARTICLES, BloomSettings.EFFECT_DISTANCE};
        static final Setting[] AUTO = {BloomSettings.ADAPTIVE, BloomSettings.ADAPTIVE_TARGET, BloomSettings.LOW_FPS_TIP};
        static final List<Setting> SETTINGS = concat(SHADERS, SKIES, PARTICLES, AUTO);

        private final BloomMenuScreen screen;

        PerfPage(BloomMenuScreen screen) {
            this.screen = screen;
        }

        @Override
        public List<Element> build() {
            List<Element> rows = new ArrayList<>();
            Set<Setting> used = new LinkedHashSet<>();
            rows.add(new LiveInfo("performance", () -> {
                BloomClient bc = BloomClient.get();
                int fps = bc.frames().fps(System.nanoTime());
                float f = bc.budget().factor();
                return fps + " FPS · shaders: " + PostEffects.status() + " · particles: " + bc.particles().count() + " ("
                        + Math.round(f * (BloomSettings.LITE_MODE.on() ? 50 : 100)) + "% budget"
                        + (f < 0.99f ? ", reduced while FPS is low" : "") + ")";
            }));
            section(rows, screen, used, "Shader effects", SHADERS);
            section(rows, screen, used, "Skyboxes", SKIES);
            rows.add(new LiveInfo("skybox", () -> {
                String reason = SkyTextures.reductionReason();
                int f = SkyTextures.maxFace();
                String base = "Skyboxes load at up to " + f + " px faces (" + Math.max(1, f * 4 / 1024) + "K), about "
                        + (f * 3 * f * 2 * 4 / (1024 * 1024)) + " MB of video memory each. Shrinking happens on a background thread.";
                return reason != null ? reason : base;
            }));
            section(rows, screen, used, "Particles", PARTICLES);
            section(rows, screen, used, "Automatic quality", AUTO);
            rows.add(new Rows.Info("info", "Everything here changes real rendering work: glow buffers, sharpening taps, skybox texture size, "
                    + "the starfield and the particle budget. With all shader values neutral, MOTION skips the shader pass entirely.",
                    Theme.palette().textDim()));
            rows.add(new Rows.Buttons(List.of(resetButton(screen, "Performance", used))));
            return rows;
        }
    }

    // ------------------------------------------------------------------ General

    static final class GeneralVisualsPage implements Page {
        static final Setting[] MENU = {BloomSettings.VISUALS_POSITION, BloomSettings.VISUALS_DIM, BloomSettings.PAUSE_SINGLEPLAYER,
                BloomSettings.REDUCE_MOTION, BloomSettings.THEME};

        private final BloomMenuScreen screen;

        GeneralVisualsPage(BloomMenuScreen screen) {
            this.screen = screen;
        }

        @Override
        public List<Element> build() {
            List<Element> rows = new ArrayList<>();
            Set<Setting> used = new LinkedHashSet<>();
            section(rows, screen, used, "All visuals", BloomSettings.ENABLED);
            rows.add(new Rows.Header("Configuration"));
            Button load = new Button("Load preset…", "import", Button.Style.NORMAL, () -> openLoad()).tooltip("Switch to any built-in or saved preset");
            loadButton = load;
            rows.add(new Rows.Buttons(List.of(
                    new Button("Save now", "check", Button.Style.PRIMARY, () -> {
                        BloomClient.get().saveNow();
                        screen.message("Saved", "Your settings and presets are written to disk. (MOTION also saves automatically a moment after every change.)");
                    }).tooltip("Write settings to disk immediately"),
                    new Button("Save as preset…", "plus", Button.Style.NORMAL, () -> saveAsPreset(screen)),
                    load,
                    new Button("Restore defaults…", "reset", Button.Style.DANGER, () -> screen.confirm("Restore default visuals?",
                            "Every setting in this menu goes back to its default. Your saved presets, HUD and imported skyboxes are kept.", "Restore", true, () -> {
                                for (Setting s : allVisualSettings()) s.reset();
                                for (Setting s : MENU) s.reset();
                                screen.requestRebuild();
                            })))));
            section(rows, screen, used, "Menu", MENU);
            rows.add(new KeybindsPage.KeyRow(screen, BloomKeys.VISUALS_MENU));
            rows.add(new KeybindsPage.KeyRow(screen, BloomKeys.MENU));
            rows.add(new KeybindsPage.KeyRow(screen, BloomKeys.TOGGLE_VISUALS));
            rows.add(new Rows.Info("info", "Tab / Shift+Tab switch sections. Type anywhere to search. Esc or the menu key closes the menu.",
                    Theme.palette().textDim()));
            rows.add(new Rows.Buttons(List.of(
                    new Button("Open full MOTION menu", "grid", Button.Style.GHOST,
                            () -> Mc.setScreen(new BloomMenuScreen(screen.parentScreen()))),
                    resetButton(screen, "General", used))));
            return rows;
        }

        private Button loadButton;

        private void openLoad() {
            ProfileManager pm = BloomClient.get().profiles();
            List<ChoiceSetting.Option> opts = new ArrayList<>();
            for (Profile p : pm.all()) opts.add(new ChoiceSetting.Option(p.id(), p.name() + (p.builtIn() ? "" : " (yours)")));
            int x = loadButton != null ? loadButton.x : 0, y = loadButton != null ? loadButton.y + loadButton.h + 2 : 0;
            screen.openDropdown(x, y, 150, opts, pm.selectedId(), id -> {
                pm.select(id);
                screen.requestRebuild();
            });
        }
    }

    // ------------------------------------------------------------------

    private static List<Setting> concat(Setting[]... groups) {
        List<Setting> out = new ArrayList<>();
        for (Setting[] g : groups) out.addAll(List.of(g));
        return out;
    }
}
