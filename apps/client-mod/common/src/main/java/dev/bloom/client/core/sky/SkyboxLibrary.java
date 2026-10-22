package dev.bloom.client.core.sky;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.io.SafeFiles;
import dev.bloom.client.core.profile.ProfileCodec;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * The player's own skyboxes in {@code config/bloomclient/skyboxes/}: {@code <id>.png} (a 3×2
 * atlas) plus an optional {@code <id>.json} with its name and render options. Imports always
 * write new files under names MOTION chooses, so an import can never overwrite or escape the
 * folder. Removing a skybox moves it to {@code skyboxes/deleted/}.
 */
public final class SkyboxLibrary {
    public static final long MAX_PNG_BYTES = 32L * 1024 * 1024;
    public static final int MAX_SKYBOXES = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Render options stored next to a custom skybox. */
    public record Meta(String name, String blend, boolean rotate, float speed, float[] axis, String suggested, int tint, String origin) {
        public static Meta simple(String name, int tint, String origin) {
            return new Meta(name, "alpha", false, 1f, new float[]{0, 0, 1}, "any", tint, origin);
        }

        /** Blend modes MOTION can draw. Others fall back to alpha blending. */
        public boolean additive() {
            return switch (blend) {
                case "add", "screen", "dodge" -> true;
                default -> false;
            };
        }
    }

    public record Imported(SkyboxCatalog.Entry entry, Meta meta, List<String> notes) {
    }

    private final Path dir;
    private final ImageCodec codec;
    private final java.util.Map<String, Meta> metas = new java.util.concurrent.ConcurrentHashMap<>();

    public SkyboxLibrary(Path dir, ImageCodec codec) {
        this.dir = dir;
        this.codec = codec;
    }

    public Path dir() {
        return dir;
    }

    public Meta meta(String entryId) {
        return metas.get(entryId);
    }

    public Path textureFile(String entryId) throws IOException {
        if (!entryId.startsWith(SkyboxCatalog.CUSTOM_PREFIX)) throw new IOException("Not a custom skybox");
        return SafeFiles.resolveInside(dir, entryId.substring(SkyboxCatalog.CUSTOM_PREFIX.length()) + ".png");
    }

    /** Re-reads the folder and publishes the list to {@link SkyboxCatalog}. */
    public void scan() {
        metas.clear();
        List<SkyboxCatalog.Entry> entries = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.png")) {
                for (Path f : ds) {
                    if (entries.size() >= MAX_SKYBOXES) break;
                    String file = f.getFileName().toString();
                    String stem = file.substring(0, file.length() - 4);
                    if (!SafeFiles.isSafeName(stem) || !Files.isRegularFile(f)) continue;
                    int face = prepareFile(f, stem);
                    if (face <= 0) continue;
                    Meta m = readMeta(stem);
                    String id = SkyboxCatalog.CUSTOM_PREFIX + stem;
                    metas.put(id, m);
                    SkyboxCatalog.setCustomFace(id, face);
                    entries.add(new SkyboxCatalog.Entry(id, m.name(), describe(m), false, m.tint()));
                }
            } catch (IOException e) {
                Log.warn("Could not list custom skyboxes", e);
            }
        }
        entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        SkyboxCatalog.setCustom(entries);
    }

    /**
     * Checks a PNG found in the folder (e.g. placed there by the launcher or by hand). A 3×2
     * atlas is used as-is; a 2:1 panorama is converted to an atlas, with the original kept in
     * {@code skyboxes/originals/}. Anything else is skipped (and logged), never deleted.
     */
    /** Returns the face size of a usable skybox file (converting it first if needed), or 0 to skip it. */
    private int prepareFile(Path f, String stem) {
        try {
            byte[] head;
            try (java.io.InputStream in = Files.newInputStream(f)) {
                head = in.readNBytes(64);
            }
            PngInfo info = PngInfo.read(head);
            if (info == null) {
                Log.warn("Skipping skybox " + f.getFileName() + ": not a PNG (or larger than " + PngInfo.MAX_DIMENSION + " px)");
                return 0;
            }
            if (CubeLayout.checkAtlasSize(info.width, info.height) == null) return info.width / 3;
            boolean cross = CubeFormats.looksLikeCross(info.width, info.height);
            if (!Panorama.looksLikePanorama(info.width, info.height) && !cross) {
                Log.warn("Skipping skybox " + f.getFileName() + ": " + CubeLayout.checkAtlasSize(info.width, info.height));
                return 0;
            }
            byte[] png = SafeFiles.readLimited(f, MAX_PNG_BYTES);
            RgbaImage src = codec.decodePng(png);
            RgbaImage atlas = cross ? CubeFormats.crossToAtlas(src) : Panorama.toAtlas(src, Panorama.faceSizeFor(src.width));
            Path originals = dir.resolve("originals");
            Files.createDirectories(originals);
            Files.copy(f, SafeFiles.uniqueIn(originals, stem, ".png"));
            SafeFiles.writeAtomic(f, codec.encodePng(atlas));
            Log.info("Converted " + (cross ? "cubemap cross " : "360° panorama ") + f.getFileName() + " to a skybox (original kept in skyboxes/originals)");
            return atlas.width / 3;
        } catch (Exception | OutOfMemoryError e) {
            Log.warn("Could not prepare skybox " + f.getFileName(), e);
            return 0;
        }
    }

    private static String describe(Meta m) {
        String src = m.origin() == null || m.origin().isEmpty() ? "Imported skybox" : "Imported from " + m.origin();
        return switch (m.suggested()) {
            case "night" -> src + " · best at night";
            case "day" -> src + " · best by day";
            default -> src;
        };
    }

    private Meta readMeta(String stem) {
        String fallbackName = stem.replace('_', ' ');
        try {
            Path f = SafeFiles.resolveInside(dir, stem + ".json");
            if (!Files.exists(f)) return Meta.simple(fallbackName, 0x8899AA, "");
            JsonObject o = JsonParser.parseString(SafeFiles.readText(f, 16 * 1024)).getAsJsonObject();
            String name = ProfileCodec.cleanName(str(o, "name", fallbackName));
            String blend = str(o, "blend", "alpha").toLowerCase(Locale.ROOT);
            boolean rotate = o.has("rotate") && o.get("rotate").isJsonPrimitive() && o.get("rotate").getAsBoolean();
            float speed = num(o, "speed", 1f);
            float[] axis = {0, 0, 1};
            JsonElement ax = o.get("axis");
            if (ax != null && ax.isJsonArray() && ax.getAsJsonArray().size() == 3) {
                for (int i = 0; i < 3; i++) {
                    JsonElement e = ax.getAsJsonArray().get(i);
                    axis[i] = e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : 0;
                }
            }
            String suggested = str(o, "suggested", "any");
            if (!List.of("day", "night", "any").contains(suggested)) suggested = "any";
            Integer tint = dev.bloom.client.core.settings.ColorSetting.parseHex(str(o, "tint", "#8899AA"));
            return new Meta(name.isEmpty() ? fallbackName : name, blend, rotate, Float.isFinite(speed) ? speed : 1f, axis, suggested,
                    tint == null ? 0x8899AA : tint, ProfileCodec.cleanText(str(o, "origin", ""), 64));
        } catch (Exception e) {
            Log.warn("Ignoring unreadable skybox settings for " + stem, e);
            return Meta.simple(fallbackName, 0x8899AA, "");
        }
    }

    private static String str(JsonObject o, String k, String def) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static float num(JsonObject o, String k, float def) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : def;
    }

    // ------------------------------------------------------------------ import

    /**
     * Imports a PNG: a 3×2 skybox atlas is copied as-is (re-encoded); a 2:1 panorama is
     * converted into an atlas. Anything else is rejected with an explanation.
     */
    public Imported importPng(byte[] png, String originalFileName) throws IOException {
        List<String> notes = new ArrayList<>();
        RgbaImage atlas = toAtlas(png, notes);
        String name = displayName(originalFileName);
        Meta meta = Meta.simple(name, averageColor(atlas), ProfileCodec.cleanText(baseName(originalFileName), 64));
        return save(atlas, meta, notes);
    }

    /**
     * Decodes and validates one PNG and returns it as a 3×2 atlas: an atlas as-is, a 2:1 panorama or a
     * 4×3 cubemap cross converted. Anything else is rejected with an explanation of what fits.
     */
    RgbaImage toAtlas(byte[] png, List<String> notes) throws IOException {
        if (png.length > MAX_PNG_BYTES) throw new IOException("The image is larger than 32 MB.");
        PngInfo info = PngInfo.read(png);
        if (info == null) throw new IOException("That file isn't a PNG image (or it is larger than 8192 pixels).");
        String problem = CubeLayout.checkAtlasSize(info.width, info.height);
        if (problem == null) return codec.decodePng(png);
        if (Panorama.looksLikePanorama(info.width, info.height)) {
            notes.add("Converted from a 360° panorama (" + info.width + "×" + info.height + ").");
            RgbaImage pano = codec.decodePng(png);
            return Panorama.toAtlas(pano, Panorama.faceSizeFor(pano.width));
        }
        if (CubeFormats.looksLikeCross(info.width, info.height)) {
            notes.add("Converted from a cubemap cross (" + info.width + "×" + info.height + ", " + info.width / 4 + " px faces).");
            return CubeFormats.crossToAtlas(codec.decodePng(png));
        }
        throw new IOException(problem + " Also accepted: 360° panoramas (2:1), cubemap crosses (4:3) and a folder or .zip with six face images.");
    }

    /**
     * Imports six separate face images (file name -> PNG bytes), e.g. px/nx/py/ny/pz/nz.png or
     * right/left/top/bottom/front/back.png. Missing, non-square or mismatched faces are reported.
     */
    public Imported importFaces(Map<String, byte[]> files, String name) throws IOException {
        Map<String, byte[]> byFace = new LinkedHashMap<>();
        Map<String, int[]> sizes = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String face = CubeFormats.faceFor(e.getKey());
            if (face == null) continue;
            if (byFace.containsKey(face)) throw new IOException("Two files look like the same face: " + e.getKey() + ".");
            PngInfo info = PngInfo.read(e.getValue());
            if (info == null) throw new IOException(baseName(e.getKey()) + " isn't a PNG image.");
            byFace.put(face, e.getValue());
            sizes.put(face, new int[]{info.width, info.height});
        }
        if (byFace.isEmpty()) throw new IOException("No face images found. Name them px/nx/py/ny/pz/nz.png or right/left/top/bottom/front/back.png.");
        List<String> problems = CubeFormats.checkFaces(sizes);
        if (!problems.isEmpty()) throw new IOException(String.join(" ", problems));
        Map<String, RgbaImage> faces = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : byFace.entrySet()) faces.put(e.getKey(), codec.decodePng(e.getValue()));
        RgbaImage atlas = CubeFormats.facesToAtlas(faces);
        Meta meta = Meta.simple(displayName(name), averageColor(atlas), ProfileCodec.cleanText(baseName(name), 64));
        List<String> notes = new ArrayList<>();
        notes.add("Built from six face images (" + atlas.width / 3 + " px faces).");
        return save(atlas, meta, notes);
    }

    /** Replaces the image of an imported skybox, keeping its name and settings. */
    public List<String> replace(String entryId, byte[] png) throws IOException {
        Meta m = metas.get(entryId);
        if (m == null) throw new IOException("Only imported skyboxes can be replaced.");
        List<String> notes = new ArrayList<>();
        RgbaImage atlas = toAtlas(png, notes);
        SafeFiles.writeAtomic(textureFile(entryId), codec.encodePng(atlas));
        writeMeta(entryId.substring(SkyboxCatalog.CUSTOM_PREFIX.length()),
                new Meta(m.name(), m.blend(), m.rotate(), m.speed(), m.axis(), m.suggested(), averageColor(atlas), m.origin()));
        scan();
        return notes;
    }

    /** Imports one OptiFine sky layer from a pack opened with {@link OptiFineSky#open}. */
    public Imported importOptiFineLayer(OptiFineSky.Pack pack, OptiFineSky.Layer layer, String packName) throws IOException {
        byte[] png = pack.read(layer.texturePath(), MAX_PNG_BYTES);
        PngInfo info = PngInfo.read(png);
        if (info == null) throw new IOException(layer.name() + ": the texture isn't a valid PNG.");
        String problem = CubeLayout.checkAtlasSize(info.width, info.height);
        if (problem != null) throw new IOException(layer.name() + ": " + problem);
        RgbaImage atlas = codec.decodePng(png);
        List<String> notes = new ArrayList<>();
        if (!layer.unsupported().isEmpty()) {
            notes.add("Ignored OptiFine options: " + String.join(", ", layer.unsupported()) + ". MOTION shows the layer whenever it's selected.");
        }
        Meta base = new Meta(ProfileCodec.cleanName(displayName(packName) + " " + layer.name()), layer.blend(), layer.rotate(), layer.speed(), layer.axis(),
                layer.suggestedTime(), averageColor(atlas), ProfileCodec.cleanText(baseName(packName), 64));
        if (!base.additive() && !List.of("alpha", "replace", "overlay").contains(layer.blend())) {
            notes.add("Blend mode \"" + layer.blend() + "\" isn't supported; using normal blending.");
        }
        return save(atlas, base, notes);
    }

    private Imported save(RgbaImage atlas, Meta meta, List<String> notes) throws IOException {
        Files.createDirectories(dir);
        if (SkyboxCatalog.custom().size() >= MAX_SKYBOXES) throw new IOException("You have the maximum of " + MAX_SKYBOXES + " custom skyboxes.");
        String stem = SafeFiles.slug(meta.name(), "skybox");
        Path png = SafeFiles.uniqueIn(dir, stem, ".png");
        String finalStem = png.getFileName().toString();
        finalStem = finalStem.substring(0, finalStem.length() - 4);
        SafeFiles.writeAtomic(png, codec.encodePng(atlas));
        writeMeta(finalStem, meta);
        scan();
        String id = SkyboxCatalog.CUSTOM_PREFIX + finalStem;
        return new Imported(SkyboxCatalog.find(id), metas.get(id), notes);
    }

    private void writeMeta(String stem, Meta m) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("format", 1);
        o.addProperty("name", m.name());
        o.addProperty("blend", m.blend());
        o.addProperty("rotate", m.rotate());
        o.addProperty("speed", m.speed());
        JsonArray ax = new JsonArray();
        for (float f : m.axis()) ax.add(f);
        o.add("axis", ax);
        o.addProperty("suggested", m.suggested());
        o.addProperty("tint", dev.bloom.client.core.settings.ColorSetting.hex(m.tint()));
        if (m.origin() != null && !m.origin().isEmpty()) o.addProperty("origin", m.origin());
        SafeFiles.writeAtomic(SafeFiles.resolveInside(dir, stem + ".json"), GSON.toJson(o));
    }

    public void rename(String entryId, String newName) throws IOException {
        Meta m = metas.get(entryId);
        String clean = ProfileCodec.cleanName(newName);
        if (m == null || clean.isEmpty()) throw new IOException("Enter a name.");
        writeMeta(entryId.substring(SkyboxCatalog.CUSTOM_PREFIX.length()),
                new Meta(clean, m.blend(), m.rotate(), m.speed(), m.axis(), m.suggested(), m.tint(), m.origin()));
        scan();
    }

    /** Moves the skybox's files into skyboxes/deleted/. */
    public void remove(String entryId) throws IOException {
        Path png = textureFile(entryId);
        String stem = entryId.substring(SkyboxCatalog.CUSTOM_PREFIX.length());
        Path trash = dir.resolve("deleted");
        Files.createDirectories(trash);
        long t = System.currentTimeMillis();
        if (Files.exists(png)) Files.move(png, SafeFiles.uniqueIn(trash, stem + "-" + t, ".png"));
        Path json = SafeFiles.resolveInside(dir, stem + ".json");
        if (Files.exists(json)) Files.move(json, SafeFiles.uniqueIn(trash, stem + "-" + t, ".json"));
        scan();
    }

    static String baseName(String path) {
        String n = path == null ? "" : path;
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        return slash >= 0 ? n.substring(slash + 1) : n;
    }

    static String displayName(String fileName) {
        String n = baseName(fileName);
        n = n.replaceAll("(?i)\\.(png|zip|json)$", "").replace('_', ' ').replace('-', ' ');
        String clean = ProfileCodec.cleanName(n);
        return clean.isEmpty() ? "Custom skybox" : clean;
    }

    /** Average colour of the atlas (sampled), used for previews and menu swatches. */
    public static int averageColor(RgbaImage img) {
        long r = 0, g = 0, b = 0, n = 0;
        int step = Math.max(1, Math.min(img.width, img.height) / 64);
        for (int y = 0; y < img.height; y += step) {
            for (int x = 0; x < img.width; x += step) {
                int c = img.get(x, y);
                int a = c >>> 24;
                if (a < 16) continue;
                r += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
                n++;
            }
        }
        if (n == 0) return 0x8899AA;
        return (int) ((r / n) << 16 | (g / n) << 8 | (b / n));
    }
}
