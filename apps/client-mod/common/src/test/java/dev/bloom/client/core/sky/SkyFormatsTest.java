package dev.bloom.client.core.sky;

import com.google.gson.JsonParser;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.ChoiceSetting;
import dev.bloom.client.core.visual.Themes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SkyFormatsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private SkyboxLibrary lib;
    private final ImageIoCodec codec = new ImageIoCodec();

    @Before
    public void setUp() throws Exception {
        lib = new SkyboxLibrary(tmp.newFolder("skyboxes").toPath(), codec);
        lib.scan();
    }

    /** A smooth test sky: colour encodes direction, so any wrong mapping shows up as a big error. */
    private static int skyColor(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        int r = Math.round((x / l * 0.5f + 0.5f) * 255), g = Math.round((y / l * 0.5f + 0.5f) * 255), b = Math.round((z / l * 0.5f + 0.5f) * 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Six OpenGL-convention faces of {@link #skyColor} (Minecraft z = -GL z). */
    private static Map<String, RgbaImage> faces(int n) {
        Map<String, RgbaImage> out = new LinkedHashMap<>();
        for (int f = 0; f < 6; f++) {
            RgbaImage img = new RgbaImage(n, n);
            for (int py = 0; py < n; py++) {
                for (int px = 0; px < n; px++) {
                    float sc = 2 * (px + 0.5f) / n - 1, tc = 2 * (py + 0.5f) / n - 1;
                    float[] d = switch (f) { // OpenGL cubemap face directions
                        case 0 -> new float[]{1, -tc, -sc};
                        case 1 -> new float[]{-1, -tc, sc};
                        case 2 -> new float[]{sc, 1, tc};
                        case 3 -> new float[]{sc, -1, -tc};
                        case 4 -> new float[]{sc, -tc, 1};
                        default -> new float[]{-sc, -tc, -1};
                    };
                    img.set(px, py, skyColor(d[0], d[1], -d[2]));
                }
            }
            out.put(CubeFormats.FACES[f], img);
        }
        return out;
    }

    private static int maxError(RgbaImage atlas) {
        int worst = 0;
        java.util.Random r = new java.util.Random(4);
        for (int i = 0; i < 4000; i++) {
            float x = (float) r.nextGaussian(), y = (float) r.nextGaussian(), z = (float) r.nextGaussian();
            int got = CubeFormats.sampleAtlas(atlas.argb, atlas.width, atlas.height, x, y, z), want = skyColor(x, y, z);
            for (int sh = 0; sh < 24; sh += 8) worst = Math.max(worst, Math.abs(((got >> sh) & 255) - ((want >> sh) & 255)));
        }
        return worst;
    }

    @Test
    public void sixFacesAndCrossesMapToMinecraftDirections() {
        RgbaImage atlas = CubeFormats.facesToAtlas(faces(64));
        assertEquals(192, atlas.width);
        assertTrue("faces land in the right place and orientation (max error " + maxError(atlas) + ")", maxError(atlas) <= 8);

        // The same faces laid out as a horizontal cross.
        Map<String, RgbaImage> f = faces(64);
        RgbaImage cross = new RgbaImage(256, 192);
        int[][] pos = {{2, 1}, {0, 1}, {1, 0}, {1, 2}, {1, 1}, {3, 1}};
        for (int i = 0; i < 6; i++) {
            RgbaImage img = f.get(CubeFormats.FACES[i]);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) cross.set(pos[i][0] * 64 + x, pos[i][1] * 64 + y, img.get(x, y));
        }
        assertTrue(CubeFormats.looksLikeCross(256, 192));
        assertFalse(CubeFormats.looksLikeCross(256, 128));
        assertTrue(maxError(CubeFormats.crossToAtlas(cross)) <= 8);
    }

    @Test
    public void facesAreNamedFlexiblyAndValidated() {
        assertEquals("px", CubeFormats.faceFor("sky/right.png"));
        assertEquals("nz", CubeFormats.faceFor("Cloudy_back.PNG"));
        assertEquals("py", CubeFormats.faceFor("posy.png"));
        assertEquals("ny", CubeFormats.faceFor("my sky-down.png"));
        assertNull(CubeFormats.faceFor("readme.png"));
        Map<String, int[]> sizes = new LinkedHashMap<>();
        for (String f : List.of("px", "nx", "py", "pz", "nz")) sizes.put(f, new int[]{64, 64});
        sizes.put("nz", new int[]{64, 32});
        List<String> problems = CubeFormats.checkFaces(sizes);
        assertTrue(problems.toString(), problems.stream().anyMatch(s -> s.contains("Missing the bottom")));
        assertTrue(problems.toString(), problems.stream().anyMatch(s -> s.contains("isn't square")));
    }

    @Test
    public void libraryImportsCrossesAndFacesAndExplainsBadInput() throws Exception {
        // cross
        RgbaImage cross = new RgbaImage(256, 192);
        java.util.Arrays.fill(cross.argb, 0xFF336699);
        SkyboxLibrary.Imported imp = lib.importPng(codec.encodePng(cross), "My Cross.png");
        assertTrue(imp.notes().toString(), imp.notes().get(0).contains("cubemap cross"));
        assertEquals(64, SkyboxCatalog.nativeFace(imp.entry().id()));
        assertEquals("64 px faces", SkyboxCatalog.resolutionLabel(imp.entry().id()));

        // six faces, one missing
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String n : List.of("right", "left", "top", "bottom", "front")) files.put(n + ".png", ImageIoCodec.png(32, 32, 0xFF884422));
        try {
            lib.importFaces(files, "set");
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Missing the back (-Z) face"));
        }
        files.put("back.png", ImageIoCodec.png(32, 32, 0xFF884422));
        SkyboxLibrary.Imported faces = lib.importFaces(files, "Six faces");
        assertEquals(96, faces.entry() == null ? -1 : SkyboxCatalog.nativeFace(faces.entry().id()) * 3);

        // something that isn't a sky
        try {
            lib.importPng(ImageIoCodec.png(100, 30, 0xFF000000), "banner.png");
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("cubemap crosses (4:3)"));
        }

        // replace keeps the name, swaps the pixels
        lib.replace(imp.entry().id(), ImageIoCodec.png(96, 64, 0xFFAA0000));
        assertEquals("My Cross", SkyboxCatalog.find(imp.entry().id()).name());
        assertEquals(32, SkyboxCatalog.nativeFace(imp.entry().id()));
    }

    @Test
    public void shrinkingAveragesWithinFacesOnly() {
        // Left half of the atlas red, right half blue: faces must not bleed into each other.
        int n = 8;
        int[] px = new int[n * 3 * n * 2];
        for (int y = 0; y < n * 2; y++) for (int x = 0; x < n * 3; x++) px[y * n * 3 + x] = (x / n) % 2 == 0 ? 0xFF0000FF : 0xFFFF0000;
        int[] size = new int[2];
        int[] out = CubeFormats.shrinkAtlas(px, n * 3, n * 2, 2, size);
        assertEquals(6, size[0]);
        assertEquals(4, size[1]);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 6; x++) assertEquals((x / 2) % 2 == 0 ? 0xFF0000FF : 0xFFFF0000, out[y * 6 + x]);
        // Already small enough: unchanged.
        assertSame(px, CubeFormats.shrinkAtlas(px, n * 3, n * 2, 1024, size));
    }

    @Test
    public void previewLooksWhereTheCameraPoints() {
        RgbaImage atlas = CubeFormats.facesToAtlas(faces(32));
        int[] out = new int[16 * 9];
        SkyView.render(atlas.argb, atlas.width, atlas.height, 0f, 0f, 60f, 16, 9, out);
        int centre = out[4 * 16 + 8];
        // Looking north (-Z): blue channel low, red/green ~mid.
        assertTrue(Integer.toHexString(centre), (centre & 255) < 40);
        SkyView.render(atlas.argb, atlas.width, atlas.height, 90f, 0f, 60f, 16, 9, out);
        assertTrue("yaw 90 looks east (+X): red high", ((out[4 * 16 + 8] >> 16) & 255) > 215);
        SkyView.render(atlas.argb, atlas.width, atlas.height, 0f, 89f, 60f, 16, 9, out);
        assertTrue("looking up: green high", ((out[4 * 16 + 8] >> 8) & 255) > 215);
    }

    @Test
    public void lookIsIdentityWhenNeutralAndRespectsChannelOrder() {
        int argb = 0xFF336699, abgr = 0xFF996633;
        assertEquals(argb, SkyLook.apply(argb, false, 1f, 1f, 0, 0f));
        assertEquals(abgr, SkyLook.apply(abgr, true, 1f, 1f, 0, 0f));
        int grey = SkyLook.apply(argb, false, 1f, 0f, 0, 0f);
        assertEquals((grey >> 16) & 255, grey & 255);
        // Same colour, either packing.
        int a = SkyLook.apply(argb, false, 1.2f, 1.3f, 0xE3192F, 0.3f);
        int b = SkyLook.apply(abgr, true, 1.2f, 1.3f, 0xE3192F, 0.3f);
        assertEquals((a >> 16) & 255, b & 255);
        assertEquals(a & 255, (b >> 16) & 255);
    }

    @Test
    public void favouritesRoundTrip() {
        SkyFavorites f = new SkyFavorites(null);
        assertTrue(f.toggle("hw_eclipse"));
        assertTrue(f.toggle("custom:my_sky"));
        assertFalse(f.toggle("hw_eclipse"));
        SkyFavorites g = new SkyFavorites(null);
        g.load(f.save(), false);
        assertEquals(List.of("custom:my_sky"), List.copyOf(g.all()));
        g.load(JsonParser.parseString("[\"ok_id\", 5, {}, \"BAD ID!\"]"), false);
        assertEquals(List.of("ok_id", "5"), List.copyOf(g.all()));
    }

    @Test
    public void themesAreRealSettingsWithRealSkies() {
        BloomSettings.init();
        List<String> themeIds = BloomSettings.THEME.options().stream().map(ChoiceSetting.Option::id).toList();
        assertEquals(Themes.ALL.size(), themeIds.size());
        for (Themes.Info t : Themes.ALL) {
            assertTrue(t.id(), themeIds.contains(t.id()));
            String sky = t.recommendedSky();
            if (sky != null && !"none".equals(sky)) assertNotNull(t.id() + " recommends a missing sky", SkyboxCatalog.find(sky));
        }
        int hw = 0;
        for (SkyboxCatalog.Entry e : SkyboxCatalog.BUILT_IN) {
            if (e.id().startsWith("hw_")) {
                hw++;
                assertEquals(1024, SkyboxCatalog.nativeFace(e.id()));
                assertEquals("4K · 1024 px faces", SkyboxCatalog.resolutionLabel(e.id()));
            }
        }
        assertEquals(9, hw);
        assertEquals("2K · 512 px faces", SkyboxCatalog.resolutionLabel("sbs_overcast"));
    }
}
