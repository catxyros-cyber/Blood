package dev.bloom.client.game;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.particles.ParticleEnv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.DimensionSpecialEffects;
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
            day = Math.max(0f, Math.min(1f, (level.getSkyDarken(1f) - 0.2f) / 0.8f));
        }
    }

    public BiomeGroups groups() {
        return groups;
    }

    /** True in dimensions with a normal sky (the Overworld and similar modded ones). */
    public static boolean normalSky(ClientLevel level) {
        return level != null && level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL
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
        return BiomeColors.getAverageFoliageColor(level, mpos.set(x, Math.min(y, level.getMaxBuildHeight() - 1), z)) & 0xFFFFFF;
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
        Biome.Precipitation p = level.getBiome(mpos).value().getPrecipitationAt(mpos);
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
        BlockPos p = mc.gameRenderer.getMainCamera().getBlockPosition();
        return groups.groupAt(level, p.getX(), p.getY(), p.getZ());
    }
}
