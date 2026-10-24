package dev.bloom.client.core.avatar;

import dev.bloom.client.core.avatar.AvatarModel.Part;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * MOTION's built-in 3D avatars (original, low-poly takes on the Italian brainrot memes). Each one
 * is about a player tall. Ids match the "style" of avatar accessories on MOTION's server.
 */
public final class AvatarModels {
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

    private static final int BLACK = 0xFF111111, WHITE = 0xFFF6F6F2, EYE = 0xFFFDFDFD;

    /** Two eyes on a front surface at depth {@code z} (negative = front), centred on x = ±dx. */
    private static void eyes(AvatarModel.Builder b, float dx, float y, float z, float size, float pupil) {
        for (int s = -1; s <= 1; s += 2) {
            float cx = s * dx;
            b.box(cx - size / 2, y, z - 0.4f, cx + size / 2, y + size, z + 0.4f, EYE);
            b.box(cx - pupil / 2, y + size * 0.2f, z - 0.75f, cx + pupil / 2, y + size * 0.2f + pupil, z - 0.35f, BLACK);
        }
    }

    // ---------------------------------------------------------------------------- Tung Tung Tung Sahur
    private static AvatarModel sahur() {
        AvatarModel m = new AvatarModel("sahur", 31);
        int[] bark = {0xFF6B3E22, 0xFF7A4729, 0xFF5E361D, 0xFF845130, 0xFF6F4024};
        m.part(Part.BODY, 0, 9, 0)
                .cylinder(0, 0, 5.2f, 4.8f, 9, 31, 14, 11, i -> bark[i % bark.length], 0xFFC99A63, 0xFF4A2A16)
                // Face: wide eyes, worried brows, a little open mouth.
                .box(-3.4f, 22.6f, -5.6f, -0.6f, 26.2f, -4.6f, EYE).box(0.6f, 22.6f, -5.6f, 3.4f, 26.2f, -4.6f, EYE)
                .box(-2.5f, 23.2f, -5.95f, -1.3f, 24.8f, -5.5f, BLACK).box(1.3f, 23.2f, -5.95f, 2.5f, 24.8f, -5.5f, BLACK)
                .rod(-3.6f, 27.4f, -5.3f, -0.6f, 26.8f, -5.3f, 0.7f, 0xFF2B170C).rod(0.6f, 26.8f, -5.3f, 3.6f, 27.4f, -5.3f, 0.7f, 0xFF2B170C)
                .box(-1.8f, 17.6f, -5.5f, 1.8f, 19.6f, -4.7f, 0xFF2A0F08)
                .box(-1.4f, 18.9f, -5.7f, 1.4f, 19.5f, -5.3f, WHITE)
                // Wood grain lines.
                .rod(-4.6f, 13, -2, -4.6f, 20, -2, 0.35f, 0xFF4A2A16).rod(4.6f, 11, 1.5f, 4.6f, 17, 1.5f, 0.35f, 0xFF4A2A16);
        int wood = 0xFF8A5A34;
        m.part(Part.RIGHT_ARM, -5, 19, 0)
                .rod(-5, 19, 0, -8.5f, 11.5f, -1, 1.4f, wood)
                .box(-9.6f, 10.2f, -2.1f, -7.6f, 12.2f, 0.1f, wood)
                // The baseball bat.
                .rod(-8.6f, 11, -1, -9.6f, 6, -4.5f, 1.3f, 0xFFD9B27C)
                .rod(-9.6f, 6, -4.5f, -10.8f, 1.6f, -8.5f, 2.3f, 0xFFC8955A);
        m.part(Part.LEFT_ARM, 5, 19, 0)
                .rod(5, 19, 0, 8.5f, 11.5f, -1, 1.4f, wood)
                .box(7.6f, 10.2f, -2.1f, 9.6f, 12.2f, 0.1f, wood);
        for (int s = -1; s <= 1; s += 2) {
            Part leg = s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG;
            m.part(leg, s * 2, 9.5f, 0)
                    .rod(s * 2, 9.5f, 0, s * 2.3f, 1, 0, 1.6f, wood)
                    .box(s * 2.3f - 1.3f, 0, -2.8f, s * 2.3f + 1.3f, 1.2f, 1, 0xFF3A2416);
        }
        return m;
    }

    // ---------------------------------------------------------------------------- Tralalero Tralala
    private static AvatarModel tralalero() {
        AvatarModel m = new AvatarModel("tralalero", 30);
        int blue = 0xFF3D7FC4, dark = 0xFF2A5A92, belly = 0xFFE9EEF2, shoe = 0xFF1F6FE5;
        m.part(Part.BODY, 0, 9, 0)
                .cylinder(0, 0.5f, 4.4f, 5, 9, 22, 12, 3, i -> i == 1 ? blue : AvatarModel.shade(blue, 0.93f), blue, dark)
                .box(-2.8f, 10, -5, 2.8f, 21, -4.3f, belly)
                // Dorsal fin and tail.
                .box(-0.6f, 19, 3.2f, 0.6f, 23.5f, 6.8f, dark).box(-0.6f, 23.5f, 4.6f, 0.6f, 26.5f, 6.8f, dark)
                .rod(0, 11, 4.5f, 0, 8, 9.5f, 1.4f, blue).box(-0.5f, 6, 8.5f, 0.5f, 11.5f, 10.5f, dark);
        m.part(Part.HEAD, 0, 22, 0)
                .cylinder(0, 0.5f, 4.6f, 3.4f, 22, 28.5f, 12, 1, i -> blue, dark, blue)
                .box(-3, 22.6f, -9.5f, 3, 26.2f, -3, blue, dark)                // upper snout
                .box(-2.7f, 21.3f, -8.8f, 2.7f, 22.7f, -3, belly)                // jaw
                .box(-2.6f, 22.5f, -9.6f, 2.6f, 23.1f, -9.1f, WHITE)            // teeth (front)
                .box(-3.05f, 22.5f, -9.2f, -2.7f, 23.1f, -3.5f, WHITE).box(2.7f, 22.5f, -9.2f, 3.05f, 23.1f, -3.5f, WHITE);
        AvatarModel.Builder head = m.part(Part.HEAD, 0, 22, 0);
        for (int s = -1; s <= 1; s += 2) {
            head.box(s * 3.4f - 0.8f, 25.2f, -6.2f, s * 3.4f + 0.8f, 26.8f, -4.4f, EYE)
                .box(s * 3.4f - 0.35f, 25.5f, -6.5f, s * 3.4f + 0.35f, 26.3f, -6.1f, BLACK);
        }
        for (int s = -1; s <= 1; s += 2) {
            Part arm = s < 0 ? Part.RIGHT_ARM : Part.LEFT_ARM;
            m.part(arm, s * 4.8f, 19, 0).box(s * 4.6f - (s < 0 ? 2.4f : 0), 12.5f, -1.2f, s * 4.6f + (s > 0 ? 2.4f : 0), 19, 1.2f, blue, dark);
            Part leg = s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG;
            m.part(leg, s * 2.1f, 9.5f, 0)
                    .rod(s * 2.1f, 10, 0, s * 2.2f, 2, 0, 2.1f, blue)
                    .box(s * 2.2f - 1.7f, 0.5f, -3.8f, s * 2.2f + 1.7f, 2.6f, 1.5f, shoe, shoe)
                    .box(s * 2.2f - 1.8f, 0, -3.9f, s * 2.2f + 1.8f, 0.6f, 1.6f, WHITE)
                    .box(s * 2.2f - 0.8f, 2.6f, -2.8f, s * 2.2f + 0.8f, 2.9f, -0.4f, WHITE);
        }
        return m;
    }

    // ---------------------------------------------------------------------------- Bombardiro Crocodilo
    private static AvatarModel bombardiro() {
        AvatarModel m = new AvatarModel("bombardiro", 28);
        int grey = 0xFF7D8790, dgrey = 0xFF4E565D, green = 0xFF4F8A3A, dgreen = 0xFF355F27, pale = 0xFFD9D2A0, red = 0xFFC43B2E;
        m.part(Part.BODY, 0, 9, 0)
                .cylinder(0, 0.5f, 4, 4.4f, 9, 21, 12, 4, i -> i == 2 ? red : grey, grey, dgrey)
                .box(-15, 16, -1, 15, 17.2f, 3, grey, 0xFF9AA4AC)              // wings
                .box(-15.2f, 16.2f, -1.2f, -12.5f, 17, 3.2f, red).box(12.5f, 16.2f, -1.2f, 15.2f, 17, 3.2f, red)
                .box(-10, 14, -2.5f, -8, 16, 1.5f, dgrey).box(8, 14, -2.5f, 10, 16, 1.5f, dgrey)   // engines
                .box(-0.5f, 14, 3.5f, 0.5f, 23, 7, grey)                        // tail fin
                .box(-1.6f, 9.5f, -4.6f, 1.6f, 12, -3.9f, red);                 // bomb bay
        m.part(Part.HEAD, 0, 21, 0)
                .box(-3.5f, 21, -4, 3.5f, 26, 3.5f, green, dgreen)
                .box(-2.6f, 21.6f, -12, 2.6f, 24.2f, -4, green, dgreen)         // snout
                .box(-2.4f, 21, -11.2f, 2.4f, 21.7f, -4, pale)                  // jaw
                .box(-2.7f, 21.7f, -11.4f, -2.45f, 22.3f, -4.5f, WHITE).box(2.45f, 21.7f, -11.4f, 2.7f, 22.3f, -4.5f, WHITE)
                .box(-1.4f, 24.1f, -11.6f, -0.6f, 24.4f, -10.8f, dgreen).box(0.6f, 24.1f, -11.6f, 1.4f, 24.4f, -10.8f, dgreen);
        AvatarModel.Builder head = m.part(Part.HEAD, 0, 21, 0);
        for (int s = -1; s <= 1; s += 2) {
            head.box(s * 2.2f - 1, 26, -3.4f, s * 2.2f + 1, 27.8f, -1.4f, 0xFFF2C22B)
                .box(s * 2.2f - 0.2f, 26.3f, -3.7f, s * 2.2f + 0.2f, 27.5f, -3.3f, BLACK);
        }
        for (int s = -1; s <= 1; s += 2) {
            Part arm = s < 0 ? Part.RIGHT_ARM : Part.LEFT_ARM;
            m.part(arm, s * 4.4f, 18, 0)
                    .rod(s * 4.4f, 18, 0, s * 6, 11, -1, 1.6f, green)
                    .box(s * 6 - 1, 10, -2.4f, s * 6 + 1, 11.5f, 0, dgreen);
            Part leg = s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG;
            m.part(leg, s * 2, 9.5f, 0)
                    .rod(s * 2, 9.5f, 0, s * 2.2f, 1, 0, 2, green)
                    .box(s * 2.2f - 1.3f, 0, -3.2f, s * 2.2f + 1.3f, 1.3f, 1, 0xFF2A2A2A);
        }
        return m;
    }

    // ---------------------------------------------------------------------------- Ballerina Cappuccina
    private static AvatarModel ballerina() {
        AvatarModel m = new AvatarModel("ballerina", 33);
        int pink = 0xFFF4A6C6, dpink = 0xFFD97AA6, tutu = 0xFFFFC3DC, skin = 0xFFF1C7A4, cup = 0xFFF2EDE4, cupd = 0xFFCFC6B6;
        m.part(Part.BODY, 0, 13, 0)
                .cylinder(0, 0, 2.4f, 2.8f, 13, 21, 10, 1, i -> pink, pink, dpink)
                .cylinder(0, 0, 7, 5.6f, 12.4f, 14.4f, 18, 2, i -> i == 0 ? tutu : 0xFFFFD3E6, 0xFFFFD3E6, dpink)
                .rod(0, 21, 0, 0, 22.4f, 0, 1.4f, skin);
        m.part(Part.HEAD, 0, 22, 0)
                .cylinder(0, 0, 3.6f, 4.6f, 22, 29.5f, 16, 2, i -> i == 0 ? cupd : cup, 0xFF6B4026, cupd)
                .cylinder(0, 0, 4.2f, 4.3f, 29.5f, 29.9f, 16, 1, i -> 0xFFD8B98F, 0xFFE6CFA8, 0xFFD8B98F)
                .box(4.2f, 23.5f, -0.7f, 6.2f, 24.5f, 0.7f, cup).box(5.4f, 24.5f, -0.7f, 6.2f, 27.5f, 0.7f, cup)
                .box(4.2f, 27.5f, -0.7f, 6.2f, 28.4f, 0.7f, cup)
                .box(-1.4f, 27.6f, -4.7f, 1.4f, 27.9f, -4.4f, 0xFFF5B3C8);       // latte art peeking over the rim
        AvatarModel.Builder head = m.part(Part.HEAD, 0, 22, 0);
        for (int s = -1; s <= 1; s += 2) {
            head.box(s * 1.8f - 0.6f, 25.2f, -4.6f, s * 1.8f + 0.6f, 26.6f, -4.1f, 0xFF2B1C14)
                .box(s * 1.8f - 0.6f, 26.1f, -4.75f, s * 1.8f - 0.1f, 26.6f, -4.55f, WHITE)
                .box(s * 3 - 0.5f, 24, -4.5f, s * 3 + 0.5f, 24.6f, -4.1f, 0xFFF5B3C8);
        }
        head.box(-0.9f, 23.4f, -4.5f, 0.9f, 23.8f, -4.1f, 0xFFD9668A);
        for (int s = -1; s <= 1; s += 2) {
            Part arm = s < 0 ? Part.RIGHT_ARM : Part.LEFT_ARM;
            // Arms arched over the head, like a ballet fifth position.
            m.part(arm, s * 2.6f, 20.5f, 0)
                    .rod(s * 2.6f, 20.5f, 0, s * 7.6f, 27, -0.5f, 1.2f, skin)
                    .rod(s * 7.6f, 27, -0.5f, s * 1.2f, 33, -0.5f, 1.1f, skin);
            Part leg = s < 0 ? Part.RIGHT_LEG : Part.LEFT_LEG;
            m.part(leg, s * 1.2f, 13, 0)
                    .rod(s * 1.2f, 13, 0, s * 1.2f, 1.5f, 0, 1.5f, 0xFFF7C9DA)
                    .box(s * 1.2f - 0.9f, 0, -1.4f, s * 1.2f + 0.9f, 1.7f, 0.9f, dpink);
        }
        return m;
    }
}
