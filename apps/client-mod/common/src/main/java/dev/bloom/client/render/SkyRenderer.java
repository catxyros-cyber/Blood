package dev.bloom.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.sky.CubeLayout;
import dev.bloom.client.core.sky.SkyboxCatalog;
import dev.bloom.client.core.sky.SkyboxLibrary;
import dev.bloom.client.game.GameEnv;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws BLOOD's sky on top of the vanilla sky (called at the end of LevelRenderer#renderSky):
 * day/night skyboxes with crossfade, horizon blending and haze, the sun and moon, a soft glow
 * around them and BLOOD's starfield. Nothing here touches world state.
 */
public final class SkyRenderer {
    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final ResourceLocation HALO = ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/effects/halo.png");
    private static final int MAX_STARS = 3000;

    private static float[] stars; // x, y, z, size, phase, warmth per star
    private static int starCount = -1;

    private SkyRenderer() {
    }

    public static void render(Matrix4f modelView, Matrix4f projection, float partialTick, Camera camera, boolean isFoggy) {
        BloomClient bc = BloomClient.get();
        if (bc == null || !bc.skyActive()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!GameEnv.normalSky(level) || isFoggy) return;
        if (camera.getFluidInCamera() != FogType.NONE) return;
        if (camera.getEntity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS))) return;

        float timeOfDay = level.getTimeOfDay(partialTick); // 0 = noon, 0.5 = midnight
        float rain = level.getRainLevel(partialTick);
        float raw = clamp01((float) Math.cos(timeOfDay * Math.PI * 2) * 2f + 0.5f); // vanilla daylight curve
        float width = 1f - 0.85f * BloomSettings.SKY_TRANSITION.value();
        float dayW = smoothstep(0.5f - width / 2f, 0.5f + width / 2f, raw);

        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        RenderSystem.depthMask(false);
        try {
            // Always called: turning the skybox off fades back to Minecraft's sky instead of popping.
            drawSkyboxes(pose, timeOfDay, dayW, rain, BloomSettings.SKY_ENABLED.on());
            if (BloomSettings.SKY_ENABLED.on()) drawHorizon(pose);
            if (BloomSettings.SKY_STARS.on() && !BloomSettings.LITE_MODE.on()) drawStars(pose, level.getStarBrightness(partialTick) * (1f - rain), timeOfDay);
            boolean covered = BloomSettings.SKY_ENABLED.on() && !BloomSettings.SKY_DAY.is(SkyboxCatalog.NONE);
            // Sunrise/sunset skies have their own sun painted in: don't add a second one.
            String shownNow = preview != null ? preview : BloomSettings.SKY_DAY.id();
            if (paintedSun(shownNow) || paintedSun(BloomSettings.SKY_NIGHT.id())) covered = false;
            if (BloomSettings.SKY_ENABLED.on() && BloomSettings.SKY_SUN_MOON.on() && covered) drawSunMoon(pose, level, timeOfDay, rain);
            if (BloomSettings.SUN_GLOW.on()) drawGlow(pose, timeOfDay, rain, raw);
        } finally {
            Draw.restoreBlend();
            RenderSystem.depthMask(true);
        }
    }

    // ------------------------------------------------------------------ skyboxes

    private static long fadeMs() {
        return Math.round(BloomSettings.SKY_FADE_TIME.value() * 1000f);
    }
    private static String shownDay, shownNight, fromDay, fromNight;
    private static long fadeStart;

    /** "Try in world" from the sky browser: shown day and night until cleared (menu closed or applied). */
    private static volatile String preview;

    public static void setPreview(String id) {
        preview = id;
    }

    public static String preview() {
        return preview;
    }

    private static void drawSkyboxes(PoseStack pose, float timeOfDay, float dayW, float rain, boolean enabled) {
        String p = preview;
        String dayId = p != null ? p : enabled ? BloomSettings.SKY_DAY.id() : SkyboxCatalog.NONE;
        String nightId = p != null ? p : enabled ? BloomSettings.SKY_NIGHT.id() : SkyboxCatalog.NONE;
        long now = System.currentTimeMillis();
        if (shownDay == null) {
            shownDay = dayId;
            shownNight = nightId;
        } else if (!dayId.equals(shownDay) || !nightId.equals(shownNight)) {
            // Smooth transitions: keep drawing the previous skyboxes underneath and fade the new ones in.
            boolean smooth = BloomSettings.SMOOTH_TRANSITIONS.on() && !BloomSettings.REDUCE_MOTION.on() && fadeMs() > 0;
            fromDay = smooth ? shownDay : null;
            fromNight = smooth ? shownNight : null;
            fadeStart = now;
            shownDay = dayId;
            shownNight = nightId;
        }
        float t = 1f;
        if (fromDay != null) {
            t = (now - fadeStart) / (float) Math.max(1L, fadeMs());
            if (t >= 1f) {
                fromDay = fromNight = null;
                t = 1f;
            } else {
                t = t * t * (3f - 2f * t);
            }
        }
        float dimming = 1f - 0.45f * rain; // rain darkens the sky like vanilla's
        boolean newHasSky = !SkyboxCatalog.NONE.equals(dayId) || !SkyboxCatalog.NONE.equals(nightId);
        if (fromDay != null) {
            // Opaque old set underneath + new set at alpha t on top = a true crossfade. If the new
            // choice is "no skybox", the old one fades out instead.
            drawSet(pose, fromDay, fromNight, timeOfDay, dayW, dimming, newHasSky ? 1f : 1f - t);
        }
        drawSet(pose, dayId, nightId, timeOfDay, dayW, dimming, t);
    }

    private static void drawSet(PoseStack pose, String dayId, String nightId, float timeOfDay, float dayW, float dimming, float scale) {
        if (scale <= 0.002f) return;
        float dayOp = BloomSettings.SKY_DAY_OPACITY.value() * scale;
        float nightOp = BloomSettings.SKY_NIGHT_OPACITY.value() * scale;
        boolean hasDay = !SkyboxCatalog.NONE.equals(dayId);
        boolean hasNight = !SkyboxCatalog.NONE.equals(nightId);

        if (hasDay && hasNight && dayId.equals(nightId)) {
            float o = dayOp + (nightOp - dayOp) * (1f - dayW);
            drawBox(pose, dayId, o, dimming, timeOfDay);
            return;
        }
        // Day/night crossfade: weights for (night, day) such that the vanilla sky shows through
        // only by the configured opacity. See docs/CUSTOM_SKYBOXES.md.
        float a2 = hasDay ? dayOp * dayW : 0f;
        float nightWeight = hasNight ? nightOp * (1f - dayW) : 0f;
        float a1 = a2 >= 0.999f ? 0f : nightWeight / (1f - a2);
        if (a1 > 0.002f) drawBox(pose, nightId, Math.min(1f, a1), dimming, timeOfDay);
        if (a2 > 0.002f) drawBox(pose, dayId, a2, dimming, timeOfDay);
    }

    private static void drawBox(PoseStack pose, String id, float alpha, float dimming, float timeOfDay) {
        ResourceLocation tex = SkyTextures.get(id);
        if (tex == null) return;
        SkyboxLibrary.Meta meta = id.startsWith(SkyboxCatalog.CUSTOM_PREFIX) ? BloomClient.get().skyboxes().meta(id) : null;
        boolean additive = meta != null && meta.additive();
        pose.pushPose();
        float minutes = (System.nanoTime() / 1_000_000_000f) / 60f;
        float spin = BloomSettings.SKY_ROTATION.value() + (minutes * BloomSettings.SKY_ROTATION_SPEED.value()) % 360f;
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        if (meta != null && meta.rotate()) {
            float[] ax = meta.axis();
            float len = (float) Math.sqrt(ax[0] * ax[0] + ax[1] * ax[1] + ax[2] * ax[2]);
            if (len > 1e-4f) {
                pose.mulPose(new Quaternionf().rotationAxis((float) (timeOfDay * Math.PI * 2 * meta.speed()),
                        ax[0] / len, ax[1] / len, ax[2] / len));
            }
        }
        if (additive) Draw.blendAdditive();
        else Draw.blendAlpha();
        float c = dimming * (additive ? alpha : 1f);
        float a = additive ? 1f : alpha;
        BufferBuilder b = Draw.beginTexColor();
        // Inset each face by exactly half a texel: neighbouring faces of the atlas can't bleed into
        // each other, and edge texels (which lie on the cube edge) meet their neighbours seamlessly.
        int faceSize = SkyTextures.faceSize(id);
        float insetU = 0.5f / (3f * faceSize), insetV = 0.5f / (2f * faceSize);
        for (int face = 0; face < 6; face++) {
            float[] uv = CubeLayout.UV[face];
            float u0 = uv[0] + insetU, v0 = uv[1] + insetV, u1 = uv[2] - insetU, v1 = uv[3] - insetV;
            pose.pushPose();
            rotateFace(pose, face);
            Matrix4f m = pose.last().pose();
            b.addVertex(m, -100f, -100f, -100f).setUv(u0, v0).setColor(c, c, c, a);
            b.addVertex(m, -100f, -100f, 100f).setUv(u0, v1).setColor(c, c, c, a);
            b.addVertex(m, 100f, -100f, 100f).setUv(u1, v1).setColor(c, c, c, a);
            b.addVertex(m, 100f, -100f, -100f).setUv(u1, v0).setColor(c, c, c, a);
            pose.popPose();
        }
        Draw.drawTexColor(b, 24, tex);
        pose.popPose();
    }

    /** Same rotations as OptiFine/FabricSkyBoxes so imported skies line up (see CubeLayout). */
    private static void rotateFace(PoseStack pose, int face) {
        switch (face) {
            case CubeLayout.NORTH -> pose.mulPose(Axis.XP.rotationDegrees(90f));
            case CubeLayout.SOUTH -> {
                pose.mulPose(Axis.XP.rotationDegrees(-90f));
                pose.mulPose(Axis.YP.rotationDegrees(180f));
            }
            case CubeLayout.TOP -> pose.mulPose(Axis.XP.rotationDegrees(180f));
            case CubeLayout.EAST -> {
                pose.mulPose(Axis.ZP.rotationDegrees(90f));
                pose.mulPose(Axis.YP.rotationDegrees(-90f));
            }
            case CubeLayout.WEST -> {
                pose.mulPose(Axis.ZP.rotationDegrees(-90f));
                pose.mulPose(Axis.YP.rotationDegrees(90f));
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ horizon & haze

    private static void drawHorizon(PoseStack pose) {
        float blend = BloomSettings.SKY_HORIZON.value();
        float haze = BloomSettings.HAZE.value();
        if (blend <= 0.01f && haze <= 0.01f) return;
        float[] fog = RenderSystem.getShaderFogColor();
        int hz = BloomSettings.HAZE_COLOR.rgb();
        Matrix4f m = pose.last().pose();
        Draw.blendAlpha();
        BufferBuilder b = Draw.beginColor();
        int seg = 48;
        int verts = 0;
        // Each band: {y bottom, y top, alpha bottom, alpha top} in direction space (radius 100).
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) Math.cos(a0) * 100f, z0 = (float) Math.sin(a0) * 100f;
            float x1 = (float) Math.cos(a1) * 100f, z1 = (float) Math.sin(a1) * 100f;
            if (blend > 0.01f) {
                verts += band(b, m, x0, z0, x1, z1, -60f, 0f, fog[0], fog[1], fog[2], blend, blend);
                verts += band(b, m, x0, z0, x1, z1, 0f, 30f * blend + 8f, fog[0], fog[1], fog[2], blend, 0f);
            }
            if (haze > 0.01f) {
                float a = Math.min(0.85f, haze * 0.8f);
                verts += band(b, m, x0, z0, x1, z1, -12f, 0f, Draw.r(hz), Draw.g(hz), Draw.b(hz), 0f, a);
                verts += band(b, m, x0, z0, x1, z1, 0f, 40f, Draw.r(hz), Draw.g(hz), Draw.b(hz), a, 0f);
            }
        }
        Draw.drawColor(b, verts);
    }

    private static int band(BufferBuilder b, Matrix4f m, float x0, float z0, float x1, float z1, float yBottom, float yTop,
                            float r, float g, float bl, float aBottom, float aTop) {
        b.addVertex(m, x0, yBottom, z0).setColor(r, g, bl, aBottom);
        b.addVertex(m, x1, yBottom, z1).setColor(r, g, bl, aBottom);
        b.addVertex(m, x1, yTop, z1).setColor(r, g, bl, aTop);
        b.addVertex(m, x0, yTop, z0).setColor(r, g, bl, aTop);
        return 4;
    }

    // ------------------------------------------------------------------ sun, moon, glow

    private static void drawSunMoon(PoseStack pose, ClientLevel level, float timeOfDay, float rain) {
        float alpha = 1f - rain;
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90f));
        pose.mulPose(Axis.XP.rotationDegrees(timeOfDay * 360f));
        Matrix4f m = pose.last().pose();
        Draw.blendAdditive();
        BufferBuilder b = Draw.beginTexColor();
        b.addVertex(m, -30f, 100f, -30f).setUv(0f, 0f).setColor(1f, 1f, 1f, alpha);
        b.addVertex(m, 30f, 100f, -30f).setUv(1f, 0f).setColor(1f, 1f, 1f, alpha);
        b.addVertex(m, 30f, 100f, 30f).setUv(1f, 1f).setColor(1f, 1f, 1f, alpha);
        b.addVertex(m, -30f, 100f, 30f).setUv(0f, 1f).setColor(1f, 1f, 1f, alpha);
        Draw.drawTexColor(b, 4, SUN);

        int phase = level.getMoonPhase();
        int px = phase % 4, py = phase / 4 % 2;
        float u0 = px / 4f, v0 = py / 2f, u1 = (px + 1) / 4f, v1 = (py + 1) / 2f;
        b = Draw.beginTexColor();
        b.addVertex(m, -20f, -100f, 20f).setUv(u1, v1).setColor(1f, 1f, 1f, alpha);
        b.addVertex(m, 20f, -100f, 20f).setUv(u0, v1).setColor(1f, 1f, 1f, alpha);
        b.addVertex(m, 20f, -100f, -20f).setUv(u0, v0).setColor(1f, 1f, 1f, alpha);
        b.addVertex(m, -20f, -100f, -20f).setUv(u1, v0).setColor(1f, 1f, 1f, alpha);
        Draw.drawTexColor(b, 4, MOON);
        pose.popPose();
    }

    private static void drawGlow(PoseStack pose, float timeOfDay, float rain, float daylight) {
        float strength = BloomSettings.SUN_GLOW_STRENGTH.value() * (1f - rain);
        if (strength <= 0.01f) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90f));
        pose.mulPose(Axis.XP.rotationDegrees(timeOfDay * 360f));
        Matrix4f m = pose.last().pose();
        Draw.blendAdditive();
        BufferBuilder b = Draw.beginTexColor();
        // Sun halo: warmer and larger near the horizon.
        float sunA = strength * 0.55f;
        float s = 70f;
        b.addVertex(m, -s, 99f, -s).setUv(0f, 0f).setColor(1f, 0.86f, 0.72f, sunA);
        b.addVertex(m, s, 99f, -s).setUv(1f, 0f).setColor(1f, 0.86f, 0.72f, sunA);
        b.addVertex(m, s, 99f, s).setUv(1f, 1f).setColor(1f, 0.86f, 0.72f, sunA);
        b.addVertex(m, -s, 99f, s).setUv(0f, 1f).setColor(1f, 0.86f, 0.72f, sunA);
        float moonA = strength * 0.35f * (1f - daylight);
        float ms = 42f;
        b.addVertex(m, -ms, -99f, ms).setUv(0f, 0f).setColor(0.8f, 0.86f, 1f, moonA);
        b.addVertex(m, ms, -99f, ms).setUv(1f, 0f).setColor(0.8f, 0.86f, 1f, moonA);
        b.addVertex(m, ms, -99f, -ms).setUv(1f, 1f).setColor(0.8f, 0.86f, 1f, moonA);
        b.addVertex(m, -ms, -99f, -ms).setUv(0f, 1f).setColor(0.8f, 0.86f, 1f, moonA);
        Draw.drawTexColor(b, 8, HALO);
        pose.popPose();
    }

    // ------------------------------------------------------------------ stars

    private static void ensureStars() {
        int wanted = Math.round(MAX_STARS * BloomSettings.SKY_STAR_DENSITY.value());
        if (wanted == starCount) return;
        starCount = wanted;
        stars = new float[wanted * 6];
        java.util.Random r = new java.util.Random(10842L);
        int n = 0;
        while (n < wanted) {
            float x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
            float d = x * x + y * y + z * z;
            if (d < 0.01f || d > 1f) continue;
            float inv = 1f / (float) Math.sqrt(d);
            int o = n * 6;
            stars[o] = x * inv * 100f;
            stars[o + 1] = y * inv * 100f;
            stars[o + 2] = z * inv * 100f;
            stars[o + 3] = 0.12f + r.nextFloat() * r.nextFloat() * 0.3f;
            stars[o + 4] = r.nextFloat() * 6.28f;
            stars[o + 5] = r.nextFloat();
            n++;
        }
    }

    private static void drawStars(PoseStack pose, float brightness, float timeOfDay) {
        if (brightness <= 0.01f) return;
        ensureStars();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90f));
        pose.mulPose(Axis.XP.rotationDegrees(timeOfDay * 360f));
        Matrix4f m = pose.last().pose();
        Draw.blendAdditive();
        BufferBuilder b = Draw.beginColor();
        float t = (System.nanoTime() % 1_000_000_000_000L) / 1_000_000_000f;
        int verts = 0;
        for (int i = 0; i < starCount; i++) {
            int o = i * 6;
            float x = stars[o], y = stars[o + 1], z = stars[o + 2], s = stars[o + 3];
            float tw = 0.7f + 0.3f * (float) Math.sin(t * (1.3f + stars[o + 5]) + stars[o + 4]);
            float a = brightness * tw;
            float warm = stars[o + 5];
            float r = 0.85f + 0.15f * warm, g = 0.88f + 0.08f * warm, bl = 1f - 0.15f * warm;
            // Billboard-ish quad: two perpendicular axes in the tangent plane.
            float ax, ay, az;
            if (Math.abs(y) < 90f) {
                ax = -z;
                ay = 0;
                az = x;
            } else {
                ax = 1;
                ay = 0;
                az = 0;
            }
            float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            ax = ax / al * s;
            ay = ay / al * s;
            az = az / al * s;
            // bx = normalize(cross(pos, a)) * s
            float bx = y * az - z * ay, by = z * ax - x * az, bz = x * ay - y * ax;
            float bln = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            bx = bx / bln * s;
            by = by / bln * s;
            bz = bz / bln * s;
            b.addVertex(m, x - ax - bx, y - ay - by, z - az - bz).setColor(r, g, bl, a);
            b.addVertex(m, x + ax - bx, y + ay - by, z + az - bz).setColor(r, g, bl, a);
            b.addVertex(m, x + ax + bx, y + ay + by, z + az + bz).setColor(r, g, bl, a);
            b.addVertex(m, x - ax + bx, y - ay + by, z - az + bz).setColor(r, g, bl, a);
            verts += 4;
        }
        Draw.drawColor(b, verts);
        pose.popPose();
    }

    private static boolean paintedSun(String id) {
        return id.contains("sunrise") || id.contains("sunset");
    }

    static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }
}
