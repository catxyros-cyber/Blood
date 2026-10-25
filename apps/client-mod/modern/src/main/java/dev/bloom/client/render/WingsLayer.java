package dev.bloom.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.bloom.client.game.CapeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * MOTION wings: two soft, textured wings on the player's back. They rest folded behind the
 * player and breathe slowly when standing still, open as the player moves, and spread and flap
 * while falling, jumping or flying. Every movement eases in, so they never snap.
 *
 * <p>The texture is one wing with its root (shoulder) halfway down the left edge and the tip on
 * the right; the other wing is mirrored. Transparency is kept, so feathered edges stay soft.
 */
public final class WingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Wing length in model pixels (16 = one block). */
    private static final float LENGTH = 22f;
    /** Where the shoulder sits down the texture's left edge (0 top … 1 bottom). */
    private static final float ROOT_V = 0.62f;

    private static final class Motion {
        float open, phase;
        long at;
    }

    private final Map<Integer, Motion> motion = new HashMap<>();
    private long lastPrune;

    public WingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || state.isSpectator) return;
        if (state.chestEquipment != null && state.chestEquipment.is(Items.ELYTRA)) return; // the elytra takes the back
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(state.id);
        if (!(e instanceof AbstractClientPlayer player)) return;
        Identifier tex;
        try {
            tex = CapeManager.wingsFor(player.getUUID());
        } catch (RuntimeException ex) {
            return;
        }
        if (tex == null) return;

        // How open the wings are (0 folded … 1 spread), eased toward a target from movement.
        boolean airborne = !player.onGround() && !player.isInWater();
        float target = state.isFallFlying ? 1f
                : airborne ? 0.78f
                : player.isSprinting() ? 0.62f
                : Math.min(0.38f, state.walkAnimationSpeed * 0.8f);
        long now = System.nanoTime();
        Motion m = motion.computeIfAbsent(state.id, k -> new Motion());
        float dt = m.at == 0 ? 0f : Math.min(0.1f, (now - m.at) / 1e9f);
        m.at = now;
        m.open += (target - m.open) * (1f - (float) Math.exp(-dt * 4f));
        // Flap speed: a slow breath at rest, quicker as they open.
        m.phase += dt * (1.1f + 7f * m.open * m.open);
        if (now - lastPrune > 5_000_000_000L) {
            lastPrune = now;
            motion.values().removeIf(x -> now - x.at > 10_000_000_000L);
        }

        float aspect = CapeManager.wingAspect(tex);
        float length = LENGTH;
        float height = Math.max(6f, Math.min(28f, length / Math.max(0.4f, aspect)));
        // Folded: tips point back and slightly down. Open: out to the side.
        float yaw = lerp(56f, 10f, m.open);
        float flap = (float) Math.sin(m.phase) * lerp(1f, 8.5f, m.open);
        float droop = lerp(-2f, 5f, m.open);

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        int overlay = OverlayTexture.NO_OVERLAY;
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            // Hinge a little below the shoulders, just behind the back.
            pose.translate(side * 1.5f / 16f, 3.5f / 16f, 2.6f / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(-side * yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(side * (droop + flap)));
            final int s = side;
            final float w = length / 16f, h = height / 16f;
            // The wave runs from shoulder to tip: the outer feathers trail the stroke.
            final float phase = m.phase, wave = lerp(0.012f, 0.06f, m.open);
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(tex), (p, vc) -> wing(p, vc, s, w, h, light, overlay, phase, wave));
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * The whole texture on a gently curved sheet, hinged at the shoulder: the image's own
     * transparency draws the silhouette, so the art (a wing that sweeps up and out) shows exactly
     * as painted. Model space has +y pointing down, so the texture's top goes to negative y.
     */
    private static void wing(PoseStack.Pose p, VertexConsumer vc, int side, float w, float h, int light, int overlay, float phase, float wave) {
        final int segments = 20;
        final float top = -ROOT_V * h, bottom = (1f - ROOT_V) * h;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float x0 = side * w * t0;
            float x1 = side * w * t1;
            float z0 = depth(t0) + bend(t0, phase, wave);
            float z1 = depth(t1) + bend(t1, phase, wave);
            // Normal of this strip (it curves back along the span), so light falls across the wing.
            float dx = x1 - x0, dz = z1 - z0;
            float nl = (float) Math.sqrt(dx * dx + dz * dz);
            float nx = nl > 0 ? -dz / nl * side : 0f, nz = nl > 0 ? Math.abs(dx) / nl : 1f;
            vertex(p, vc, x0, bottom, z0, t0, 1f, light, overlay, 0xFFFFFFFF, nx, nz);
            vertex(p, vc, x0, top, z0, t0, 0f, light, overlay, 0xFFFFFFFF, nx, nz);
            vertex(p, vc, x1, top, z1, t1, 0f, light, overlay, 0xFFFFFFFF, nx, nz);
            vertex(p, vc, x1, bottom, z1, t1, 1f, light, overlay, 0xFFFFFFFF, nx, nz);
        }
    }

    /** Span-wise bend: the tip lags the stroke and curls a little, like real primaries. */
    private static float bend(float t, float phase, float wave) {
        return (float) Math.sin(phase - t * 1.6f) * wave * t * t;
    }

    private static float depth(float t) {
        return (float) Math.sin(t * Math.PI) * 0.08f + t * 0.02f;
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z,
                               float u, float v, int light, int overlay) {
        vertex(p, vc, x, y, z, u, v, light, overlay, 0xFFFFFFFF);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z,
                               float u, float v, int light, int overlay, int tint) {
        vertex(p, vc, x, y, z, u, v, light, overlay, tint, 0f, 1f);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z,
                               float u, float v, int light, int overlay, int tint, float nx, float nz) {
        vc.addVertex(p, x, y, z).setColor(tint).setUv(u, v).setOverlay(overlay)
                .setLight(light).setNormal(p, nx, 0f, nz);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
