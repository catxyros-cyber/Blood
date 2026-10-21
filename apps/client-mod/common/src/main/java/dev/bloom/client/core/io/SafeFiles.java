package dev.bloom.client.core.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.regex.Pattern;

/** File helpers shared by settings, profiles and imports: atomic writes, size limits, safe names. */
public final class SafeFiles {
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9][a-z0-9_\\-]{0,47}");
    private static final java.util.Set<String> WINDOWS_RESERVED = java.util.Set.of(
            "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private SafeFiles() {
    }

    /** Writes via a temp file in the same folder, then renames over the target. */
    public static void writeAtomic(Path target, byte[] data) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "." + target.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, data);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static void writeAtomic(Path target, String text) throws IOException {
        writeAtomic(target, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads a whole file, refusing anything larger than {@code maxBytes} (and non-regular files). */
    public static byte[] readLimited(Path file, long maxBytes) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a regular file");
        long size = Files.size(file);
        if (size > maxBytes) throw new IOException("File is too large (" + size / 1024 + " KB, limit " + maxBytes / 1024 + " KB)");
        try (InputStream in = Files.newInputStream(file)) {
            return readLimited(in, maxBytes);
        }
    }

    public static byte[] readLimited(InputStream in, long maxBytes) throws IOException {
        byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
        if (data.length > maxBytes) throw new IOException("Data is too large (limit " + maxBytes / 1024 + " KB)");
        return data;
    }

    public static String readText(Path file, long maxBytes) throws IOException {
        return new String(readLimited(file, maxBytes), StandardCharsets.UTF_8);
    }

    /**
     * Turns any display name into a safe file stem: lowercase a-z, 0-9, '_' and '-',
     * at most 48 characters, never a Windows device name. Falls back to {@code fallback}.
     */
    public static String slug(String name, String fallback) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        StringBuilder b = new StringBuilder();
        boolean dash = false;
        for (int i = 0; i < s.length() && b.length() < 48; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                b.append(c);
                dash = false;
            } else if (b.length() > 0 && !dash) {
                b.append('_');
                dash = true;
            }
        }
        while (b.length() > 0 && b.charAt(b.length() - 1) == '_') b.setLength(b.length() - 1);
        String out = b.toString();
        if (out.isEmpty() || WINDOWS_RESERVED.contains(out)) return fallback;
        return out;
    }

    public static boolean isSafeName(String stem) {
        return stem != null && SAFE_NAME.matcher(stem).matches() && !WINDOWS_RESERVED.contains(stem);
    }

    /**
     * Resolves {@code name} (a bare file name) inside {@code dir}, refusing anything that
     * would escape it ("..", separators, absolute paths, drive letters, alternate data streams).
     */
    public static Path resolveInside(Path dir, String name) throws IOException {
        if (name == null || name.isEmpty() || name.length() > 128) throw new IOException("Invalid file name");
        if (name.contains("/") || name.contains("\\") || name.contains(":") || name.contains("\0")
                || name.equals(".") || name.equals("..") || name.startsWith(".")) {
            throw new IOException("Invalid file name");
        }
        Path base = dir.toAbsolutePath().normalize();
        Path p = base.resolve(name).normalize();
        if (!p.getParent().equals(base)) throw new IOException("Invalid file name");
        return p;
    }

    /** Picks "stem.ext", "stem_2.ext", … so an existing file is never overwritten. */
    public static Path uniqueIn(Path dir, String stem, String ext) throws IOException {
        Path p = resolveInside(dir, stem + ext);
        for (int i = 2; Files.exists(p); i++) {
            if (i > 999) throw new IOException("Too many files named " + stem);
            p = resolveInside(dir, stem + "_" + i + ext);
        }
        return p;
    }
}
