package dev.bloom.client.game;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.particles.ParticleEnv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** Answers the particle system's questions about the current client world. */
public final class GameEnv implements ParticleEnv {
    private static final int UNLOADED = Integer.MAX_VALUE / 4;
    private final BiomeGroups groups = new BiomeGroups();
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private float rain;
    private float day;

    /** Call once per tick before the particle system ticks. */
    public void update(ClientLevel level) {
        if (level != this.level) groups.clear();
        this.level = level;
        if (level != null) {
            rain = level.getRainLevel(1f);
            day = dev.bloom.client.core.sky.SkyTime.daylight(dev.bloom.client.core.sky.SkyTime.timeOfDay(level.getDayTime(), 1f)) * (1f - 0.3f * rain);
        }
    }

    public BiomeGroups groups() {
        return groups;
    }

    /** True in dimensions with a normal sky (the Overworld and similar modded ones). */
    public static boolean normalSky(ClientLevel level) {
        return level != null && level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD
                && level.dimensionType().hasSkyLight();
    }

    @Override
    public boolean effectsAllowed() {
        return normalSky(level) && !BloomClient.get().worldEffectsPaused();
    }

    @Override
    public int groundY(int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return UNLOADED;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    @Override
    public int groundNoLeavesY(int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return UNLOADED;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    @Override
    public int canopyY(int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return Integer.MIN_VALUE;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return top > ground + 1 ? top : Integer.MIN_VALUE;
    }

    @Override
    public int foliageColor(int x, int z) {
        int y = groundY(x, z);
        if (biomeGroup(x, z) == BiomeGroups.CHERRY) return 0xF6BBD3;
        return BiomeColors.getAverageFoliageColor(level, mpos.set(x, Math.min(y, level.getMaxY()), z)) & 0xFFFFFF;
    }

    @Override
    public int biomeGroup(int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return -1;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return groups.groupAt(level, x, y, z);
    }

    @Override
    public int precipitationType(int x, int y, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return PRECIP_NONE;
        mpos.set(x, y, z);
        Biome.Precipitation p = level.getBiome(mpos).value().getPrecipitationAt(mpos, level.getSeaLevel());
        return switch (p) {
            case RAIN -> PRECIP_RAIN;
            case SNOW -> PRECIP_SNOW;
            default -> PRECIP_NONE;
        };
    }

    @Override
    public float rainLevel() {
        return rain;
    }

    @Override
    public float daylight() {
        return day;
    }

    /** Biome group under the camera, for biome-based profile switching. */
    public int groupAtCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (level == null || mc.gameRenderer == null) return -1;
        BlockPos p = mc.gameRenderer.getMainCamera().blockPosition();
        return groups.groupAt(level, p.getX(), p.getY(), p.getZ());
    }
}
