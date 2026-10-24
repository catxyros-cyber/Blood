package dev.bloom.client.core.avatar;

import dev.bloom.client.core.avatar.AvatarModel.Part;
import dev.bloom.client.core.avatar.AvatarModel.Shader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static dev.bloom.client.core.avatar.AvatarModel.mix;
import static dev.bloom.client.core.avatar.AvatarModel.noise;
import static dev.bloom.client.core.avatar.AvatarModel.rgb;
import static dev.bloom.client.core.avatar.AvatarModel.shade;

/**
 * MOTION's built-in 3D avatars: original, smooth-shaded takes on the Italian brainrot memes, each
 * about a player tall. Ids match the "style" of avatar accessories on MOTION's server.
 */
public final class AvatarModels {
    // Declared before the models are built (static fields initialise in order).
    private static final int BLACK = rgb(0x0E0E10), WHITE = rgb(0xF7F7F4), EYE_WHITE = rgb(0xFBFBF8);
    private static final Map<String, AvatarModel> MODELS = new LinkedHashMap<>();

    static {
        MODELS.put("sahur", sahur());
        MODELS.put("tralalero", tralalero());
        MODELS.put("bombardiro", bombardiro());
        MODELS.put("ballerina", ballerina());
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


    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** A glossy cartoon eye: white, a dark pupil looking forward and a highlight. */
    private static void eye(AvatarModel.Builder b, float x, float y, float z, float w, float h, float pupil) {
        b.ellipsoid(x, y, z, w, h, w * 0.6f, Shader.solid(EYE_WHITE));
        b.ellipsoid(x, y - h * 0.08f, z - w * 0.5f, pupil, pupil * 1.15f, pupil * 0.45f, 8, 12, Shader.solid(BLACK));
        b.sphere(x - pupil * 0.35f, y + pupil * 0.4f, z - w * 0.5f - pupil * 0.42f, pupil * 0.3f, WHITE);
    }

    // ---------------------------------------------------------------------------- Tung Tung Tung Sahur
    private static AvatarModel sahur() {
        AvatarModel m = new AvatarModel("sahur");
        int barkDark = rgb(0x4A2914), barkMid = rgb(0x6E4126), barkLight = rgb(0x93603A);
        Shader bark = (x, y, z, nx, ny, nz) -> {
            double ang = Math.atan2(z, x);
            float grain = (float) (0.5 + 0.5 * Math.sin(ang * 22 + Math.sin(y * 0.9) * 1.6 + noise(x, y, z) * 2.2));
            int c = mix(barkDark, barkLight, 0.25f + 0.55f * grain * grain + 0.2f * noise(x * 2, y * 0.7f, z * 2));
            if (ny > 0.85f) c = mix(rgb(0xC9935A), rgb(0x9B6A3E), (float) (0.5 + 0.5 * Math.sin(Math.sqrt(x * x + z * z) * 3.2)));
            boolean face = nz < -0.55f && y > 16 && y < 29;
            if (face) c = mix(c, barkLight, 0.35f);
            return shade(c, 0.78f + 0.22f * smooth(9, 14, y));
        };
        AvatarModel.Builder body = m.part(Part.BODY, 0, 9, 0);
        body.lathe(0, 0, new float[]{9, 10, 13, 18, 23, 27, 29.4f, 30.6f, 31.2f}, new float[]{4.6f, 5.0f, 5.25f, 5.3f, 5.25f, 5.0f, 4.4f, 3.0f, 0.01f}, 24, bark);
        // Face: big worried eyes, brows, a nose knot and an open mouth.
        for (int s = -1; s <= 1; s += 2) {
            eye(body, s * 2.05f, 24.6f, -5.2f, 1.5f, 1.85f, 0.62f);
            body.tube(new float[][]{{s * 3.6f, 27.1f, -4.55f}, {s * 2.3f, 27.9f, -5.0f}, {s * 0.8f, 27.5f, -5.15f}}, new float[]{0.32f, 0.42f, 0.34f}, 8, Shader.solid(rgb(0x24130A)));
        }
        body.ellipsoid(0, 22.2f, -5.2f, 0.85f, 1.05f, 0.7f, Shader.solid(barkLight));
        body.ellipsoid(0, 19.7f, -5.0f, 1.95f, 1.2f, 0.55f, Shader.solid(rgb(0x2A0E07)));
        body.box(-1.25f, 20.35f, -5.55f, 1.25f, 20.85f, -5.15f, WHITE);
        body.ellipsoid(0, 19.15f, -5.3f, 1.05f, 0.45f, 0.25f, Shader.solid(rgb(0xB8424A)));
        // A couple of knots in the wood.
        body.ellipsoid(4.9f, 14, 1.6f, 0.45f, 0.8f, 0.8f, Shader.solid(barkDark));
        body.ellipsoid(-4.2f, 28, 2.4f, 0.4f, 0.65f, 0.7f, Shader.solid(barkDark));

        Shader limbWood = (x, y, z, nx, ny, nz) -> mix(rgb(0x7B4B2A), rgb(0x9C6840), noise(x * 3, y * 3, z * 3));
        AvatarModel.Builder ra = m.part(Part.RIGHT_ARM, -5, 19, 0);
        ra.tube(new float[][]{{-4.8f, 19, 0}, {-7.2f, 14.6f, -0.6f}, {-8.4f, 11.4f, -1.7f}}, new float[]{0.95f, 0.85f, 0.8f}, 10, limbWood);
        ra.sphere(-8.6f, 11.0f, -2.0f, 1.15f, rgb(0x8A5A34));
        // The bat: a turned ash bat, thin at the handle and fat at the barrel.
        Shader ash = (x, y, z, nx, ny, nz) -> mix(rgb(0xD9B27A), rgb(0xE8C895), noise(x * 4, y * 2, z * 4));
        ra.tube(new float[][]{{-8.6f, 11.6f, -2.0f}, {-9.2f, 8.8f, -4.0f}, {-9.8f, 6.0f, -6.0f}, {-10.4f, 3.4f, -7.9f}}, new float[]{0.5f, 0.7f, 1.15f, 1.45f}, 12, ash);
        ra.sphere(-8.5f, 12.3f, -1.5f, 0.7f, rgb(0xC9A06A));
        AvatarModel.Builder la = m.part(Part.LEFT_ARM, 5, 19, 0);
        la.tube(new float[][]{{4.8f, 19, 0}, {7.2f, 14.6f, -0.6f}, {8.2f, 11.6f, -1.2f}}, new float[]{0.95f, 0.85f, 0.8f}, 10, limbWood);
        la.sphere(8.4f, 11.2f, -1.4f, 1.15f, rgb(0x8A5A34));
        for (int s = -1; s <= 1; s += 2) {
            AvatarModel.Builder leg = m.part(s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG, s * 2.1f, 10, 0);
            leg.tube(new float[][]{{s * 2.1f, 10, 0}, {s * 2.2f, 5.5f, -0.2f}, {s * 2.3f, 1.8f, 0}}, new float[]{1.05f, 0.95f, 0.9f}, 10, limbWood);
            leg.ellipsoid(s * 2.4f, 0.9f, -1.1f, 1.35f, 0.9f, 2.1f, Shader.solid(rgb(0x4A2C18)));
        }
        return m;
    }

    // ---------------------------------------------------------------------------- Tralalero Tralala
    private static AvatarModel tralalero() {
        AvatarModel m = new AvatarModel("tralalero");
        int blue = rgb(0x3C82CC), deep = rgb(0x24578F), belly = rgb(0xEEF2F5);
        Shader shark = (x, y, z, nx, ny, nz) -> {
            int top = mix(blue, deep, smooth(-0.2f, 0.9f, nz) * 0.7f + 0.15f * noise(x * 2, y * 2, z * 2));
            float under = smooth(-0.25f, -0.6f, nz) * (1 - smooth(21, 23.5f, y));
            return mix(top, belly, under);
        };
        AvatarModel.Builder body = m.part(Part.BODY, 0, 9, 0);
        body.lathe(0, 0.4f, new float[]{8, 8.8f, 11, 15, 19, 22, 23.5f}, new float[]{2.6f, 3.9f, 4.8f, 5.0f, 4.7f, 4.0f, 3.3f}, 22, shark);
        body.slab(new float[][]{{0, 20.5f, 3.6f}, {0, 28.8f, 7.6f}, {0, 21.5f, 7.2f}}, 0.8f, Shader.solid(deep));
        body.tube(new float[][]{{0, 12.5f, 3.6f}, {0, 11, 6.5f}, {0, 10, 9}}, new float[]{1.6f, 1.1f, 0.7f}, 10, shark);
        body.slab(new float[][]{{0, 10, 8.6f}, {0, 15.5f, 11.5f}, {0, 13.2f, 9.6f}}, 0.6f, Shader.solid(deep));
        body.slab(new float[][]{{0, 10, 8.6f}, {0, 5.5f, 11.0f}, {0, 8.0f, 9.4f}}, 0.6f, Shader.solid(deep));

        AvatarModel.Builder head = m.part(Part.HEAD, 0, 22.5f, 0);
        Shader face = (x, y, z, nx, ny, nz) -> {
            int top = mix(blue, deep, smooth(-0.2f, 0.9f, ny) * 0.4f + 0.12f * noise(x * 2, y * 2, z * 2));
            return mix(top, belly, smooth(-0.1f, -0.45f, ny));
        };
        head.ellipsoid(0, 24.6f, -2.0f, 4.1f, 3.5f, 6.4f, 14, 22, face);
        // Grinning mouth with teeth.
        float[][] mouth = {{-3.3f, 23.4f, -4.4f}, {-2.0f, 22.7f, -6.9f}, {0, 22.45f, -8.2f}, {2.0f, 22.7f, -6.9f}, {3.3f, 23.4f, -4.4f}};
        head.tube(mouth, new float[]{0.26f, 0.32f, 0.34f, 0.32f, 0.26f}, 8, Shader.solid(rgb(0x5A1A24)));
        for (int i = 0; i < 9; i++) {
            float t = i / 8f * (mouth.length - 1);
            int a = Math.min(mouth.length - 2, (int) t);
            float f = t - a;
            float x = mouth[a][0] + (mouth[a + 1][0] - mouth[a][0]) * f, y = mouth[a][1] + (mouth[a + 1][1] - mouth[a][1]) * f, z = mouth[a][2] + (mouth[a + 1][2] - mouth[a][2]) * f;
            head.slab(new float[][]{{x - 0.33f, y + 0.05f, z - 0.25f}, {x + 0.33f, y + 0.05f, z - 0.25f}, {x, y - 0.75f, z - 0.25f}}, 0.22f, Shader.solid(WHITE));
        }
        for (int s = -1; s <= 1; s += 2) {
            eye(head, s * 2.95f, 26.0f, -4.6f, 0.9f, 1.0f, 0.48f);
            for (int g = 0; g < 3; g++) {
                head.tube(new float[][]{{s * 3.95f, 23.6f, 0.2f + g * 0.9f}, {s * 4.05f, 25.2f, 0.4f + g * 0.9f}}, new float[]{0.14f, 0.14f}, 6, Shader.solid(deep));
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            AvatarModel.Builder fin = m.part(s < 0 ? Part.RIGHT_ARM : Part.LEFT_ARM, s * 4.6f, 18.5f, 0);
            fin.slab(new float[][]{{s * 4.3f, 19.2f, -0.9f}, {s * 9.6f, 13.6f, 0.6f}, {s * 4.4f, 15.0f, 1.3f}}, 0.7f, Shader.solid(blue));
            AvatarModel.Builder leg = m.part(s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG, s * 2.1f, 9.5f, 0);
            leg.tube(new float[][]{{s * 2.1f, 9.5f, 0.2f}, {s * 2.2f, 5.5f, 0}, {s * 2.3f, 2.4f, -0.3f}}, new float[]{1.25f, 1.15f, 1.05f}, 10, Shader.solid(blue));
            // Chunky blue trainers with a white sole and laces.
            Shader trainer = (x, y, z, nx, ny, nz) -> nz < -0.75f && y < 1.6f ? WHITE : mix(rgb(0x1F6FE5), rgb(0x1858BA), smooth(0.2f, 0.9f, nz));
            leg.ellipsoid(s * 2.3f, 1.35f, -1.0f, 1.8f, 1.2f, 2.95f, 10, 16, trainer);
            leg.ellipsoid(s * 2.3f, 0.42f, -1.0f, 1.9f, 0.42f, 3.05f, 8, 16, Shader.solid(WHITE));
            for (int l = 0; l < 3; l++) leg.box(s * 2.3f - 0.75f, 2.35f, -2.4f + l * 0.7f, s * 2.3f + 0.75f, 2.6f, -2.15f + l * 0.7f, WHITE);
        }
        return m;
    }

    // ---------------------------------------------------------------------------- Bombardiro Crocodilo
    private static AvatarModel bombardiro() {
        AvatarModel m = new AvatarModel("bombardiro");
        int grey = rgb(0x7F8A94), dgrey = rgb(0x4B535A), red = rgb(0xC4372B), green = rgb(0x4E8A39), dgreen = rgb(0x2F5A23), pale = rgb(0xDCD3A0);
        Shader metal = (x, y, z, nx, ny, nz) -> {
            int c = mix(grey, rgb(0xA4AEB6), smooth(0.2f, 0.9f, ny) * 0.4f + smooth(-0.9f, 0f, nz) * 0.15f);
            if (y > 15.8f && y < 17.2f) c = red;
            float panel = Math.abs(((y * 0.62f) % 1f) - 0.5f);
            if (panel > 0.47f) c = shade(c, 0.72f);
            return c;
        };
        AvatarModel.Builder body = m.part(Part.BODY, 0, 9, 0);
        body.lathe(0, 0.5f, new float[]{9, 10, 13, 15.75f, 15.85f, 17.15f, 17.25f, 20.5f, 22.5f}, new float[]{3.2f, 4.0f, 4.5f, 4.43f, 4.43f, 4.4f, 4.4f, 4.0f, 3.0f}, 22, metal);
        for (int s = -1; s <= 1; s += 2) {
            Shader wing = (x, y, z, nx, ny, nz) -> Math.abs(x) > 13.4f ? red : mix(grey, rgb(0xA4AEB6), ny > 0 ? 0.35f : 0f);
            body.slab(new float[][]{{s * 3.6f, 17.6f, -1.8f}, {s * 16.2f, 16.9f, 0.4f}, {s * 16.2f, 16.9f, 3.0f}, {s * 3.6f, 17.6f, 3.6f}}, 0.9f, wing);
            body.tube(new float[][]{{s * 8.6f, 15.6f, -3.6f}, {s * 8.6f, 15.6f, -0.5f}, {s * 8.6f, 15.6f, 2.8f}}, new float[]{1.0f, 1.25f, 0.9f}, 12, Shader.solid(dgrey));
            body.box(s * 8.6f - 2.6f, 15.45f, -4.6f, s * 8.6f + 2.6f, 15.75f, -4.35f, rgb(0x2B2F33));
            body.box(s * 8.6f - 0.15f, 13.0f, -4.6f, s * 8.6f + 0.15f, 18.2f, -4.35f, rgb(0x2B2F33));
        }
        body.slab(new float[][]{{0, 14.0f, 3.8f}, {0, 23.6f, 7.4f}, {0, 15.2f, 8.2f}}, 0.7f, metal);
        body.ellipsoid(0, 11.0f, -3.6f, 1.6f, 1.0f, 1.2f, Shader.solid(red));

        AvatarModel.Builder head = m.part(Part.HEAD, 0, 22, 0);
        Shader croc = (x, y, z, nx, ny, nz) -> {
            float scales = noise(x * 3.2f, y * 3.2f, z * 3.2f);
            int c = mix(dgreen, green, 0.45f + 0.55f * scales);
            return mix(c, pale, smooth(-0.15f, -0.55f, ny));
        };
        head.ellipsoid(0, 24.6f, -0.6f, 3.8f, 3.0f, 4.2f, 12, 20, croc);
        head.ellipsoid(0, 23.4f, -7.2f, 2.5f, 1.6f, 5.6f, 12, 20, croc);
        for (int i = 0; i < 5; i++) head.sphere(0, 24.9f, -4.4f - i * 1.4f, 0.42f, dgreen);
        for (int s = -1; s <= 1; s += 2) {
            head.sphere(s * 0.8f, 24.4f, -12.4f, 0.35f, rgb(0x1E3A16));
            for (int i = 0; i < 7; i++) {
                float z = -4.6f - i * 1.15f;
                float x = s * (2.35f - i * 0.12f);
                head.slab(new float[][]{{x, 22.6f, z - 0.35f}, {x, 22.6f, z + 0.35f}, {x, 21.8f, z}}, 0.25f, Shader.solid(WHITE));
            }
            head.sphere(s * 2.0f, 27.0f, -2.6f, 1.05f, rgb(0xF2C22B));
            head.ellipsoid(s * 2.0f, 27.0f, -3.55f, 0.18f, 0.75f, 0.25f, 6, 10, Shader.solid(BLACK));
        }
        for (int s = -1; s <= 1; s += 2) {
            AvatarModel.Builder arm = m.part(s < 0 ? Part.RIGHT_ARM : Part.LEFT_ARM, s * 4.2f, 18.5f, 0);
            arm.tube(new float[][]{{s * 4.2f, 18.5f, 0}, {s * 6.0f, 14.5f, -0.8f}, {s * 6.4f, 11.6f, -1.6f}}, new float[]{0.95f, 0.85f, 0.8f}, 10, croc);
            for (int c = -1; c <= 1; c++) arm.limb(s * 6.4f + c * 0.45f, 11.4f, -1.9f, s * 6.5f + c * 0.6f, 10.4f, -2.7f, 0.22f, 0.08f, Shader.solid(pale));
            AvatarModel.Builder leg = m.part(s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG, s * 2.1f, 9.5f, 0);
            leg.tube(new float[][]{{s * 2.1f, 9.5f, 0}, {s * 2.2f, 5.5f, -0.1f}, {s * 2.3f, 2.0f, 0}}, new float[]{1.2f, 1.1f, 1.0f}, 10, croc);
            leg.ellipsoid(s * 2.4f, 1.0f, -0.9f, 1.45f, 1.0f, 2.2f, Shader.solid(rgb(0x2A2A2C)));
        }
        return m;
    }

    // ---------------------------------------------------------------------------- Ballerina Cappuccina
    private static AvatarModel ballerina() {
        AvatarModel m = new AvatarModel("ballerina");
        int pink = rgb(0xF4A6C6), dpink = rgb(0xD97AA6), tutu = rgb(0xFFC9E0), skin = rgb(0xF1C7A4), cup = rgb(0xF4EFE7), coffee = rgb(0x6B4026), foam = rgb(0xE6CFA8);
        AvatarModel.Builder body = m.part(Part.BODY, 0, 13, 0);
        body.lathe(0, 0, new float[]{13, 14.5f, 17, 19.5f, 21.5f}, new float[]{2.2f, 2.5f, 2.2f, 2.8f, 2.3f}, 18,
                (x, y, z, nx, ny, nz) -> mix(dpink, pink, 0.6f + 0.4f * smooth(-0.5f, 0.6f, -nz)));
        body.lathe(0, 0, new float[]{12.0f, 12.5f, 13.2f, 13.9f, 14.4f}, new float[]{2.4f, 6.8f, 7.4f, 6.6f, 2.6f}, 36,
                (x, y, z, nx, ny, nz) -> mix(tutu, rgb(0xFFE3EF), (float) Math.abs(Math.sin(Math.atan2(z, x) * 12))));
        body.tube(new float[][]{{0, 21, 0}, {0, 22.8f, 0}}, new float[]{0.9f, 0.85f}, 10, Shader.solid(skin));

        AvatarModel.Builder head = m.part(Part.HEAD, 0, 22.5f, 0);
        Shader ceramic = (x, y, z, nx, ny, nz) -> {
            if (ny > 0.9f && y > 29.8f) {
                double r = Math.sqrt(x * x + z * z), a = Math.atan2(z, x);
                return mix(foam, coffee, (float) (0.5 + 0.5 * Math.sin(r * 2.6 + a * 2)) * 0.55f);
            }
            return mix(rgb(0xD9D1C2), cup, 0.55f + 0.45f * smooth(-0.6f, 0.4f, -nz));
        };
        head.lathe(0, 0, new float[]{22.4f, 23.0f, 25.0f, 27.5f, 29.6f, 30.0f}, new float[]{2.8f, 3.4f, 4.0f, 4.45f, 4.7f, 4.6f}, 28, ceramic);
        float[][] rim = new float[25][];
        float[] rimR = new float[25];
        for (int i = 0; i < 25; i++) {
            double a = 2 * Math.PI * i / 24;
            rim[i] = new float[]{(float) Math.cos(a) * 4.68f, 30.0f, (float) Math.sin(a) * 4.68f};
            rimR[i] = 0.28f;
        }
        head.tube(rim, rimR, 8, Shader.solid(cup));
        head.tube(new float[][]{{4.2f, 24.0f, 0}, {6.1f, 24.5f, 0}, {6.8f, 26.3f, 0}, {6.0f, 28.0f, 0}, {4.5f, 28.4f, 0}}, new float[]{0.5f, 0.55f, 0.55f, 0.55f, 0.5f}, 10, Shader.solid(cup));
        for (int s = -1; s <= 1; s += 2) {
            head.ellipsoid(s * 1.6f, 26.5f, -4.25f, 0.55f, 0.75f, 0.32f, 8, 12, Shader.solid(rgb(0x2B1C14)));
            head.sphere(s * 1.6f - 0.18f, 26.85f, -4.55f, 0.17f, WHITE);
            head.tube(new float[][]{{s * 1.15f, 27.35f, -4.3f}, {s * 1.6f, 27.6f, -4.35f}, {s * 2.15f, 27.45f, -4.25f}}, new float[]{0.09f, 0.1f, 0.09f}, 6, Shader.solid(BLACK));
            head.ellipsoid(s * 2.65f, 25.2f, -3.95f, 0.7f, 0.4f, 0.2f, 6, 10, Shader.solid(rgb(0xF6A9C2)));
        }
        head.tube(new float[][]{{-0.9f, 24.75f, -4.3f}, {0, 24.3f, -4.42f}, {0.9f, 24.75f, -4.3f}}, new float[]{0.17f, 0.2f, 0.17f}, 6, Shader.solid(rgb(0xD9506F)));

        for (int s = -1; s <= 1; s += 2) {
            AvatarModel.Builder arm = m.part(s < 0 ? Part.RIGHT_ARM : Part.LEFT_ARM, s * 2.4f, 20.5f, 0);
            // Arms arched over the head, ballet's fifth position.
            arm.tube(new float[][]{{s * 2.4f, 20.5f, 0}, {s * 6.2f, 24.2f, -0.4f}, {s * 7.0f, 28.6f, -0.4f}, {s * 4.2f, 32.2f, -0.4f}, {s * 1.2f, 33.2f, -0.4f}},
                    new float[]{0.72f, 0.66f, 0.6f, 0.55f, 0.5f}, 10, Shader.solid(skin));
            arm.sphere(s * 0.9f, 33.2f, -0.4f, 0.62f, skin);
            AvatarModel.Builder leg = m.part(s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG, s * 1.15f, 12.5f, 0);
            leg.tube(new float[][]{{s * 1.15f, 12.5f, 0}, {s * 1.2f, 6.5f, 0}, {s * 1.2f, 1.9f, 0}}, new float[]{0.85f, 0.78f, 0.62f}, 10, Shader.solid(rgb(0xF8D2E1)));
            leg.ellipsoid(s * 1.2f, 0.9f, -0.5f, 0.85f, 0.9f, 1.6f, 10, 14, Shader.solid(rgb(0xF0B6CC)));
            leg.tube(new float[][]{{s * 0.4f, 2.4f, -0.1f}, {s * 1.2f, 3.6f, -0.85f}, {s * 2.0f, 2.4f, -0.1f}}, new float[]{0.1f, 0.1f, 0.1f}, 6, Shader.solid(dpink));
        }
        return m;
    }
}
