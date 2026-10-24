package dev.bloom.client.core.avatar;

import dev.bloom.client.core.avatar.AvatarModel.Builder;
import dev.bloom.client.core.avatar.AvatarModel.Part;
import dev.bloom.client.core.avatar.AvatarModel.Shader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static dev.bloom.client.core.avatar.AvatarModel.mix;
import static dev.bloom.client.core.avatar.AvatarModel.noise;
import static dev.bloom.client.core.avatar.AvatarModel.rgb;
import static dev.bloom.client.core.avatar.AvatarModel.shade;
import static dev.bloom.client.core.avatar.AvatarModel.smooth;
import static dev.bloom.client.core.avatar.AvatarModel.spline;

/**
 * MOTION's built-in 3D avatars: original, smooth-shaded takes on the Italian brainrot memes, each
 * about a player tall. Ids match the "style" of avatar accessories on MOTION's server.
 */
public final class AvatarModels {
    // Declared before the models are built (static fields initialise in order).
    private static final int BLACK = rgb(0x0B0B0E), WHITE = rgb(0xF8F8F5), EYE_WHITE = rgb(0xFCFCFA);
    private static final float AO = 0.55f;
    private static final Map<String, AvatarModel> MODELS = new LinkedHashMap<>();

    static {
        MODELS.put("sahur", sahur().bake(AO));
        MODELS.put("tralalero", tralalero().bake(AO));
        MODELS.put("bombardiro", bombardiro().bake(AO));
        MODELS.put("ballerina", ballerina().bake(AO));
    }

    private AvatarModels() {
    }

    public static Set<String> ids() {
        return MODELS.keySet();
    }

    /** The model for an id, or null. */
    public static AvatarModel get(String id) {
        return id == null ? null : MODELS.get(id);
    }

    // ---------------------------------------------------------------------------- helpers

    private static float[] v(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    private static float[] n(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] add(float[] p, float[] d, float s) {
        return new float[]{p[0] + d[0] * s, p[1] + d[1] * s, p[2] + d[2] * s};
    }

    /** A cross-section of a loft (centre, half-width, half-height) where it crosses depth z. */
    private record Section(float x, float y, float z, float w, float h) {
        /** A point on this section's ellipse at angle {@code deg} (0 = +x, 90 = top), pushed out by {@code out}. */
        float[] at(float deg, float out) {
            double a = Math.toRadians(deg);
            float[] nn = normal(deg);
            return new float[]{x + (float) Math.cos(a) * w + nn[0] * out, y + (float) Math.sin(a) * h + nn[1] * out, z};
        }

        float[] normal(float deg) {
            double a = Math.toRadians(deg);
            return n(new float[]{(float) Math.cos(a) / w, (float) Math.sin(a) / h, 0});
        }
    }

    private static Section section(float[][] pts, float[] rw, float[] rh, float z) {
        int last = pts.length - 1;
        boolean inc = pts[last][2] > pts[0][2];
        float lo = 0, hi = last;
        for (int i = 0; i < 40; i++) {
            float mid = (lo + hi) / 2;
            if ((spline(pts, mid)[2] < z) == inc) lo = mid;
            else hi = mid;
        }
        float t = (lo + hi) / 2;
        float[] c = spline(pts, t);
        return new Section(c[0], c[1], c[2], spline(rw, t), spline(rh, t));
    }

    /**
     * A glossy cartoon eye facing {@code fwd}: a white with soft edges, a coloured iris, a pupil
     * (round or a slit) and two highlights.
     */
    private static void eye(Builder b, float[] c, float[] fwd, float w, float h, float d, float iris, int irisColor, float pupil, boolean slit) {
        float[] f = n(fwd);
        float[] s = n(cross(v(0, 1, 0), f));
        float[] u = cross(f, s);
        b.blob(c, s, u, w, h, d, -90, 90, 14, 20, (x, y, z, nx, ny, nz) ->
                mix(rgb(0xBFC3CC), EYE_WHITE, smooth(-0.15f, 0.55f, nx * f[0] + ny * f[1] + nz * f[2])));
        int dark = shade(irisColor, 0.45f), light = mix(irisColor, WHITE, 0.25f);
        float[] ic = add(c, f, d * 0.8f);
        b.blob(ic, s, u, iris, iris, d * 0.3f, -90, 90, 10, 18, (x, y, z, nx, ny, nz) ->
                mix(dark, light, smooth(0.35f, 0.97f, nx * f[0] + ny * f[1] + nz * f[2])));
        if (pupil > 0) {
            b.blob(add(c, f, d * 0.98f), s, u, slit ? pupil * 0.32f : pupil, slit ? pupil * 1.35f : pupil, d * 0.16f, -90, 90, 8, 14, Shader.solid(BLACK));
        }
        float[] hl = add(add(add(c, f, d * 1.12f), s, iris * 0.4f), u, iris * 0.42f);
        b.sphere(hl[0], hl[1], hl[2], iris * 0.27f, WHITE);
        float[] hl2 = add(add(add(c, f, d * 1.1f), s, -iris * 0.32f), u, -iris * 0.36f);
        b.sphere(hl2[0], hl2[1], hl2[2], iris * 0.11f, WHITE);
    }

    /** A ring of {@code n} points around {@code c} in the plane facing {@code fwd}. */
    private static float[][] ring(float[] c, float[] fwd, float r, int count) {
        float[] f = n(fwd);
        float[] s = n(cross(Math.abs(f[1]) > 0.9f ? v(0, 0, 1) : v(0, 1, 0), f));
        float[] u = cross(f, s);
        float[][] pts = new float[count + 1][];
        for (int i = 0; i <= count; i++) {
            double a = 2 * Math.PI * i / count;
            pts[i] = add(add(c, s, (float) Math.cos(a) * r), u, (float) Math.sin(a) * r);
        }
        return pts;
    }

    private static float[] radii(int count, float r) {
        float[] out = new float[count];
        java.util.Arrays.fill(out, r);
        return out;
    }

    // ---------------------------------------------------------------------------- Tung Tung Tung Sahur
    private static AvatarModel sahur() {
        AvatarModel m = new AvatarModel("sahur");
        int woodDark = rgb(0x5E3519), woodMid = rgb(0x9A6234), woodLight = rgb(0xC48C55), endGrain = rgb(0xDDB077);
        // The log: carved grooves run down its back and sides; the face is smooth.
        AvatarModel.Wobble grooves = (a, y) -> {
            float front = (float) -Math.sin(a);
            float faceMask = 1 - 0.9f * smooth(0.35f, 0.8f, front) * smooth(13.5f, 16.5f, y) * (1 - smooth(29.5f, 31f, y));
            double g = Math.sin(a * 14 + 0.4 * Math.sin(y * 0.45)) + 0.35 * Math.sin(a * 33 + y * 0.3);
            return 1 + 0.022f * (float) g * faceMask;
        };
        Shader bark = (x, y, z, nx, ny, nz) -> {
            float r = (float) Math.sqrt(x * x + z * z);
            if (ny > 0.75f && y > 30.8f) {
                float ringT = 0.5f + 0.5f * (float) Math.sin(r * 3.4f + noise(x, 0, z) * 1.4f);
                return mix(endGrain, rgb(0xB57E46), ringT * 0.55f + smooth(4.2f, 5.2f, r) * 0.6f);
            }
            float a = (float) Math.atan2(z, x);
            float front = -z / Math.max(0.01f, r);
            float faceMask = 1 - 0.9f * smooth(0.35f, 0.8f, front) * smooth(13.5f, 16.5f, y) * (1 - smooth(29.5f, 31f, y));
            float g = (float) (0.5 + 0.5 * Math.sin(a * 14 + 0.4 * Math.sin(y * 0.45)));
            float streak = noise(a * 9f, y * 0.28f, 1.7f);
            int c = mix(woodMid, woodLight, 0.15f + 0.65f * streak);
            c = mix(c, woodDark, (1 - g) * (1 - g) * 0.75f * faceMask);
            return c;
        };
        Builder body = m.part(Part.BODY, 0, 9, 0);
        body.lathe(0, 0,
                new float[]{8.3f, 8.45f, 9.0f, 10.2f, 14, 20, 26, 29.8f, 30.9f, 31.4f, 31.55f},
                new float[]{0.01f, 3.6f, 4.9f, 5.45f, 5.65f, 5.7f, 5.6f, 5.4f, 5.0f, 4.0f, 0.01f}, 56, 44, grooves, bark);
        // Knots.
        for (float[] k : new float[][]{{5.55f, 13.5f, 1.8f}, {-4.6f, 27.6f, 3.1f}, {1.6f, 11.2f, 5.4f}}) {
            float[] nn = n(v(k[0], 0, k[2]));
            body.blob(k, v(0, 1, 0), v(-nn[2], 0, nn[0]), 0.95f, 0.7f, 0.35f, -90, 90, 8, 14, (x, y, z, nx, ny, nz) -> {
                float t = (float) Math.sqrt((y - k[1]) * (y - k[1]) + (x - k[0]) * (x - k[0]) + (z - k[2]) * (z - k[2]));
                return mix(rgb(0x3E220F), woodMid, smooth(0.2f, 0.9f, t));
            });
        }

        // Face: huge staring eyes, heavy brows, a knot of a nose and a wide open grin.
        float faceR = 5.7f;
        for (int s = -1; s <= 1; s += 2) {
            float ex = s * 2.35f;
            float ez = -(float) Math.sqrt(faceR * faceR - ex * ex) + 0.55f;
            eye(body, v(ex, 24.7f, ez), v(s * 0.32f, 0.04f, -1), 1.85f, 2.15f, 1.05f, 1.02f, rgb(0x3B2414), 0.62f, false);
            // Lower lid crease and a heavy brow.
            body.tube(new float[][]{{s * 0.75f, 28.0f, -5.75f}, {s * 2.3f, 28.75f, -5.55f}, {s * 3.8f, 28.35f, -4.95f}, {s * 4.5f, 27.6f, -4.35f}},
                    new float[]{0.42f, 0.58f, 0.5f, 0.3f}, 10, (x, y, z, nx, ny, nz) -> mix(rgb(0x1F1008), rgb(0x3A2111), smooth(0.2f, 1f, ny)));
            body.tube(new float[][]{{s * 0.9f, 22.35f, -5.55f}, {s * 2.35f, 22.15f, -5.4f}, {s * 3.7f, 22.6f, -4.75f}},
                    new float[]{0.18f, 0.22f, 0.16f}, 8, Shader.solid(shade(woodMid, 0.75f)));
        }
        body.blob(v(0, 22.9f, -5.85f), v(1, 0, 0), v(0, 1, 0), 0.85f, 1.25f, 0.75f, -90, 90, 10, 14, (x, y, z, nx, ny, nz) -> mix(woodMid, woodLight, smooth(-0.6f, 0.4f, -nz + ny * 0.3f)));
        // Mouth: a dark open cavity ringed by a lip, top teeth and a tongue.
        float my = 19.3f;
        body.blob(v(0, my, -5.25f), v(1, 0, 0), v(0, 1, 0), 2.45f, 1.35f, 0.55f, -90, 90, 10, 20, Shader.solid(rgb(0x260905)));
        float[][] lip = new float[25][];
        for (int i = 0; i <= 24; i++) {
            double a = 2 * Math.PI * i / 24;
            float x = (float) Math.cos(a) * 2.6f, y = my + (float) Math.sin(a) * 1.5f;
            lip[i] = v(x, y, -(float) Math.sqrt(faceR * faceR - x * x) + 0.05f);
        }
        body.tube(lip, radii(25, 0.3f), 8, Shader.solid(shade(woodMid, 0.82f)));
        body.blob(v(0, my + 0.75f, -5.55f), v(1, 0, 0), v(0, 1, 0), 1.75f, 0.42f, 0.32f, -90, 90, 8, 16, Shader.solid(rgb(0xF4EFE2)));
        body.blob(v(0, my - 0.65f, -5.4f), v(1, 0, 0), v(0, 1, 0), 1.25f, 0.55f, 0.4f, -90, 90, 8, 16, (x, y, z, nx, ny, nz) -> mix(rgb(0x9E2F3A), rgb(0xD45A66), smooth(-0.3f, 0.8f, ny)));

        Shader limbWood = (x, y, z, nx, ny, nz) -> mix(rgb(0x8E5A31), rgb(0xB57D4A), 0.3f + 0.7f * noise(x * 3, y * 0.6f, z * 3));
        Shader hand = (x, y, z, nx, ny, nz) -> mix(rgb(0x8A5630), rgb(0xB27A47), smooth(-0.5f, 0.8f, ny));
        // Right arm with the bat, raised, ready to bonk.
        Builder ra = m.part(Part.RIGHT_ARM, -5.2f, 21, 0);
        ra.tube(new float[][]{{-5.0f, 21.2f, 0}, {-6.6f, 18.6f, -0.2f}, {-7.6f, 16.4f, -0.5f}, {-8.1f, 14.2f, -1.4f}, {-8.2f, 12.9f, -2.2f}},
                new float[]{1.0f, 0.88f, 0.92f, 0.8f, 0.72f}, 12, limbWood);
        ra.blob(v(-8.25f, 12.0f, -2.75f), v(0, 1, 0), v(1, 0, 0), 1.25f, 1.05f, 0.95f, -90, 90, 10, 16, hand);
        for (int f = 0; f < 4; f++) {
            float fy = 12.85f - f * 0.62f;
            ra.blob(v(-8.0f, fy, -3.65f), v(1, 0, 0), v(0, 1, 0), 0.62f, 0.34f, 0.42f, -90, 90, 8, 12, hand);
        }
        ra.blob(v(-7.2f, 12.6f, -3.1f), v(0, 1, 0), v(1, 0, 0), 0.62f, 0.36f, 0.4f, -90, 90, 8, 12, hand);
        // The bat: grip tape on the handle, a knob, a fat ash barrel.
        Shader bat = (x, y, z, nx, ny, nz) -> {
            if (y < 13.9f) {
                float spiral = (float) Math.sin(y * 7 + Math.atan2(z + 2.6, x + 8.3) * 1);
                return spiral > 0.6f ? rgb(0x5E1A15) : rgb(0x9A2E24);
            }
            return mix(rgb(0xD8AE72), rgb(0xEBCB95), 0.3f + 0.7f * noise(x * 5, y * 0.5f, z * 5));
        };
        ra.tube(new float[][]{{-8.3f, 10.1f, -2.9f}, {-8.45f, 13.6f, -2.3f}, {-8.8f, 17.4f, -1.4f}, {-9.2f, 21.4f, -0.4f}, {-9.4f, 24.3f, 0.3f}},
                new float[]{0.48f, 0.5f, 0.82f, 1.28f, 1.45f}, 14, bat);
        ra.blob(v(-8.28f, 9.75f, -2.98f), v(1, 0, 0), v(0, 0, 1), 0.8f, 0.8f, 0.3f, -90, 90, 6, 14, Shader.solid(rgb(0x7D2219)));

        Builder la = m.part(Part.LEFT_ARM, 5.2f, 21, 0);
        la.tube(new float[][]{{5.0f, 21.2f, 0}, {6.6f, 18.4f, -0.1f}, {7.6f, 16.0f, -0.2f}, {8.0f, 13.6f, -0.7f}},
                new float[]{1.0f, 0.88f, 0.92f, 0.76f}, 12, limbWood);
        la.blob(v(8.1f, 12.5f, -0.95f), v(0, 1, 0), v(1, 0, 0), 1.35f, 0.95f, 1.05f, -90, 90, 10, 16, hand);
        la.blob(v(7.3f, 12.9f, -1.85f), v(0, 1, 0), v(1, 0, 0), 0.6f, 0.36f, 0.4f, -90, 90, 8, 12, hand);

        for (int s = -1; s <= 1; s += 2) {
            Builder leg = m.part(s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG, s * 2.3f, 9.5f, 0);
            leg.tube(new float[][]{{s * 2.3f, 10.2f, 0}, {s * 2.35f, 6.0f, -0.35f}, {s * 2.4f, 2.0f, 0.1f}}, new float[]{1.08f, 0.92f, 0.88f}, 12, limbWood);
            leg.loft(new float[][]{{s * 2.4f, 0.95f, 1.05f}, {s * 2.45f, 0.9f, -0.6f}, {s * 2.5f, 0.78f, -2.5f}},
                    new float[]{1.2f, 1.4f, 1.3f}, new float[]{0.95f, 0.9f, 0.78f}, 14, hand);
        }
        return m;
    }

    // ---------------------------------------------------------------------------- Tralalero Tralala
    private static AvatarModel tralalero() {
        AvatarModel m = new AvatarModel("tralalero");
        int blue = rgb(0x4189D3), deep = rgb(0x235A9A), belly = rgb(0xF1F4F7);
        float[][] pts = {{0, 18.2f, -13.6f}, {0, 18.45f, -12.0f}, {0, 18.15f, -9.2f}, {0, 17.6f, -4.5f}, {0, 17.6f, 0.5f}, {0, 18.0f, 5.5f}, {0, 18.6f, 10.0f}, {0, 19.0f, 13.0f}, {0, 19.2f, 14.6f}};
        float[] rw = {0.5f, 2.35f, 4.1f, 5.2f, 5.3f, 4.2f, 2.5f, 1.25f, 0.8f};
        float[] rh = {0.45f, 2.05f, 3.9f, 5.3f, 5.5f, 4.4f, 2.6f, 1.35f, 1.0f};
        Shader skin = (x, y, z, nx, ny, nz) -> {
            float wave = 0.1f * (float) Math.sin(z * 0.55f);
            int top = mix(blue, deep, smooth(0.25f, 1f, ny) * 0.55f + 0.12f * noise(x * 1.5f, y * 1.5f, z * 1.5f));
            return mix(top, belly, smooth(-0.05f + wave, -0.32f + wave, ny));
        };
        Builder body = m.spine(0, 17.6f, -6.5f, 7, 5, 9);
        body.loft(pts, rw, rh, 36, 48, null, skin);

        // Grinning mouth under the snout, lined with teeth.
        int mouthN = 17;
        float[][] mouth = new float[mouthN][];
        float[][] normals = new float[mouthN][];
        for (int i = 0; i < mouthN; i++) {
            float s = -1 + 2f * i / (mouthN - 1);
            float z = -6.4f - 4.4f * (1 - s * s);
            float deg = s >= 0 ? -90 + 66 * s : -90 + 66 * s;
            Section sec = section(pts, rw, rh, z);
            mouth[i] = sec.at(deg, -0.12f);
            normals[i] = sec.normal(deg);
        }
        body.tube(mouth, radii(mouthN, 0.62f), 10, Shader.solid(rgb(0x4A0E1C)));
        for (int i = 1; i < mouthN - 1; i++) {
            float[] p = mouth[i], nrm = normals[i];
            float[] t = n(new float[]{mouth[i + 1][0] - mouth[i - 1][0], mouth[i + 1][1] - mouth[i - 1][1], mouth[i + 1][2] - mouth[i - 1][2]});
            float[] up = n(cross(nrm, t));
            if (up[1] * 0.4f - up[2] < 0) up = new float[]{-up[0], -up[1], -up[2]};
            float[] o = add(p, nrm, 0.5f);
            float[] a0 = add(add(o, t, -0.3f), up, 0.55f), a1 = add(add(o, t, 0.3f), up, 0.55f), tip = add(o, up, -0.38f);
            body.slab(new float[][]{a0, a1, tip}, 0.2f, Shader.solid(WHITE));
            if (i % 2 == 0 && i > 2 && i < mouthN - 3) {
                float[] b0 = add(add(o, t, -0.26f), up, -0.6f), b1 = add(add(o, t, 0.26f), up, -0.6f), btip = add(o, up, 0.22f);
                body.slab(new float[][]{b0, b1, btip}, 0.2f, Shader.solid(rgb(0xEDEBE4)));
            }
        }
        // Eyes on the sides of the head, gills behind them.
        for (int s = -1; s <= 1; s += 2) {
            Section sec = section(pts, rw, rh, -8.2f);
            float deg = s > 0 ? 24 : 156;
            float[] c = sec.at(deg, -0.3f);
            float[] nn = sec.normal(deg);
            eye(body, c, v(nn[0] * 0.85f, nn[1] * 0.4f + 0.05f, -0.75f), 1.35f, 1.45f, 0.8f, 0.82f, rgb(0x1E2A44), 0.45f, false);
            for (int g = 0; g < 3; g++) {
                float gz = -4.4f + g * 1.05f;
                float[][] gill = new float[5][];
                for (int k = 0; k < 5; k++) {
                    float d = s > 0 ? -22 + k * 13 : 202 - k * 13;
                    Section gs = section(pts, rw, rh, gz + 0.35f * (float) Math.sin(Math.PI * k / 4));
                    gill[k] = gs.at(d, 0.02f);
                }
                body.tube(gill, new float[]{0.08f, 0.15f, 0.17f, 0.15f, 0.08f}, 6, Shader.solid(rgb(0x1A4A84)));
            }
        }
        Shader fin = (x, y, z, nx, ny, nz) -> mix(blue, deep, 0.35f + 0.4f * smooth(20, 28, y));
        body.blade(new float[][]{{0, 22.2f, -3.6f}, {0, 25.6f, -1.6f}, {0, 29.0f, 2.3f}}, new float[][]{{0, 22.4f, 3.4f}, {0, 25.0f, 2.3f}, {0, 29.0f, 2.3f}}, 1.35f, 18, 14, fin);
        // Tail: two lobes, swaying with the walk.
        body.blade(new float[][]{{0, 19.4f, 12.6f}, {0, 22.6f, 14.6f}, {0, 26.0f, 17.6f}}, new float[][]{{0, 19.3f, 15.2f}, {0, 21.6f, 16.2f}, {0, 26.0f, 17.6f}}, 0.95f, 16, 12, fin);
        body.blade(new float[][]{{0, 18.8f, 12.8f}, {0, 16.4f, 14.6f}, {0, 14.2f, 16.8f}}, new float[][]{{0, 19.0f, 15.2f}, {0, 17.2f, 16.0f}, {0, 14.2f, 16.8f}}, 0.95f, 16, 12, fin);
        // Small pelvic fins.
        for (int s = -1; s <= 1; s += 2) {
            body.blade(new float[][]{{s * 2.4f, 14.4f, 6.5f}, {s * 4.4f, 12.6f, 8.6f}}, new float[][]{{s * 2.2f, 14.8f, 9.2f}, {s * 4.4f, 12.6f, 8.6f}}, 0.6f, 12, 8, fin);
        }

        // Pectoral fins swing like arms.
        for (int s = -1; s <= 1; s += 2) {
            Builder f = m.part(s < 0 ? Part.RIGHT_ARM : Part.LEFT_ARM, s * 4.4f, 14.8f, -4.0f);
            f.blade(new float[][]{{s * 4.0f, 15.4f, -5.7f}, {s * 7.0f, 13.8f, -4.6f}, {s * 9.8f, 11.4f, -1.4f}},
                    new float[][]{{s * 4.0f, 14.6f, -2.0f}, {s * 6.8f, 12.8f, -1.6f}, {s * 9.8f, 11.4f, -1.4f}}, 1.05f, 16, 12, fin);
        }

        // Three legs in chunky blue trainers.
        Shader legSkin = (x, y, z, nx, ny, nz) -> mix(deep, blue, 0.55f + 0.45f * smooth(-0.6f, 0.6f, -nz));
        float[][] legAt = {{-2.8f, -3.3f}, {2.8f, -3.3f}, {0, 5.4f}};
        for (int l = 0; l < 3; l++) {
            float x = legAt[l][0], z = legAt[l][1];
            Builder leg = m.part(l == 0 ? Part.RIGHT_LEG : l == 1 ? Part.LEFT_LEG : Part.RIGHT_LEG, x, 13.6f, z);
            leg.tube(new float[][]{{x, 15.0f, z}, {x, 11.0f, z - 0.15f}, {x, 7.4f, z - 0.45f}, {x, 4.8f, z - 0.15f}, {x, 2.9f, z}},
                    new float[]{1.6f, 1.4f, 1.12f, 1.12f, 1.0f}, 14, legSkin);
            sneaker(leg, x, z);
        }
        return m;
    }

    /** A chunky trainer centred on (x, z): blue upper, white midsole, laces and a side stripe. */
    private static void sneaker(Builder b, float x, float z) {
        int upper = rgb(0x2470E2), upperDark = rgb(0x1652B0);
        b.loft(new float[][]{{x, 0.36f, z + 2.0f}, {x, 0.36f, z - 0.5f}, {x, 0.42f, z - 3.2f}}, new float[]{1.55f, 1.75f, 1.45f}, new float[]{0.36f, 0.36f, 0.32f}, 18, 12, null, Shader.solid(rgb(0x2D3138)));
        b.loft(new float[][]{{x, 0.95f, z + 2.1f}, {x, 0.92f, z - 0.5f}, {x, 0.98f, z - 3.25f}}, new float[]{1.68f, 1.86f, 1.55f}, new float[]{0.42f, 0.42f, 0.4f}, 18, 12, null, Shader.solid(WHITE));
        b.loft(new float[][]{{x, 1.9f, z + 1.75f}, {x, 2.15f, z - 0.2f}, {x, 1.65f, z - 2.3f}, {x, 1.38f, z - 3.1f}}, new float[]{1.5f, 1.62f, 1.45f, 1.1f}, new float[]{1.15f, 1.5f, 0.92f, 0.58f}, 20, 18, null,
                (px, py, pz, nx, ny, nz) -> mix(upperDark, upper, 0.5f + 0.5f * smooth(-0.3f, 0.8f, ny)));
        // Toe cap and heel tab.
        b.blob(v(x, 1.45f, z - 2.6f), v(1, 0, 0), v(0, 0, 1), 1.25f, 0.9f, 0.62f, 0, 90, 6, 16, Shader.solid(WHITE));
        b.blob(v(x, 2.6f, z + 1.95f), v(1, 0, 0), v(0, 1, 0), 0.55f, 0.8f, 0.35f, -90, 90, 8, 12, Shader.solid(WHITE));
        for (int s = -1; s <= 1; s += 2) {
            b.tube(new float[][]{{x + s * 1.66f, 1.35f, z + 1.4f}, {x + s * 1.78f, 1.75f, z - 0.4f}, {x + s * 1.6f, 1.95f, z - 1.9f}}, new float[]{0.16f, 0.2f, 0.12f}, 6, Shader.solid(WHITE));
        }
        for (int l = 0; l < 4; l++) {
            float lz = z - 0.15f - l * 0.62f;
            float top = 3.55f - l * 0.4f;
            b.tube(new float[][]{{x - 0.85f, top - 0.2f, lz}, {x, top + 0.08f, lz - 0.05f}, {x + 0.85f, top - 0.2f, lz}}, radii(3, 0.15f), 6, Shader.solid(WHITE));
        }
        b.blob(v(x, 3.0f, z - 0.4f), v(1, 0, 0), v(0, 0, 1), 0.9f, 1.6f, 0.5f, 0, 90, 6, 14, Shader.solid(upperDark));
    }

    // ---------------------------------------------------------------------------- Bombardiro Crocodilo
    private static AvatarModel bombardiro() {
        AvatarModel m = new AvatarModel("bombardiro");
        int grey = rgb(0x8E99A2), light = rgb(0xBBC4CA), red = rgb(0xC9362A), dark = rgb(0x4A5157);
        int green = rgb(0x4F9A3C), dgreen = rgb(0x2D5E22), pale = rgb(0xE6DCA6);
        Shader metal = (x, y, z, nx, ny, nz) -> {
            int c = mix(grey, light, smooth(-0.1f, 0.95f, ny) * 0.55f);
            float pz = ((z + 100) % 3.1f);
            if (pz < 0.13f) c = shade(c, 0.72f);
            if (Math.abs(y - 17.1f) < 0.07f && nz > -0.5f) c = shade(c, 0.8f);
            if (z < -3.3f && z > -4.1f) c = red;
            if (z > 8.2f && z < 9.1f) c = rgb(0xE8C33A);
            return c;
        };
        Builder body = m.part(Part.BODY, 0, 17, 0);
        float[][] fus = {{0, 17.0f, -4.6f}, {0, 17.2f, -1.2f}, {0, 17.3f, 4.0f}, {0, 17.8f, 9.0f}, {0, 18.6f, 13.0f}, {0, 19.2f, 15.6f}};
        body.loft(fus, new float[]{3.3f, 4.2f, 4.1f, 3.0f, 1.8f, 0.9f}, new float[]{3.3f, 4.4f, 4.3f, 3.1f, 1.9f, 1.0f}, 32, 40, null, metal);
        // Cockpit canopy with frames.
        body.blob(v(0, 20.7f, -0.6f), v(0, 0, 1), v(1, 0, 0), 3.0f, 2.05f, 1.75f, 0, 90, 8, 24, (x, y, z, nx, ny, nz) -> {
            if (Math.abs(z + 0.6f) < 0.12f || Math.abs(z - 1.2f) < 0.12f || Math.abs(z + 2.2f) < 0.12f || Math.abs(x) < 0.1f) return rgb(0x3A3F44);
            return mix(rgb(0x5DA9D6), rgb(0xD9F1FF), smooth(0.2f, 0.95f, ny) * 0.8f + 0.2f * smooth(-0.5f, 0.5f, -nz));
        });
        // Wings with roundels, engines and spinning propellers, bombs underneath.
        for (int s = -1; s <= 1; s += 2) {
            final int side = s;
            Shader wing = (x, y, z, nx, ny, nz) -> {
                float dx = x - side * 11.4f, dz = z - 1.25f, d = (float) Math.sqrt(dx * dx + dz * dz);
                int c = mix(grey, light, ny > 0 ? 0.45f : 0.1f);
                if (ny > 0.3f && d < 2.2f) c = d < 0.9f ? red : d < 1.6f ? WHITE : rgb(0x24364F);
                if (Math.abs(x) > 15.3f) c = red;
                return c;
            };
            body.blade(new float[][]{{s * 2.8f, 16.4f, -2.6f}, {s * 9.0f, 16.8f, -1.6f}, {s * 15.0f, 17.2f, 0.1f}, {s * 16.3f, 17.25f, 1.3f}},
                    new float[][]{{s * 2.8f, 16.4f, 4.6f}, {s * 9.0f, 16.8f, 3.9f}, {s * 15.0f, 17.2f, 2.6f}, {s * 16.3f, 17.25f, 1.3f}}, 1.5f, 20, 24, wing);
            float ex = s * 8.0f;
            body.loft(new float[][]{{ex, 16.1f, -3.9f}, {ex, 16.2f, -2.6f}, {ex, 16.3f, 1.6f}, {ex, 16.5f, 4.6f}}, new float[]{1.25f, 1.7f, 1.55f, 0.55f}, new float[]{1.25f, 1.75f, 1.6f, 0.6f}, 20, 16, null,
                    (x, y, z, nx, ny, nz) -> z < -3.2f ? rgb(0x2E3236) : mix(dark, rgb(0x6F777D), smooth(-0.2f, 0.9f, ny) * 0.6f));
            Builder prop = m.part(Part.PROP, ex, 16.1f, -4.5f);
            prop.blob(v(ex, 16.1f, -4.4f), v(0, 1, 0), v(1, 0, 0), 0.8f, 0.8f, 1.45f, 0, 90, 6, 16, Shader.solid(red));
            for (int k = 0; k < 3; k++) {
                double a = 2 * Math.PI * k / 3 + 0.3;
                float[] d = v((float) Math.cos(a), (float) Math.sin(a), 0), p = v(-d[1], d[0], 0);
                float[] hub = v(ex, 16.1f, -4.55f);
                prop.blade(new float[][]{add(add(hub, d, 0.5f), p, 0.32f), add(add(hub, d, 2.0f), p, 0.55f), add(hub, d, 3.5f)},
                        new float[][]{add(add(hub, d, 0.5f), p, -0.32f), add(add(hub, d, 2.0f), p, -0.35f), add(hub, d, 3.5f)}, 0.32f, 10, 10,
                        (x, y, z, nx, ny, nz) -> {
                            float r = (float) Math.sqrt((x - ex) * (x - ex) + (y - 16.1f) * (y - 16.1f));
                            return r > 2.9f ? rgb(0xF2C42E) : rgb(0x2A2D31);
                        });
            }
            float bx = s * 5.3f;
            body.box(bx - 0.2f, 14.9f, 0.4f, bx + 0.2f, 16.4f, 1.6f, dark);
            body.loft(new float[][]{{bx, 14.2f, -1.8f}, {bx, 14.2f, -0.6f}, {bx, 14.2f, 2.2f}, {bx, 14.2f, 3.3f}}, new float[]{0.35f, 0.88f, 0.9f, 0.32f}, new float[]{0.35f, 0.88f, 0.9f, 0.32f}, 16, 14, null,
                    (x, y, z, nx, ny, nz) -> z < -1.3f ? red : mix(rgb(0x3F4A2E), rgb(0x6A7A4E), smooth(-0.3f, 0.9f, ny)));
            for (int f = 0; f < 4; f++) {
                double a = Math.PI / 2 * f + Math.PI / 4;
                float fx = (float) Math.cos(a), fy = (float) Math.sin(a);
                body.slab(new float[][]{{bx + fx * 0.2f, 14.2f + fy * 0.2f, 2.7f}, {bx + fx * 0.95f, 14.2f + fy * 0.95f, 3.6f}, {bx + fx * 0.95f, 14.2f + fy * 0.95f, 4.2f}, {bx + fx * 0.2f, 14.2f + fy * 0.2f, 3.9f}}, 0.12f, Shader.solid(rgb(0x3F4A2E)));
            }
        }
        // Tail: stabilisers striped like a racer.
        Shader tailFin = (x, y, z, nx, ny, nz) -> {
            int c = mix(grey, light, 0.3f);
            if (y > 21.2f) c = ((int) ((y - 21.2f) / 0.9f)) % 2 == 0 ? red : WHITE;
            return c;
        };
        for (int s = -1; s <= 1; s += 2) {
            body.blade(new float[][]{{s * 0.8f, 18.8f, 11.4f}, {s * 4.6f, 19.0f, 13.2f}, {s * 6.3f, 19.05f, 14.5f}},
                    new float[][]{{s * 0.8f, 18.8f, 15.2f}, {s * 4.6f, 19.0f, 15.4f}, {s * 6.3f, 19.05f, 14.5f}}, 0.8f, 16, 12, tailFin);
        }
        body.blade(new float[][]{{0, 19.4f, 10.0f}, {0, 22.6f, 12.8f}, {0, 24.6f, 14.9f}}, new float[][]{{0, 19.6f, 15.4f}, {0, 23.0f, 15.6f}, {0, 24.6f, 14.9f}}, 0.9f, 16, 14, tailFin);

        // The crocodile head: long jaws, overlapping teeth, slit eyes and pilot goggles.
        Builder head = m.part(Part.HEAD, 0, 17.4f, -3.8f);
        Shader croc = (x, y, z, nx, ny, nz) -> {
            float cell = noise(x * 2.6f, y * 2.6f, z * 2.6f);
            float bump = (float) Math.abs(Math.sin(x * 2.4f) * Math.sin(z * 2.4f + y * 0.8f));
            int c = mix(dgreen, green, 0.35f + 0.45f * cell + 0.2f * bump);
            float under = smooth(-0.1f, -0.5f, ny);
            int belly = mix(pale, rgb(0xC9BE84), (float) Math.abs(Math.sin(z * 2.2f)) * 0.5f);
            return mix(c, belly, under);
        };
        float[][] up = {{0, 18.6f, -2.6f}, {0, 18.9f, -5.4f}, {0, 18.5f, -8.6f}, {0, 18.15f, -12.0f}, {0, 18.2f, -14.4f}, {0, 18.1f, -15.5f}};
        float[] uw = {3.5f, 3.3f, 2.5f, 2.0f, 2.05f, 1.3f}, uh = {3.0f, 2.6f, 1.55f, 1.1f, 1.05f, 0.65f};
        head.loft(up, uw, uh, 32, 44, null, croc);
        float[][] lo = {{0, 16.0f, -3.0f}, {0, 15.7f, -7.0f}, {0, 15.85f, -11.0f}, {0, 16.15f, -14.0f}, {0, 16.3f, -15.0f}};
        float[] lw = {2.9f, 2.3f, 1.85f, 1.75f, 1.2f}, lh = {1.5f, 0.95f, 0.75f, 0.7f, 0.5f};
        head.loft(lo, lw, lh, 28, 36, null, croc);
        head.loft(new float[][]{{0, 16.85f, -5.2f}, {0, 16.8f, -9.0f}, {0, 16.9f, -14.2f}}, new float[]{1.9f, 1.75f, 1.35f}, new float[]{0.5f, 0.38f, 0.3f}, 18, 16, null, Shader.solid(rgb(0x7A2734)));
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 10; i++) {
                float z = -5.6f - i * 1.0f;
                Section sec = section(up, uw, uh, z);
                float x = s * sec.w() * 0.88f, y = sec.y() - sec.h() * 0.66f;
                float len = 0.8f + (i % 3 == 1 ? 0.3f : 0);
                head.slab(new float[][]{{x, y, z - 0.3f}, {x, y, z + 0.3f}, {x, y - len, z}}, 0.3f, Shader.solid(WHITE));
            }
            for (int i = 0; i < 8; i++) {
                float z = -6.1f - i * 1.05f;
                Section sec = section(lo, lw, lh, z);
                float x = s * sec.w() * 0.88f, y = sec.y() + sec.h() * 0.55f;
                head.slab(new float[][]{{x, y, z - 0.28f}, {x, y, z + 0.28f}, {x, y + 0.7f, z}}, 0.28f, Shader.solid(rgb(0xEEEADF)));
            }
            // Brow ridge, eye and a heavy lid.
            head.blob(v(s * 1.95f, 20.9f, -5.2f), v(1, 0, 0), v(0, 0, 1), 1.35f, 1.45f, 1.25f, -90, 90, 12, 16, croc);
            float[] ec = v(s * 2.05f, 21.25f, -5.75f), ef = v(s * 0.5f, 0.45f, -1);
            eye(head, ec, ef, 1.0f, 1.0f, 0.8f, 0.92f, rgb(0xF2C230), 0.62f, true);
            float[] f = n(ef), sd = n(cross(v(0, 1, 0), f)), u = cross(f, sd);
            head.blob(ec, sd, f, 1.12f, 1.0f, 1.12f, -90, -18, 6, 16, croc);
            // Nostrils on the tip of the snout.
            head.sphere(s * 0.6f, 19.0f, -14.75f, 0.52f, croc);
            head.sphere(s * 0.62f, 19.42f, -14.85f, 0.2f, rgb(0x0F1A0C));
        }
        for (int i = 0; i < 4; i++) {
            for (int s = -1; s <= 1; s += 2) {
                float z = -7.0f - i * 1.4f;
                Section sec = section(up, uw, uh, z);
                head.sphere(s * 0.75f, sec.y() + sec.h() - 0.12f, z, 0.3f, croc);
            }
        }
        // Pilot goggles on the forehead and their strap.
        Section strapAt = section(up, uw, uh, -2.7f);
        float[][] strap = new float[11][];
        for (int i = 0; i <= 10; i++) strap[i] = strapAt.at(i * 18, 0.12f);
        head.tube(strap, radii(11, 0.26f), 8, Shader.solid(rgb(0x5A3A22)));
        for (int s = -1; s <= 1; s += 2) {
            float[] gc = v(s * 1.5f, 21.45f, -2.75f), gf = v(s * 0.3f, 0.9f, -0.45f);
            float[] gfn = n(gf), gs = n(cross(v(0, 1, 0), gfn)), gu = cross(gfn, gs);
            head.blob(gc, gs, gu, 0.95f, 0.85f, 0.45f, -90, 90, 10, 16, (x, y, z, nx, ny, nz) -> mix(rgb(0x2C6E78), rgb(0xBFF3F0), smooth(0.4f, 1f, ny)));
            head.tube(ring(gc, gf, 1.0f, 18), radii(19, 0.24f), 8, Shader.solid(rgb(0xC0913A)));
        }

        // It flies: lift the plane to chest height, with stubby crocodile legs dangling underneath.
        m.shift(0, 3.0f, 0).hover(0.8f);
        for (int s = -1; s <= 1; s += 2) {
            Builder leg = m.part(s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG, s * 2.4f, 16.6f, 1.2f);
            leg.tube(new float[][]{{s * 2.3f, 17.4f, 1.4f}, {s * 3.2f, 14.6f, 0.9f}, {s * 3.3f, 12.4f, 1.5f}},
                    new float[]{1.45f, 1.2f, 1.0f}, 14, croc);
            leg.loft(new float[][]{{s * 3.3f, 11.6f, 2.2f}, {s * 3.35f, 11.2f, 0.7f}, {s * 3.4f, 10.7f, -0.9f}}, new float[]{1.05f, 1.35f, 1.4f}, new float[]{0.75f, 0.66f, 0.5f}, 14, 12, null, croc);
            for (int c = -1; c <= 1; c++) {
                float cx = s * 3.4f + c * 0.6f;
                leg.slab(new float[][]{{cx - 0.2f, 10.55f, -1.0f}, {cx + 0.2f, 10.55f, -1.0f}, {cx, 10.1f, -1.9f}}, 0.32f, Shader.solid(pale));
            }
        }
        return m;
    }

    // ---------------------------------------------------------------------------- Ballerina Cappuccina
    private static AvatarModel ballerina() {
        AvatarModel m = new AvatarModel("ballerina");
        int pink = rgb(0xF39BC0), dpink = rgb(0xD06E9C), skin = rgb(0xFFD5B8), tights = rgb(0xF7D6E2);
        int cup = rgb(0xF6F1EA), coffee = rgb(0x7A4527), foam = rgb(0xEEDDBF);
        Shader skinS = (x, y, z, nx, ny, nz) -> mix(rgb(0xEDBFA2), skin, 0.6f + 0.4f * smooth(-0.6f, 0.8f, ny + 0.3f));

        Builder body = m.part(Part.BODY, 0, 13, 0);
        // Leotard torso with a sweetheart neckline.
        body.loft(new float[][]{{0, 12.6f, 0.1f}, {0, 14.3f, 0.05f}, {0, 16.5f, 0.1f}, {0, 18.8f, 0}, {0, 20.6f, 0.1f}, {0, 21.6f, 0.15f}},
                new float[]{2.25f, 2.35f, 1.75f, 2.35f, 2.8f, 1.7f}, new float[]{1.6f, 1.7f, 1.3f, 1.55f, 1.45f, 1.05f}, 28, 36, null,
                (x, y, z, nx, ny, nz) -> {
                    if (y > 20.25f + 0.35f * (float) Math.cos(x * 1.7f) && Math.abs(x) < 1.55f && nz < 0) return skin;
                    return mix(dpink, pink, 0.55f + 0.45f * smooth(-0.4f, 0.6f, -nz) - 0.25f * smooth(0.6f, 1f, Math.abs(nx)));
                });
        body.tube(new float[][]{{0, 21.0f, 0.15f}, {0, 22.4f, 0.15f}, {0, 23.4f, 0.1f}}, new float[]{0.8f, 0.74f, 0.72f}, 14, skinS);
        // Tutu: three ruffled layers of tulle and a satin waistband with a bow.
        // Tutu: one fluffy skirt of tiered, ruffled tulle.
        AvatarModel.Wobble ruffle = (a, yy) -> 1 + 0.05f * (float) Math.sin(a * 15 + yy * 2.1f) + 0.022f * (float) Math.sin(a * 37 + yy);
        body.lathe(0, 0.1f, new float[]{13.0f, 12.85f, 13.05f, 13.6f, 13.95f, 14.35f, 14.8f, 15.15f, 15.45f, 15.6f, 15.65f},
                new float[]{2.3f, 5.0f, 7.6f, 8.15f, 7.5f, 8.0f, 7.4f, 7.6f, 6.6f, 4.0f, 2.3f}, 76, 46, ruffle,
                (x, y, z, nx, ny, nz) -> {
                    float a = (float) Math.atan2(z, x);
                    float fold = 0.5f + 0.5f * (float) Math.sin(a * 15 + y * 2.1f);
                    int c = mix(rgb(0xFF9FCB), rgb(0xFFD9EB), smooth(13.0f, 15.5f, y));
                    return mix(shade(c, 0.94f), mix(c, WHITE, 0.3f), 0.5f * fold + 0.5f * smooth(-0.4f, 0.9f, ny));
                });
        float[][] band = new float[25][];
        for (int i = 0; i < band.length; i++) {
            double a = 2 * Math.PI * i / 24;
            band[i] = v((float) Math.cos(a) * 2.3f, 15.25f, 0.1f + (float) Math.sin(a) * 1.68f);
        }
        body.tube(band, radii(band.length, 0.3f), 8, Shader.solid(dpink));
        body.blob(v(-0.7f, 15.35f, -1.75f), v(1, 0.25f, 0), v(0, 1, 0), 0.75f, 0.45f, 0.3f, -90, 90, 8, 12, Shader.solid(dpink));
        body.blob(v(0.7f, 15.35f, -1.75f), v(1, -0.25f, 0), v(0, 1, 0), 0.75f, 0.45f, 0.3f, -90, 90, 8, 12, Shader.solid(dpink));
        body.sphere(0, 15.3f, -1.85f, 0.3f, rgb(0xB85585));

        // Head: a cappuccino cup on a saucer, latte-art heart and all.
        Builder head = m.part(Part.HEAD, 0, 23.3f, 0);
        head.lathe(0, 0, new float[]{22.95f, 22.9f, 23.05f, 23.4f, 23.6f, 23.55f, 23.35f, 23.25f, 23.3f},
                new float[]{0.9f, 2.8f, 4.0f, 4.95f, 5.25f, 5.35f, 5.0f, 3.6f, 0.9f}, 48, 24, null,
                (x, y, z, nx, ny, nz) -> mix(rgb(0xD9CFC2), cup, 0.45f + 0.55f * smooth(-0.5f, 0.7f, ny)));
        Shader ceramic = (x, y, z, nx, ny, nz) -> {
            float r = (float) Math.sqrt(x * x + z * z);
            if (ny > 0.9f && y < 30.2f && r < 4.3f) {
                float hx = x / 2.1f, hz = (z + 0.3f) / 2.1f;
                float hy = -hz + 0.25f;
                float heart = (hx * hx + hy * hy - 1) * (hx * hx + hy * hy - 1) * (hx * hx + hy * hy - 1) - hx * hx * hy * hy * hy;
                if (heart < 0) return rgb(0xFBF5EA);
                return mix(foam, coffee, smooth(2.6f, 4.2f, r) * 0.85f + 0.12f * noise(x * 3, 0, z * 3));
            }
            return mix(rgb(0xD4CBBF), cup, 0.45f + 0.55f * smooth(-0.7f, 0.5f, -nz * 0.6f + ny * 0.6f));
        };
        head.lathe(0, 0, new float[]{23.4f, 23.55f, 24.4f, 26.2f, 28.2f, 29.9f, 30.35f, 30.5f, 30.35f, 29.9f},
                new float[]{2.3f, 2.95f, 3.45f, 3.95f, 4.38f, 4.68f, 4.72f, 4.55f, 4.32f, 4.28f}, 48, 40, null, ceramic);
        head.tube(new float[][]{{4.0f, 24.9f, 0}, {5.7f, 24.9f, 0}, {6.65f, 26.6f, 0}, {6.0f, 28.3f, 0}, {4.45f, 28.65f, 0}}, new float[]{0.5f, 0.56f, 0.58f, 0.56f, 0.5f}, 12, ceramic);
        for (int s = -1; s <= 1; s += 2) {
            float ex = s * 1.6f, ez = -(float) Math.sqrt(4.2f * 4.2f - ex * ex) + 0.08f;
            eye(head, v(ex, 27.1f, ez), v(s * 0.38f, 0.05f, -1), 0.66f, 0.85f, 0.36f, 0.52f, rgb(0x4A2A16), 0.28f, false);
            for (int l = 0; l < 3; l++) {
                float lx = s * (1.95f + l * 0.22f), ly = 27.75f - l * 0.32f;
                float lz = -(float) Math.sqrt(4.3f * 4.3f - lx * lx) - 0.02f;
                head.tube(new float[][]{{lx, ly, lz}, {lx + s * 0.4f, ly + 0.28f, lz - 0.08f}}, new float[]{0.09f, 0.04f}, 5, Shader.solid(BLACK));
            }
            head.tube(new float[][]{{s * 1.0f, 28.25f, -4.1f}, {s * 1.6f, 28.5f, -4.12f}, {s * 2.2f, 28.35f, -3.95f}}, new float[]{0.07f, 0.1f, 0.07f}, 6, Shader.solid(rgb(0x5A3A2A)));
            float bx = s * 2.6f, bz = -(float) Math.sqrt(4.15f * 4.15f - bx * bx) + 0.05f;
            head.blob(v(bx, 25.55f, bz), v(s * 0.8f, 0, -0.6f), v(0, 1, 0), 0.72f, 0.42f, 0.14f, -90, 90, 8, 12, Shader.solid(rgb(0xF7A7C0)));
        }
        head.tube(new float[][]{{-0.85f, 25.25f, -4.0f}, {0, 24.75f, -4.08f}, {0.85f, 25.25f, -4.0f}}, new float[]{0.16f, 0.2f, 0.16f}, 8, Shader.solid(rgb(0xD94F72)));

        // Arms rounded low and open (bras bas), hands floating over the tutu.
        for (int s = -1; s <= 1; s += 2) {
            Builder arm = m.part(s < 0 ? Part.RIGHT_ARM : Part.LEFT_ARM, s * 2.6f, 20.5f, 0);
            arm.tube(new float[][]{{s * 2.4f, 20.6f, 0.1f}, {s * 3.9f, 19.3f, -0.2f}, {s * 5.0f, 17.8f, -0.7f}, {s * 6.0f, 16.6f, -1.6f}, {s * 6.5f, 16.0f, -2.4f}},
                    new float[]{0.72f, 0.62f, 0.52f, 0.45f, 0.36f}, 12, skinS);
            arm.blob(v(s * 6.75f, 15.75f, -3.05f), v(s * 0.35f, -0.3f, -0.9f), v(s * 0.9f, 0, 0.35f), 0.85f, 0.42f, 0.22f, -90, 90, 8, 14, skinS);
            arm.blob(v(s * 6.3f, 15.95f, -3.15f), v(s * 0.1f, -0.2f, -1f), v(0, 1, 0), 0.38f, 0.16f, 0.14f, -90, 90, 6, 10, skinS);
        }
        // Long legs in pink tights and satin pointe shoes with crossed ribbons.
        for (int s = -1; s <= 1; s += 2) {
            Builder leg = m.part(s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG, s * 1.2f, 13.4f, 0);
            float x = s * 1.22f;
            leg.tube(new float[][]{{x, 14.2f, 0.1f}, {x, 10.6f, -0.05f}, {x, 7.6f, -0.1f}, {x, 5.4f, 0.12f}, {x, 2.7f, 0.1f}},
                    new float[]{1.22f, 1.02f, 0.76f, 0.82f, 0.54f}, 14, (px, py, pz, nx, ny, nz) -> mix(rgb(0xE7B6CA), tights, 0.5f + 0.5f * smooth(-0.6f, 0.6f, -nz)));
            leg.loft(new float[][]{{x, 0.95f, 0.85f}, {x, 1.0f, -0.25f}, {x, 0.72f, -1.65f}}, new float[]{0.72f, 0.82f, 0.58f}, new float[]{0.95f, 1.0f, 0.7f}, 16, 14, null,
                    (px, py, pz, nx, ny, nz) -> py < 0.32f ? rgb(0xC9789B) : mix(rgb(0xEBA3C2), rgb(0xFBD3E3), smooth(-0.3f, 0.8f, ny)));
            for (int r = 0; r < 2; r++) {
                float a = r == 0 ? 1 : -1;
                leg.tube(new float[][]{{x - 0.62f, 1.6f, 0.0f}, {x, 2.5f + a * 0.0f, -0.6f * a}, {x + 0.62f, 3.4f, 0.0f}}, radii(3, 0.11f), 6, Shader.solid(rgb(0xE48DB3)));
            }
        }
        return m;
    }
}
