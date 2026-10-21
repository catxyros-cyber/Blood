package dev.bloom.client.gui;

import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.visual.Themes;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * The Halloween themes' backdrop decorations (embers, bats, mist, wisps, webs, moons, pumpkins),
 * drawn behind BLOOD's menus with plain rectangles and the circle sprite: no extra textures and
 * only a few hundred fills per frame. Respects "Theme decorations", "Reduce motion" and the
 * interface effects level, and fades with the menu.
 */
public final class ThemeDecor {
    private static final int N = 48;
    private static final float[][] P = new float[N][5]; // x, y (0..1), speed, phase, size
    private static boolean seeded;
    private static float time;

    private ThemeDecor() {
    }

    private static void seed() {
        Random r = new Random(1031);
        for (float[] p : P) {
            p[0] = r.nextFloat();
            p[1] = r.nextFloat();
            p[2] = 0.4f + r.nextFloat() * 0.6f;
            p[3] = r.nextFloat() * 6.283f;
            p[4] = r.nextFloat();
        }
        seeded = true;
    }

    public static boolean active() {
        return BloomSettings.THEME_DECOR.on() && Anim.decorations()
                && Themes.get(BloomSettings.THEME.id()).decor() != Themes.Decor.NONE;
    }

    /** Draws the current theme's decoration over (x, y, w, h). {@code a} = menu opacity 0..1. */
    public static void render(GuiGraphics g, int x, int y, int w, int h, float dt, float a) {
        if (!active() || a <= 0.01f) return;
        if (!seeded) seed();
        boolean moving = Anim.motion();
        if (moving) time += dt;
        Themes.Info t = Themes.get(BloomSettings.THEME.id());
        int accent = Theme.palette().accent() & 0xFFFFFF;
        switch (t.decor()) {
            case EMBERS -> embers(g, x, y, w, h, dt, a, accent, 0xFFD27A, moving);
            case BLOOD_MOON -> {
                moon(g, x + w - Math.min(w, h) / 5, y + Math.min(w, h) / 5, Math.min(w, h) / 9, 0x7A0A14, 0xC8142A, a);
                embers(g, x, y, w, h, dt, a * 0.8f, 0xD8142A, 0xFF5A48, moving);
            }
            case PUMPKINS -> {
                embers(g, x, y, w, h, dt, a, 0xF07A1A, 0xFFC27A, moving);
                for (int i = 0; i < 3; i++) pumpkin(g, x + 14 + i * 22, y + h - 18, 2, a * 0.55f);
                pumpkin(g, x + w - 34, y + h - 18, 2, a * 0.55f);
            }
            case MIST -> mist(g, x, y, w, h, a);
            case BATS -> {
                moon(g, x + w - Math.min(w, h) / 6, y + Math.min(w, h) / 6, Math.min(w, h) / 12, 0x5A4A7A, 0xE8E0FF, a * 0.8f);
                bats(g, x, y, w, h, a);
            }
            case WISPS -> wisps(g, x, y, w, h, a);
            case WEBS -> {
                web(g, x, y, Math.min(w, h) / 4, 1, 1, a);
                web(g, x + w, y, Math.min(w, h) / 5, -1, 1, a);
            }
            case ECLIPSE -> eclipse(g, x + w - Math.min(w, h) / 5, y + Math.min(w, h) / 5, Math.min(w, h) / 10, a);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    private static void embers(GuiGraphics g, int x, int y, int w, int h, float dt, float a, int c1, int c2, boolean moving) {
        for (float[] p : P) {
            if (moving) {
                p[1] -= dt * 0.03f * p[2];
                p[0] += dt * 0.006f * (float) Math.sin(time * 0.8f + p[3]);
                if (p[1] < -0.02f) {
                    p[1] = 1.02f;
                    p[0] = (float) Math.random();
                }
            }
            float flicker = 0.55f + 0.45f * (float) Math.sin(time * (2 + p[2] * 3) + p[3]);
            int px = x + (int) (p[0] * w), py = y + (int) (p[1] * h);
            int s = p[4] > 0.85f ? 2 : 1;
            int col = Theme.lerp(0xFF000000 | c1, 0xFF000000 | c2, p[4]);
            float fade = Math.min(1f, Math.min(p[1] * 4f, (1 - p[1]) * 6f));
            g.fill(px, py, px + s, py + s, Theme.alpha(col, a * 0.55f * flicker * Math.max(0, fade)));
        }
    }

    private static void moon(GuiGraphics g, int cx, int cy, int r, int glow, int disc, float a) {
        Ui.circle(g, cx, cy, r * 3, Theme.alpha(0xFF000000 | glow, a * 0.10f));
        Ui.circle(g, cx, cy, (int) (r * 1.7f), Theme.alpha(0xFF000000 | glow, a * 0.18f));
        Ui.circle(g, cx, cy, r, Theme.alpha(0xFF000000 | disc, a * 0.55f));
        // a few darker "maria"
        Ui.circle(g, cx - r / 3, cy - r / 4, r / 3, Theme.alpha(0xFF000000 | glow, a * 0.25f));
        Ui.circle(g, cx + r / 4, cy + r / 3, r / 4, Theme.alpha(0xFF000000 | glow, a * 0.2f));
    }

    private static void eclipse(GuiGraphics g, int cx, int cy, int r, float a) {
        Ui.circle(g, cx, cy, r * 3, Theme.alpha(0xFF7A0A14, a * 0.10f));
        Ui.circle(g, cx, cy, (int) (r * 1.35f), Theme.alpha(0xFFE04050, a * 0.35f));
        Ui.circle(g, cx, cy, (int) (r * 1.12f), Theme.alpha(0xFFFFC0B0, a * 0.35f));
        Ui.circle(g, cx, cy, r, Theme.alpha(0xFF050203, a * 0.95f));
    }

    /** 8×7 pixel pumpkin: 1 = body, 2 = stem, 3 = carved face. */
    private static final String[] PUMPKIN_ART = {
            "...22...",
            ".111111.",
            "11311311",
            "11111111",
            "13333331",
            "11333311",
            ".111111."};

    private static void pumpkin(GuiGraphics g, int x, int y, int s, float a) {
        for (int row = 0; row < PUMPKIN_ART.length; row++) {
            String line = PUMPKIN_ART[row];
            for (int col = 0; col < line.length(); col++) {
                int c = switch (line.charAt(col)) {
                    case '1' -> 0xFFE0701A;
                    case '2' -> 0xFF3A6A2A;
                    case '3' -> 0xFF2A0E02;
                    default -> 0;
                };
                if (c != 0) g.fill(x + col * s, y + row * s, x + col * s + s, y + row * s + s, Theme.alpha(c, a));
            }
        }
    }

    private static void mist(GuiGraphics g, int x, int y, int w, int h, float a) {
        int bands = 7;
        for (int i = 0; i < bands; i++) {
            float fy = 0.55f + i * 0.065f;
            float drift = (float) Math.sin(time * 0.15f + i * 1.7f) * w * 0.08f;
            int by = y + (int) (fy * h), bh = Math.max(6, h / 18);
            int bx = x + (int) drift - w / 6, bw = w + w / 3;
            g.fillGradient(bx, by, bx + bw, by + bh, Theme.alpha(0xFF6E8A80, 0f), Theme.alpha(0xFF6E8A80, a * 0.09f));
            g.fillGradient(bx, by + bh, bx + bw, by + bh * 2, Theme.alpha(0xFF6E8A80, a * 0.09f), Theme.alpha(0xFF6E8A80, 0f));
        }
    }

    private static final String[] BAT = {"x.....x", "xx.x.xx", ".xxxxx.", "..x.x.."};

    private static void bats(GuiGraphics g, int x, int y, int w, int h, float a) {
        for (int i = 0; i < 6; i++) {
            float[] p = P[i];
            float bx = (p[0] + time * 0.012f * p[2]) % 1f, by = 0.08f + p[1] * 0.35f + 0.02f * (float) Math.sin(time * 2 + p[3]);
            boolean flap = Math.sin(time * 9 * p[2] + p[3]) > 0;
            int px = x + (int) (bx * w), py = y + (int) (by * h);
            for (int r = 0; r < BAT.length; r++) {
                String line = flap && r == 0 ? "......." : BAT[r];
                for (int c = 0; c < line.length(); c++) {
                    if (line.charAt(c) == 'x') g.fill(px + c, py + r, px + c + 1, py + r + 1, Theme.alpha(0xFF050308, a * 0.8f));
                }
            }
        }
    }

    private static void wisps(GuiGraphics g, int x, int y, int w, int h, float a) {
        for (int k = 0; k < 4; k++) {
            float[] p = P[k];
            float base = 0.25f + 0.15f * k;
            for (int i = 0; i < 70; i++) {
                float u = i / 70f;
                float wx = (u + time * 0.02f * p[2] + p[0]) % 1f;
                float wy = base + 0.05f * (float) Math.sin(u * 9 + time * 0.6f + p[3]) + 0.02f * (float) Math.sin(u * 23 + p[3]);
                float fade = (float) Math.sin(u * Math.PI);
                int col = Theme.lerp(0xFFFF7A1A, 0xFFFFF4E6, fade);
                int px = x + (int) (wx * w), py = y + (int) (wy * h);
                g.fill(px, py, px + 2, py + 1, Theme.alpha(col, a * 0.22f * fade));
            }
        }
    }

    /** A corner web: radial threads and spiral rings, drawn as 1 px dots. dx/dy = direction into the screen. */
    private static void web(GuiGraphics g, int ox, int oy, int r, int dx, int dy, float a) {
        int col = Theme.alpha(0xFFD8D8D8, a * 0.28f);
        int spokes = 7;
        for (int s = 0; s < spokes; s++) {
            double ang = (s + 0.5) / spokes * Math.PI / 2;
            double cx = Math.cos(ang) * dx, cy = Math.sin(ang) * dy;
            for (int d = 0; d < r; d += 2) g.fill(ox + (int) (cx * d), oy + (int) (cy * d), ox + (int) (cx * d) + 1, oy + (int) (cy * d) + 1, col);
        }
        for (int ring = 1; ring <= 5; ring++) {
            double rr = r * ring / 5.5;
            for (int s = 0; s < spokes - 1; s++) {
                double a0 = (s + 0.5) / spokes * Math.PI / 2, a1 = (s + 1.5) / spokes * Math.PI / 2;
                for (int k = 0; k <= 8; k++) {
                    double t = k / 8.0, ang = a0 + (a1 - a0) * t;
                    double sag = 1 - 0.08 * Math.sin(t * Math.PI); // threads sag between spokes
                    int px = ox + (int) (Math.cos(ang) * rr * sag * dx), py = oy + (int) (Math.sin(ang) * rr * sag * dy);
                    g.fill(px, py, px + 1, py + 1, col);
                }
            }
        }
    }
}
