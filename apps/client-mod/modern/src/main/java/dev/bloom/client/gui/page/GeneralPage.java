package dev.bloom.client.gui.page;

import dev.bloom.client.Mc;
import dev.bloom.client.BloomClient;
import dev.bloom.client.BloomKeys;
import dev.bloom.client.core.compat.CompatReport;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.HudEditorScreen;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.widget.Button;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class GeneralPage extends GenericPage {
    public GeneralPage(BloomMenuScreen screen) {
        super(screen, Category.GENERAL);
    }

    @Override
    public List<Element> build() {
        BloomClient bc = BloomClient.get();
        Theme.Palette p = Theme.palette();
        List<Element> rows = new ArrayList<>();
        rows.add(new Rows.Info("info", "MOTION Client " + bc.platform().modVersion() + " on " + bc.platform().loaderName()
                + " · Minecraft " + "1.21.11"
                + ". Everything here is visual and client-side: it never changes the world, the server or other players.", Theme.accentArgb()));
        rows.add(new Rows.Info("eye", "FREE LOOK — hold the key below to look around while your player keeps moving. Click the key to rebind it.", Theme.accentArgb()));
        rows.add(new KeybindsPage.KeyRow(screen, BloomKeys.FREE_LOOK));
        rows.add(new Rows.Info("search", "ZOOM — hold the key below to zoom in, scroll to zoom further. Zoom amount, smoothing and toggle mode are under Zoom further down.", Theme.accentArgb()));
        rows.add(new KeybindsPage.KeyRow(screen, BloomKeys.ZOOM));
        if (bc.shaderActive()) {
            rows.add(new Rows.Info("warning", BloomSettings.SKY_WITH_SHADERS.on()
                    ? "A shader pack is active and \"Use with shader packs\" is on. Results depend on the shader pack."
                    : "A shader pack is active, so MOTION's skybox, fog changes and world particles are paused. The HUD and menu still work. You can override this under Skyboxes → Compatibility.", p.warn()));
        }
        for (CompatReport.Note n : bc.compatNotes()) {
            int col = switch (n.level()) {
                case OK -> p.ok();
                case INFO -> Theme.accentArgb();
                case WARNING -> p.warn();
            };
            rows.add(new Rows.Info(n.level() == CompatReport.Level.WARNING ? "warning" : n.level() == CompatReport.Level.OK ? "check" : "info",
                    n.name() + ": " + n.message(), col));
        }
        rows.add(new Rows.Buttons(List.of(
                new Button("HUD editor", "hud", Button.Style.PRIMARY, () -> Mc.setScreen(new HudEditorScreen(screen))),
                new Button("Open config folder", "folder", Button.Style.NORMAL, () -> {
                    try {
                        Files.createDirectories(bc.configDir());
                        dev.bloom.client.game.OsOpen.open(bc.configDir());
                    } catch (Exception e) {
                        screen.message("Couldn't open the folder", bc.configDir().toString());
                    }
                }).tooltip("Settings, profiles and custom skyboxes live here"),
                resetPageButton(screen, category),
                new Button("Reset everything…", "trash", Button.Style.DANGER, () -> screen.confirm("Reset all MOTION settings?",
                        "Every MOTION setting goes back to its default and the Blood Moon profile is selected. Your saved profiles, HUD layouts and imported skyboxes are kept.",
                        "Reset all", true, () -> {
                            BloomSettings.resetAll();
                            bc.profiles().select(dev.bloom.client.core.profile.BuiltInProfiles.DEFAULT_ID);
                            screen.requestRebuild();
                        })))));
        rows.addAll(Pages.settings(category, screen));
        return rows;
    }
}
