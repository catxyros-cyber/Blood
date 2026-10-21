package dev.bloom.client.gui.widget;

import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.core.settings.ColorSetting;

import java.util.List;
import java.util.function.Consumer;

/** What controls need from the screen that contains them (popups, focus, toasts). */
public interface Host {
    void openDropdown(int x, int y, int w, List<ChoiceSetting.Option> options, String selected, Consumer<String> onPick);

    void openColorPicker(int x, int y, ColorSetting setting);

    void focus(Element e);

    boolean isFocused(Element e);

    void rebuild();

    void confirm(String title, String message, String confirmLabel, boolean danger, Runnable onConfirm);

    void prompt(String title, String initial, String confirmLabel, Consumer<String> onConfirm);

    void message(String title, String message);
}
