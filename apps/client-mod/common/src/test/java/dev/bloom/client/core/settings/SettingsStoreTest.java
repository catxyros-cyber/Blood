package dev.bloom.client.core.settings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class SettingsStoreTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private Path file;

    @Before
    public void setUp() throws Exception {
        BloomSettings.resetAll();
        file = tmp.newFolder("cfg").toPath().resolve("settings.json");
    }

    private SettingsStore store() {
        return new SettingsStore(file, BloomSettings.all(), now::get);
    }

    private JsonObject readFile() throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    /** Simulates the launcher editing the file (bumps revision, sets updatedBy). */
    private void launcherEdit(String key, Object value) throws Exception {
        JsonObject root = readFile();
        root.addProperty("revision", root.get("revision").getAsLong() + 1);
        root.addProperty("updatedBy", "launcher");
        JsonObject s = root.getAsJsonObject("settings");
        if (value instanceof Boolean b) s.addProperty(key, b);
        else if (value instanceof Number n) s.addProperty(key, n);
        else s.addProperty(key, value.toString());
        Files.writeString(file, root.toString());
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000));
    }

    @Test
    public void createsFileWithAllDefaults() throws Exception {
        store().load();
        JsonObject root = readFile();
        assertEquals(1, root.get("format").getAsInt());
        assertEquals(1, root.get("revision").getAsLong());
        assertEquals("game", root.get("updatedBy").getAsString());
        assertEquals(BloomSettings.all().size(), root.getAsJsonObject("settings").size());
        assertTrue(root.getAsJsonObject("settings").get("petals.enabled").getAsBoolean());
    }

    @Test
    public void changesAreDebouncedThenSaved() throws Exception {
        SettingsStore st = store();
        st.load();
        BloomSettings.PETAL_DENSITY.set(0.9f);
        st.tick();
        assertEquals(0.5f, readFile().getAsJsonObject("settings").get("petals.density").getAsFloat(), 1e-6);
        now.addAndGet(SettingsStore.SAVE_DELAY_MS + 1);
        st.tick();
        assertEquals(0.9f, readFile().getAsJsonObject("settings").get("petals.density").getAsFloat(), 1e-6);
        assertEquals(2, readFile().get("revision").getAsLong());
    }

    @Test
    public void loadsValuesIgnoresInvalidOnesAndKeepsUnknownKeys() throws Exception {
        Files.writeString(file, "{\"format\":1,\"revision\":7,\"settings\":{\"petals.density\":0.2,\"sky.day\":\"no_such_sky\","
                + "\"rain.enabled\":\"yes\",\"future.option\":42},\"futureBlock\":{\"a\":1}}");
        SettingsStore st = store();
        st.load();
        assertEquals(0.2f, BloomSettings.PETAL_DENSITY.value(), 1e-6);
        assertTrue(BloomSettings.SKY_DAY.is("fs_013_sunset"));
        assertTrue(BloomSettings.RAIN.on());
        BloomSettings.SNOW.set(true);
        st.flush();
        JsonObject root = readFile();
        assertEquals(8, root.get("revision").getAsLong());
        assertEquals(42, root.getAsJsonObject("settings").get("future.option").getAsInt());
        assertEquals(1, root.getAsJsonObject("futureBlock").get("a").getAsInt());
    }

    @Test
    public void corruptFileIsBackedUpNotDeleted() throws Exception {
        Files.writeString(file, "{ this is not json");
        SettingsStore st = store();
        st.load();
        assertNotNull(st.takeLoadProblem());
        try (var s = Files.list(file.getParent())) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().startsWith("settings.json.corrupt-")));
        }
        assertTrue(readFile().has("settings"));
    }

    @Test
    public void launcherEditsAreMergedWithoutLosingInGameChanges() throws Exception {
        SettingsStore st = store();
        st.load();
        BloomSettings.SNOW.set(true); // in-game edit, not yet saved
        launcherEdit("petals.density", 0.95);
        launcherEdit("snow.enabled", false); // conflicting edit: in-game change is newer
        now.addAndGet(SettingsStore.CHECK_INTERVAL_MS + 1);
        st.tick();
        assertEquals(0.95f, BloomSettings.PETAL_DENSITY.value(), 1e-6);
        assertTrue(BloomSettings.SNOW.on());
        now.addAndGet(SettingsStore.SAVE_DELAY_MS + 1);
        st.tick();
        JsonObject s = readFile().getAsJsonObject("settings");
        assertEquals(0.95f, s.get("petals.density").getAsFloat(), 1e-6);
        assertTrue(s.get("snow.enabled").getAsBoolean());
    }

    @Test
    public void saveRereadsLauncherChangesFirst() throws Exception {
        SettingsStore st = store();
        st.load();
        BloomSettings.FIREFLIES.set(false);
        launcherEdit("dust.enabled", true);
        st.flush(); // must not overwrite the launcher's dust.enabled
        JsonObject s = readFile().getAsJsonObject("settings");
        assertTrue(s.get("dust.enabled").getAsBoolean());
        assertFalse(s.get("fireflies.enabled").getAsBoolean());
        assertTrue(BloomSettings.DUST.on());
    }

    @Test
    public void externalChangeListenerGetsChangedKeys() throws Exception {
        SettingsStore st = store();
        st.load();
        java.util.Set<String> seen = new java.util.HashSet<>();
        st.addExternalChangeListener((k, s) -> seen.addAll(k));
        launcherEdit("sky.day", "fs_classic_night_3");
        st.checkExternal();
        assertEquals(java.util.Set.of("sky.day"), seen);
        assertTrue(BloomSettings.SKY_DAY.is("fs_classic_night_3"));
    }
}
