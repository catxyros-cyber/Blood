package dev.bloom.client.gui.page;

import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.widget.Button;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;

import java.util.ArrayList;
import java.util.List;

/** A page that is just its settings, plus "Reset this page". */
public class GenericPage implements Page {
    protected final BloomMenuScreen screen;
    protected final Category category;

    public GenericPage(BloomMenuScreen screen, Category category) {
        this.screen = screen;
        this.category = category;
    }

    public static Button resetPageButton(BloomMenuScreen screen, Category c) {
        return new Button("Reset page", "reset", Button.Style.GHOST, () -> screen.confirm("Reset " + c.label() + "?",
                "Every setting on this page goes back to its default. Other pages are not affected.", "Reset", true, () -> {
                    for (Setting s : BloomSettings.in(c)) s.reset();
                    screen.requestRebuild();
                })).tooltip("Reset every setting on this page");
    }

    @Override
    public List<Element> build() {
        List<Element> rows = new ArrayList<>();
        rows.add(new Rows.Buttons(List.of(resetPageButton(screen, category))));
        rows.addAll(Pages.settings(category, screen));
        return rows;
    }
}
