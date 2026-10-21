package dev.bloom.client.game;

import dev.bloom.client.core.settings.BloomSettings;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;
import java.util.Optional;

/**
 * Sorts biomes into the groups listed in {@link BloomSettings#BIOME_GROUPS} using their
 * registry names, so modded biomes ("…_cherry_forest", "…_tundra") are grouped sensibly too.
 * Results are cached per 16×16 column; the cache is cleared when the world changes.
 */
public final class BiomeGroups {
    public static final int CHERRY = index("cherry_grove"), FLOWER = index("flower"), FOREST = index("forest"),
            PLAINS = index("plains"), TAIGA = index("taiga"), JUNGLE = index("jungle"), SAVANNA = index("savanna"),
            SWAMP = index("swamp"), BEACH = index("beach"), MOUNTAIN = index("mountain"), SNOWY = index("snowy"),
            DRY = index("dry");

    private final Long2IntOpenHashMap cache = new Long2IntOpenHashMap();
    private Level cachedFor;

    public BiomeGroups() {
        cache.defaultReturnValue(Integer.MIN_VALUE);
    }

    private static int index(String id) {
        for (int i = 0; i < BloomSettings.BIOME_GROUPS.size(); i++) {
            if (BloomSettings.BIOME_GROUPS.get(i).id().equals(id)) return i;
        }
        throw new IllegalStateException(id);
    }

    public int groupAt(Level level, int x, int y, int z) {
        if (level != cachedFor || cache.size() > 8192) {
            cache.clear();
            cachedFor = level;
        }
        long key = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
        int g = cache.get(key);
        if (g != Integer.MIN_VALUE) return g;
        BlockPos pos = new BlockPos(x, y, z);
        g = classify(level.getBiome(pos), pos);
        cache.put(key, g);
        return g;
    }

    public void clear() {
        cache.clear();
        cachedFor = null;
    }

    static int classify(Holder<Biome> holder, BlockPos pos) {
        Optional<ResourceKey<Biome>> key = holder.unwrapKey();
        String name = key.map(k -> k.location().getPath().toLowerCase(Locale.ROOT)).orElse("");
        int byName = classifyName(name);
        if (byName >= 0) return byName;
        try {
            if (holder.value().coldEnoughToSnow(pos)) return SNOWY;
        } catch (RuntimeException ignored) {
        }
        return PLAINS;
    }

    /** Name rules, most specific first. Returns -1 when nothing matches. */
    public static int classifyName(String n) {
        if (n.contains("cherry") || n.contains("sakura") || n.contains("blossom")) return CHERRY;
        if (n.contains("flower") || n.contains("meadow") || n.contains("sunflower") || n.contains("lavender")) return FLOWER;
        if (n.contains("snow") || n.contains("frozen") || n.contains("ice") || n.contains("tundra")
                || n.contains("glacier") || n.contains("jagged") || n.equals("grove")) return SNOWY;
        if (n.contains("swamp") || n.contains("mangrove") || n.contains("marsh") || n.contains("bog")) return SWAMP;
        if (n.contains("jungle") || n.contains("bamboo") || n.contains("rainforest")) return JUNGLE;
        if (n.contains("taiga") || n.contains("pine") || n.contains("spruce")) return TAIGA;
        if (n.contains("forest") || n.contains("wood") || n.contains("birch") || n.contains("grove")) return FOREST;
        if (n.contains("savanna") || n.contains("shrubland")) return SAVANNA;
        if (n.contains("beach") || n.contains("ocean") || n.contains("river") || n.contains("shore") || n.contains("coast")) return BEACH;
        if (n.contains("desert") || n.contains("badlands") || n.contains("mesa") || n.contains("dune") || n.contains("outback")) return DRY;
        if (n.contains("peak") || n.contains("mountain") || n.contains("hill") || n.contains("slope")
                || n.contains("windswept") || n.contains("stony") || n.contains("cliff") || n.contains("highland")) return MOUNTAIN;
        if (n.contains("plain") || n.contains("prairie") || n.contains("field") || n.contains("steppe")) return PLAINS;
        return -1;
    }
}
