package dev.bloom.client.gui.page;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.io.SafeFiles;
import dev.bloom.client.core.profile.Profile;
import dev.bloom.client.core.profile.ProfileCodec;
import dev.bloom.client.core.profile.ProfileManager;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.FilePicker;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import dev.bloom.client.gui.widget.Button;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfilesPage extends GenericPage {
    public ProfilesPage(BloomMenuScreen screen) {
        super(screen, Category.PROFILES);
    }

    private ProfileManager pm() {
        return BloomClient.get().profiles();
    }

    @Override
    public List<Element> build() {
        List<Element> rows = new ArrayList<>();
        rows.add(new Rows.Buttons(List.of(
                new Button("Save current look…", "plus", Button.Style.PRIMARY, () -> screen.prompt("New profile", "My profile", "Create", name -> {
                    try {
                        Profile p = pm().create(name);
                        pm().select(p.id());
                    } catch (Exception e) {
                        screen.message("Couldn't create the profile", e.getMessage());
                    }
                    screen.requestRebuild();
                })).tooltip("Create a profile from your current settings"),
                new Button("Import code", "import", Button.Style.NORMAL, this::importClipboard)
                        .tooltip("Paste a BLOOM1: share code (or profile JSON) from your clipboard"),
                new Button("Import file…", "folder", Button.Style.NORMAL, () -> FilePicker.open("Choose a BLOOD profile",
                        BloomClient.get().configDir(), "BLOOD profiles (.json)", new String[]{"*.json"}, r -> r.ifPresent(this::importFile))))));
        rows.add(new Rows.Info("info", "Profiles remember your sky, weather, particles, colours, HUD style and quality. Changes you make are saved into the active profile automatically. Imported profiles contain settings only — nothing in them can run code.", Theme.palette().textDim()));
        rows.add(new Rows.Header("Your profiles"));
        for (Profile p : pm().all()) rows.add(new ProfileCard(p));
        rows.add(new Rows.Header("Switch automatically by biome"));
        rows.addAll(Pages.settings(Category.PROFILES, screen));
        for (ChoiceSetting.Option g : BloomSettings.BIOME_GROUPS) rows.add(new BiomeRuleRow(g));
        return rows;
    }

    // ------------------------------------------------------------------ import

    private void importClipboard() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        importText(text);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        for (Path f : files) importFile(f);
    }

    private void importFile(Path file) {
        try {
            importText(new String(SafeFiles.readLimited(file, ProfileCodec.MAX_IMPORT_BYTES), StandardCharsets.UTF_8));
        } catch (Exception e) {
            screen.message("Couldn't read the file", e.getMessage());
        }
    }

    private void importText(String text) {
        try {
            ProfileCodec.ImportResult r = ProfileCodec.parse(text, BloomSettings::get);
            Profile p = pm().importProfile(r);
            StringBuilder msg = new StringBuilder("\"" + p.name() + "\" was added to your profiles. Use \"Apply\" to switch to it.");
            for (String w : r.warnings()) msg.append("\n").append(w);
            screen.message("Profile imported", msg.toString());
        } catch (ProfileCodec.ImportException e) {
            screen.message("Couldn't import", e.getMessage());
        } catch (Exception e) {
            screen.message("Couldn't import", e.getMessage());
        }
        screen.requestRebuild();
    }

    // ------------------------------------------------------------------ rows

    final class ProfileCard extends Element {
        private final Profile profile;
        private final Rows.Buttons buttons;
        private final List<Button> list;

        ProfileCard(Profile profile) {
            this.profile = profile;
            List<Button> b = new ArrayList<>();
            boolean active = profile.id().equals(pm().selectedId());
            Button apply = new Button(active ? "Active" : "Apply", active ? "check" : "play", active ? Button.Style.NORMAL : Button.Style.PRIMARY, () -> {
                pm().select(profile.id());
                screen.requestRebuild();
            });
            apply.enabled = !active;
            b.add(apply);
            b.add(new Button("", "copy", Button.Style.GHOST, () -> {
                try {
                    pm().duplicate(profile.id());
                } catch (Exception e) {
                    screen.message("Couldn't duplicate", e.getMessage());
                }
                screen.requestRebuild();
            }).tooltip("Duplicate"));
            if (!profile.builtIn()) {
                b.add(new Button("", "edit", Button.Style.GHOST, () -> screen.prompt("Rename profile", profile.name(), "Rename", name -> {
                    try {
                        pm().rename(profile.id(), name);
                    } catch (Exception e) {
                        screen.message("Couldn't rename", e.getMessage());
                    }
                    screen.requestRebuild();
                })).tooltip("Rename"));
            }
            b.add(new Button("", "link", Button.Style.GHOST, () -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(ProfileCodec.toShareCode(profile));
                BloomClient.get().toast("Share code copied", "Send it to a friend; they can use \"Import code\".");
            }).tooltip("Copy share code"));
            b.add(new Button("", "export", Button.Style.GHOST, () -> FilePicker.save("Export profile",
                    BloomClient.get().configDir().resolve("exports").resolve(SafeFiles.slug(profile.name(), "profile") + ".bloomprofile.json"),
                    "BLOOD profile", new String[]{"*.json"}, r -> r.ifPresent(path -> {
                        try {
                            Path target = path.toString().toLowerCase(Locale.ROOT).endsWith(".json") ? path : Path.of(path + ".json");
                            SafeFiles.writeAtomic(target, ProfileCodec.toPrettyJson(profile));
                            BloomClient.get().toast("Profile exported", target.getFileName().toString());
                        } catch (Exception e) {
                            screen.message("Couldn't export", e.getMessage());
                        }
                    }))).tooltip("Export to a file"));
            if (profile.builtIn()) {
                if (profile.modified()) {
                    b.add(new Button("", "reset", Button.Style.GHOST, () -> screen.confirm("Reset \"" + profile.name() + "\"?",
                            "This built-in profile goes back to its original look. Your edits are kept in profiles/deleted.", "Reset", true, () -> {
                                try {
                                    pm().resetBuiltIn(profile.id());
                                } catch (Exception e) {
                                    screen.message("Couldn't reset", e.getMessage());
                                }
                                screen.requestRebuild();
                            })).tooltip("Restore the original look"));
                }
            } else {
                b.add(new Button("", "trash", Button.Style.DANGER, () -> screen.confirm("Delete \"" + profile.name() + "\"?",
                        "The profile is moved to profiles/deleted, so you can still recover it.", "Delete", true, () -> {
                            try {
                                pm().delete(profile.id());
                            } catch (Exception e) {
                                screen.message("Couldn't delete", e.getMessage());
                            }
                            screen.requestRebuild();
                        })).tooltip("Delete"));
            }
            buttons = new Rows.Buttons(b);
            list = b;
        }

        @Override
        public int preferredHeight(int width) {
            return 30 + Math.max(10, Ui.wrappedHeight(profile.description(), width - 20, 2));
        }

        @Override
        public int layout(int x, int y, int width) {
            int h = super.layout(x, y, width);
            buttons.layout(x + width - 8 - buttonsWidth(), y + 5, buttonsWidth());
            return h;
        }

        private int buttonsWidth() {
            int total = 0;
            for (Button b : list) total += b.naturalWidth() + 4;
            return total;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            boolean active = profile.id().equals(pm().selectedId());
            boolean biomeActive = profile.id().equals(pm().active().id()) && !active;
            Ui.smallPanel(g, x, y, w, h - 3, active ? Theme.alpha(Theme.accentArgb(), 0.12f) : p.surface());
            if (active) g.fill(x, y + 4, x + 2, y + h - 7, Theme.accentArgb());
            int tx = x + 10;
            int titleMax = Math.max(40, w - buttonsWidth() - 100);
            Ui.text(g, Ui.ellipsize(profile.name(), titleMax), tx, y + 9, p.text());
            int bx = tx + Math.min(Ui.font().width(profile.name()), titleMax) + 6;
            if (profile.builtIn()) bx += tag(g, bx, y + 8, profile.modified() ? "Built-in · edited" : "Built-in", p.textDim()) + 4;
            if (biomeActive) tag(g, bx, y + 8, "Active (biome)", Theme.accentArgb());
            Ui.textWrapped(g, profile.description().isEmpty() ? "Your own profile." : profile.description(), tx, y + 24, w - 20, p.textFaint(), 2);
            // buttons take the top-right; keep text clear of them by limiting the title width above
            buttons.render(g, mx, my, dt);
        }

        private int tag(GuiGraphics g, int x, int y, String text, int col) {
            int w = Ui.font().width(text) + 8;
            Ui.pill(g, x, y - 1, w, 11, Theme.alpha(col, 0.18f));
            Ui.text(g, text, x + 4, y + 1, col);
            return w;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return buttons.mouseClicked(mx, my, button);
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            return buttons.tooltip(mx, my);
        }
    }

    final class BiomeRuleRow extends Element {
        private final ChoiceSetting.Option group;

        BiomeRuleRow(ChoiceSetting.Option group) {
            this.group = group;
        }

        @Override
        public int preferredHeight(int width) {
            return 20;
        }

        private int ddX() {
            return x + w - 8 - 140;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            boolean enabled = BloomSettings.PROFILE_BY_BIOME.on();
            Ui.text(g, group.label(), x + 12, y + 6, enabled ? p.textDim() : p.textFaint());
            String rule = pm().biomeRules().get(group.id());
            Profile target = rule != null ? pm().get(rule) : null;
            boolean over = enabled && Ui.inside(mx, my, ddX(), y + 2, 140, 16);
            Ui.smallPanel(g, ddX(), y + 2, 140, 16, over ? p.surfaceActive() : p.surface());
            Ui.text(g, Ui.ellipsize(target != null ? target.name() : "— keep current", 116), ddX() + 6, y + 6,
                    target != null && enabled ? p.text() : p.textFaint());
            Ui.icon(g, "chevron_down", ddX() + 126, y + 5, 10, p.textDim());
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!BloomSettings.PROFILE_BY_BIOME.on() || !Ui.inside(mx, my, ddX(), y + 2, 140, 16)) return false;
            Ui.click();
            List<ChoiceSetting.Option> opts = new ArrayList<>();
            opts.add(new ChoiceSetting.Option("", "— keep current"));
            for (Profile p : pm().all()) opts.add(new ChoiceSetting.Option(p.id(), p.name()));
            Map<String, String> rules = pm().biomeRules();
            screen.openDropdown(ddX(), y + 20, 140, opts, rules.getOrDefault(group.id(), ""), id -> {
                pm().setBiomeRule(group.id(), id.isEmpty() ? null : id);
                screen.requestRebuild();
            });
            return true;
        }

        @Override
        public List<String> tooltip(int mx, int my) {
            if (!BloomSettings.PROFILE_BY_BIOME.on()) return List.of("Turn on \"Switch profile by biome\" first");
            return null;
        }
    }
}
