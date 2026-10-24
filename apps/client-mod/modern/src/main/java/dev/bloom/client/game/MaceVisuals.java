package dev.bloom.client.game;

import dev.bloom.client.Mc;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.Items;

/** Cosmetic mace motes only; this class never changes item use, hit detection, or damage. */
public final class MaceVisuals {
    private static final double MAX_DIST_SQ = 28 * 28;

    private MaceVisuals() {
    }

    public static void tick(Minecraft mc) {
        if (!BloomSettings.MACE_VISUALS.on() || mc.level == null || mc.isPaused()) return;
        var camera = Mc.camera().position();
        long tick = mc.level.getGameTime();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.isInvisible() || player.isSpectator() || player.position().distanceToSqr(camera) > MAX_DIST_SQ) continue;
            if (player.getMainHandItem().is(Items.MACE)) mote(mc, player, tick, 1);
            if (player.getOffhandItem().is(Items.MACE)) mote(mc, player, tick, -1);
        }
    }

    private static void mote(Minecraft mc, AbstractClientPlayer player, long tick, int side) {
        float yaw = (float) Math.toRadians(player.getYRot());
        double rightX = Math.cos(yaw), rightZ = Math.sin(yaw);
        double frontX = -Math.sin(yaw), frontZ = Math.cos(yaw);
        double handX = player.getX() + rightX * side * 0.36 + frontX * 0.18;
        double handY = player.getY() + (player.isCrouching() ? 0.95 : 1.18);
        double handZ = player.getZ() + rightZ * side * 0.36 + frontZ * 0.18;
        double a = tick * 0.38 + side * Math.PI;
        double radius = 0.11;
        int accent = 0x39FF88;
        int gold = 0xFFD36A;
        float blend = (float) ((Math.sin(a) + 1.0) * 0.5);
        int color = lerp(accent, gold, blend);
        mc.level.addParticle(new DustParticleOptions(color, 0.55f),
                handX + Math.cos(a) * radius, handY + Math.sin(a * 1.7) * 0.09, handZ + Math.sin(a) * radius,
                -Math.cos(a) * 0.006, 0.012, -Math.sin(a) * 0.006);
        if (Math.floorMod(tick + side, 4) == 0) {
            double b = a + Math.PI;
            mc.level.addParticle(new DustParticleOptions(lerp(gold, 0xE9FFF5, blend), 0.38f),
                    handX + Math.cos(b) * 0.17, handY + Math.sin(b * 1.7) * 0.12, handZ + Math.sin(b) * 0.17,
                    0, 0.018, 0);
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}