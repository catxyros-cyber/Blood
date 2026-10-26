package dev.bloom.client.core.perf;

import dev.bloom.client.core.particles.AdaptiveBudget;
import dev.bloom.client.core.particles.WindField;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class PerfTest {
    @Test
    public void fpsIsFramesInTheLastSecond() {
        FrameStats s = new FrameStats();
        long t = 0;
        for (int i = 0; i <= 120; i++) {
            s.frame(t);
            t += 10_000_000L; // 100 FPS
        }
        long now = t - 10_000_000L;
        assertEquals(100, s.fps(now), 1);
        assertEquals(10.0, s.averageMs(now, 1_000_000_000L), 0.01);
        s.frame(now + 50_000_000L); // one 50 ms hitch
        assertEquals(50.0, s.worstMs(now + 50_000_000L, 1_000_000_000L), 0.01);
        assertTrue(s.onePercentLowFps(now + 50_000_000L, 2_000_000_000L) < 30);
    }

    @Test
    public void longPausesAreIgnored() {
        FrameStats s = new FrameStats();
        s.frame(0);
        s.frame(10_000_000_000L);
        assertEquals(0, s.samples());
    }

    @Test
    public void cpsCountsLastSecondOnly() {
        CpsCounter c = new CpsCounter();
        for (int i = 0; i < 12; i++) c.click(0, 1000 + i * 100);
        assertEquals(10, c.cps(0, 2150));
        assertEquals(0, c.cps(1, 2150));
        assertEquals(0, c.cps(0, 5000));
    }

    @Test
    public void lowFpsAdvisorSuggestsOnlyAfterSustainedDropAndRespectsCooldown() {
        LowFpsAdvisor a = new LowFpsAdvisor();
        List<LowFpsAdvisor.Candidate> on = List.of(new LowFpsAdvisor.Candidate("petals.enabled", "Petals", 3),
                new LowFpsAdvisor.Candidate("rain.enabled", "Rain", 5));
        assertNull(a.update(0, 20, 40, on, true));
        assertNull(a.update(10_000, 20, 40, on, true));
        List<LowFpsAdvisor.Candidate> s = a.update(16_000, 20, 40, on, true);
        assertNotNull(s);
        assertEquals("rain.enabled", s.get(0).settingKey());
        assertNull(a.update(40_000, 20, 40, on, true));
        assertNull(a.update(60_000, 20, 40, on, true));
        assertNull(a.update(100_000, 20, 40, on, false)); // paused
        a.silence();
        assertNull(a.update(2_000_000, 10, 40, on, true));
    }

    @Test
    public void adaptiveBudgetDropsAndRecovers() {
        AdaptiveBudget b = new AdaptiveBudget();
        for (int i = 0; i < 20; i++) b.update(true, 30, 60);
        assertEquals(AdaptiveBudget.MIN, b.factor(), 1e-6);
        for (int i = 0; i < 30; i++) b.update(true, 120, 60);
        assertEquals(1f, b.factor(), 1e-6);
        b.update(true, 30, 60);
        b.update(false, 30, 60);
        assertEquals(1f, b.factor(), 1e-6);
    }

    @Test
    public void windBlowsTowardsItsDirection() {
        WindField w = new WindField();
        w.update(true, 1f, 0f, 0f, 0f, 100);
        assertTrue(w.baseZ() > 0.1f); // yaw 0 = south = +Z
        assertEquals(0, w.baseX(), 1e-4);
        w.update(true, 1f, 90f, 0f, 0f, 100);
        assertTrue(w.baseX() < -0.1f); // yaw 90 = west = -X
        w.update(false, 1f, 90f, 0f, 0f, 100);
        assertEquals(0, w.speed(), 0);
    }

    @Test
    public void fpsSessionAverageAndLows() {
        FpsSession s = new FpsSession();
        for (int i = 0; i < 990; i++) s.frame(5.0); // 200 FPS
        for (int i = 0; i < 10; i++) s.frame(20.0); // ten 50 FPS stutters: exactly the slowest 1%
        assertEquals(1000, s.frames());
        assertEquals(1000.0 / (990 * 5.0 + 10 * 20.0) * 1000, s.averageFps(), 0.5);
        assertEquals(50.0, s.lowFps(0.01), 0.5);
        s.frame(250.0); // a frame slower than the histogram range is still counted exactly
        assertEquals(4.0, s.lowFps(0.0005), 0.01);
    }

    @Test
    public void fpsRecorderCountsOnlyGameplayAndSplitsOnContextChange() {
        FpsRecorder r = new FpsRecorder();
        java.util.Map<String, Object> ctx = java.util.Map.of("renderDistance", 12, "vsync", false);
        assertNull(r.update(ctx, 0));
        long t = 0;
        // 40 s of gameplay at 100 FPS, with a menu open in the middle that must not count.
        for (int i = 0; i < 4000; i++) {
            r.frame(t, true);
            t += 10_000_000L;
        }
        for (int i = 0; i < 500; i++) {
            r.frame(t, false);
            t += 100_000_000L; // slow frames behind a menu
        }
        com.google.gson.JsonObject done = r.update(java.util.Map.of("renderDistance", 16, "vsync", false), 50_000);
        assertNotNull(done);
        assertEquals(100.0, done.get("avgFps").getAsDouble(), 0.5);
        assertEquals(40, done.get("seconds").getAsLong());
        assertEquals(12, done.get("renderDistance").getAsInt());
        // A short session isn't kept.
        r.frame(t, true);
        r.frame(t + 10_000_000L, true);
        assertNull(r.finish(60_000));
    }

    @Test
    public void fpsLogKeepsTheNewestSessions() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("fpslog");
        java.nio.file.Path file = dir.resolve(".bloom/fps-sessions.json");
        for (int i = 0; i < FpsRecorder.KEEP + 5; i++) {
            com.google.gson.JsonObject e = new com.google.gson.JsonObject();
            e.addProperty("n", i);
            FpsRecorder.append(file, e);
        }
        com.google.gson.JsonArray a = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject().getAsJsonArray("sessions");
        assertEquals(FpsRecorder.KEEP, a.size());
        assertEquals(FpsRecorder.KEEP + 4, a.get(a.size() - 1).getAsJsonObject().get("n").getAsInt());
    }
}
