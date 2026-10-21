package dev.bloom.client.core.sky;

/**
 * The 3×2 skybox atlas used by OptiFine/MCPatcher sky layers (and FabricSkyBoxes'
 * "square-textured" skyboxes), which BLOOD uses too:
 *
 * <pre>
 *   +--------+--------+--------+
 *   | bottom |  top   | south  |
 *   +--------+--------+--------+
 *   |  west  | north  |  east  |
 *   +--------+--------+--------+
 * </pre>
 *
 * Each face is drawn as the quad (-1,-1,-1)…(1,-1,1) on the bottom plane, with texture
 * corner (minU,minV) at (-1,-1,-1), then rotated into place. {@link #direction} gives the
 * world direction for any point of a face (s, t in 0..1 across the face, t downwards in the
 * image), which the panorama converter and the built-in sky generator use.
 */
public final class CubeLayout {
    public static final int BOTTOM = 0, NORTH = 1, SOUTH = 2, TOP = 3, EAST = 4, WEST = 5;

    /** {minU, minV, maxU, maxV} per face, in the face order above. */
    public static final float[][] UV = {
            {0f, 0f, 1f / 3f, 0.5f},          // bottom
            {1f / 3f, 0.5f, 2f / 3f, 1f},     // north
            {2f / 3f, 0f, 1f, 0.5f},          // south
            {1f / 3f, 0f, 2f / 3f, 0.5f},     // top
            {2f / 3f, 0.5f, 1f, 1f},          // east
            {0f, 0.5f, 1f / 3f, 1f}           // west
    };

    private CubeLayout() {
    }

    /** Unnormalised direction (x, y, z) for face-local coordinates. Minecraft axes: +X east, +Y up, +Z south. */
    public static void direction(int face, float s, float t, float[] out) {
        float a = 2f * s - 1f, b = 2f * t - 1f;
        switch (face) {
            case BOTTOM -> set(out, a, -1f, b);
            case NORTH -> set(out, a, -b, -1f);
            case SOUTH -> set(out, -a, -b, 1f);
            case TOP -> set(out, a, 1f, -b);
            case EAST -> set(out, 1f, -b, a);
            case WEST -> set(out, -1f, -b, -a);
            default -> throw new IllegalArgumentException("face " + face);
        }
    }

    private static void set(float[] o, float x, float y, float z) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
    }

    /** Null when the size is a usable atlas, otherwise a message for the player. */
    public static String checkAtlasSize(int w, int h) {
        if (w <= 0 || h <= 0) return "The image is empty.";
        if (w * 2 != h * 3) return "A skybox image must be 3 squares wide and 2 tall (for example 1536×1024). This one is " + w + "×" + h + ".";
        if (w % 3 != 0) return "The image width must divide evenly into 3 faces.";
        int face = w / 3;
        if (face < 16) return "The image is too small (each face must be at least 16 pixels).";
        if (face > 2048) return "The image is too large (each face can be at most 2048 pixels; this one is " + face + ").";
        return null;
    }
}
