package dev.bloom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/** Hold-to-look-around camera that keeps the local player's movement and server-facing yaw fixed. */
public final class FreeLook {
    private static LocalPlayer player;
    private static boolean active;
    private static float lockedYaw, lockedPitch, lockedHeadYaw, lockedBodyYaw;
    private static float cameraYaw, cameraPitch;

    private FreeLook() {}

    /** Called each client tick so the key works as a hold, not a toggle. */
    public static void tick(Minecraft minecraft) {
        LocalPlayer current = minecraft.player;
        boolean held = current != null && minecraft.screen == null && BloomKeys.FREE_LOOK.isDown();
        if (!held) {
            stop();
            return;
        }
        if (!active || player != current) {
            player = current;
            lockedYaw = current.getYRot();
            lockedPitch = current.getXRot();
            lockedHeadYaw = current.getYHeadRot();
            lockedBodyYaw = current.yBodyRot;
            cameraYaw = lockedYaw;
            cameraPitch = lockedPitch;
            active = true;
        }
    }

    /** Capture each mouse turn for the camera, then restore the player's movement orientation. */
    public static void afterMouseTurn(LocalPlayer current) {
        if (!active || player == null || current != player) return;
        float yawDelta = current.getYRot() - lockedYaw;
        if (yawDelta > 180f) yawDelta -= 360f;
        if (yawDelta < -180f) yawDelta += 360f;
        cameraYaw += yawDelta;
        cameraPitch = Math.max(-90f, Math.min(90f, cameraPitch + current.getXRot() - lockedPitch));
        restorePlayer();
    }

    public static float viewYaw(Entity entity, float partialTick) {
        return active && entity == player ? cameraYaw : entity.getViewYRot(partialTick);
    }

    public static float viewPitch(Entity entity, float partialTick) {
        return active && entity == player ? cameraPitch : entity.getViewXRot(partialTick);
    }

    private static void stop() {
        if (!active) return;
        restorePlayer();
        player = null;
        active = false;
    }

    private static void restorePlayer() {
        player.setYRot(lockedYaw);
        player.setXRot(lockedPitch);
        player.setYHeadRot(lockedHeadYaw);
        player.setYBodyRot(lockedBodyYaw);
    }
}
