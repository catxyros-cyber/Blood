package dev.bloom.client.core.sky;

import dev.bloom.client.core.io.SafeFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads OptiFine/MCPatcher custom sky layers ({@code optifine/sky/world0/skyN.properties})
 * from a resource pack (.zip or folder) so they can be imported as MOTION skyboxes.
 *
 * <p>Supported: the layer texture (3×2 layout), fade times (used to suggest day or night),
 * blend (add / alpha-style modes), rotate/speed/axis. Not supported and reported to the
 * player: weather, biome and height conditions, and the "transition" option. Only the
 * Overworld ({@code world0}) is read. Nothing in a pack is executed; only text and PNG
 * entries matching the sky path are read, with size limits.
 */
public final class OptiFineSky {
    public static final long MAX_PROPERTIES = 64 * 1024;
    public static final long MAX_TEXTURE = 32L * 1024 * 1024;
    public static final int MAX_ENTRIES = 50_000;
    public static final int MAX_LAYERS = 64;

    private static final Pattern PROPS = Pattern.compile("assets/minecraft/(optifine|mcpatcher)/sky/world0/([A-Za-z0-9_\\-]+)\\.properties");

    public record Layer(String name, String propertiesPath, String texturePath, int fadeInStart, int fadeInEnd,
                        int fadeOutStart, int fadeOutEnd, String blend, boolean rotate, float speed, float[] axis,
                        List<String> unsupported) {
        /** "day", "night" or "any", from when the layer is visible. */
        public String suggestedTime() {
            if (fadeInStart < 0) return "any";
            boolean noon = visibleAt(6000), midnight = visibleAt(18000);
            if (midnight && !noon) return "night";
            if (noon && !midnight) return "day";
            return "any";
        }

        /** True when the layer is at least partly visible at the given time of day (ticks). */
        public boolean visibleAt(int tick) {
            if (fadeInStart < 0) return true;
            int start = fadeInStart, end = fadeOutEnd;
            int t = Math.floorMod(tick - start, 24000);
            int len = Math.floorMod(end - start, 24000);
            return t <= len;
        }
    }

    public record ScanResult(List<Layer> layers, List<String> problems) {
    }

    private OptiFineSky() {
    }

    /** "hh:mm" (sunrise 6:00) to day ticks (sunrise 0). Returns -1 when invalid. */
    public static int parseTime(String s) {
        if (s == null) return -1;
        Matcher m = Pattern.compile("\\s*(\\d{1,2}):(\\d{2})\\s*").matcher(s);
        if (!m.matches()) return -1;
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        if (h > 24 || min > 59) return -1;
        return Math.floorMod(h * 1000 + Math.round(min / 60f * 1000) - 6000, 24000);
    }

    /** Parses one layer's properties text. {@code propsPath} is its path inside the pack. */
    public static Layer parseLayer(String propsPath, String text) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(text));
        String dir = propsPath.substring(0, propsPath.lastIndexOf('/') + 1);
        String stem = propsPath.substring(dir.length(), propsPath.length() - ".properties".length());
        String source = p.getProperty("source");
        String tex = source == null || source.isBlank() ? dir + stem + ".png" : resolve(dir, source.trim());
        if (tex == null) throw new IOException("Unsupported texture path: " + source);
        List<String> unsupported = new ArrayList<>();
        for (String k : List.of("weather", "biomes", "heights", "transition")) if (p.getProperty(k) != null) unsupported.add(k);
        int fis = parseTime(p.getProperty("startFadeIn"));
        int fie = parseTime(p.getProperty("endFadeIn"));
        int fos = parseTime(p.getProperty("startFadeOut"));
        int foe = parseTime(p.getProperty("endFadeOut"));
        if (fis < 0 || fie < 0 || foe < 0) {
            fis = fie = fos = foe = -1; // no (valid) fade: always visible
        } else if (fos < 0) {
            // OptiFine allows omitting startFadeOut: it mirrors the fade-in duration.
            fos = Math.floorMod(foe - Math.floorMod(fie - fis, 24000), 24000);
        }
        String blend = p.getProperty("blend", "add").trim().toLowerCase(Locale.ROOT);
        boolean rotate = !"false".equalsIgnoreCase(p.getProperty("rotate", "true").trim());
        float speed = parseFloat(p.getProperty("speed"), 1f);
        float[] axis = {0f, 0f, 1f};
        String ax = p.getProperty("axis");
        if (ax != null) {
            String[] parts = ax.trim().split("\\s+");
            if (parts.length == 3) {
                float[] a = {parseFloat(parts[0], 0), parseFloat(parts[1], 0), parseFloat(parts[2], 0)};
                if (a[0] != 0 || a[1] != 0 || a[2] != 0) axis = a;
            }
        }
        return new Layer(stem, propsPath, tex, fis, fie, fos, foe, blend, rotate, speed, axis, unsupported);
    }

    private static float parseFloat(String s, float def) {
        if (s == null) return def;
        try {
            float f = Float.parseFloat(s.trim());
            return Float.isFinite(f) ? f : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Resolves an OptiFine texture path to a path inside the pack, or null if it escapes. */
    static String resolve(String dir, String source) {
        String path;
        if (source.startsWith("./")) path = dir + source.substring(2);
        else if (source.startsWith("~/")) path = "assets/minecraft/optifine/" + source.substring(2);
        else if (source.startsWith("assets/")) path = source;
        else if (source.contains(":")) {
            String ns = source.substring(0, source.indexOf(':'));
            if (!ns.matches("[a-z0-9_.\\-]+")) return null;
            path = "assets/" + ns + "/" + source.substring(source.indexOf(':') + 1);
        } else path = "assets/minecraft/" + source;
        path = path.replace('\\', '/');
        if (!path.endsWith(".png")) path = path + ".png";
        for (String seg : path.split("/")) if (seg.equals("..") || seg.equals(".") || seg.isEmpty()) return null;
        return path;
    }

    // ------------------------------------------------------------------ pack access

    /** Read-only view of a resource pack (zip or folder). */
    public interface Pack extends AutoCloseable {
        List<String> list() throws IOException;

        byte[] read(String path, long maxBytes) throws IOException;

        @Override
        void close() throws IOException;
    }

    public static Pack open(Path source) throws IOException {
        if (Files.isDirectory(source)) return new FolderPack(source);
        return new ZipPack(source);
    }

    public static ScanResult scan(Pack pack) throws IOException {
        List<Layer> layers = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<String> entries = pack.list();
        java.util.Set<String> all = new java.util.HashSet<>(entries);
        for (String e : entries) {
            if (!PROPS.matcher(e).matches()) continue;
            if (layers.size() >= MAX_LAYERS) {
                problems.add("Only the first " + MAX_LAYERS + " sky layers were read.");
                break;
            }
            try {
                String text = new String(pack.read(e, MAX_PROPERTIES), StandardCharsets.UTF_8);
                Layer l = parseLayer(e, text);
                if (!all.contains(l.texturePath())) {
                    problems.add(l.name() + ": texture " + l.texturePath() + " is missing from the pack.");
                    continue;
                }
                layers.add(l);
            } catch (IOException ex) {
                problems.add(e.substring(e.lastIndexOf('/') + 1) + ": " + ex.getMessage());
            }
        }
        layers.sort((a, b) -> naturalCompare(a.name(), b.name()));
        if (layers.isEmpty() && problems.isEmpty()) {
            problems.add("No OptiFine sky layers were found (expected assets/minecraft/optifine/sky/world0/sky1.properties).");
        }
        return new ScanResult(layers, problems);
    }

    private static int naturalCompare(String a, String b) {
        String na = a.replaceAll("\\D", ""), nb = b.replaceAll("\\D", "");
        if (!na.isEmpty() && !nb.isEmpty() && na.length() < 10 && nb.length() < 10) {
            int c = Integer.compare(Integer.parseInt(na), Integer.parseInt(nb));
            if (c != 0) return c;
        }
        return a.compareTo(b);
    }

    private static final class ZipPack implements Pack {
        private final ZipFile zip;

        ZipPack(Path file) throws IOException {
            if (!Files.isRegularFile(file)) throw new IOException("Not a file");
            this.zip = new ZipFile(file.toFile());
        }

        @Override
        public List<String> list() throws IOException {
            List<String> out = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            int n = 0;
            while (en.hasMoreElements()) {
                if (++n > MAX_ENTRIES) throw new IOException("The pack has too many files.");
                ZipEntry e = en.nextElement();
                if (!e.isDirectory()) out.add(e.getName());
            }
            return out;
        }

        @Override
        public byte[] read(String path, long maxBytes) throws IOException {
            ZipEntry e = zip.getEntry(path);
            if (e == null || e.isDirectory()) throw new IOException("Missing " + path);
            try (InputStream in = zip.getInputStream(e)) {
                return SafeFiles.readLimited(in, maxBytes);
            }
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }

    private static final class FolderPack implements Pack {
        private final Path root;

        FolderPack(Path root) throws IOException {
            this.root = root.toRealPath();
        }

        @Override
        public List<String> list() throws IOException {
            Path sky = root.resolve("assets/minecraft");
            if (!Files.isDirectory(sky)) return List.of();
            List<String> out = new ArrayList<>();
            try (Stream<Path> s = Files.walk(sky, 8)) {
                s.limit(MAX_ENTRIES).filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .forEach(p -> out.add(root.relativize(p).toString().replace('\\', '/')));
            }
            return out;
        }

        @Override
        public byte[] read(String path, long maxBytes) throws IOException {
            Path p = root.resolve(path).normalize();
            if (!p.startsWith(root)) throw new IOException("Invalid path");
            if (!p.toRealPath().startsWith(root)) throw new IOException("Invalid path");
            return SafeFiles.readLimited(p, maxBytes);
        }

        @Override
        public void close() {
        }
    }
}
