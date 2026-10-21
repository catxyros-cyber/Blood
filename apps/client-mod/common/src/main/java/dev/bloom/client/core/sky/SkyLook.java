package dev.bloom.client.core.sky;

/**
 * The skybox "look" (brightness, saturation, colour tint) applied to one pixel. Shared by the
 * in-game texture processing and the menu preview so both show the same colours. Pure Java.
 */
public final class SkyLook {
    private SkyLook() {
    }

    public static boolean neutral(float brightness, float saturation, float tintStrength) {
        return Math.abs(brightness - 1f) < 1e-3 && Math.abs(saturation - 1f) < 1e-3 && tintStrength < 1e-3;
    }

    /**
     * @param px  a pixel; {@code abgr} = true for NativeImage/OpenGL order (R in the low byte), false for ARGB
     * @param tintRgb tint as 0xRRGGBB
     */
    public static int apply(int px, boolean abgr, float brightness, float saturation, int tintRgb, float tintStrength) {
        int a = px >>> 24;
        float r, g, b;
        if (abgr) {
            r = (px & 255) / 255f;
            g = ((px >> 8) & 255) / 255f;
            b = ((px >> 16) & 255) / 255f;
        } else {
            r = ((px >> 16) & 255) / 255f;
            g = ((px >> 8) & 255) / 255f;
            b = (px & 255) / 255f;
        }
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        r = luma + (r - luma) * saturation;
        g = luma + (g - luma) * saturation;
        b = luma + (b - luma) * saturation;
        if (tintStrength > 0) {
            float tr = ((tintRgb >> 16) & 255) / 255f, tg = ((tintRgb >> 8) & 255) / 255f, tb = (tintRgb & 255) / 255f;
            float k = 0.35f + 0.65f * luma;
            r += (tr * k - r) * tintStrength;
            g += (tg * k - g) * tintStrength;
            b += (tb * k - b) * tintStrength;
        }
        int ir = clamp(r * brightness), ig = clamp(g * brightness), ib = clamp(b * brightness);
        return abgr ? (a << 24) | (ib << 16) | (ig << 8) | ir : (a << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }
}
