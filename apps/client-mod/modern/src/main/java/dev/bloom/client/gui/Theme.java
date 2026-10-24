package dev.bloom.client.gui;

import dev.bloom.client.core.settings.BloomSettings;

/** Menu colour schemes. All colours are 0xAARRGGBB. */
public final class Theme {
    public record Palette(int window, int sidebar, int surface, int surfaceHover, int surfaceActive, int border,
                          int text, int textDim, int textFaint, int accent, int onAccent, int warn, int danger, int ok) {
    }

    // window, sidebar, surface, surfaceHover, surfaceActive, border, text, textDim, textFaint, accent, onAccent, warn, danger, ok
    private static final Palette MOTION = new Palette(0xF0070A08, 0xF00B0F0D, 0xFF111713, 0xFF18211C, 0xFF203027, 0xFF263A2F,
            0xFFF2F7F4, 0xFFA9B8B0, 0xFF6E7D75, 0xFF39FF88, 0xFF03140A, 0xFFF2C46B, 0xFFFF6B6B, 0xFF8BE3A6);
    private static final Palette CRIMSON = new Palette(0xF0120C0E, 0xF0181012, 0xFF1F1517, 0xFF2A1C1F, 0xFF372428, 0xFF3D2A2E,
            0xFFF6EFEF, 0xFFBCADAE, 0xFF85767A, 0xFFE3192F, 0xFFFFFFFF, 0xFFF2C46B, 0xFFFF6B6B, 0xFF8BE3A6);
    private static final Palette OBSIDIAN = new Palette(0xF2060606, 0xF20B0B0B, 0xFF121212, 0xFF1B1A1A, 0xFF252323, 0xFF2D2A2A,
            0xFFF4F1F1, 0xFFAAA4A4, 0xFF716B6B, 0xFFFF1F3D, 0xFFFFFFFF, 0xFFF2C46B, 0xFFFF6B6B, 0xFF8BE3A6);
    private static final Palette ASH = new Palette(0xF0151618, 0xF01B1C1E, 0xFF232426, 0xFF2D2E31, 0xFF38393C, 0xFF3F4144,
            0xFFF1F1F2, 0xFFB4B6B9, 0xFF7B7D81, 0xFFDC2F3C, 0xFFFFFFFF, 0xFFF0C86E, 0xFFFF7A7A, 0xFF8BE3A6);
    private static final Palette EMBER = new Palette(0xF0140E0B, 0xF01A120E, 0xFF231913, 0xFF2E2119, 0xFF3A2A20, 0xFF433024,
            0xFFF8F0EA, 0xFFC5AFA1, 0xFF8A7466, 0xFFFF4A1F, 0xFF1F0A03, 0xFFF0C86E, 0xFFFF7A7A, 0xFF8BE3A6);
    private static final Palette MONO = new Palette(0xF0141414, 0xF01B1B1B, 0xFF232323, 0xFF2D2D2D, 0xFF383838, 0xFF3C3C3C,
            0xFFF2F2F2, 0xFFB5B5B5, 0xFF7E7E7E, 0xFFE6E6E6, 0xFF151515, 0xFFE8C36A, 0xFFFF7A7A, 0xFF9BE3A6);

    // Halloween themes
    private static final Palette BLOOD_MOON = new Palette(0xF0090506, 0xF00E0608, 0xFF160A0C, 0xFF210F12, 0xFF2E1418, 0xFF3A161C,
            0xFFF6ECEC, 0xFFC0A6A8, 0xFF856A6E, 0xFFD8142A, 0xFFFFFFFF, 0xFFF2C46B, 0xFFFF6B6B, 0xFF8BE3A6);
    private static final Palette PUMPKIN_NIGHT = new Palette(0xF0121110, 0xF0181614, 0xFF201D1A, 0xFF2A2521, 0xFF36302A, 0xFF403830,
            0xFFF8F0E6, 0xFFC8B8A4, 0xFF8A7A68, 0xFFF07A1A, 0xFF1A0C02, 0xFFF2C46B, 0xFFFF6B6B, 0xFF8BE3A6);
    private static final Palette HAUNTED_FOREST = new Palette(0xF00A0F0D, 0xF00E1512, 0xFF141D19, 0xFF1B2722, 0xFF23332C, 0xFF2A3C34,
            0xFFEAF2EE, 0xFFA8BCB2, 0xFF6E8278, 0xFF4FB07A, 0xFF06140C, 0xFFF2C46B, 0xFFFF6B6B, 0xFF8BE3A6);
    private static final Palette MIDNIGHT = new Palette(0xF00C0814, 0xF0110C1C, 0xFF181226, 0xFF211932, 0xFF2B2140, 0xFF33284A,
            0xFFF2ECFF, 0xFFB8ACD4, 0xFF7C6E9A, 0xFF9B5CFF, 0xFFFFFFFF, 0xFFF2C46B, 0xFFFF6B6B, 0xFF8BE3A6);
    private static final Palette GHOSTFIRE = new Palette(0xF0080706, 0xF00D0B0A, 0xFF151210, 0xFF1E1A16, 0xFF28221C, 0xFF332B24,
            0xFFFFF6EE, 0xFFD0C0B0, 0xFF8E7C6C, 0xFFFF8A3A, 0xFF1A0A02, 0xFFF2C46B, 0xFFFF6B6B, 0xFF8BE3A6);
    private static final Palette SPIDERWEB = new Palette(0xF00B0B0B, 0xF0101010, 0xFF181818, 0xFF222222, 0xFF2C2C2C, 0xFF3A3A3A,
            0xFFF5F5F5, 0xFFB0B0B0, 0xFF707070, 0xFFE8E8E8, 0xFF101010, 0xFFE8C36A, 0xFFFF7A7A, 0xFF9BE3A6);
    private static final Palette CRIMSON_ECLIPSE = new Palette(0xF0100607, 0xF014080A, 0xFF1C0C0E, 0xFF261114, 0xFF32161A, 0xFF3E1A20,
            0xFFF8EEEE, 0xFFC8A8AC, 0xFF8A6A70, 0xFFB0101F, 0xFFFFFFFF, 0xFFF2C46B, 0xFFFF6B6B, 0xFF8BE3A6);

    private Theme() {
    }

    public static Palette palette() {
        return palette(BloomSettings.THEME.id());
    }

    /** Unknown ids (including the old Bloom themes) fall back to Crimson. */
    public static Palette palette(String themeId) {
        return switch (themeId) {
            case "motion" -> MOTION;
            case "obsidian" -> OBSIDIAN;
            case "ash" -> ASH;
            case "ember" -> EMBER;
            case "mono" -> MONO;
            case "blood_moon" -> BLOOD_MOON;
            case "pumpkin_night" -> PUMPKIN_NIGHT;
            case "haunted_forest" -> HAUNTED_FOREST;
            case "midnight_halloween" -> MIDNIGHT;
            case "ghostfire" -> GHOSTFIRE;
            case "spiderweb" -> SPIDERWEB;
            case "crimson_eclipse" -> CRIMSON_ECLIPSE;
            default -> CRIMSON;
        };
    }

    /** The accent colour (RGB) used across menus and HUD. */
    public static int accent() {
        if (BloomSettings.ACCENT_FROM_THEME.on()) return palette().accent() & 0xFFFFFF;
        return BloomSettings.ACCENT.rgb();
    }

    public static int accentArgb() {
        return 0xFF000000 | accent();
    }

    /** Readable text colour on top of the accent. */
    public static int onAccent() {
        int c = accent();
        float l = (0.2126f * ((c >> 16) & 255) + 0.7152f * ((c >> 8) & 255) + 0.0722f * (c & 255)) / 255f;
        return l > 0.55f ? 0xFF140C0E : 0xFFFFFFFF;
    }

    public static int alpha(int argb, float a) {
        int base = argb >>> 24;
        return ((int) (base * Math.max(0, Math.min(1, a))) << 24) | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
