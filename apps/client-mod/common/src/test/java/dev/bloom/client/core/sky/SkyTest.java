package dev.bloom.client.core.sky;

import dev.bloom.client.core.settings.BloomSettings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class SkyTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private SkyboxLibrary lib;

    @Before
    public void setUp() throws Exception {
        SkyboxCatalog.setCustom(List.of());
        lib = new SkyboxLibrary(tmp.newFolder("skyboxes").toPath(), new ImageIoCodec());
    }

    @Test
    public void faceCentresPointAlongTheirAxes() {
        float[] d = new float[3];
        int[][] expect = {{0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}};
        for (int f = 0; f < 6; f++) {
            CubeLayout.direction(f, 0.5f, 0.5f, d);
            for (int i = 0; i < 3; i++) assertEquals("face " + f, expect[f][i], d[i], 1e-6);
        }
        // Side faces: top of the image (t=0) points up, so skies are drawn upright.
        for (int f : new int[]{CubeLayout.NORTH, CubeLayout.SOUTH, CubeLayout.EAST, CubeLayout.WEST}) {
            CubeLayout.direction(f, 0.5f, 0f, d);
            assertEquals(1f, d[1], 1e-6);
        }
        // Adjacent faces share edges: north's right edge is east's left edge.
        float[] a = new float[3], b = new float[3];
        CubeLayout.direction(CubeLayout.NORTH, 1f, 0.3f, a);
        CubeLayout.direction(CubeLayout.EAST, 0f, 0.3f, b);
        assertArrayEquals(a, b, 1e-6f);
        CubeLayout.direction(CubeLayout.EAST, 1f, 0.3f, a);
        CubeLayout.direction(CubeLayout.SOUTH, 0f, 0.3f, b);
        assertArrayEquals(a, b, 1e-6f);
        CubeLayout.direction(CubeLayout.SOUTH, 1f, 0.3f, a);
        CubeLayout.direction(CubeLayout.WEST, 0f, 0.3f, b);
        assertArrayEquals(a, b, 1e-6f);
        CubeLayout.direction(CubeLayout.TOP, 0.4f, 1f, a);
        CubeLayout.direction(CubeLayout.NORTH, 0.4f, 0f, b);
        assertArrayEquals(a, b, 1e-6f);
    }

    @Test
    public void atlasSizeRules() {
        assertNull(CubeLayout.checkAtlasSize(1536, 1024));
        assertNull(CubeLayout.checkAtlasSize(48, 32));
        assertNotNull(CubeLayout.checkAtlasSize(1024, 1024));
        assertNotNull(CubeLayout.checkAtlasSize(30, 20));
        assertNotNull(CubeLayout.checkAtlasSize(6300, 4200));
    }

    @Test
    public void panoramaConversionPutsTheSkyUpAndGroundDown() {
        RgbaImage pano = new RgbaImage(256, 128);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 256; x++) pano.set(x, y, y < 64 ? 0xFF0000FF : 0xFF00FF00);
        RgbaImage atlas = Panorama.toAtlas(pano, 32);
        assertEquals(96, atlas.width);
        // top face centre (column 1, row 0) is sky-blue; bottom face centre (column 0, row 0) is green
        assertEquals(0xFF0000FF, atlas.get(48, 16));
        assertEquals(0xFF00FF00, atlas.get(16, 16));
        // north face: upper part sky, lower part ground
        assertEquals(0xFF0000FF, atlas.get(48, 36));
        assertEquals(0xFF00FF00, atlas.get(48, 60));
    }

    @Test
    public void pngHeaderIsCheckedBeforeDecoding() throws IOException {
        assertNull(PngInfo.read("not a png at all, just some text here".getBytes()));
        byte[] png = ImageIoCodec.png(48, 32, 0xFFFFFFFF);
        PngInfo i = PngInfo.read(png);
        assertEquals(48, i.width);
        // Forge the header to claim a gigantic image.
        png[16] = 0x7F;
        assertNull(PngInfo.read(png));
    }

    @Test
    public void importAtlasAndPanoramaRejectOthers() throws IOException {
        SkyboxLibrary.Imported a = lib.importPng(ImageIoCodec.png(96, 64, 0xFFFF8899), "C:\\Users\\me\\My Sky.png");
        assertEquals("custom:my_sky", a.entry().id());
        assertEquals("My Sky", a.entry().name());
        assertEquals(0xFF8899, a.meta().tint());
        SkyboxLibrary.Imported b = lib.importPng(ImageIoCodec.png(256, 128, 0xFF112233), "my_sky.png");
        assertEquals("custom:my_sky_2", b.entry().id());
        assertEquals(1, b.notes().size());
        try {
            lib.importPng(ImageIoCodec.png(100, 100, 0xFF000000), "square.png");
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("3 squares wide"));
        }
        assertEquals(2, SkyboxCatalog.custom().size());
        assertTrue(BloomSettings.SKY_DAY.isValid("custom:my_sky"));
        lib.rename("custom:my_sky", "Evening");
        assertEquals("Evening", SkyboxCatalog.find("custom:my_sky").name());
        lib.remove("custom:my_sky");
        assertNull(SkyboxCatalog.find("custom:my_sky"));
        assertTrue(Files.list(lib.dir().resolve("deleted")).count() == 2);
    }

    @Test
    public void optifineTimesAndPaths() throws IOException {
        assertEquals(0, OptiFineSky.parseTime("6:00"));
        assertEquals(12000, OptiFineSky.parseTime("18:00"));
        assertEquals(18000, OptiFineSky.parseTime("0:00"));
        assertEquals(500, OptiFineSky.parseTime("06:30"));
        assertEquals(-1, OptiFineSky.parseTime("25:00"));
        assertEquals("assets/minecraft/optifine/sky/world0/stars.png", OptiFineSky.resolve("assets/minecraft/optifine/sky/world0/", "./stars.png"));
        assertEquals("assets/minecraft/textures/sky.png", OptiFineSky.resolve("x/", "textures/sky"));
        assertNull(OptiFineSky.resolve("x/", "../../../evil.png"));
        assertNull(OptiFineSky.resolve("x/", "./a/../../b.png"));

        OptiFineSky.Layer night = OptiFineSky.parseLayer("assets/minecraft/optifine/sky/world0/sky1.properties",
                "startFadeIn=17:00\nendFadeIn=18:30\nendFadeOut=6:30\nblend=add\nrotate=true\nweather=clear\n");
        assertEquals("night", night.suggestedTime());
        assertEquals(List.of("weather"), night.unsupported());
        OptiFineSky.Layer always = OptiFineSky.parseLayer("assets/minecraft/optifine/sky/world0/sky2.properties", "blend=alpha\n");
        assertEquals("any", always.suggestedTime());
        assertEquals("assets/minecraft/optifine/sky/world0/sky2.png", always.texturePath());
        OptiFineSky.Layer day = OptiFineSky.parseLayer("assets/minecraft/optifine/sky/world0/sky3.properties",
                "startFadeIn=5:00\nendFadeIn=6:00\nstartFadeOut=17:00\nendFadeOut=18:00\n");
        assertEquals("day", day.suggestedTime());
    }

    @Test
    public void importLayersFromAZipPackSafely() throws IOException {
        Path zip = tmp.getRoot().toPath().resolve("Pretty Skies.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            put(out, "pack.mcmeta", "{}".getBytes());
            put(out, "assets/minecraft/optifine/sky/world0/sky1.properties",
                    "source=./night.png\nstartFadeIn=17:00\nendFadeIn=18:30\nendFadeOut=6:30\nblend=add\n".getBytes());
            put(out, "assets/minecraft/optifine/sky/world0/night.png", ImageIoCodec.png(48, 32, 0xFF101030));
            put(out, "assets/minecraft/optifine/sky/world0/sky2.properties", "source=../../../../../evil.png\n".getBytes());
            put(out, "assets/minecraft/optifine/sky/world0/sky3.properties", "source=./missing.png\n".getBytes());
            put(out, "../../evil.properties", "x".getBytes());
        }
        try (OptiFineSky.Pack pack = OptiFineSky.open(zip)) {
            OptiFineSky.ScanResult r = OptiFineSky.scan(pack);
            assertEquals(1, r.layers().size());
            assertEquals(2, r.problems().size());
            SkyboxLibrary.Imported imp = lib.importOptiFineLayer(pack, r.layers().get(0), "Pretty Skies.zip");
            assertEquals("night", imp.meta().suggested());
            assertTrue(imp.meta().additive());
            assertTrue(imp.meta().rotate());
            assertEquals("Pretty Skies sky1", imp.entry().name());
        }
        try (var s = Files.list(lib.dir())) {
            assertTrue(s.allMatch(p -> p.getFileName().toString().startsWith("pretty_skies_sky1")));
        }
    }

    @Test
    public void folderPacksWork() throws IOException {
        Path root = tmp.newFolder("pack").toPath();
        Path sky = root.resolve("assets/minecraft/optifine/sky/world0");
        Files.createDirectories(sky);
        Files.writeString(sky.resolve("sky1.properties"), "blend=alpha\n", StandardCharsets.UTF_8);
        Files.write(sky.resolve("sky1.png"), ImageIoCodec.png(48, 32, 0xFF808080));
        try (OptiFineSky.Pack pack = OptiFineSky.open(root)) {
            OptiFineSky.ScanResult r = OptiFineSky.scan(pack);
            assertEquals(1, r.layers().size());
            assertNotNull(lib.importOptiFineLayer(pack, r.layers().get(0), "pack"));
            try {
                pack.read("../outside.txt", 100);
                fail();
            } catch (IOException expected) {
            }
        }
    }

    @Test
    public void panoramaDroppedInFolderIsConvertedAndOriginalKept() throws Exception {
        Path dir = lib.dir();
        Files.write(dir.resolve("my_pano.png"), ImageIoCodec.png(256, 128, 0xFF336699));
        Files.write(dir.resolve("wrong_size.png"), ImageIoCodec.png(100, 100, 0xFF000000));
        Files.write(dir.resolve("not_png.png"), "hello".getBytes(StandardCharsets.UTF_8));
        lib.scan();
        assertEquals(1, SkyboxCatalog.custom().size());
        assertEquals("custom:my_pano", SkyboxCatalog.custom().get(0).id());
        RgbaImage atlas = new ImageIoCodec().decodePng(Files.readAllBytes(dir.resolve("my_pano.png")));
        assertEquals(atlas.width * 2, atlas.height * 3);
        assertTrue(Files.exists(dir.resolve("originals/my_pano.png")));
        // invalid files are skipped, never deleted
        assertTrue(Files.exists(dir.resolve("wrong_size.png")));
        assertTrue(Files.exists(dir.resolve("not_png.png")));
        // a second scan doesn't convert again
        lib.scan();
        assertFalse(Files.exists(dir.resolve("originals/my_pano_2.png")));
    }

    private static void put(ZipOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }
}
