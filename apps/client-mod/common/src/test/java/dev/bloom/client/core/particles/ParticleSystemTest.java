package dev.bloom.client.core.particles;

import dev.bloom.client.core.settings.BloomSettings;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ParticleSystemTest {
    /** Flat world at y=64, one biome group everywhere, trees on even chunks. */
    static final class FakeEnv implements ParticleEnv {
        boolean allowed = true;
        int group = 0; // cherry_grove
        int precip = PRECIP_RAIN;
        float rain = 0f;
        float day = 1f;
        int groundCalls;

        @Override public boolean effectsAllowed() { return allowed; }
        @Override public int groundY(int x, int z) { groundCalls++; return hasTree(x, z) ? 70 : 64; }
        @Override public int groundNoLeavesY(int x, int z) { return 64; }
        @Override public int canopyY(int x, int z) { return hasTree(x, z) ? 70 : Integer.MIN_VALUE; }
        @Override public int foliageColor(int x, int z) { return 0x48B518; }
        @Override public int biomeGroup(int x, int z) { return group; }
        @Override public int precipitationType(int x, int y, int z) { return precip; }
        @Override public float rainLevel() { return rain; }
        @Override public float daylight() { return day; }
        private static boolean hasTree(int x, int z) { return ((x >> 3) + (z >> 3)) % 2 == 0; }
    }

    FakeEnv env;
    ParticleSystem ps;

    @Before
    public void setUp() {
        BloomSettings.resetAll();
        env = new FakeEnv();
        ps = new ParticleSystem();
    }

    void run(int ticks) {
        for (int i = 0; i < ticks; i++) ps.tick(env, 0.5, 66, 0.5, 1f);
    }

    @Test
    public void petalsFillUpToTheirTargetAndStayWithinTheLimit() {
        run(200);
        int target = ps.target(ParticleSystem.PETAL);
        assertTrue(target > 100);
        assertTrue("petals " + ps.count(ParticleSystem.PETAL), ps.count(ParticleSystem.PETAL) > target * 0.8);
        assertTrue(ps.count() <= BloomSettings.MAX_PARTICLES.intValue());
    }

    @Test
    public void nothingIsSimulatedWhenEverythingIsOff() {
        BloomSettings.ENABLED.set(false);
        run(50);
        assertEquals(0, ps.count());
        assertEquals(0, env.groundCalls);

        BloomSettings.ENABLED.set(true);
        BloomSettings.PETALS.set(false);
        BloomSettings.LEAVES.set(false);
        BloomSettings.FIREFLIES.set(false);
        BloomSettings.RAIN.set(false);
        run(50);
        assertEquals(0, ps.count());
        assertEquals(0, env.groundCalls);
    }

    @Test
    public void turningAnEffectOffRemovesItsParticles() {
        run(100);
        assertTrue(ps.count(ParticleSystem.PETAL) > 0);
        BloomSettings.PETALS.set(false);
        run(1);
        assertEquals(0, ps.count(ParticleSystem.PETAL));
    }

    @Test
    public void biomeAwarePetalsOnlySpawnInChosenBiomes() {
        env.group = 3; // plains: not selected by default
        run(100);
        assertEquals(0, ps.count(ParticleSystem.PETAL));
        BloomSettings.PETALS_EVERYWHERE.set(true);
        run(100);
        assertTrue(ps.count(ParticleSystem.PETAL) > 0);
    }

    @Test
    public void otherDimensionsGetNothing() {
        run(50);
        env.allowed = false;
        run(1);
        assertEquals(0, ps.count());
    }

    @Test
    public void adaptiveBudgetScalesCountsDown() {
        run(200);
        int full = ps.count(ParticleSystem.PETAL);
        for (int i = 0; i < 300; i++) ps.tick(env, 0.5, 66, 0.5, 0.25f);
        assertTrue(ps.count(ParticleSystem.PETAL) < full * 0.5);
        // Saved settings untouched.
        assertEquals(1500, BloomSettings.MAX_PARTICLES.intValue());
    }

    @Test
    public void rainFollowsWeatherUnlessAlways() {
        BloomSettings.PETALS.set(false);
        run(60);
        assertEquals(0, ps.count(ParticleSystem.RAIN));
        env.rain = 1f;
        run(60);
        assertTrue(ps.count(ParticleSystem.RAIN) > 50);
        env.rain = 0f;
        BloomSettings.RAIN_MODE.set("always");
        run(60);
        assertTrue(ps.count(ParticleSystem.RAIN) > 50);
        env.precip = ParticleEnv.PRECIP_SNOW;
        BloomSettings.RAIN_MODE.set("follow");
        env.rain = 1f;
        run(80);
        assertEquals(0, ps.count(ParticleSystem.RAIN));
    }

    @Test
    public void snowRespectsWhereSetting() {
        BloomSettings.SNOW.set(true);
        run(60);
        assertEquals(0, ps.count(ParticleSystem.SNOW)); // default: snowy biomes only
        env.precip = ParticleEnv.PRECIP_SNOW;
        run(60);
        assertTrue(ps.count(ParticleSystem.SNOW) > 0);
        BloomSettings.SNOW_WHERE.set("weather");
        run(700); // flakes already in the air finish falling
        assertEquals(0, ps.count(ParticleSystem.SNOW));
    }

    @Test
    public void firefliesOnlyAtNight() {
        run(100);
        assertEquals(0, ps.count(ParticleSystem.FIREFLY));
        env.day = 0f;
        run(100);
        assertTrue(ps.count(ParticleSystem.FIREFLY) > 0);
    }

    @Test
    public void particlesStayAboveGroundAndNearThePlayer() {
        env.rain = 1f;
        run(300);
        List<float[]> seen = new ArrayList<>();
        ps.render((t, s, x, y, z, size, r, tilt, vx, vy, vz, argb) -> seen.add(new float[]{x, y, z}),
                1f, 0.5, 66, 0.5, 0, 0, 1, -1f);
        assertFalse(seen.isEmpty());
        float max = BloomSettings.EFFECT_DISTANCE.value();
        for (float[] p : seen) {
            assertTrue(p[1] + 66 >= 63.9f);
            assertTrue(Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]) <= max + 0.01);
        }
    }

    @Test
    public void frustumCullingSkipsParticlesBehindTheCamera() {
        run(200);
        int all = ps.render((t, s, x, y, z, size, r, tilt, vx, vy, vz, argb) -> {
        }, 1f, 0.5, 66, 0.5, 0, 0, 1, -1f);
        int front = ps.render((t, s, x, y, z, size, r, tilt, vx, vy, vz, argb) -> assertTrue(z > -1.6f),
                1f, 0.5, 66, 0.5, 0, 0, 1, 0.5f);
        assertTrue(front < all * 0.6);
    }

    @Test
    public void windPushesPetalsDownwind() {
        BloomSettings.WIND_STRENGTH.set(1f);
        BloomSettings.WIND_DIRECTION.set(270f); // towards +X (east)
        BloomSettings.WIND_VARIATION.set(0f);
        BloomSettings.WIND_GUSTS.set(0f);
        run(200);
        double[] sum = {0};
        int[] n = {0};
        ps.render((t, s, x, y, z, size, r, tilt, vx, vy, vz, argb) -> {
            if (t == ParticleSystem.PETAL && vy != 0) {
                sum[0] += vx;
                n[0]++;
            }
        }, 1f, 0.5, 66, 0.5, 0, 0, 1, -1f);
        assertTrue(n[0] > 0);
        assertTrue("mean vx " + sum[0] / n[0], sum[0] / n[0] > 0.05);
    }
}
