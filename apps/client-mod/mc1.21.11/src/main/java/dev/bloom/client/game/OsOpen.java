package dev.bloom.client.game;

import dev.bloom.client.core.Log;
import net.minecraft.util.Util;

import java.io.File;
import java.nio.file.Path;

/** Opens a folder in the system file manager through Minecraft's platform helper (any version). */
public final class OsOpen {
    private OsOpen() {
    }

    public static void open(Path path) {
        Object os = Util.getPlatform();
        try {
            os.getClass().getMethod("openPath", Path.class).invoke(os, path);
            return;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            os.getClass().getMethod("openFile", File.class).invoke(os, path.toFile());
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.warn("Couldn't open " + path, e);
        }
    }
}
