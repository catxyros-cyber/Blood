package dev.bloom.client.render;

import org.joml.Matrix4f;

/** The camera matrices of the frame being rendered, captured at the start of LevelRenderer#renderLevel. */
public final class WorldMatrices {
    public static final Matrix4f MODEL_VIEW = new Matrix4f();
    public static final Matrix4f PROJECTION = new Matrix4f();

    private WorldMatrices() {
    }

    public static void capture(Matrix4f modelView, Matrix4f projection) {
        MODEL_VIEW.set(modelView);
        PROJECTION.set(projection);
    }
}
