package dev.bloom.client.game;

import dev.bloom.client.core.fx.HitShapes;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * MOTION hit effects: when you hit something, a shockwave ring spreads at its feet, a slash
 * trail cuts across it and sparks burst out, in your colour. Purely visual and client-side:
 * nothing here changes the attack, reach, damage or what the server sees.
 */
public final class HitEffects {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int MAX_ACTIVE = 12;
    private static final List<Effect> ACTIVE = new ArrayList<>();

    private record Effect(Entity target, double yaw, int color, long startTick) {
    }

    private HitEffects() {
    }

    /** Called (from MultiPlayerGameModeMixin) when the local player attacks an entity. */
    public static void onAttack(Minecraft mc, Entity target) {
        if (!BloomSettings.ENABLED.on() || !BloomSettings.HITS.on() || mc.level == null || mc.player == null) return;
        if (!(target instanceof LivingEntity) || target.isInvisible()) return;
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
        int color = BloomSettings.HIT_RAINBOW.on() ? HitShapes.rainbow(System.nanoTime() / 1e9) : BloomSettings.HIT_COLOR.rgb();
        ACTIVE.add(new Effect(target, mc.player.getYRot(), color, mc.level.getGameTime()));
        if (BloomSettings.HIT_SPARKS.on()) sparks(mc, target, color);
    }

    /** Client tick: advance the rings and slashes. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.isPaused()) {
            if (mc.level == null) ACTIVE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        float amount = BloomSettings.HIT_INTENSITY.value();
        ACTIVE.removeIf(e -> now - e.startTick() >= HitShapes.LIFETIME || e.target().isRemoved() || e.target().level() != mc.level);
        for (Effect e : ACTIVE) {
            int age = (int) (now - e.startTick());
            Vec3 pos = e.target().position();
            double h = e.target().getBbHeight();
            if (BloomSettings.HIT_RINGS.on()) ring(mc, e, pos, age, amount);
            if (BloomSettings.HIT_SLASH.on() && age < 3) slash(mc, e, pos.add(0, h * 0.55, 0), h, age, amount);
        }
    }

    private static void ring(Minecraft mc, Effect e, Vec3 feet, int age, float amount) {
        double r = HitShapes.ringRadius(age);
        int count = Math.max(8, Math.round(26 * amount));
        float fade = 1f - age / (float) HitShapes.LIFETIME;
        float size = 0.35f + 0.6f * fade;
        int color = HitShapes.lighten(e.color(), age * 0.05f);
        for (double[] p : HitShapes.ring(r, count, age * 0.21)) {
            mc.level.addParticle(dust(color, size), feet.x + p[0], feet.y + 0.06, feet.z + p[2], 0, 0.004, 0);
        }
        // A second, fainter ring a little behind the first gives the spread some depth.
        if (age >= 1 && age % 2 == 1) {
            for (double[] p : HitShapes.ring(r * 0.72, Math.max(6, count / 2), age * 0.4)) {
                mc.level.addParticle(dust(HitShapes.lighten(e.color(), 0.4f), size * 0.7f), feet.x + p[0], feet.y + 0.04, feet.z + p[2], 0, 0, 0);
            }
        }
    }

    private static void slash(Minecraft mc, Effect e, Vec3 center, double height, int age, float amount) {
        int steps = Math.max(6, Math.round(14 * amount));
        double size = Math.max(0.7, height * 0.62);
        double from = age / 3.0, to = (age + 1) / 3.0;
        double[][] pts = HitShapes.slash(e.yaw(), from, to, steps, size);
        for (int i = 0; i < pts.length; i++) {
            double[] p = pts[i];
            float t = (float) (from + (to - from) * i / (pts.length - 1.0));
            // Thick and bright in the middle of the cut, thin at the ends.
            float thick = 0.45f + 0.9f * (float) Math.sin(t * Math.PI);
            mc.level.addParticle(dust(HitShapes.lighten(e.color(), 0.15f + 0.25f * (1 - thick / 1.35f)), thick),
                    center.x + p[0], center.y + p[1], center.z + p[2], 0, 0, 0);
            if (i % 3 == 0) {
                mc.level.addParticle(ParticleTypes.END_ROD, center.x + p[0], center.y + p[1], center.z + p[2],
                        (RANDOM.nextDouble() - 0.5) * 0.01, 0.005, (RANDOM.nextDouble() - 0.5) * 0.01);
            }
        }
    }

    private static void sparks(Minecraft mc, Entity target, int color) {
        float amount = BloomSettings.HIT_INTENSITY.value();
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
        int n = Math.max(4, Math.round(10 * amount));
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, up = RANDOM.nextDouble() * 0.25 - 0.05;
            double sp = 0.12 + RANDOM.nextDouble() * 0.18;
            mc.level.addParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CRIT,
                    c.x, c.y, c.z, Math.cos(a) * sp, up + 0.05, Math.sin(a) * sp);
            mc.level.addParticle(dust(HitShapes.lighten(color, RANDOM.nextFloat() * 0.5f), 0.5f + RANDOM.nextFloat() * 0.4f),
                    c.x + Math.cos(a) * 0.25, c.y + up, c.z + Math.sin(a) * 0.25, Math.cos(a) * sp * 0.4, up * 0.3, Math.sin(a) * sp * 0.4);
        }
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), Math.max(0.05f, Math.min(4f, size)));
    }
}
