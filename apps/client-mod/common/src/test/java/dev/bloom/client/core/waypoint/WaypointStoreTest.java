package dev.bloom.client.core.waypoint;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class WaypointStoreTest {
    private static final String W = "server:play.example.net";
    private static final String OVER = "minecraft:overworld";

    @Test
    public void namesCountUpAndSkipUsedOnes() {
        WaypointStore s = new WaypointStore(null);
        assertEquals("Waypoint 1", s.add(W, null, 1, 64, 1, OVER, 0).name());
        s.add(W, "Waypoint 2", 2, 64, 2, OVER, 0);
        assertEquals("Waypoint 3", s.nextName(W));
        assertEquals("Waypoint 1", s.nextName("other"));
    }

    @Test
    public void namesAreCleaned() {
        WaypointStore s = new WaypointStore(null);
        assertEquals("Base", s.add(W, "  §cBa\u0007se ", 0, 0, 0, OVER, 0).name());
        assertEquals("Waypoint", s.add(W, "   ", 0, 0, 0, OVER, 0).name());
        assertEquals(32, s.add(W, "x".repeat(80), 0, 0, 0, OVER, 0).name().length());
    }

    @Test
    public void onlyOneDeathWaypointAndItClearsWhenReached() {
        WaypointStore s = new WaypointStore(null);
        s.setDeath(W, 10, 70, 10, OVER, 1);
        s.setDeath(W, 100, 70, 100, OVER, 2);
        assertEquals(1, s.list(W).size());
        assertEquals(100, s.list(W).get(0).x());
        assertFalse(s.clearDeathIfReached(W, 100, 70, 100, "minecraft:the_nether", 4));
        assertFalse(s.clearDeathIfReached(W, 0, 70, 0, OVER, 4));
        assertTrue(s.clearDeathIfReached(W, 101, 70, 101, OVER, 4));
        assertTrue(s.list(W).isEmpty());
    }

    @Test
    public void visibleFiltersByDimension() {
        WaypointStore s = new WaypointStore(null);
        s.add(W, "Home", 0, 64, 0, OVER, 0);
        s.add(W, "Portal", 0, 64, 0, "minecraft:the_nether", 0);
        assertEquals(1, s.visible(W, OVER).size());
        assertEquals("Portal", s.visible(W, "minecraft:the_nether").get(0).name());
    }

    @Test
    public void renameAndRemove() {
        WaypointStore s = new WaypointStore(null);
        WaypointStore.Waypoint w = s.add(W, "Home", 0, 64, 0, OVER, 0);
        assertTrue(s.rename(W, w, "Base"));
        assertEquals("Base", s.list(W).get(0).name());
        assertTrue(s.remove(W, s.list(W).get(0)));
        assertTrue(s.list(W).isEmpty());
    }

    @Test
    public void saveAndLoadRoundTripAndSurviveDamage() throws Exception {
        Path dir = Files.createTempDirectory("motion-wp");
        Path f = dir.resolve("waypoints.json");
        WaypointStore s = new WaypointStore(f);
        s.add(W, "Home", -120, 64, 340, OVER, 5);
        s.setDeath("world:My Base", 1, 2, 3, "minecraft:the_end", 6);
        s.save();
        assertFalse(s.dirty());

        WaypointStore t = new WaypointStore(f);
        t.load();
        assertEquals(s.list(W), t.list(W));
        assertTrue(t.list("world:My Base").get(0).death());

        Files.writeString(f, "{\"worlds\":{\"a\":[{\"name\":\"ok\",\"x\":1,\"y\":2,\"z\":3},{\"name\":\"bad\"}]}}");
        t.load();
        assertEquals(1, t.list("a").size());
        Files.writeString(f, "not json");
        t.load();
        assertTrue(t.list("a").isEmpty());
    }

    @Test(expected = IllegalStateException.class)
    public void perWorldLimit() {
        WaypointStore s = new WaypointStore(null);
        for (int i = 0; i <= WaypointStore.MAX_PER_WORLD; i++) s.add(W, null, i, 0, 0, OVER, 0);
    }
}
