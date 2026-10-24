package dev.bloom.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.avatar.AvatarModel;
import dev.bloom.client.core.avatar.AvatarModels;
import dev.bloom.client.game.AvatarStatePatch;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws a player's 3D MOTION avatar in place of their normal model: coloured geometry from
 * {@link AvatarModels}, animated like a player (walking, looking around, swinging).
 */
public final class AvatarLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Plain white texture: the avatar's colours come from the vertices. */
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/entity/avatar_white.png");

    public AvatarLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isSpectator) return;
        AvatarModel model = AvatarModels.get(AvatarStatePatch.modelFor(state));
        if (model == null) return;
        final float[] q;
        try {
            q = model.pose(AvatarModel.Angles.walking(state.walkAnimationPos, state.walkAnimationSpeed, xRot, yRot, state.attackTime, state.ageInTicks));
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("avatar", e);
            return;
        }
        int overlay = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        if (state.isCrouching) pose.translate(0f, 3.2f / 16f, 0f);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (p, vc) -> emit(p, vc, q, light, overlay));
        pose.popPose();
    }

    private static void emit(PoseStack.Pose p, VertexConsumer vc, float[] q, int light, int overlay) {
        for (int i = 0; i < q.length; i += 16) {
            float nx = q[i + 12], ny = q[i + 13], nz = q[i + 14];
            int color = Float.floatToRawIntBits(q[i + 15]);
            // Both windings, so faces show whatever the culling and mirroring of this pass.
            for (int v = 0; v < 4; v++) vertex(p, vc, q, i + v * 3, color, light, overlay, nx, ny, nz);
            for (int v = 3; v >= 0; v--) vertex(p, vc, q, i + v * 3, color, light, overlay, nx, ny, nz);
        }
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float[] q, int at, int color, int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(p, q[at], q[at + 1], q[at + 2]).setColor(color).setUv(0.5f, 0.5f).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
    }
}
