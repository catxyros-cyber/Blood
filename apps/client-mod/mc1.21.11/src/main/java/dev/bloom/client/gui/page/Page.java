package dev.bloom.client.gui.page;

import dev.bloom.client.gui.widget.Element;

import java.nio.file.Path;
import java.util.List;

/** One category page of the MOTION menu. */
public interface Page {
    List<Element> build();

    default void onFilesDrop(List<Path> files) {
    }

    default void tick() {
    }
}
