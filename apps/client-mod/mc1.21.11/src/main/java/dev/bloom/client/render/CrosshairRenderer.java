package dev.bloom.client.render;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.GameType;

/**
 * BLOOD's custom crosshair (HUD → Crosshair). Drawn in place of the vanilla crosshair from
 * {@code GuiMixin}; it follows the same visibility rules (first person only, vanilla handles
 * spectator mode and the F3 axis crosshair). If anything goes wrong it switches itself off
 * for the session and Minecraft's crosshair comes back.
 */
public final class CrosshairRenderer {
    private static boolean failed;

    private CrosshairRenderer() {
    }

    /** Returns true when BLOOD drew (or deliberately hid) the crosshair, so vanilla's is skipped. */
    public static boolean render(GuiGraphics g) {
        if (failed || !BloomClient.ready() || !BloomSettings.ENABLED.on() || !BloomSettings.CROSSHAIR.on()) return false;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return false;
            if (dev.bloom.client.game.ClientCompat.debugScreenShown() && mc.player != null && !mc.player.isReducedDebugInfo()
                    && !mc.options.reducedDebugInfo().get()) return false;
            if (!mc.options.getCameraType().isFirstPerson()) return true;
            draw(g, g.guiWidth() / 2, g.guiHeight() / 2);
            return true;
        } catch (RuntimeException e) {
            failed = true;
            Log.warn("Custom crosshair failed; using Minecraft's crosshair until restart", e);
            return false;
        }
    }

    private static void draw(GuiGraphics g, int cx, int cy) {
        int size = BloomSettings.CROSSHAIR_SIZE.intValue();
        int gap = BloomSettings.CROSSHAIR_GAP.intValue();
        int t = Math.max(1, BloomSettings.CROSSHAIR_THICKNESS.intValue());
        int alpha = Math.round(BloomSettings.CROSSHAIR_OPACITY.value() * 255f);
        int color = (alpha << 24) | BloomSettings.CROSSHAIR_COLOR.rgb();
        int outline = (Math.round(alpha * 0.6f) << 24);
        boolean border = BloomSettings.CROSSHAIR_OUTLINE.on();
        String style = BloomSettings.CROSSHAIR_STYLE.id();
        // Lines are t pixels wide and centred on the screen centre (cx, cy).
        int a = cx - t / 2, b = cy - t / 2;
        int[][] rects = switch (style) {
            case "dot" -> new int[][]{{a, b, a + t, b + t}};
            case "square" -> {
                int r = size + gap;
                yield new int[][]{{cx - r, cy - r, cx + r + 1, cy - r + t}, {cx - r, cy + r + 1 - t, cx + r + 1, cy + r + 1},
                        {cx - r, cy - r, cx - r + t, cy + r + 1}, {cx + r + 1 - t, cy - r, cx + r + 1, cy + r + 1}};
            }
            default -> {
                int[] left = {a - gap - size, b, a - gap, b + t};
                int[] right = {a + t + gap, b, a + t + gap + size, b + t};
                int[] up = {a, b - gap - size, a + t, b - gap};
                int[] down = {a, b + t + gap, a + t, b + t + gap + size};
                int[] dot = {a, b, a + t, b + t};
                yield switch (style) {
                    case "t" -> new int[][]{left, right, down};
                    case "cross_dot" -> new int[][]{left, right, up, down, dot};
                    default -> new int[][]{left, right, up, down};
                };
            }
        };
        if (border) for (int[] r : rects) g.fill(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, outline);
        for (int[] r : rects) g.fill(r[0], r[1], r[2], r[3], color);
    }
}
