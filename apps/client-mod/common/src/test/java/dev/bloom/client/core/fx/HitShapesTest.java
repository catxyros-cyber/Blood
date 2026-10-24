package dev.bloom.client.core.fx;

import org.junit.Test;

import static org.junit.Assert.*;

public class HitShapesTest {
    @Test
    public void ringIsACircleOfTheGivenRadius() {
        double[][] pts = HitShapes.ring(1.5, 24, 0.3);
        assertEquals(24, pts.length);
        for (double[] p : pts) {
            assertEquals(1.5, Math.hypot(p[0], p[2]), 1e-9);
            assertEquals(0, p[1], 0);
        }
        assertEquals(3, HitShapes.ring(1, 0, 0).length);
    }

    @Test
    public void ringsGrowAndStopGrowing() {
        assertEquals(0.35, HitShapes.ringRadius(0), 1e-9);
        assertTrue(HitShapes.ringRadius(3) > HitShapes.ringRadius(1));
        assertEquals(1.6, HitShapes.ringRadius(HitShapes.LIFETIME - 1), 1e-9);
        assertEquals(1.6, HitShapes.ringRadius(100), 1e-9);
    }

    @Test
    public void slashStaysNearTheTargetAndFacesTheAttacker() {
        // Attacker faces +Z (yaw 0), so the slash plane is spread along X and bulges toward -Z.
        double[][] pts = HitShapes.slash(0, 0, 1, 20, 1.0);
        double maxX = 0, minZ = 0, maxZ = 0;
        for (double[] p : pts) {
            assertTrue(Math.abs(p[0]) <= 1.01 && Math.abs(p[1]) <= 1.01);
            maxX = Math.max(maxX, Math.abs(p[0]));
            minZ = Math.min(minZ, p[2]);
            maxZ = Math.max(maxZ, p[2]);
        }
        assertTrue("spread across the target", maxX > 0.5);
        assertTrue("curves toward the attacker", minZ < -0.05);
        assertTrue("never behind the target", maxZ <= 1e-9);
        // The first point is up and to the left of the last one (as the attacker sees it).
        assertTrue(pts[0][1] > pts[pts.length - 1][1]);
    }

    @Test
    public void swingAnimationIsAFullTurnThatStartsAndEndsStill() {
        assertEquals(0f, HitShapes.swingDegrees(0f), 1e-4);
        assertEquals(180f, HitShapes.swingDegrees(0.5f), 1e-3);
        assertEquals(360f, HitShapes.swingDegrees(1f), 1e-4);
        assertEquals(360f, HitShapes.swingDegrees(7f), 1e-4);
        assertTrue(HitShapes.swingDegrees(0.05f) < 5f);
    }

    @Test
    public void rainbowCyclesThroughBrightColours() {
        assertEquals(0xFF0000, HitShapes.rainbow(0));
        assertEquals(0xFF0000, HitShapes.rainbow(4));
        int c = HitShapes.rainbow(1.5);
        assertNotEquals(0xFF0000, c);
        assertEquals(0xFFFFFF, HitShapes.lighten(0x000000, 1f));
        assertEquals(0x39FF88, HitShapes.lighten(0x39FF88, 0f));
    }
}
