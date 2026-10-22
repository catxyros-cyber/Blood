package dev.bloom.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** MOTION's block outline: custom colour, opacity, thickness and an optional face tint. */
public final class OutlineRenderer {
    private OutlineRenderer() {
    }

    /** Returns true when MOTION drew the outline (vanilla's should then be skipped). */
    public static boolean render(PoseStack poseStack, Entity entity, double camX, double camY, double camZ, BlockPos pos, BlockState state) {
        if (!BloomSettings.ENABLED.on() || !BloomSettings.OUTLINE.on()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(entity));
        if (shape.isEmpty()) return false;

        PoseStack full = new PoseStack();
        full.mulPose(WorldMatrices.MODEL_VIEW);
        full.mulPose(poseStack.last().pose());
        PoseStack.Pose pose = full.last();
        float ox = (float) (pos.getX() - camX), oy = (float) (pos.getY() - camY), oz = (float) (pos.getZ() - camZ);
        int rgb = BloomSettings.OUTLINE_COLOR.rgb();
        float r = Draw.r(rgb), g = Draw.g(rgb), b = Draw.b(rgb);
        float a = BloomSettings.OUTLINE_OPACITY.value();

        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Draw.blendAlpha();
        try {
            float fill = BloomSettings.OUTLINE_FILL.value();
            if (fill > 0.01f) {
                BufferBuilder q = Draw.beginColor();
                int[] count = {0};
                Matrix4f m = pose.pose();
                shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
                    float e = 0.002f;
                    count[0] += box(q, m, ox + (float) x1 - e, oy + (float) y1 - e, oz + (float) z1 - e,
                            ox + (float) x2 + e, oy + (float) y2 + e, oz + (float) z2 + e, r, g, b, fill);
                });
                Draw.drawColor(q, count[0]);
            }
            RenderSystem.lineWidth(BloomSettings.OUTLINE_WIDTH.value() * Math.max(1f, mc.getWindow().getWidth() / 1920f));
            BufferBuilder lines = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            int[] n = {0};
            shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
                float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-6f) return;
                dx /= len;
                dy /= len;
                dz /= len;
                lines.addVertex(pose, ox + (float) x1, oy + (float) y1, oz + (float) z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz);
                lines.addVertex(pose, ox + (float) x2, oy + (float) y2, oz + (float) z2).setColor(r, g, b, a).setNormal(pose, dx, dy, dz);
                n[0] += 2;
            });
            if (n[0] > 0) {
                RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                BufferUploader.drawWithShader(lines.buildOrThrow());
            } else {
                var mesh = lines.build();
                if (mesh != null) mesh.close();
            }
        } finally {
            RenderSystem.lineWidth(1f);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            Draw.restoreBlend();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
        return true;
    }

    private static int box(BufferBuilder q, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                           float r, float g, float b, float a) {
        // six faces
        quad(q, m, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        quad(q, m, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        quad(q, m, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        quad(q, m, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        quad(q, m, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        quad(q, m, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
        return 24;
    }

    private static void quad(BufferBuilder q, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float a) {
        q.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        q.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        q.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        q.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
    }
}
