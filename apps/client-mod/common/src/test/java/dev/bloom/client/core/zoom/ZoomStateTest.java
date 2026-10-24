package dev.bloom.client.core.zoom;

import org.junit.Test;

import static org.junit.Assert.*;

public class ZoomStateTest {
    private static final long MS = 1_000_000L;

    @Test
    public void holdZoomsOnlyWhileHeld() {
        ZoomState z = new ZoomState();
        z.key(true, false, true);
        assertTrue(z.wanted());
        assertEquals(0.25, z.fovMultiplier(0, 4f, false), 1e-6);
        z.key(false, false, true);
        assertFalse(z.wanted());
        assertEquals(1.0, z.fovMultiplier(16 * MS, 4f, false), 1e-6);
    }

    @Test
    public void togglePressesFlipOnRisingEdgeOnly() {
        ZoomState z = new ZoomState();
        z.key(true, true, true);
        z.key(true, true, true); // still held: no second flip
        assertTrue(z.wanted());
        z.key(false, true, true);
        assertTrue(z.wanted());
        z.key(true, true, true);
        assertFalse(z.wanted());
    }

    @Test
    public void openingAScreenEndsZoomAndScroll() {
        ZoomState z = new ZoomState();
        z.key(true, true, true);
        z.scroll(3, 4f);
        z.key(true, true, false);
        assertFalse(z.wanted());
        assertEquals(1f, z.target(4f), 0f);
    }

    @Test
    public void scrollMultipliesAndClampsAndResetsOnRelease() {
        ZoomState z = new ZoomState();
        z.key(true, false, true);
        z.scroll(1, 4f);
        assertEquals(5f, z.target(4f), 1e-4);
        z.scroll(-2, 4f);
        assertEquals(3.2f, z.target(4f), 1e-4);
        z.scroll(100, 4f);
        assertEquals(ZoomState.MAX, z.target(4f), 0f);
        z.key(false, false, true);
        z.key(true, false, true);
        assertEquals(4f, z.target(4f), 0f);
        z.key(false, false, true);
        z.scroll(5, 4f); // ignored when not zoomed
        assertEquals(1f, z.target(4f), 0f);
    }

    @Test
    public void smoothZoomEasesTowardsTheTargetAndSettles() {
        ZoomState z = new ZoomState();
        z.fovMultiplier(0, 4f, true);
        z.key(true, false, true);
        double first = z.fovMultiplier(16 * MS, 4f, true);
        assertTrue("moves in", first < 1.0 && first > 0.25);
        long t = 16 * MS;
        for (int i = 0; i < 120; i++) z.fovMultiplier(t += 16 * MS, 4f, true);
        assertEquals(0.25, z.fovMultiplier(t + 16 * MS, 4f, true), 1e-6);
        z.key(false, false, true);
        assertTrue(z.active());
        for (int i = 0; i < 120; i++) z.fovMultiplier(t += 16 * MS, 4f, true);
        assertFalse(z.active());
    }

    @Test
    public void aLongHitchDoesNotOvershoot() {
        ZoomState z = new ZoomState();
        z.fovMultiplier(0, 4f, true);
        z.key(true, false, true);
        double m = z.fovMultiplier(5_000 * MS, 4f, true);
        assertTrue(m >= 0.25);
    }
}
