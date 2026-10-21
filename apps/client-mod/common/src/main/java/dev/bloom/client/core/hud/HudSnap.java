package dev.bloom.client.core.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapping for the HUD editor: while dragging, a widget's edges and centre snap to the
 * screen edges, screen centre lines, other widgets' edges/centres and (optionally) a grid.
 * Returns guide lines to draw so the player can see what it snapped to.
 */
public final class HudSnap {
    public record Rect(float x, float y, float w, float h) {
        public float right() {
            return x + w;
        }

        public float bottom() {
            return y + h;
        }

        public float cx() {
            return x + w / 2f;
        }

        public float cy() {
            return y + h / 2f;
        }
    }

    /** A guide line: vertical when {@code vertical}, at {@code pos} on the other axis. */
    public record Guide(boolean vertical, float pos) {
    }

    public record Result(float x, float y, List<Guide> guides) {
    }

    public static final float THRESHOLD = 4f;
    public static final float EDGE_MARGIN = 2f;

    private HudSnap() {
    }

    /**
     * @param proposed where the widget would be without snapping
     * @param others   other visible widgets
     * @param grid     grid size in GUI pixels, or 0 for no grid
     */
    public static Result snap(Rect proposed, List<Rect> others, float screenW, float screenH, float grid) {
        List<Float> xTargets = new ArrayList<>();
        List<Float> yTargets = new ArrayList<>();
        xTargets.add(EDGE_MARGIN);
        xTargets.add(screenW - EDGE_MARGIN);
        xTargets.add(screenW / 2f);
        yTargets.add(EDGE_MARGIN);
        yTargets.add(screenH - EDGE_MARGIN);
        yTargets.add(screenH / 2f);
        for (Rect o : others) {
            xTargets.add(o.x());
            xTargets.add(o.right());
            xTargets.add(o.cx());
            yTargets.add(o.y());
            yTargets.add(o.bottom());
            yTargets.add(o.cy());
        }
        List<Guide> guides = new ArrayList<>();
        float[] xs = {proposed.x(), proposed.cx(), proposed.right()};
        float[] ys = {proposed.y(), proposed.cy(), proposed.bottom()};
        float nx = proposed.x();
        float ny = proposed.y();

        Snap sx = best(xs, xTargets);
        if (sx != null) {
            nx += sx.delta;
            guides.add(new Guide(true, sx.target));
        } else if (grid > 0) {
            nx = Math.round(nx / grid) * grid;
        }
        Snap sy = best(ys, yTargets);
        if (sy != null) {
            ny += sy.delta;
            guides.add(new Guide(false, sy.target));
        } else if (grid > 0) {
            ny = Math.round(ny / grid) * grid;
        }
        // Never let a widget leave the screen.
        nx = Math.max(0, Math.min(screenW - proposed.w(), nx));
        ny = Math.max(0, Math.min(screenH - proposed.h(), ny));
        return new Result(nx, ny, guides);
    }

    private record Snap(float delta, float target) {
    }

    private static Snap best(float[] points, List<Float> targets) {
        Snap best = null;
        float bestDist = THRESHOLD + 0.001f;
        for (float p : points) {
            for (float t : targets) {
                float d = Math.abs(t - p);
                if (d < bestDist) {
                    bestDist = d;
                    best = new Snap(t - p, t);
                }
            }
        }
        return best;
    }
}
