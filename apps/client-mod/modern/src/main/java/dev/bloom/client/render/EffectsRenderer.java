package dev.bloom.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.particles.ParticleSystem;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws MOTION's particles. Called once per frame after the world's weather is drawn
 * (Fabric: WorldRenderEvents.LAST, NeoForge: RenderLevelStageEvent AFTER_WEATHER).
 * Vertices are transformed on the CPU with the camera's model-view matrix, so the result
 * doesn't depend on whatever matrix state other mods leave behind.
 */
public final class EffectsRenderer {
    public static final Identifier ATLAS = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/effects/atlas.png");
    private static final float CELL = 0.25f;
    private static final float PAD = 0.5f / 128f;
    private static int failures;

    private EffectsRenderer() {
    }

    public static void render(BloomClient bc, Matrix4f modelView, Matrix4f projection, Camera camera, float partialTick) {
        ParticleSystem ps = bc.particles();
        if (ps.count() == 0 || failures > 5) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        try {
            draw(ps, level, modelView, projection, camera, partialTick);
        } catch (RuntimeException e) {
            failures++;
            Log.warn("MOTION particle rendering failed" + (failures > 5 ? "; particles disabled for this session" : ""), e);
        }
    }

    private static void draw(ParticleSystem ps, ClientLevel level, Matrix4f modelView, Matrix4f projection, Camera camera, float partialTick) {
        var pos = camera.position();
        // Camera axes from yaw/pitch (Minecraft: yaw 0 looks south, +Z).
        float yaw = (float) Math.toRadians(camera.yRot()), pitch = (float) Math.toRadians(camera.xRot());
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float sy = (float) Math.sin(yaw), cy = (float) Math.cos(yaw);
        Vector3f look = new Vector3f(-sy * cp, -sp, cy * cp);
        Vector3f left = new Vector3f(cy, 0f, sy);
        Vector3f up = new Vector3f(look).cross(left);
        float tanX = 1f / Math.max(1e-4f, Math.abs(projection.m00()));
        float tanY = 1f / Math.max(1e-4f, Math.abs(projection.m11()));
        float half = (float) Math.atan(Math.sqrt(tanX * tanX + tanY * tanY)) + 0.2f;
        float cosLimit = half >= Math.PI ? -1f : (float) Math.cos(half);
        float sky = dev.bloom.client.core.sky.SkyTime.daylight(dev.bloom.client.core.sky.SkyTime.timeOfDay(level.getDayTime(), partialTick));
        sky *= 1f - 0.4f * level.getRainLevel(partialTick);
        float light = 0.32f + 0.68f * sky;

        Sink sink = new Sink(left, up, light);
        // Pass 1: petals, leaves, snow, rain, splashes (normal blending).
        sink.glowPass = false;
        sink.begin();
        ps.render(sink, partialTick, pos.x, pos.y, pos.z, look.x(), look.y(), look.z(), cosLimit);
        Draw.draw(sink.batch, ATLAS, modelView, Draw.Blend.ALPHA, true);
        // Pass 2: fireflies and motes (additive glow).
        if (ps.count(ParticleSystem.FIREFLY) + ps.count(ParticleSystem.DUST) > 0) {
            sink.glowPass = true;
            sink.begin();
            ps.render(sink, partialTick, pos.x, pos.y, pos.z, look.x(), look.y(), look.z(), cosLimit);
            Draw.draw(sink.batch, ATLAS, modelView, Draw.Blend.ADDITIVE, true);
        }
    }

    private static final class Sink implements ParticleSystem.QuadSink {
        final float lx, ly, lz, ux, uy, uz;
        final float light;
        Draw.Batch batch;
        BufferBuilder builder;
        boolean glowPass;

        Sink(Vector3f left, Vector3f up, float light) {
            lx = left.x();
            ly = left.y();
            lz = left.z();
            ux = up.x();
            uy = up.y();
            uz = up.z();
            this.light = light;
        }

        void begin() {
            batch = Draw.texColor();
            builder = batch.v();
        }

        @Override
        public void particle(int type, int shape, float x, float y, float z, float size, float rot, float tilt,
                             float vx, float vy, float vz, int argb) {
            boolean glow = type == ParticleSystem.FIREFLY || type == ParticleSystem.DUST;
            if (glow != glowPass) return;
            float a = ((argb >>> 24) & 255) / 255f;
            float lit = glow ? 1f : light;
            float r = Draw.r(argb) * lit, g = Draw.g(argb) * lit, b = Draw.b(argb) * lit;
            int col, row;
            switch (type) {
                case ParticleSystem.PETAL -> {
                    col = Math.min(3, shape);
                    row = 0;
                }
                case ParticleSystem.LEAF -> {
                    col = shape & 1;
                    row = 1;
                }
                case ParticleSystem.SNOW -> {
                    if (shape == 1) {
                        col = 3;
                        row = 1;
                    } else if (shape == 2) {
                        col = 0;
                        row = 2;
                    } else {
                        col = 2;
                        row = 1;
                    }
                }
                case ParticleSystem.RAIN -> {
                    col = 2;
                    row = 2;
                }
                case ParticleSystem.SPLASH -> {
                    col = 3;
                    row = 2;
                }
                default -> {
                    col = 1;
                    row = 2;
                }
            }
            float u0 = col * CELL + PAD, v0 = row * CELL + PAD, u1 = (col + 1) * CELL - PAD, v1 = (row + 1) * CELL - PAD;
            float h = size * 0.5f;
            float ax, ay, az, bx, by, bz;
            switch (type) {
                case ParticleSystem.PETAL, ParticleSystem.LEAF -> {
                    float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
                    float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
                    ax = c * h;
                    ay = 0;
                    az = s * h;
                    bx = -s * st * h;
                    by = ct * h;
                    bz = c * st * h;
                }
                case ParticleSystem.RAIN -> {
                    float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    if (len < 1e-4f) return;
                    float dx = vx / len, dy = vy / len, dz = vz / len;
                    // width axis perpendicular to the streak and the view direction
                    float wx = dy * z - dz * y, wy = dz * x - dx * z, wz = dx * y - dy * x;
                    float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
                    if (wl < 1e-4f) return;
                    float w = 0.035f;
                    ax = wx / wl * w;
                    ay = wy / wl * w;
                    az = wz / wl * w;
                    bx = dx * size * 0.5f;
                    by = dy * size * 0.5f;
                    bz = dz * size * 0.5f;
                }
                default -> {
                    ax = lx * h;
                    ay = ly * h;
                    az = lz * h;
                    bx = ux * h;
                    by = uy * h;
                    bz = uz * h;
                }
            }
            builder.addVertex(x - ax + bx, y - ay + by, z - az + bz).setUv(u0, v0).setColor(r, g, b, a);
            builder.addVertex(x - ax - bx, y - ay - by, z - az - bz).setUv(u0, v1).setColor(r, g, b, a);
            builder.addVertex(x + ax - bx, y + ay - by, z + az - bz).setUv(u1, v1).setColor(r, g, b, a);
            builder.addVertex(x + ax + bx, y + ay + by, z + az + bz).setUv(u1, v0).setColor(r, g, b, a);
            batch.quad();
        }
    }

    /** True when there is anything to draw this frame (lets loaders skip the call cheaply). */
    public static boolean active() {
        BloomClient bc = BloomClient.get();
        return bc != null && BloomSettings.ENABLED.on() && bc.particles().count() > 0;
    }
}
