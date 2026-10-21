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
}
