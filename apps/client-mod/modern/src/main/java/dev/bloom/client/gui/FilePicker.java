package dev.bloom.client.gui;

import net.minecraft.client.Minecraft;
//#if MC < 26.3
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
//#endif

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Native open/save/folder dialogs. Up to 26.2 they come from LWJGL's tinyfd (shipped with
 * Minecraft); 26.3 dropped it with the move to SDL, so there MOTION asks the operating system
 * directly (PowerShell on Windows, AppleScript on macOS, zenity or kdialog on Linux). Dialogs run on
 * a background thread so the game keeps rendering; the result is handed back on the game thread.
 */
public final class FilePicker {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MOTION file dialog");
        t.setDaemon(true);
        return t;
    });

    private enum Kind {OPEN, SAVE, FOLDER}

    private FilePicker() {
    }

    public static void open(String title, Path start, String filterLabel, String[] patterns, Consumer<Optional<Path>> then) {
        run(then, () -> {
//#if MC < 26.3
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(patterns.length);
                for (String p : patterns) filters.put(stack.UTF8(p));
                filters.flip();
                return TinyFileDialogs.tinyfd_openFileDialog(title, start == null ? null : start.toAbsolutePath() + java.io.File.separator,
                        filters, filterLabel, false);
            }
//#else
            return system(Kind.OPEN, title, start, filterLabel, patterns);
//#endif
        });
    }

    public static void save(String title, Path suggested, String filterLabel, String[] patterns, Consumer<Optional<Path>> then) {
        run(then, () -> {
//#if MC < 26.3
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(patterns.length);
                for (String p : patterns) filters.put(stack.UTF8(p));
                filters.flip();
                return TinyFileDialogs.tinyfd_saveFileDialog(title, suggested == null ? null : suggested.toAbsolutePath().toString(),
                        filters, filterLabel);
            }
//#else
            return system(Kind.SAVE, title, suggested, filterLabel, patterns);
//#endif
        });
    }

    public static void folder(String title, Path start, Consumer<Optional<Path>> then) {
        run(then, () -> {
//#if MC < 26.3
            return TinyFileDialogs.tinyfd_selectFolderDialog(title, start == null ? null : start.toAbsolutePath().toString());
//#else
            return system(Kind.FOLDER, title, start, null, new String[0]);
//#endif
        });
    }

    private interface Dialog {
        String show() throws Exception;
    }

    private static void run(Consumer<Optional<Path>> then, Dialog dialog) {
        EXECUTOR.submit(() -> {
            String result;
            try {
                result = dialog.show();
            } catch (Throwable t) {
                result = null;
            }
            String r = result == null || result.isBlank() ? null : result.trim();
            Minecraft.getInstance().execute(() -> then.accept(Optional.ofNullable(r).map(Paths::get)));
        });
    }

    // ------------------------------------------------------------------ operating system dialogs

    /** Shows the platform's own dialog and returns the chosen path, or null. */
    static String system(Kind kind, String title, Path start, String filterLabel, String[] patterns) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String safeTitle = title.replaceAll("[\"'`$\\\\]", "");
        String dir = start == null ? "" : (kind == Kind.SAVE && start.getParent() != null ? start.getParent() : start).toAbsolutePath().toString();
        String name = kind == Kind.SAVE && start != null ? start.getFileName().toString() : "";
        List<String> cmd = new ArrayList<>();
        if (os.contains("win")) {
            String filter = patterns.length == 0 ? "All files|*.*" : (filterLabel == null ? "Files" : filterLabel.replace("|", "")) + "|" + String.join(";", patterns);
            String script = "Add-Type -AssemblyName System.Windows.Forms;" + switch (kind) {
                case OPEN -> "$d=New-Object System.Windows.Forms.OpenFileDialog;$d.Filter='" + ps(filter) + "';";
                case SAVE -> "$d=New-Object System.Windows.Forms.SaveFileDialog;$d.Filter='" + ps(filter) + "';$d.FileName='" + ps(name) + "';";
                case FOLDER -> "$d=New-Object System.Windows.Forms.FolderBrowserDialog;$d.Description='" + ps(safeTitle) + "';";
            } + (kind == Kind.FOLDER ? "$d.SelectedPath='" + ps(dir) + "';" : "$d.Title='" + ps(safeTitle) + "';$d.InitialDirectory='" + ps(dir) + "';")
                    + "if($d.ShowDialog() -eq 'OK'){[Console]::OutputEncoding=[Text.Encoding]::UTF8;" + (kind == Kind.FOLDER ? "$d.SelectedPath" : "$d.FileName") + "}";
            cmd.addAll(List.of("powershell", "-NoProfile", "-STA", "-Command", script));
        } else if (os.contains("mac")) {
            String where = dir.isEmpty() ? "" : " default location POSIX file \"" + dir.replace("\"", "") + "\"";
            String expr = switch (kind) {
                case OPEN -> "choose file with prompt \"" + safeTitle + "\"" + where;
                case SAVE -> "choose file name with prompt \"" + safeTitle + "\" default name \"" + name.replace("\"", "") + "\"" + where;
                case FOLDER -> "choose folder with prompt \"" + safeTitle + "\"" + where;
            };
            cmd.addAll(List.of("osascript", "-e", "POSIX path of (" + expr + ")"));
        } else {
            cmd.addAll(List.of("zenity", "--file-selection", "--title=" + safeTitle));
            if (kind == Kind.SAVE) cmd.addAll(List.of("--save", "--confirm-overwrite", "--filename=" + (dir.isEmpty() ? name : dir + "/" + name)));
            else if (!dir.isEmpty()) cmd.add("--filename=" + dir + "/");
            if (kind == Kind.FOLDER) cmd.add("--directory");
            if (kind != Kind.FOLDER && patterns.length > 0) cmd.add("--file-filter=" + (filterLabel == null ? "Files" : filterLabel) + " | " + String.join(" ", patterns));
        }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        String out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            out = r.readLine();
        }
        p.waitFor();
        return p.exitValue() == 0 ? out : null;
    }

    /** Escapes a value for a single-quoted PowerShell string. */
    private static String ps(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
