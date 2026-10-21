package dev.bloom.client.core.sky;

/**
 * Renders a perspective view of a 3×2 sky atlas on the CPU, for the menu's live preview. It samples
 * the atlas exactly the way the in-game renderer maps it ({@link CubeFormats#sampleAtlas}), so the
 * preview shows the real result: same texture, orientation and seams (none). Pure Java.
 */
public final class SkyView {
    private SkyView() {
    }

    /**
     * @param yawDeg   0 = looking north, 90 = east
     * @param pitchDeg positive = looking up
     * @param fovDeg   vertical field of view
     * @param out      w*h pixels, same packing as {@code atlas}
     */
    public static void render(int[] atlas, int aw, int ah, float yawDeg, float pitchDeg, float fovDeg, int w, int h, int[] out) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double tan = Math.tan(Math.toRadians(fovDeg) / 2);
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        // Camera basis: forward f, right r, up u (Minecraft: north = -Z, east = +X).
        double fx = sy * cp, fy = sp, fz = -cy * cp;
        double rx = cy, rz = sy;
        double ux = -sy * sp, uy = cp, uz = cy * sp;
        for (int j = 0; j < h; j++) {
            double v = (1 - 2 * (j + 0.5) / h) * tan;
            for (int i = 0; i < w; i++) {
                double u = (2 * (i + 0.5) / w - 1) * tan * w / h;
                float dx = (float) (fx + rx * u + ux * v);
                float dy = (float) (fy + uy * v);
                float dz = (float) (fz + rz * u + uz * v);
                out[j * w + i] = CubeFormats.sampleAtlas(atlas, aw, ah, dx, dy, dz);
            }
        }
    }
}
