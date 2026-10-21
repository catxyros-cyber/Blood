package dev.bloom.client.core.particles;

import dev.bloom.client.core.settings.BloomSettings;

/**
 * BLOOD's decorative particles: cherry petals, leaves, snow, rain (+ splashes), fireflies and
 * floating motes. Client-side only, purely visual, and independent of Minecraft's particle
 * engine (so it can't fill up or slow down vanilla particles).
 *
 * <p>Particles live in flat arrays (no per-particle objects). The simulation runs at 20 Hz
 * in {@link #tick}; rendering interpolates. Particles far from the camera are thinned out
 * and removed, those outside the view are skipped, and the total never exceeds the
 * particle limit times the adaptive budget. When every effect is off, nothing is simulated.
 */
public final class ParticleSystem {
    public static final int PETAL = 0, LEAF = 1, SNOW = 2, RAIN = 3, SPLASH = 4, FIREFLY = 5, DUST = 6;
    public static final int TYPES = 7;
    public static final int CAPACITY = 6000 + 800; // max particle limit + splash headroom

    private static final int STATE_FALLING = 0, STATE_SETTLED = 1;
    private static final int SAFETY_LIFE = 20 * 40;

    /** Receives each visible particle, camera-relative, ready to be turned into a quad. */
    public interface QuadSink {
        /**
         * @param rot   spin around the vertical axis (radians) — petals and leaves
         * @param tilt  tilt from upright (radians); π/2 = lying flat
         * @param argb  colour with fade alpha applied
         */
        void particle(int type, int shape, float x, float y, float z, float size, float rot, float tilt,
                      float vx, float vy, float vz, int argb);
    }

    // ---- particle data (struct of arrays) ----
    private final float[] x = new float[CAPACITY], y = new float[CAPACITY], z = new float[CAPACITY];
    private final float[] px = new float[CAPACITY], py = new float[CAPACITY], pz = new float[CAPACITY];
    private final float[] vx = new float[CAPACITY], vy = new float[CAPACITY], vz = new float[CAPACITY];
    private final float[] rot = new float[CAPACITY], prot = new float[CAPACITY], spin = new float[CAPACITY];
    private final float[] tilt = new float[CAPACITY], size = new float[CAPACITY], phase = new float[CAPACITY];
    private final float[] fall = new float[CAPACITY], ground = new float[CAPACITY];
    private final int[] color = new int[CAPACITY], age = new int[CAPACITY], life = new int[CAPACITY];
    private final byte[] type = new byte[CAPACITY], shape = new byte[CAPACITY], state = new byte[CAPACITY];
    private int count;
    private final int[] typeCount = new int[TYPES];
    private final int[] target = new int[TYPES];

    private final WindField wind = new WindField();
    private final float[] w = new float[3];
    private float time;
    private long seed = 0x9E3779B97F4A7C15L;
    private double camX, camY, camZ;
    private int lastRendered;

    public WindField wind() {
        return wind;
    }

    public int count() {
        return count;
    }

    public int count(int t) {
        return typeCount[t];
    }

    public int target(int t) {
        return target[t];
    }

    public int lastRendered() {
        return lastRendered;
    }

    public void clear() {
        count = 0;
        java.util.Arrays.fill(typeCount, 0);
        java.util.Arrays.fill(target, 0);
    }

    // ------------------------------------------------------------------ random

    private float rand() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (seed >>> 40) / (float) (1L << 24);
    }

    private float range(float a, float b) {
        return a + (b - a) * rand();
    }

    // ------------------------------------------------------------------ tick

    /**
     * Advances the simulation one game tick.
     *
     * @param budget adaptive factor in (0, 1]
     */
    public void tick(ParticleEnv env, double cx, double cy, double cz, float budget) {
        time += 1f;
        camX = cx;
        camY = cy;
        camZ = cz;
        wind.update(BloomSettings.WIND.on(), BloomSettings.WIND_STRENGTH.value(), BloomSettings.WIND_DIRECTION.value(),
                BloomSettings.WIND_VARIATION.value(), BloomSettings.WIND_GUSTS.value(), time);

        if (!BloomSettings.ENABLED.on() || env == null || !env.effectsAllowed()) {
            clear();
            return;
        }
        computeTargets(env, budget);
        boolean any = false;
        for (int t = 0; t < TYPES; t++) if (target[t] > 0) any = true;
        if (!any && count == 0) return;

        simulate(env);
        if (any) spawn(env);
    }

    private void computeTargets(ParticleEnv env, float budget) {
        float total = BloomSettings.MAX_PARTICLES.value() * Math.max(0.05f, Math.min(1f, budget));
        float day = env.daylight();
        float rain = env.rainLevel();
        target[PETAL] = BloomSettings.PETALS.on() ? (int) (total * 0.45f * BloomSettings.PETAL_DENSITY.value()) : 0;
        target[LEAF] = BloomSettings.LEAVES.on() ? (int) (total * 0.22f * BloomSettings.LEAF_DENSITY.value()) : 0;
        float snowMul = switch (BloomSettings.SNOW_INTENSITY.id()) {
            case "heavy" -> 1f;
            case "medium" -> 0.65f;
            default -> 0.35f;
        };
        target[SNOW] = BloomSettings.SNOW.on() ? (int) (total * 0.6f * snowMul * BloomSettings.SNOW_DENSITY.value()) : 0;
        float rainAmount = BloomSettings.RAIN_MODE.is("always") ? 1f : rain;
        target[RAIN] = BloomSettings.RAIN.on() ? (int) (total * 0.55f * BloomSettings.RAIN_DENSITY.value() * rainAmount) : 0;
        target[SPLASH] = 0;
        float night = clamp01((0.42f - day) / 0.25f);
        target[FIREFLY] = BloomSettings.FIREFLIES.on() ? (int) Math.min(160, total * 0.06f * BloomSettings.FIREFLY_DENSITY.value() * night) : 0;
        float sunny = clamp01((day - 0.5f) / 0.3f) * (1f - rain);
        target[DUST] = BloomSettings.DUST.on() ? (int) Math.min(300, total * 0.12f * BloomSettings.DUST_DENSITY.value() * sunny) : 0;
        int sum = 0;
        for (int t = 0; t < TYPES; t++) sum += target[t];
        int limit = CAPACITY - 800;
        if (sum > limit) {
            float k = limit / (float) sum;
            for (int t = 0; t < TYPES; t++) target[t] = (int) (target[t] * k);
        }
    }

    private float maxDistance() {
        return BloomSettings.EFFECT_DISTANCE.value();
    }

    private float radiusFor(int t) {
        float d = maxDistance();
        return switch (t) {
            case PETAL -> Math.min(d, BloomSettings.PETAL_RADIUS.value());
            case LEAF -> Math.min(d, 24f);
            case SNOW -> Math.min(d, 32f);
            case RAIN -> Math.min(d, 20f);
            case FIREFLY -> Math.min(d, 24f);
            case DUST -> Math.min(d, 12f);
            default -> d;
        };
    }

    private static boolean enabledSetting(int t) {
        return switch (t) {
            case PETAL -> BloomSettings.PETALS.on();
            case LEAF -> BloomSettings.LEAVES.on();
            case SNOW -> BloomSettings.SNOW.on();
            case RAIN -> BloomSettings.RAIN.on();
            case FIREFLY -> BloomSettings.FIREFLIES.on();
            case DUST -> BloomSettings.DUST.on();
            default -> true;
        };
    }

    private void simulate(ParticleEnv env) {
        float maxD = maxDistance() * 1.15f;
        float maxD2 = maxD * maxD;
        float drift = BloomSettings.WIND.on() ? BloomSettings.WIND_DRIFT.value() : 0f;
        float petalSpin = BloomSettings.PETAL_SPIN.value();
        boolean settle = BloomSettings.PETAL_SETTLE.on();
        boolean splashes = BloomSettings.RAIN_SPLASHES.on();
        int ti = (int) time;
        int i = 0;
        while (i < count) {
            int t = type[i];
            float dx = (float) (x[i] - camX), dz = (float) (z[i] - camZ);
            boolean kill = !enabledSetting(t) || (t == SPLASH && !splashes)
                    || dx * dx + dz * dz > maxD2 || ++age[i] >= life[i];
            if (!kill) {
                px[i] = x[i];
                py[i] = y[i];
                pz[i] = z[i];
                prot[i] = rot[i];
                kill = step(env, i, t, drift, petalSpin, settle, splashes, ti);
            }
            if (kill) {
                removeAt(i);
            } else {
                i++;
            }
        }
    }

    /** Moves one particle. Returns true when it should be removed. */
    private boolean step(ParticleEnv env, int i, int t, float drift, float petalSpin, boolean settle, boolean splashes, int ti) {
        if (state[i] == STATE_SETTLED) return false; // resting on the ground until its life runs out
        // Refresh the ground height every 8 ticks (staggered), every tick for fast rain.
        if (t == RAIN || ((i + ti) & 7) == 0) {
            int bx = floor(x[i]), bz = floor(z[i]);
            ground[i] = t == LEAF ? env.groundNoLeavesY(bx, bz) : env.groundY(bx, bz);
        }
        switch (t) {
            case PETAL, LEAF -> {
                wind.sample(x[i], y[i], z[i], time, w);
                float sway = (float) Math.sin(phase[i] + age[i] * 0.13f) * (t == LEAF ? 0.035f : 0.025f) * petalSpin;
                float swayZ = (float) Math.cos(phase[i] * 1.7f + age[i] * 0.11f) * 0.02f * petalSpin;
                vx[i] += (w[0] * drift + sway - vx[i]) * 0.08f;
                vz[i] += (w[1] * drift + swayZ - vz[i]) * 0.08f;
                float flutter = (float) Math.sin(phase[i] * 2.3f + age[i] * 0.21f) * 0.012f * petalSpin;
                vy[i] += (-fall[i] + w[2] * drift + flutter - vy[i]) * 0.1f;
                rot[i] += spin[i];
                tilt[i] = 0.6f + (float) Math.sin(phase[i] + age[i] * 0.09f) * 0.5f * Math.min(1f, petalSpin);
            }
            case SNOW -> {
                wind.sample(x[i], y[i], z[i], time, w);
                float sway = (float) Math.sin(phase[i] + age[i] * 0.07f) * 0.012f;
                vx[i] += (w[0] * drift + sway - vx[i]) * 0.06f;
                vz[i] += (w[1] * drift - vz[i]) * 0.06f;
                vy[i] += (-fall[i] - vy[i]) * 0.1f;
                rot[i] += spin[i];
            }
            case RAIN -> {
                vx[i] = wind.baseX() * 1.4f;
                vz[i] = wind.baseZ() * 1.4f;
            }
            case SPLASH -> vy[i] -= 0.04f;
            case FIREFLY -> {
                float s = 0.05f;
                float tt = time * 0.02f;
                vx[i] += (Noise.noise3(x[i] * s, tt, phase[i], 5) * 0.035f - vx[i]) * 0.05f;
                vz[i] += (Noise.noise3(phase[i], tt, z[i] * s, 7) * 0.035f - vz[i]) * 0.05f;
                float hover = (float) (ground[i] + 1.4f - y[i]) * 0.002f;
                vy[i] += (Noise.noise3(x[i] * s, phase[i], tt, 9) * 0.015f + hover - vy[i]) * 0.05f;
            }
            case DUST -> {
                wind.sample(x[i], y[i], z[i], time, w);
                vx[i] += (w[0] * drift * 0.3f + Noise.noise3(phase[i], time * 0.01f, 0, 3) * 0.006f - vx[i]) * 0.04f;
                vz[i] += (w[1] * drift * 0.3f - vz[i]) * 0.04f;
                vy[i] += (Noise.noise3(0, phase[i], time * 0.01f, 4) * 0.004f - vy[i]) * 0.04f;
            }
            default -> {
            }
        }
        x[i] += vx[i];
        y[i] += vy[i];
        z[i] += vz[i];
        if (y[i] <= ground[i]) {
            switch (t) {
                case PETAL, LEAF -> {
                    if (!settle) return true;
                    y[i] = ground[i] + 0.02f;
                    px[i] = x[i];
                    py[i] = y[i];
                    pz[i] = z[i];
                    vx[i] = vy[i] = vz[i] = 0;
                    tilt[i] = (float) (Math.PI / 2);
                    state[i] = STATE_SETTLED;
                    life[i] = age[i] + (int) range(50, 110);
                }
                case RAIN -> {
                    if (splashes && rand() < 0.6f) {
                        int s = spawnSlot(SPLASH);
                        if (s >= 0) {
                            initSplash(s, x[i], ground[i] + 0.02f, z[i], color[i]);
                        }
                    }
                    return true;
                }
                case FIREFLY, DUST -> {
                    y[i] = ground[i] + 0.1f;
                    vy[i] = Math.abs(vy[i]);
                }
                default -> {
                    return true;
                }
            }
        }
        return false;
    }

    private void removeAt(int i) {
        typeCount[type[i]]--;
        int last = --count;
        if (i != last) copy(last, i);
    }

    private void copy(int from, int to) {
        x[to] = x[from];
        y[to] = y[from];
        z[to] = z[from];
        px[to] = px[from];
        py[to] = py[from];
        pz[to] = pz[from];
        vx[to] = vx[from];
        vy[to] = vy[from];
        vz[to] = vz[from];
        rot[to] = rot[from];
        prot[to] = prot[from];
        spin[to] = spin[from];
        tilt[to] = tilt[from];
        size[to] = size[from];
        phase[to] = phase[from];
        fall[to] = fall[from];
        ground[to] = ground[from];
        color[to] = color[from];
        age[to] = age[from];
        life[to] = life[from];
        type[to] = type[from];
        shape[to] = shape[from];
        state[to] = state[from];
    }

    private int spawnSlot(int t) {
        if (count >= CAPACITY) return -1;
        int i = count++;
        typeCount[t]++;
        type[i] = (byte) t;
        state[i] = STATE_FALLING;
        age[i] = 0;
        shape[i] = 0;
        spin[i] = 0;
        tilt[i] = 0;
        rot[i] = prot[i] = rand() * 6.2831855f;
        phase[i] = rand() * 100f;
        vx[i] = vy[i] = vz[i] = 0;
        return i;
    }

    private void place(int i, float sx, float sy, float sz, float gy) {
        x[i] = px[i] = sx;
        y[i] = py[i] = sy;
        z[i] = pz[i] = sz;
        ground[i] = gy;
    }

    // ------------------------------------------------------------------ spawning

    private void spawn(ParticleEnv env) {
        for (int t = 0; t < TYPES; t++) {
            int want = target[t] - typeCount[t];
            if (want <= 0) continue;
            boolean prewarm = typeCount[t] < target[t] / 2;
            int attempts = Math.min(want, prewarm ? target[t] / 8 + 8 : target[t] / 25 + 3);
            for (int a = 0; a < attempts; a++) trySpawn(env, t, prewarm);
        }
    }

    private void trySpawn(ParticleEnv env, int t, boolean prewarm) {
        float r = radiusFor(t);
        // Denser near the player: uniform over the disc, then thin out the outer ring.
        float dist = r * (float) Math.sqrt(rand());
        if (dist > r * 0.55f && rand() < (dist - r * 0.55f) / (r * 0.45f) * 0.55f) return;
        float ang = rand() * 6.2831855f;
        // Spawn a little upwind so particles drift into view instead of out of it.
        float upwind = Math.min(r * 0.4f, 25f);
        float sx = (float) camX + (float) Math.cos(ang) * dist - wind.baseX() * upwind * 4f;
        float sz = (float) camZ + (float) Math.sin(ang) * dist - wind.baseZ() * upwind * 4f;
        int bx = floor(sx), bz = floor(sz);
        int gy = env.groundY(bx, bz);
        float cy = (float) camY;
        switch (t) {
            case PETAL -> {
                float h = BloomSettings.PETAL_HEIGHT.value();
                if (gy > cy + h + 4) return; // player is underground or under a cliff
                if (!BloomSettings.PETALS_EVERYWHERE.on()) {
                    int g = env.biomeGroup(bx, bz);
                    if (g < 0 || !BloomSettings.PETAL_BIOMES.containsIndex(g)) return;
                }
                float top = Math.max(gy + 2, cy + h * range(0.7f, 1.2f));
                float sy = prewarm ? range(gy + 0.5f, top) : top;
                int i = spawnSlot(PETAL);
                if (i < 0) return;
                place(i, sx, sy, sz, gy);
                float sz2 = BloomSettings.PETAL_SIZE.value();
                size[i] = range(0.11f, 0.18f) * sz2;
                fall[i] = range(0.028f, 0.05f) * BloomSettings.PETAL_SPEED.value();
                spin[i] = (rand() - 0.5f) * 0.3f * BloomSettings.PETAL_SPIN.value();
                color[i] = petalColor();
                shape[i] = (byte) pickShape();
                life[i] = SAFETY_LIFE;
            }
            case LEAF -> {
                int canopy = env.canopyY(bx, bz);
                if (canopy == Integer.MIN_VALUE || canopy > cy + 28 || canopy < cy - 20) return;
                int floorY = env.groundNoLeavesY(bx, bz);
                if (floorY >= canopy - 1) return;
                float sy = prewarm ? range(floorY + 0.5f, canopy - 1f) : canopy - range(1f, 2.5f);
                int i = spawnSlot(LEAF);
                if (i < 0) return;
                place(i, sx, sy, sz, floorY);
                size[i] = range(0.14f, 0.22f);
                fall[i] = range(0.03f, 0.045f);
                spin[i] = (rand() - 0.5f) * 0.25f;
                color[i] = shade(env.foliageColor(bx, bz), range(0.85f, 1.05f));
                shape[i] = (byte) (rand() < 0.5f ? 0 : 1);
                life[i] = (int) range(160, 260);
            }
            case SNOW -> {
                if (gy > cy + 24) return;
                String where = BloomSettings.SNOW_WHERE.id();
                if (!"everywhere".equals(where)) {
                    int p = env.precipitationType(bx, Math.max(gy, (int) cy), bz);
                    if (p != ParticleEnv.PRECIP_SNOW) return;
                    if ("weather".equals(where) && env.rainLevel() < 0.2f) return;
                }
                float top = Math.max(gy + 2, cy + range(14f, 22f));
                float sy = prewarm ? range(gy + 0.5f, top) : top;
                int i = spawnSlot(SNOW);
                if (i < 0) return;
                place(i, sx, sy, sz, gy);
                size[i] = range(0.05f, 0.1f) * BloomSettings.SNOW_SIZE.value();
                fall[i] = range(0.045f, 0.08f) * BloomSettings.SNOW_SPEED.value()
                        * ("heavy".equals(BloomSettings.SNOW_INTENSITY.id()) ? 1.25f : 1f);
                spin[i] = (rand() - 0.5f) * 0.08f;
                color[i] = BloomSettings.SNOW_COLOR.rgb();
                shape[i] = (byte) BloomSettings.SNOW_SHAPE.index();
                life[i] = SAFETY_LIFE;
            }
            case RAIN -> {
                if (gy > cy + 20) return;
                if (BloomSettings.RAIN_MODE.is("follow")) {
                    int p = env.precipitationType(bx, Math.max(gy, (int) cy), bz);
                    if (p != ParticleEnv.PRECIP_RAIN) return;
                }
                float top = Math.max(gy + 2, cy + range(10f, 16f));
                float sy = prewarm ? range(gy + 1f, top) : top;
                int i = spawnSlot(RAIN);
                if (i < 0) return;
                place(i, sx, sy, sz, gy);
                vy[i] = -range(0.85f, 1.1f);
                size[i] = range(0.55f, 0.85f);
                color[i] = BloomSettings.RAIN_TINT.rgb();
                life[i] = 60;
            }
            case FIREFLY -> {
                if (gy < cy - 10 || gy > cy + 10) return;
                int g = env.biomeGroup(bx, bz);
                if (!fireflyBiome(g)) return;
                int i = spawnSlot(FIREFLY);
                if (i < 0) return;
                place(i, sx, gy + range(0.4f, 3f), sz, gy);
                size[i] = range(0.06f, 0.1f);
                color[i] = rand() < 0.7f ? 0xE6FA78 : 0xFFE37A;
                life[i] = (int) range(140, 300);
            }
            case DUST -> {
                if (gy > cy + 2) return; // indoors / underground
                int i = spawnSlot(DUST);
                if (i < 0) return;
                place(i, sx, Math.max(gy + 0.3f, cy + range(-1.5f, 4f)), sz, gy);
                size[i] = range(0.025f, 0.045f);
                color[i] = 0xFFF2D6;
                life[i] = (int) range(100, 220);
            }
            default -> {
            }
        }
    }

    private void initSplash(int i, float sx, float sy, float sz, int rgb) {
        place(i, sx, sy, sz, sy - 0.05f);
        vx[i] = (rand() - 0.5f) * 0.06f;
        vz[i] = (rand() - 0.5f) * 0.06f;
        vy[i] = range(0.06f, 0.12f);
        size[i] = range(0.04f, 0.07f);
        color[i] = rgb;
        life[i] = (int) range(4, 7);
    }

    private static boolean fireflyBiome(int g) {
        if (g < 0) return false;
        String id = BloomSettings.BIOME_GROUPS.get(g).id();
        return switch (id) {
            case "cherry_grove", "flower", "forest", "plains", "swamp", "jungle" -> true;
            default -> false;
        };
    }

    private int pickShape() {
        long mask = BloomSettings.PETAL_SHAPES.mask();
        if (mask == 0) return 0;
        int n = Long.bitCount(mask);
        int k = (int) (rand() * n);
        for (int s = 0; s < 64; s++) {
            if ((mask & (1L << s)) != 0 && k-- == 0) return s;
        }
        return 0;
    }

    private int petalColor() {
        int a = BloomSettings.PETAL_COLOR_A.rgb();
        int b = BloomSettings.PETAL_COLOR_B.rgb();
        float v = BloomSettings.PETAL_VARIATION.value();
        float t = rand();
        int c = lerpRgb(a, b, t);
        return shade(c, 1f + (rand() - 0.5f) * 0.3f * v);
    }

    // ------------------------------------------------------------------ render

    /**
     * Emits visible particles. Positions are interpolated and relative to the camera.
     *
     * @param look     normalised camera look vector
     * @param cosLimit particles whose direction from the camera has a smaller dot product
     *                 with {@code look} are culled (pass cos of half the diagonal FOV plus a margin)
     */
    public int render(QuadSink sink, float partial, double cx, double cy, double cz,
                      float lookX, float lookY, float lookZ, float cosLimit) {
        float maxD = maxDistance();
        float fadeStart = maxD * 0.8f;
        int rendered = 0;
        for (int i = 0; i < count; i++) {
            float rx = (float) (px[i] + (x[i] - px[i]) * partial - cx);
            float ry = (float) (py[i] + (y[i] - py[i]) * partial - cy);
            float rz = (float) (pz[i] + (z[i] - pz[i]) * partial - cz);
            float d2 = rx * rx + ry * ry + rz * rz;
            if (d2 > maxD * maxD) continue;
            float d = (float) Math.sqrt(d2);
            if (d > 1.5f && (rx * lookX + ry * lookY + rz * lookZ) < cosLimit * d) continue;
            int t = type[i];
            float a = fadeAlpha(i, t);
            if (d > fadeStart) a *= 1f - (d - fadeStart) / (maxD - fadeStart);
            if (a <= 0.01f) continue;
            float r = prot[i] + (rot[i] - prot[i]) * partial;
            sink.particle(t, shape[i], rx, ry, rz, size[i], r, tilt[i], vx[i], vy[i], vz[i], ((int) (a * 255f) << 24) | color[i]);
            rendered++;
        }
        lastRendered = rendered;
        return rendered;
    }

    private float fadeAlpha(int i, int t) {
        int ag = age[i], lf = life[i];
        float in = Math.min(1f, ag / (t == FIREFLY || t == DUST ? 30f : 8f));
        float out = Math.min(1f, (lf - ag) / (t == SPLASH ? 3f : 20f));
        float a = Math.max(0f, Math.min(in, out));
        return switch (t) {
            case RAIN -> a * 0.55f;
            case SPLASH -> a * 0.6f;
            case DUST -> a * 0.55f;
            case FIREFLY -> a * (0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(phase[i] + ag * 0.18f)));
            default -> a;
        };
    }

    // ------------------------------------------------------------------ helpers

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    static int lerpRgb(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return (r << 16) | (g << 8) | bl;
    }

    static int shade(int rgb, float k) {
        int r = Math.min(255, Math.max(0, (int) (((rgb >> 16) & 255) * k)));
        int g = Math.min(255, Math.max(0, (int) (((rgb >> 8) & 255) * k)));
        int b = Math.min(255, Math.max(0, (int) ((rgb & 255) * k)));
        return (r << 16) | (g << 8) | b;
    }
}
