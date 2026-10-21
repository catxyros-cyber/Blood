package dev.bloom.client.core.particles;

/** What the particle system needs to know about the world. Implemented by the game layer. */
public interface ParticleEnv {
    int PRECIP_NONE = 0;
    int PRECIP_RAIN = 1;
    int PRECIP_SNOW = 2;

    /** Particles only run in dimensions with weather and sky (the Overworld). */
    boolean effectsAllowed();

    /** Y of the first air block above the highest motion-blocking block (including leaves). */
    int groundY(int x, int z);

    /** Like {@link #groundY} but ignoring leaves (the ground under a tree). */
    int groundNoLeavesY(int x, int z);

    /** Top of a leaf canopy at (x, z) (when the highest block there is leaves), or Integer.MIN_VALUE. */
    int canopyY(int x, int z);

    /** Foliage colour at (x, z) as 0xRRGGBB. */
    int foliageColor(int x, int z);

    /** Index into BloomSettings.BIOME_GROUPS, or -1 when unknown/not loaded. */
    int biomeGroup(int x, int z);

    /** What the biome would precipitate there (PRECIP_*), regardless of current weather. */
    int precipitationType(int x, int y, int z);

    /** 0 = clear, 1 = full rain (Level#getRainLevel). */
    float rainLevel();

    /** 0 = night, 1 = full day. */
    float daylight();
}
