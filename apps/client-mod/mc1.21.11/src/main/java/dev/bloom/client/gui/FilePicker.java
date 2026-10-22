package dev.bloom.client.gui;

import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Native open/save dialogs (LWJGL tinyfd, which ships with Minecraft). Dialogs run on a
 * background thread so the game keeps rendering; the result is handed back on the game thread.
 */
public final class FilePicker {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BLOOD file dialog");
        t.setDaemon(true);
        return t;
    });

    private FilePicker() {
    }

    public static void open(String title, Path start, String filterLabel, String[] patterns, Consumer<Optional<Path>> then) {
        EXECUTOR.submit(() -> {
            String result;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(patterns.length);
                for (String p : patterns) filters.put(stack.UTF8(p));
                filters.flip();
                result = TinyFileDialogs.tinyfd_openFileDialog(title, start == null ? null : start.toAbsolutePath() + java.io.File.separator,
                        filters, filterLabel, false);
            } catch (Throwable t) {
                result = null;
            }
            String r = result;
            Minecraft.getInstance().execute(() -> then.accept(Optional.ofNullable(r).map(Paths::get)));
        });
    }

    public static void save(String title, Path suggested, String filterLabel, String[] patterns, Consumer<Optional<Path>> then) {
        EXECUTOR.submit(() -> {
            String result;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(patterns.length);
                for (String p : patterns) filters.put(stack.UTF8(p));
                filters.flip();
                result = TinyFileDialogs.tinyfd_saveFileDialog(title, suggested == null ? null : suggested.toAbsolutePath().toString(),
                        filters, filterLabel);
            } catch (Throwable t) {
                result = null;
            }
            String r = result;
            Minecraft.getInstance().execute(() -> then.accept(Optional.ofNullable(r).map(Paths::get)));
        });
    }

    public static void folder(String title, Path start, Consumer<Optional<Path>> then) {
        EXECUTOR.submit(() -> {
            String result;
            try {
                result = TinyFileDialogs.tinyfd_selectFolderDialog(title, start == null ? null : start.toAbsolutePath().toString());
            } catch (Throwable t) {
                result = null;
            }
            String r = result;
            Minecraft.getInstance().execute(() -> then.accept(Optional.ofNullable(r).map(Paths::get)));
        });
    }
}
