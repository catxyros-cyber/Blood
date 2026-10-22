package dev.bloom.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.math.Axis;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.sky.CubeLayout;
import dev.bloom.client.core.sky.SkyTime;
import dev.bloom.client.core.sky.SkyboxCatalog;
import dev.bloom.client.core.sky.SkyboxLibrary;
import dev.bloom.client.game.GameEnv;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws BLOOD's sky on top of the vanilla sky (called at the end of Minecraft's sky pass):
 * day/night skyboxes with crossfade, horizon blending and haze, the sun and moon, a soft glow
 * around them and BLOOD's starfield. Nothing here touches world state.
 */
public final class SkyRenderer {
    private static final Identifier SUN = Identifier.withDefaultNamespace("textures/environment/celestial/sun.png");
    private static final Identifier[] MOON = {
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/full_moon.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/waning_gibbous.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/third_quarter.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/waning_crescent.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/new_moon.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/waxing_crescent.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/first_quarter.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/waxing_gibbous.png"),
    };
    private static final Identifier HALO = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "textures/effects/halo.png");
    private static final int MAX_STARS = 3000;

    private static float[] stars; // x, y, z, size, phase, warmth per star
    private static int starCount = -1;

    private SkyRenderer() {
    }

    /** @param modelView the sky's model-view (camera rotation, no translation) */
    public static void render(Matrix4f modelView, float partialTick, Camera camera) {
        BloomClient bc = BloomClient.get();
        if (bc == null || !bc.skyActive()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!GameEnv.normalSky(level)) return;
        if (camera.getFluidInCamera() != FogType.NONE) return;
        if (camera.entity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS))) return;

        long dayTime = level.getDayTime();
        float timeOfDay = SkyTime.timeOfDay(dayTime, partialTick); // 0 = noon, 0.5 = midnight
        float rain = level.getRainLevel(partialTick);
        float raw = SkyTime.daylight(timeOfDay);
        float width = 1f - 0.85f * BloomSettings.SKY_TRANSITION.value();
        float dayW = smoothstep(0.5f - width / 2f, 0.5f + width / 2f, raw);

        Matrix4f base = new Matrix4f(modelView);
        // Always called: turning the skybox off fades back to Minecraft's sky instead of popping.
        drawSkyboxes(base, timeOfDay, dayW, rain, BloomSettings.SKY_ENABLED.on());
        if (BloomSettings.SKY_ENABLED.on()) drawHorizon(base);
        if (BloomSettings.SKY_STARS.on() && !BloomSettings.LITE_MODE.on()) drawStars(base, SkyTime.starBrightness(timeOfDay) * (1f - rain), timeOfDay);
        boolean covered = BloomSettings.SKY_ENABLED.on() && !BloomSettings.SKY_DAY.is(SkyboxCatalog.NONE);
        // Sunrise/sunset skies have their own sun painted in: don't add a second one.
        String shownNow = preview != null ? preview : BloomSettings.SKY_DAY.id();
        if (paintedSun(shownNow) || paintedSun(BloomSettings.SKY_NIGHT.id())) covered = false;
        if (BloomSettings.SKY_ENABLED.on() && BloomSettings.SKY_SUN_MOON.on() && covered) drawSunMoon(base, dayTime, timeOfDay, rain);
        if (BloomSettings.SUN_GLOW.on()) drawGlow(base, timeOfDay, rain, raw);
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

    private static void drawSkyboxes(Matrix4f base, float timeOfDay, float dayW, float rain, boolean enabled) {
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
            drawSet(base, fromDay, fromNight, timeOfDay, dayW, dimming, newHasSky ? 1f : 1f - t);
        }
        drawSet(base, dayId, nightId, timeOfDay, dayW, dimming, t);
    }

    private static void drawSet(Matrix4f base, String dayId, String nightId, float timeOfDay, float dayW, float dimming, float scale) {
        if (scale <= 0.002f) return;
        float dayOp = BloomSettings.SKY_DAY_OPACITY.value() * scale;
        float nightOp = BloomSettings.SKY_NIGHT_OPACITY.value() * scale;
        boolean hasDay = !SkyboxCatalog.NONE.equals(dayId);
        boolean hasNight = !SkyboxCatalog.NONE.equals(nightId);

        if (hasDay && hasNight && dayId.equals(nightId)) {
            float o = dayOp + (nightOp - dayOp) * (1f - dayW);
            drawBox(base, dayId, o, dimming, timeOfDay);
            return;
        }
        float a2 = hasDay ? dayOp * dayW : 0f;
        float nightWeight = hasNight ? nightOp * (1f - dayW) : 0f;
        float a1 = a2 >= 0.999f ? 0f : nightWeight / (1f - a2);
        if (a1 > 0.002f) drawBox(base, nightId, Math.min(1f, a1), dimming, timeOfDay);
        if (a2 > 0.002f) drawBox(base, dayId, a2, dimming, timeOfDay);
    }

    private static void drawBox(Matrix4f base, String id, float alpha, float dimming, float timeOfDay) {
        SkyTextures.Bound tex = SkyTextures.get(id);
        if (tex == null) return;
        SkyboxLibrary.Meta meta = id.startsWith(SkyboxCatalog.CUSTOM_PREFIX) ? BloomClient.get().skyboxes().meta(id) : null;
        boolean additive = meta != null && meta.additive();
        Matrix4f m0 = new Matrix4f(base);
        float minutes = (System.nanoTime() / 1_000_000_000f) / 60f;
        float spin = BloomSettings.SKY_ROTATION.value() + (minutes * BloomSettings.SKY_ROTATION_SPEED.value()) % 360f;
        m0.rotate(Axis.YP.rotationDegrees(spin));
        if (meta != null && meta.rotate()) {
            float[] ax = meta.axis();
            float len = (float) Math.sqrt(ax[0] * ax[0] + ax[1] * ax[1] + ax[2] * ax[2]);
            if (len > 1e-4f) {
                m0.rotate(new Quaternionf().rotationAxis((float) (timeOfDay * Math.PI * 2 * meta.speed()),
                        ax[0] / len, ax[1] / len, ax[2] / len));
            }
        }
        float c = dimming * (additive ? alpha : 1f);
        float a = additive ? 1f : alpha;
        Draw.Batch batch = Draw.texColor();
        BufferBuilder b = batch.v();
        // Inset each face by exactly half a texel: neighbouring faces of the atlas can't bleed into
        // each other, and edge texels (which lie on the cube edge) meet their neighbours seamlessly.
        int faceSize = tex.faceSize();
        float insetU = 0.5f / (3f * faceSize), insetV = 0.5f / (2f * faceSize);
        for (int face = 0; face < 6; face++) {
            float[] uv = CubeLayout.UV[face];
            float u0 = uv[0] + insetU, v0 = uv[1] + insetV, u1 = uv[2] - insetU, v1 = uv[3] - insetV;
            Matrix4f m = rotateFace(new Matrix4f(m0), face);
            b.addVertex(m, -100f, -100f, -100f).setUv(u0, v0).setColor(c, c, c, a);
            b.addVertex(m, -100f, -100f, 100f).setUv(u0, v1).setColor(c, c, c, a);
            b.addVertex(m, 100f, -100f, 100f).setUv(u1, v1).setColor(c, c, c, a);
            b.addVertex(m, 100f, -100f, -100f).setUv(u1, v0).setColor(c, c, c, a);
            batch.quad();
        }
        // The rotation is baked into the vertices, so the shader's model-view is the identity.
        Draw.draw(batch, tex.view(), tex.sampler(), new Matrix4f(), additive ? Draw.Blend.ADDITIVE : Draw.Blend.ALPHA, false);
    }

    /** Same rotations as OptiFine/FabricSkyBoxes so imported skies line up (see CubeLayout). */
    private static Matrix4f rotateFace(Matrix4f m, int face) {
        switch (face) {
            case CubeLayout.NORTH -> m.rotate(Axis.XP.rotationDegrees(90f));
            case CubeLayout.SOUTH -> {
                m.rotate(Axis.XP.rotationDegrees(-90f));
                m.rotate(Axis.YP.rotationDegrees(180f));
            }
            case CubeLayout.TOP -> m.rotate(Axis.XP.rotationDegrees(180f));
            case CubeLayout.EAST -> {
                m.rotate(Axis.ZP.rotationDegrees(90f));
                m.rotate(Axis.YP.rotationDegrees(-90f));
            }
            case CubeLayout.WEST -> {
                m.rotate(Axis.ZP.rotationDegrees(-90f));
                m.rotate(Axis.YP.rotationDegrees(90f));
            }
            default -> {
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ horizon & haze

    private static void drawHorizon(Matrix4f m) {
        float blend = BloomSettings.SKY_HORIZON.value();
        float haze = BloomSettings.HAZE.value();
        if (blend <= 0.01f && haze <= 0.01f) return;
        float[] fog = WorldMatrices.fogColor();
        int hz = BloomSettings.HAZE_COLOR.rgb();
        Draw.Batch batch = Draw.color();
        BufferBuilder b = batch.v();
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) Math.cos(a0) * 100f, z0 = (float) Math.sin(a0) * 100f;
            float x1 = (float) Math.cos(a1) * 100f, z1 = (float) Math.sin(a1) * 100f;
            if (blend > 0.01f) {
                band(batch, b, x0, z0, x1, z1, -60f, 0f, fog[0], fog[1], fog[2], blend, blend);
                band(batch, b, x0, z0, x1, z1, 0f, 30f * blend + 8f, fog[0], fog[1], fog[2], blend, 0f);
            }
            if (haze > 0.01f) {
                float a = Math.min(0.85f, haze * 0.8f);
                band(batch, b, x0, z0, x1, z1, -12f, 0f, Draw.r(hz), Draw.g(hz), Draw.b(hz), 0f, a);
                band(batch, b, x0, z0, x1, z1, 0f, 40f, Draw.r(hz), Draw.g(hz), Draw.b(hz), a, 0f);
            }
        }
        Draw.draw(batch, m, Draw.Blend.ALPHA, false);
    }

    private static void band(Draw.Batch batch, BufferBuilder b, float x0, float z0, float x1, float z1, float yBottom, float yTop,
                             float r, float g, float bl, float aBottom, float aTop) {
        b.addVertex(x0, yBottom, z0).setColor(r, g, bl, aBottom);
        b.addVertex(x1, yBottom, z1).setColor(r, g, bl, aBottom);
        b.addVertex(x1, yTop, z1).setColor(r, g, bl, aTop);
        b.addVertex(x0, yTop, z0).setColor(r, g, bl, aTop);
        batch.quad();
    }

    // ------------------------------------------------------------------ sun, moon, glow

    private static Matrix4f celestial(Matrix4f base, float timeOfDay) {
        Matrix4f m = new Matrix4f(base);
        m.rotate(Axis.YP.rotationDegrees(-90f));
        m.rotate(Axis.XP.rotationDegrees(timeOfDay * 360f));
        return m;
    }

    private static void drawSunMoon(Matrix4f base, long dayTime, float timeOfDay, float rain) {
        float alpha = 1f - rain;
        if (alpha <= 0.01f) return;
        Matrix4f m = celestial(base, timeOfDay);
        Draw.Batch sun = Draw.texColor();
        BufferBuilder b = sun.v();
        b.addVertex(-30f, 100f, -30f).setUv(0f, 0f).setColor(1f, 1f, 1f, alpha);
        b.addVertex(30f, 100f, -30f).setUv(1f, 0f).setColor(1f, 1f, 1f, alpha);
        b.addVertex(30f, 100f, 30f).setUv(1f, 1f).setColor(1f, 1f, 1f, alpha);
        b.addVertex(-30f, 100f, 30f).setUv(0f, 1f).setColor(1f, 1f, 1f, alpha);
        sun.quad();
        Draw.draw(sun, SUN, m, Draw.Blend.ADDITIVE, false);

        Draw.Batch moon = Draw.texColor();
        b = moon.v();
        b.addVertex(-20f, -100f, 20f).setUv(1f, 1f).setColor(1f, 1f, 1f, alpha);
        b.addVertex(20f, -100f, 20f).setUv(0f, 1f).setColor(1f, 1f, 1f, alpha);
        b.addVertex(20f, -100f, -20f).setUv(0f, 0f).setColor(1f, 1f, 1f, alpha);
        b.addVertex(-20f, -100f, -20f).setUv(1f, 0f).setColor(1f, 1f, 1f, alpha);
        moon.quad();
        Draw.draw(moon, MOON[SkyTime.moonPhase(dayTime)], m, Draw.Blend.ADDITIVE, false);
    }

    private static void drawGlow(Matrix4f base, float timeOfDay, float rain, float daylight) {
        float strength = BloomSettings.SUN_GLOW_STRENGTH.value() * (1f - rain);
        if (strength <= 0.01f) return;
        Matrix4f m = celestial(base, timeOfDay);
        Draw.Batch batch = Draw.texColor();
        BufferBuilder b = batch.v();
        // Sun halo: warmer and larger near the horizon.
        float sunA = strength * 0.55f;
        float s = 70f;
        b.addVertex(-s, 99f, -s).setUv(0f, 0f).setColor(1f, 0.86f, 0.72f, sunA);
        b.addVertex(s, 99f, -s).setUv(1f, 0f).setColor(1f, 0.86f, 0.72f, sunA);
        b.addVertex(s, 99f, s).setUv(1f, 1f).setColor(1f, 0.86f, 0.72f, sunA);
        b.addVertex(-s, 99f, s).setUv(0f, 1f).setColor(1f, 0.86f, 0.72f, sunA);
        batch.quad();
        float moonA = strength * 0.35f * (1f - daylight);
        float ms = 42f;
        b.addVertex(-ms, -99f, ms).setUv(0f, 0f).setColor(0.8f, 0.86f, 1f, moonA);
        b.addVertex(ms, -99f, ms).setUv(1f, 0f).setColor(0.8f, 0.86f, 1f, moonA);
        b.addVertex(ms, -99f, -ms).setUv(1f, 1f).setColor(0.8f, 0.86f, 1f, moonA);
        b.addVertex(-ms, -99f, -ms).setUv(0f, 1f).setColor(0.8f, 0.86f, 1f, moonA);
        batch.quad();
        Draw.draw(batch, HALO, m, Draw.Blend.ADDITIVE, false);
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

    private static void drawStars(Matrix4f base, float brightness, float timeOfDay) {
        if (brightness <= 0.01f) return;
        ensureStars();
        Matrix4f m = celestial(base, timeOfDay);
        Draw.Batch batch = Draw.color();
        BufferBuilder b = batch.v();
        float t = (System.nanoTime() % 1_000_000_000_000L) / 1_000_000_000f;
        for (int i = 0; i < starCount; i++) {
            int o = i * 6;
            float x = stars[o], y = stars[o + 1], z = stars[o + 2], s = stars[o + 3];
            float tw = 0.7f + 0.3f * (float) Math.sin(t * (1.3f + stars[o + 5]) + stars[o + 4]);
            float a = brightness * tw;
            float warm = stars[o + 5];
            float r = 0.85f + 0.15f * warm, g = 0.88f + 0.08f * warm, bl = 1f - 0.15f * warm;
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
            float bx = y * az - z * ay, by = z * ax - x * az, bz = x * ay - y * ax;
            float bln = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            bx = bx / bln * s;
            by = by / bln * s;
            bz = bz / bln * s;
            b.addVertex(x - ax - bx, y - ay - by, z - az - bz).setColor(r, g, bl, a);
            b.addVertex(x + ax - bx, y + ay - by, z + az - bz).setColor(r, g, bl, a);
            b.addVertex(x + ax + bx, y + ay + by, z + az + bz).setColor(r, g, bl, a);
            b.addVertex(x - ax + bx, y - ay + by, z - az + bz).setColor(r, g, bl, a);
            batch.quad();
        }
        Draw.draw(batch, m, Draw.Blend.ADDITIVE, false);
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
