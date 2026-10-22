package dev.bloom.client.game;

import dev.bloom.client.core.cape.CapeDirectory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * MOTION auras: soft particles around players in their chosen colour. Client-side only (only
 * MOTION users see them). Styles:
 * <ul>
 *   <li>glow: a gentle haze that drifts up around the body;</li>
 *   <li>embers: small sparks rising from the feet;</li>
 *   <li>sparkles: occasional bright twinkles around the player;</li>
 *   <li>orbit: a ring of light circling the waist.</li>
 * </ul>
 * Your own aura is hidden in first person, and players further than 40 blocks away are skipped.
 */
public final class Auras {
    private static final double MAX_DIST_SQ = 40 * 40;
    private static final RandomSource RANDOM = RandomSource.create();

    private Auras() {
    }

    /** Client tick: spawn this tick's particles. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.isPaused() || mc.player == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        long t = mc.level.getGameTime();
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.isInvisible() || p.isSpectator()) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (p.position().distanceToSqr(cam) > MAX_DIST_SQ) continue;
            CapeDirectory.Aura aura = CapeManager.auraFor(p.getUUID());
            if (aura == null) continue;
            spawn(mc, p, aura, t);
        }
    }

    private static void spawn(Minecraft mc, AbstractClientPlayer p, CapeDirectory.Aura aura, long t) {
        double x = p.getX(), y = p.getY(), z = p.getZ();
        double h = p.getBbHeight();
        int rgb = aura.rgb();
        switch (aura.style()) {
            case "embers" -> {
                if (RANDOM.nextFloat() < 0.7f) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = 0.25 + RANDOM.nextDouble() * 0.3;
                    mc.level.addParticle(new DustParticleOptions(lighten(rgb, RANDOM.nextFloat() * 0.35f), 0.45f + RANDOM.nextFloat() * 0.35f),
                            x + Math.cos(a) * r, y + 0.05 + RANDOM.nextDouble() * 0.3, z + Math.sin(a) * r, 0, 0.06 + RANDOM.nextDouble() * 0.05, 0);
                }
            }
            case "sparkles" -> {
                if (RANDOM.nextFloat() < 0.35f) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = 0.45 + RANDOM.nextDouble() * 0.35;
                    mc.level.addParticle(new DustParticleOptions(lighten(rgb, 0.45f), 0.7f + RANDOM.nextFloat() * 0.5f),
                            x + Math.cos(a) * r, y + RANDOM.nextDouble() * h, z + Math.sin(a) * r, 0, 0.01, 0);
                }
            }
            case "orbit" -> {
                for (int i = 0; i < 2; i++) {
                    double a = t * 0.22 + i * Math.PI;
                    mc.level.addParticle(new DustParticleOptions(rgb, 0.8f),
                            x + Math.cos(a) * 0.65, y + h * 0.5 + Math.sin(t * 0.05 + i) * 0.15, z + Math.sin(a) * 0.65, 0, 0, 0);
                }
            }
            default -> { // glow
                if (RANDOM.nextFloat() < 0.6f) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = 0.3 + RANDOM.nextDouble() * 0.25;
                    mc.level.addParticle(new DustParticleOptions(rgb, 1.1f + RANDOM.nextFloat() * 0.6f),
                            x + Math.cos(a) * r, y + 0.2 + RANDOM.nextDouble() * (h - 0.3), z + Math.sin(a) * r, 0, 0.02, 0);
                }
            }
        }
    }

    /** Mix an RGB colour toward white. */
    private static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * f);
        g += (int) ((255 - g) * f);
        b += (int) ((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }
}
