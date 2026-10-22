package dev.bloom.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/**
 * BLOOD's block outline: custom colour, opacity, thickness and an optional face tint. Edges are
 * drawn as thin camera-facing strips (so the thickness setting works on every graphics card).
 */
public final class OutlineRenderer {
    private OutlineRenderer() {
    }

    /** Returns true when BLOOD drew the outline (vanilla's should then be skipped). */
    public static boolean render(Camera camera, Matrix4f modelView) {
        if (!BloomSettings.ENABLED.on() || !BloomSettings.OUTLINE.on()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return false;
        Entity entity = camera.entity();
        VoxelShape shape = state.getShape(mc.level, pos, entity == null ? CollisionContext.empty() : CollisionContext.of(entity));
        if (shape.isEmpty()) return false;

        Vec3 cam = camera.position();
        float ox = (float) (pos.getX() - cam.x), oy = (float) (pos.getY() - cam.y), oz = (float) (pos.getZ() - cam.z);
        int rgb = BloomSettings.OUTLINE_COLOR.rgb();
        float r = Draw.r(rgb), g = Draw.g(rgb), b = Draw.b(rgb);
        float a = BloomSettings.OUTLINE_OPACITY.value();

        float fill = BloomSettings.OUTLINE_FILL.value();
        if (fill > 0.01f) {
            Draw.Batch q = Draw.color();
            BufferBuilder v = q.v();
            shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
                float e = 0.002f;
                box(q, v, ox + (float) x1 - e, oy + (float) y1 - e, oz + (float) z1 - e,
                        ox + (float) x2 + e, oy + (float) y2 + e, oz + (float) z2 + e, r, g, b, fill);
            });
            Draw.draw(q, modelView, Draw.Blend.ALPHA, true);
        }

        // Strip half-width in blocks per block of distance: ~1 px at width 1 on a 1080p screen.
        float px = BloomSettings.OUTLINE_WIDTH.value() * 0.0011f;
        Draw.Batch lines = Draw.color();
        BufferBuilder v = lines.v();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float ax = ox + (float) x1, ay = oy + (float) y1, az = oz + (float) z1;
            float bx = ox + (float) x2, by = oy + (float) y2, bz = oz + (float) z2;
            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
            // Side vector: perpendicular to the edge and to the view direction (camera is at the origin).
            float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6f) return;
            float dist = (float) Math.sqrt(mx * mx + my * my + mz * mz);
            float w = Math.max(0.003f, px * Math.max(1f, dist)) / sl;
            sx *= w;
            sy *= w;
            sz *= w;
            v.addVertex(ax - sx, ay - sy, az - sz).setColor(r, g, b, a);
            v.addVertex(bx - sx, by - sy, bz - sz).setColor(r, g, b, a);
            v.addVertex(bx + sx, by + sy, bz + sz).setColor(r, g, b, a);
            v.addVertex(ax + sx, ay + sy, az + sz).setColor(r, g, b, a);
            lines.quad();
        });
        Draw.draw(lines, modelView, Draw.Blend.ALPHA, true);
        return true;
    }

    private static void box(Draw.Batch batch, BufferBuilder q, float x1, float y1, float z1, float x2, float y2, float z2,
                            float r, float g, float b, float a) {
        quad(batch, q, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        quad(batch, q, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        quad(batch, q, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        quad(batch, q, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        quad(batch, q, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        quad(batch, q, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private static void quad(Draw.Batch batch, BufferBuilder q, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float a) {
        q.addVertex(ax, ay, az).setColor(r, g, b, a);
        q.addVertex(bx, by, bz).setColor(r, g, b, a);
        q.addVertex(cx, cy, cz).setColor(r, g, b, a);
        q.addVertex(dx, dy, dz).setColor(r, g, b, a);
        batch.quad();
    }
}
