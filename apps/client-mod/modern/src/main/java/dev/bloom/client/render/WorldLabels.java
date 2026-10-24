package dev.bloom.client.render;

import dev.bloom.client.Mc;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.waypoint.WaypointStore;
import dev.bloom.client.game.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

import java.util.Locale;

/**
 * Labels drawn on the HUD at points in the world: waypoints (with edge arrows when off screen),
 * health indicators above players you can see, and your own name tag in third person.
 */
public final class WorldLabels {
    private static final Vector4f TMP = new Vector4f();

    private WorldLabels() {
    }

    /** Screen position of a world point: {x, y, onScreen?1:0, inFront?1:0}. */
    private static float[] project(Vec3 cam, double x, double y, double z, int w, int h) {
        TMP.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1f);
        WorldMatrices.MODEL_VIEW.transform(TMP);
        WorldMatrices.PROJECTION.transform(TMP);
        boolean front = TMP.w > 0.05f;
        float iw = 1f / Math.max(Math.abs(TMP.w), 1e-4f);
        float nx = TMP.x * iw, ny = TMP.y * iw;
        if (!front) {
            nx = -nx;
            ny = -ny;
        }
        float sx = (nx * 0.5f + 0.5f) * w;
        float sy = (1f - (ny * 0.5f + 0.5f)) * h;
        boolean on = front && sx >= 0 && sx <= w && sy >= 0 && sy <= h;
        return new float[]{sx, sy, on ? 1 : 0, front ? 1 : 0};
    }

    public static void render(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || Mc.hudHidden() || !BloomSettings.ENABLED.on()) return;
        Vec3 cam = Mc.camera().position();
        int w = g.guiWidth(), h = g.guiHeight();
        try {
            if (BloomSettings.WAYPOINTS.on()) waypoints(g, mc, cam, w, h);
            if (!BloomSettings.HEALTH_INDICATOR.is("off") || BloomSettings.OWN_NAMETAG.on()) players(g, mc, cam, w, h, partialTick);
        } catch (RuntimeException e) {
            dev.bloom.client.BloomClient.reportRenderError("world labels", e);
        }
    }

    // ------------------------------------------------------------------ waypoints

    private static void waypoints(GuiGraphics g, Minecraft mc, Vec3 cam, int w, int h) {
        Font font = mc.font;
        boolean shadow = BloomSettings.NAMETAG_SHADOW.on();
        float scale = BloomSettings.WAYPOINT_SCALE.value();
        for (WaypointStore.Waypoint wp : Waypoints.visible(mc)) {
            double dist = wp.distanceTo(cam.x, cam.y, cam.z);
            float[] p = project(cam, wp.x() + 0.5, wp.y() + 1.0, wp.z() + 0.5, w, h);
            int color = 0xFF000000 | wp.color();
            String label = wp.name();
            String sub = BloomSettings.WAYPOINT_DISTANCE.on() ? formatDistance(dist) : null;
            if (p[2] == 1) {
                int x = Math.round(p[0]), y = Math.round(p[1]);
                int r = Math.max(3, Math.round(4 * scale));
                diamond(g, x, y, r + 1, 0xC0000000);
                diamond(g, x, y, r, color);
                diamond(g, x, y, Math.max(1, r - 2), lighten(color));
                int tw = font.width(label);
                plate(g, x - tw / 2 - 3, y - r - 13, tw + 6, 11);
                g.drawString(font, label, x - tw / 2, y - r - 11, 0xFFFFFFFF, shadow);
                if (sub != null) {
                    int sw = font.width(sub);
                    g.drawString(font, sub, x - sw / 2, y + r + 3, 0xFFD0D6D2, shadow);
                }
            } else if (BloomSettings.WAYPOINT_OFFSCREEN.on()) {
                edgeArrow(g, font, p, w, h, color, label + (sub != null ? " " + sub : ""), shadow);
            }
        }
    }

    private static void edgeArrow(GuiGraphics g, Font font, float[] p, int w, int h, int color, String text, boolean shadow) {
        float cx = w / 2f, cy = h / 2f;
        float dx = p[0] - cx, dy = p[1] - cy;
        if (p[3] == 0 && Math.abs(dx) < 1 && Math.abs(dy) < 1) dy = 1; // straight behind: point down
        float margin = 18;
        float sx = (cx - margin) / Math.max(1e-3f, Math.abs(dx));
        float sy = (cy - margin) / Math.max(1e-3f, Math.abs(dy));
        float k = Math.min(sx, sy);
        int x = Math.round(cx + dx * k), y = Math.round(cy + dy * k);
        double ang = Math.atan2(dy, dx);
        // A small triangle pointing outward.
        for (int i = 0; i < 7; i++) {
            float t = i / 6f;
            int len = Math.round(7 * (1 - t));
            int px = Math.round(x + (float) Math.cos(ang) * (4 - i));
            int py = Math.round(y + (float) Math.sin(ang) * (4 - i));
            int ox = Math.round((float) -Math.sin(ang) * len / 2f), oy = Math.round((float) Math.cos(ang) * len / 2f);
            line(g, px - ox, py - oy, px + ox, py + oy, color);
        }
        int tw = font.width(text);
        int tx = Mth.clamp(x - tw / 2, 4, w - tw - 4);
        int ty = y + (dy > 0 ? -16 : 8);
        ty = Mth.clamp(ty, 4, h - 12);
        plate(g, tx - 3, ty - 2, tw + 6, 11);
        g.drawString(font, text, tx, ty, 0xFFFFFFFF, shadow);
    }

    // ------------------------------------------------------------------ players

    private static void players(GuiGraphics g, Minecraft mc, Vec3 cam, int w, int h, float pt) {
        Font font = mc.font;
        boolean shadow = BloomSettings.NAMETAG_SHADOW.on();
        double range = BloomSettings.NAMETAG_RANGE.value();
        String style = BloomSettings.HEALTH_INDICATOR.id();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.isInvisible() || p.isSpectator() || !p.isAlive()) continue;
            boolean self = p == mc.player;
            double px = Mth.lerp(pt, p.xo, p.getX()), py = Mth.lerp(pt, p.yo, p.getY()), pz = Mth.lerp(pt, p.zo, p.getZ());
            if (self) {
                if (firstPerson || !BloomSettings.OWN_NAMETAG.on()) continue;
                float[] s = project(cam, px, py + p.getBbHeight() + 0.5, pz, w, h);
                if (s[2] == 0) continue;
                String name = p.getName().getString();
                int tw = font.width(name);
                int x = Math.round(s[0]), y = Math.round(s[1]);
                plate(g, x - tw / 2 - 2, y - 9, tw + 4, 10);
                g.drawString(font, name, x - tw / 2, y - 8, 0xFFFFFFFF, shadow);
                continue;
            }
            if ("off".equals(style)) continue;
            double d = Math.sqrt((px - cam.x) * (px - cam.x) + (py - cam.y) * (py - cam.y) + (pz - cam.z) * (pz - cam.z));
            // Only players you can actually see: never through walls.
            if (d > range || !mc.player.hasLineOfSight(p)) continue;
            float[] s = project(cam, px, py + p.getBbHeight() + 0.85, pz, w, h);
            if (s[2] == 0) continue;
            health(g, font, Math.round(s[0]), Math.round(s[1]), p.getHealth(), p.getMaxHealth(), p.getAbsorptionAmount(), style, shadow);
        }
    }

    private static void health(GuiGraphics g, Font font, int x, int y, float hp, float max, float absorb, String style, boolean shadow) {
        max = Math.max(1f, max);
        float frac = Mth.clamp(hp / max, 0f, 1f);
        int col = healthColor(frac);
        switch (style) {
            case "number" -> {
                String s = String.format(Locale.ROOT, "%.1f", hp / 2f).replace(".0", "") + " ❤" + (absorb > 0 ? String.format(Locale.ROOT, " +%.0f", absorb / 2f) : "");
                int tw = font.width(s);
                plate(g, x - tw / 2 - 2, y - 9, tw + 4, 10);
                g.drawString(font, s, x - tw / 2, y - 8, col, shadow);
            }
            case "bar" -> {
                int bw = 36, bh = 4;
                int x0 = x - bw / 2, y0 = y - 6;
                g.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + bh + 1, 0xA0000000);
                g.fill(x0, y0, x0 + Math.round(bw * frac), y0 + bh, col);
                if (absorb > 0) g.fill(x0, y0, x0 + Math.round(bw * Mth.clamp(absorb / max, 0f, 1f)), y0 + 1, 0xFFFFD54F);
            }
            default -> { // hearts
                int total = Math.max(1, Math.min(10, Math.round(max / 2f)));
                int full = Mth.clamp(Math.round(hp / 2f), 0, total);
                StringBuilder on = new StringBuilder(), off = new StringBuilder();
                for (int i = 0; i < full; i++) on.append('❤');
                for (int i = full; i < total; i++) off.append('❤');
                int tw = font.width(on.toString() + off);
                int x0 = x - tw / 2;
                plate(g, x0 - 2, y - 9, tw + 4, 10);
                g.drawString(font, on.toString(), x0, y - 8, 0xFFFF4D5E, shadow);
                g.drawString(font, off.toString(), x0 + font.width(on.toString()), y - 8, 0xFF5A5F5C, shadow);
                if (absorb > 0) {
                    String a = "+" + Math.round(absorb / 2f);
                    g.drawString(font, a, x0 + tw + 3, y - 8, 0xFFFFD54F, shadow);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    static int healthColor(float frac) {
        // red → yellow → green
        float r = frac < 0.5f ? 1f : 1f - (frac - 0.5f) * 2f * 0.75f;
        float gr = frac < 0.5f ? 0.3f + frac * 1.4f : 1f;
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(gr * 220) << 8) | 0x50;
    }

    static String formatDistance(double d) {
        return d >= 1000 ? String.format(Locale.ROOT, "%.1fkm", d / 1000) : Math.round(d) + "m";
    }

    private static void plate(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x70000000);
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static int lighten(int argb) {
        int a = argb & 0xFF000000, r = (argb >> 16) & 255, gr = (argb >> 8) & 255, b = argb & 255;
        return a | ((r + (255 - r) / 2) << 16) | ((gr + (255 - gr) / 2) << 8) | (b + (255 - b) / 2);
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0 : i / (float) steps;
            int x = Math.round(x1 + (x2 - x1) * t), y = Math.round(y1 + (y2 - y1) * t);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
