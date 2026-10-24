package dev.bloom.client.core.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/** Reads the current Windows media session without blocking the Minecraft render thread. */
public final class MusicSession {
    public record Track(String title, String artist, String album, long positionMs, boolean playing, byte[] artwork) {}
    private static final AtomicReference<Track> CURRENT = new AtomicReference<>();
    private static volatile boolean started;

    private MusicSession() {}

    public static Track current() {
        if (!started) start();
        return CURRENT.get();
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) return;
        Path script = null;
        try (var in = MusicSession.class.getResourceAsStream("/assets/bloomclient/music-session.ps1")) {
            if (in == null) throw new IOException("missing media-session helper");
            script = Files.createTempFile("motion-music-", ".ps1");
            Files.copy(in, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            script.toFile().deleteOnExit();
            Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.toAbsolutePath().toString()).start();
            Path helper = script;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                process.destroy();
                try { Files.deleteIfExists(helper); } catch (IOException ignored) { }
            }, "MOTION media cleanup"));
            Thread reader = new Thread(() -> read(process), "MOTION media session");
            reader.setDaemon(true);
            reader.start();
            Thread errors = new Thread(() -> {
                try (var err = process.getErrorStream()) { err.transferTo(java.io.OutputStream.nullOutputStream()); }
                catch (IOException ignored) { }
            }, "MOTION media errors");
            errors.setDaemon(true);
            errors.start();
        } catch (Exception ignored) {
            if (script != null) try { Files.deleteIfExists(script); } catch (IOException ignored2) { }
        }
    }

    private static void read(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                    String title = str(o, "title"), artist = str(o, "artist"), album = str(o, "album");
                    byte[] artwork = null;
                    String encoded = str(o, "artwork");
                    if (!encoded.isBlank()) artwork = Base64.getDecoder().decode(encoded);
                    if (artwork != null && artwork.length > 512 * 1024) artwork = null;
                    long position = o.has("positionMs") ? Math.max(0, o.get("positionMs").getAsLong()) : 0;
                    boolean playing = o.has("playing") && o.get("playing").getAsBoolean();
                    CURRENT.set(title.isBlank() && artist.isBlank() ? null : new Track(title, artist, album, position, playing, artwork));
                } catch (RuntimeException ignored) { }
            }
        } catch (IOException ignored) { }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}