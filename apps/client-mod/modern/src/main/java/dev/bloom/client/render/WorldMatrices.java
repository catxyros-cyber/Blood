package dev.bloom.client.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Per-frame values captured at the start of LevelRenderer#renderLevel (render thread only). */
public final class WorldMatrices {
    /** Camera rotation (view matrix), no translation. */
    public static final Matrix4f MODEL_VIEW = new Matrix4f();
    public static final Matrix4f PROJECTION = new Matrix4f();
    private static final float[] FOG = {0.7f, 0.8f, 1f};
    public static float partialTick;

    private WorldMatrices() {
    }

    public static void capture(Matrix4f modelView, Matrix4f projection, Vector4f fogColor, float partial) {
        MODEL_VIEW.set(modelView);
        PROJECTION.set(projection);
        if (fogColor != null) {
            FOG[0] = fogColor.x();
            FOG[1] = fogColor.y();
            FOG[2] = fogColor.z();
        }
        partialTick = partial;
    }

    /** The world's fog colour this frame (r, g, b). Don't modify. */
    public static float[] fogColor() {
        return FOG;
    }
}
