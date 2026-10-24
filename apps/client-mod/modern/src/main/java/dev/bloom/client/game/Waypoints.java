package dev.bloom.client.game;

import dev.bloom.client.BloomClient;
import dev.bloom.client.BloomKeys;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.List;

/** Waypoints for the world you're in: the Add waypoint key, the death waypoint, saving. */
public final class Waypoints {
    private static WaypointStore store;
    private static boolean deathRecorded;
    private static long lastSave;

    private Waypoints() {
    }

    public static WaypointStore store() {
        if (store == null) {
            store = new WaypointStore(BloomClient.get().configDir().resolve("waypoints.json"));
            store.load();
        }
        return store;
    }

    /** "server:play.example.net" or "world:My World"; null when not in a world. */
    public static String worldKey(Minecraft mc) {
        if (mc.level == null) return null;
        if (mc.getSingleplayerServer() != null) {
            try {
                return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
            } catch (RuntimeException e) {
                return "world:unknown";
            }
        }
        ServerData sd = mc.getCurrentServer();
        return sd != null ? "server:" + sd.ip.toLowerCase(java.util.Locale.ROOT) : "unknown";
    }

    public static String dimension(Minecraft mc) {
        return mc.level == null ? "minecraft:overworld" : mc.level.dimension().identifier().toString();
    }

    public static List<WaypointStore.Waypoint> visible(Minecraft mc) {
        String key = worldKey(mc);
        return key == null ? List.of() : store().visible(key, dimension(mc));
    }

    /** Add a waypoint where you stand. Returns it, or null if there's no world. */
    public static WaypointStore.Waypoint addHere(Minecraft mc) {
        String key = worldKey(mc);
        if (key == null || mc.player == null) return null;
        try {
            WaypointStore.Waypoint w = store().add(key, null, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), dimension(mc), System.currentTimeMillis());
            store().save();
            return w;
        } catch (IllegalStateException full) {
            BloomClient.get().toast("Waypoints", full.getMessage());
            return null;
        }
    }

    public static void remove(Minecraft mc, WaypointStore.Waypoint w) {
        String key = worldKey(mc);
        if (key != null && store().remove(key, w)) store().save();
    }

    /** Client tick. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            deathRecorded = false;
            return;
        }
        while (BloomKeys.ADD_WAYPOINT.consumeClick()) {
            if (mc.screen != null) continue;
            WaypointStore.Waypoint w = addHere(mc);
            if (w != null) BloomClient.get().toast("Waypoint added", w.name() + " at " + w.x() + ", " + w.y() + ", " + w.z());
        }
        String key = worldKey(mc);
        if (key == null) return;
        if (mc.player.isDeadOrDying()) {
            if (!deathRecorded && BloomSettings.WAYPOINT_DEATH.on()) {
                store().setDeath(key, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), dimension(mc), System.currentTimeMillis());
            }
            deathRecorded = true;
        } else {
            deathRecorded = false;
            store().clearDeathIfReached(key, mc.player.getX(), mc.player.getY(), mc.player.getZ(), dimension(mc), 4.0);
        }
        long now = System.currentTimeMillis();
        if (store().dirty() && now - lastSave > 3000) {
            lastSave = now;
            try {
                store().save();
            } catch (RuntimeException e) {
                Log.warn("Saving waypoints failed", e);
            }
        }
    }
}
