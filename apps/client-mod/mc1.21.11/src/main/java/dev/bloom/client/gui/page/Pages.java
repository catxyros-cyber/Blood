package dev.bloom.client.gui.page;

import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;

import java.util.List;

public final class Pages {
    private Pages() {
    }

    public static Page create(Category c, BloomMenuScreen screen) {
        return switch (c) {
            case GENERAL -> new GeneralPage(screen);
            case HUD -> new HudPage(screen);
            case SKYBOX -> new SkyboxPage(screen);
            case PERFORMANCE -> new PerformancePage(screen);
            case PROFILES -> new ProfilesPage(screen);
            case KEYBINDS -> new KeybindsPage(screen);
            default -> new GenericPage(screen, c);
        };
    }

    /** Settings of a category, as rows. */
    static List<Element> settings(Category c, BloomMenuScreen screen) {
        return Rows.settingRows(BloomSettings.in(c), screen, false);
    }
}
