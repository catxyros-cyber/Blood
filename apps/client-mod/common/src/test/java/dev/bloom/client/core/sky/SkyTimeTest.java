package dev.bloom.client.core.sky;

import org.junit.Test;

import static org.junit.Assert.*;

public class SkyTimeTest {
    @Test
    public void matchesVanillaCelestialCurve() {
        assertEquals(0f, SkyTime.timeOfDay(6000, 0f), 1e-6f);     // noon
        assertEquals(0.5f, SkyTime.timeOfDay(18000, 0f), 1e-6f);  // midnight
        assertEquals(SkyTime.timeOfDay(6000, 0f), SkyTime.timeOfDay(6000 + 24000 * 5, 0f), 1e-6f);
        assertEquals(SkyTime.timeOfDay(-18000, 0f), SkyTime.timeOfDay(6000, 0f), 1e-6f);
        assertEquals(1f, SkyTime.daylight(0f), 1e-6f);
        assertEquals(0f, SkyTime.daylight(0.5f), 1e-6f);
        assertEquals(0f, SkyTime.starBrightness(0f), 1e-6f);
        assertEquals(0.5f, SkyTime.starBrightness(0.5f), 1e-6f);
        assertEquals(0, SkyTime.moonPhase(0));
        assertEquals(3, SkyTime.moonPhase(24000L * 3 + 100));
        assertEquals(0, SkyTime.moonPhase(24000L * 8));
        assertEquals(7, SkyTime.moonPhase(-1));
    }
}
