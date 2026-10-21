package dev.bloom.client.core.util;

/** ARGB colour helpers. Colours are packed as 0xAARRGGBB ints, components 0-255. */
public final class Colors {
    private Colors() {}

    public static int a(int c) {
        return (c >>> 24) & 0xFF;
    }

    public static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int b(int c) {
        return c & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    public static int argbF(float a, float r, float g, float b) {
        return argb(Math.round(a * 255f), Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
    }

    public static int rgb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int withAlpha(int c, int alpha) {
        return (clamp255(alpha) << 24) | (c & 0xFFFFFF);
    }

    public static int withAlphaF(int c, float alpha) {
        return withAlpha(c, Math.round(alpha * 255f));
    }

    public static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    public static int lerp(int c1, int c2, float t) {
        t = MathUtil.clamp01(t);
        return argb(
                Math.round(a(c1) + (a(c2) - a(c1)) * t),
                Math.round(r(c1) + (r(c2) - r(c1)) * t),
                Math.round(g(c1) + (g(c2) - g(c1)) * t),
                Math.round(b(c1) + (b(c2) - b(c1)) * t));
    }

    /** Multiply RGB by a factor (alpha unchanged). */
    public static int scale(int c, float f) {
        return argb(a(c), Math.round(r(c) * f), Math.round(g(c) * f), Math.round(b(c) * f));
    }

    /** Multiply two colours component-wise. */
    public static int multiply(int c1, int c2) {
        return argb(a(c1) * a(c2) / 255, r(c1) * r(c2) / 255, g(c1) * g(c2) / 255, b(c1) * b(c2) / 255);
    }

    /** Hue 0-360, saturation and value 0-1. */
    public static int hsv(float h, float s, float v) {
        return hsva(h, s, v, 1f);
    }

    public static int hsva(float h, float s, float v, float a) {
        h = ((h % 360f) + 360f) % 360f;
        s = MathUtil.clamp01(s);
        v = MathUtil.clamp01(v);
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return argbF(a, r + m, g + m, b + m);
    }

    /** Returns {hue 0-360, saturation 0-1, value 0-1}. */
    public static float[] toHsv(int c) {
        float r = r(c) / 255f, g = g(c) / 255f, b = b(c) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60 * (((g - b) / d) % 6);
        else if (max == g) h = 60 * (((b - r) / d) + 2);
        else h = 60 * (((r - g) / d) + 4);
        if (h < 0) h += 360;
        float s = max == 0 ? 0 : d / max;
        return new float[] {h, s, max};
    }

    /** Adjust saturation around luminance (0 = greyscale, 1 = unchanged, 2 = vivid). */
    public static int saturate(int c, float amount) {
        float r = r(c), g = g(c), b = b(c);
        float l = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        return argb(a(c), Math.round(l + (r - l) * amount), Math.round(l + (g - l) * amount), Math.round(l + (b - l) * amount));
    }

    /** Relative luminance 0-1 (sRGB approximation). */
    public static float luminance(int c) {
        return (0.2126f * r(c) + 0.7152f * g(c) + 0.0722f * b(c)) / 255f;
    }

    public static String toHex(int c, boolean withAlpha) {
        return withAlpha ? String.format("#%08X", c) : String.format("#%06X", c & 0xFFFFFF);
    }

    /** Parses #RGB, #RRGGBB or #AARRGGBB (the # is optional). Returns null if invalid. */
    public static Integer parseHex(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 3) {
                int r = Integer.parseInt(s.substring(0, 1), 16), g = Integer.parseInt(s.substring(1, 2), 16), b = Integer.parseInt(s.substring(2, 3), 16);
                return argb(255, r * 17, g * 17, b * 17);
            }
            if (s.length() == 6) return 0xFF000000 | Integer.parseInt(s, 16);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }
}
