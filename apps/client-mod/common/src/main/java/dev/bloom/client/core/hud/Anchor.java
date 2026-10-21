package dev.bloom.client.core.hud;

/**
 * Where a widget is attached on screen. Offsets are measured from this point in GUI pixels,
 * so layouts keep their shape when the window or GUI scale changes.
 */
public enum Anchor {
    TOP_LEFT(0, 0), TOP_CENTER(0.5f, 0), TOP_RIGHT(1, 0),
    MIDDLE_LEFT(0, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1, 0.5f),
    BOTTOM_LEFT(0, 1), BOTTOM_CENTER(0.5f, 1), BOTTOM_RIGHT(1, 1);

    /** 0 = left/top, 0.5 = centre, 1 = right/bottom. Also the widget's own pivot. */
    public final float fx;
    public final float fy;

    Anchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }

    /** Picks the anchor for a widget whose centre is at (cx, cy) on a w×h screen. */
    public static Anchor nearest(float cx, float cy, float w, float h) {
        int col = cx < w / 3f ? 0 : cx > w * 2f / 3f ? 2 : 1;
        int row = cy < h / 3f ? 0 : cy > h * 2f / 3f ? 2 : 1;
        return values()[row * 3 + col];
    }

    /** Screen-space left edge of a widget of width {@code ww}, given its offset. */
    public float left(float offsetX, float ww, float screenW) {
        return screenW * fx + offsetX - ww * fx;
    }

    public float top(float offsetY, float wh, float screenH) {
        return screenH * fy + offsetY - wh * fy;
    }

    /** Inverse of {@link #left}: offset that puts the widget's left edge at {@code left}. */
    public float offsetX(float left, float ww, float screenW) {
        return left - screenW * fx + ww * fx;
    }

    public float offsetY(float top, float wh, float screenH) {
        return top - screenH * fy + wh * fy;
    }
}
