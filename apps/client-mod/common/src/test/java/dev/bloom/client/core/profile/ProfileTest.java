package dev.bloom.client.core.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.core.settings.SettingsStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class ProfileTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private final AtomicLong now = new AtomicLong(5_000_000);
    private Path cfg;
    private SettingsStore store;
    private ProfileManager pm;

    @Before
    public void setUp() throws Exception {
        BloomSettings.resetAll();
        cfg = tmp.newFolder("bloomclient").toPath();
        boot();
    }

    private void boot() {
        store = new SettingsStore(cfg.resolve("settings.json"), BloomSettings.all(), now::get);
        pm = new ProfileManager(cfg.resolve("profiles"), BloomSettings.all(), store, now::get);
        store.load();
        pm.ready();
    }

    @Test
    public void builtInsUseOnlyKnownValidProfileSettings() {
        for (Profile p : BuiltInProfiles.create()) {
            for (Map.Entry<String, com.google.gson.JsonElement> e : p.values().entrySet()) {
                Setting s = BloomSettings.get(e.getKey());
                assertNotNull(p.id() + ": unknown " + e.getKey(), s);
                assertTrue(p.id() + ": not profile-scoped " + e.getKey(), s.profileScoped());
                assertTrue(p.id() + ": invalid " + e.getKey(), s.accepts(e.getValue()));
            }
        }
        assertEquals(9, BuiltInProfiles.create().size());
    }

    @Test
    public void selectingAProfileAppliesItAndResetsOtherValues() {
        BloomSettings.PETAL_DENSITY.set(0.9f);
        pm.select("competitive");
        assertFalse(BloomSettings.PETALS.on());
        assertFalse(BloomSettings.SKY_ENABLED.on());
        assertTrue(BloomSettings.OUTLINE.on());
        assertTrue(BloomSettings.QUALITY.is("low"));
        assertEquals(500, BloomSettings.MAX_PARTICLES.intValue());
        assertEquals(0.5f, BloomSettings.PETAL_DENSITY.value(), 1e-6);
        pm.select("cinematic");
        assertTrue(BloomSettings.QUALITY.is("high"));
        assertEquals(3500, BloomSettings.MAX_PARTICLES.intValue());
        assertTrue(BloomSettings.PETALS.on());
    }

    @Test
    public void nonProfileSettingsSurviveProfileSwitches() {
        BloomSettings.ADAPTIVE_TARGET.set(120);
        pm.select("minimal");
        assertEquals(120, BloomSettings.ADAPTIVE_TARGET.intValue());
    }

    @Test
    public void editingABuiltInSavesAnOverrideThatResetRemoves() throws Exception {
        pm.select("snowfall");
        BloomSettings.SNOW_DENSITY.set(1f);
        now.addAndGet(ProfileManager.SAVE_DELAY_MS + 1);
        pm.tick();
        Path f = cfg.resolve("profiles/snowfall.json");
        assertTrue(Files.exists(f));
        JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
        assertEquals("snowfall", o.get("basedOn").getAsString());
        assertTrue(pm.get("snowfall").modified());

        // Survives a restart.
        store.flush();
        BloomSettings.resetAll();
        boot();
        assertTrue(pm.get("snowfall").modified());
        assertEquals(1f, pm.get("snowfall").values().get("snow.density").getAsFloat(), 1e-6);

        pm.resetBuiltIn("snowfall");
        assertFalse(Files.exists(f));
        assertTrue(Files.exists(cfg.resolve("profiles/deleted")));
        assertEquals(0.6f, BloomSettings.SNOW_DENSITY.value(), 1e-6);
        assertFalse(pm.get("snowfall").modified());
    }

    @Test
    public void createRenameDuplicateDelete() throws Exception {
        BloomSettings.SKY_DAY.set("galaxy");
        Profile mine = pm.create("My  Sky§c!");
        assertEquals("My Sky!", mine.name());
        assertEquals("my_sky", mine.id());
        assertEquals("galaxy", mine.values().get("sky.day").getAsString());
        Profile copy = pm.duplicate(mine.id());
        assertEquals("My Sky! copy", copy.name());
        pm.rename(copy.id(), "Evening");
        assertEquals("Evening", pm.get(copy.id()).name());
        Profile dupName = pm.create("evening");
        assertEquals("evening 2", dupName.name());
        try {
            pm.rename("fantasy", "Mine");
            fail();
        } catch (java.io.IOException expected) {
        }
        pm.select(mine.id());
        pm.delete(mine.id());
        assertNull(pm.get(mine.id()));
        assertEquals(BuiltInProfiles.DEFAULT_ID, pm.selectedId());
        try (var s = Files.list(cfg.resolve("profiles/deleted"))) {
            assertEquals(1, s.count());
        }
    }

    @Test
    public void userProfileNamedLikeABuiltInGetsItsOwnId() throws Exception {
        Profile p = pm.create("Fantasy");
        assertNotEquals("fantasy", p.id());
        assertEquals("Fantasy 2", p.name());
    }

    @Test
    public void shareCodeAndJsonRoundTrip() throws Exception {
        pm.select("night_sky");
        Profile src = pm.get("night_sky");
        String code = ProfileCodec.toShareCode(src);
        assertTrue(code.startsWith("BLOOM1:"));
        ProfileCodec.ImportResult r = ProfileCodec.parse(code, BloomSettings::get);
        assertEquals("Night Sky", r.name());
        assertEquals(src.values(), r.values());
        ProfileCodec.ImportResult r2 = ProfileCodec.parse(ProfileCodec.toPrettyJson(src), BloomSettings::get);
        assertEquals(src.values(), r2.values());
        Profile imported = pm.importProfile(r2);
        assertFalse(imported.builtIn());
        Path exported = pm.exportToFile(imported.id(), cfg.resolve("exports"));
        Path exported2 = pm.exportToFile(imported.id(), cfg.resolve("exports"));
        assertNotEquals(exported, exported2);
    }

    @Test
    public void importRejectsGarbageAndSkipsBadValues() throws Exception {
        assertImportFails("hello");
        assertImportFails("{\"format\":1}");
        assertImportFails("[1,2]");
        assertImportFails("{\"type\":\"resource-pack\",\"format\":1,\"values\":{}}");
        assertImportFails("BLOOM1:@@@");
        assertImportFails("{\"format\":1,\"values\":{\"perf.adaptive\":false}}"); // not profile-scoped
        ProfileCodec.ImportResult r = ProfileCodec.parse("{\"format\":1,\"name\":\"../../evil\\u0000\",\"values\":{"
                + "\"sky.day\":\"aurora\",\"petals.density\":\"lots\",\"run.command\":\"calc.exe\"},\"hudLayout\":\"../x\"}", BloomSettings::get);
        assertEquals(1, r.values().size());
        assertEquals("../../evil", r.name());
        assertNull(r.hudLayout());
        assertEquals(1, r.warnings().size());
        Profile p = pm.importProfile(r);
        assertEquals("evil", p.id());
        assertTrue(Files.exists(cfg.resolve("profiles/evil.json")));
    }

    @Test
    public void zipBombShareCodeIsRejected() throws Exception {
        StringBuilder sb = new StringBuilder("{\"format\":1,\"name\":\"");
        sb.append("a".repeat(200_000)).append("\",\"values\":{}}");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (var out = new java.util.zip.DeflaterOutputStream(bos)) {
            out.write(sb.toString().getBytes());
        }
        assertImportFails("BLOOM1:" + java.util.Base64.getUrlEncoder().encodeToString(bos.toByteArray()));
    }

    private void assertImportFails(String text) {
        try {
            ProfileCodec.parse(text, BloomSettings::get);
            fail("accepted: " + text);
        } catch (ProfileCodec.ImportException expected) {
        }
    }

    @Test
    public void biomeRulesSwitchAfterADelayAndReturn() {
        pm.setBiomeRule("snowy", "snowfall");
        pm.onBiomeGroup("snowy");
        assertEquals(BuiltInProfiles.DEFAULT_ID, pm.active().id()); // disabled by default
        BloomSettings.PROFILE_BY_BIOME.set(true);
        pm.onBiomeGroup("snowy");
        assertEquals(BuiltInProfiles.DEFAULT_ID, pm.active().id());
        now.addAndGet(ProfileManager.BIOME_SWITCH_DELAY_MS + 1);
        pm.onBiomeGroup("snowy");
        assertEquals("snowfall", pm.active().id());
        assertTrue(BloomSettings.SNOW.on());
        assertEquals(BuiltInProfiles.DEFAULT_ID, pm.selectedId());
        pm.onBiomeGroup("forest");
        now.addAndGet(ProfileManager.BIOME_SWITCH_DELAY_MS + 1);
        pm.onBiomeGroup("forest");
        assertEquals(BuiltInProfiles.DEFAULT_ID, pm.active().id());
        assertFalse(BloomSettings.SNOW.on());
    }

    @Test
    public void launcherCanSelectAProfileBeforeLaunch() throws Exception {
        store.flush();
        Path f = cfg.resolve("settings.json");
        JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
        JsonObject prof = new JsonObject();
        prof.addProperty("active", "rainy_day");
        prof.addProperty("apply", true);
        root.add("profiles", prof);
        Files.writeString(f, root.toString());
        BloomSettings.resetAll();
        boot();
        assertEquals("rainy_day", pm.active().id());
        assertTrue(BloomSettings.RAIN_MODE.is("always"));
        store.flush();
        JsonObject saved = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("profiles").has("apply"));
        assertEquals("always", saved.getAsJsonObject("settings").get("rain.mode").getAsString());
    }
}
