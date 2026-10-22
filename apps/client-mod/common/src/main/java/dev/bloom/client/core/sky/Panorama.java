package dev.bloom.client.core.sky;

/** Converts an equirectangular (2:1) panorama into MOTION's 3×2 skybox atlas. */
public final class Panorama {
    private Panorama() {
    }

    public static boolean looksLikePanorama(int w, int h) {
        return w == h * 2 && h >= 64;
    }

    /**
     * @param faceSize output face size in pixels (the atlas is 3×faceSize by 2×faceSize)
     */
    public static RgbaImage toAtlas(RgbaImage pano, int faceSize) {
        RgbaImage out = new RgbaImage(faceSize * 3, faceSize * 2);
        float[] d = new float[3];
        for (int face = 0; face < 6; face++) {
            float[] uv = CubeLayout.UV[face];
            int x0 = Math.round(uv[0] * out.width), y0 = Math.round(uv[1] * out.height);
            for (int py = 0; py < faceSize; py++) {
                for (int px = 0; px < faceSize; px++) {
                    float s = (px + 0.5f) / faceSize, t = (py + 0.5f) / faceSize;
                    CubeLayout.direction(face, s, t, d);
                    float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                    float dx = d[0] / len, dy = d[1] / len, dz = d[2] / len;
                    // Longitude 0 at north (-Z), increasing towards east (+X).
                    double lon = Math.atan2(dx, -dz);
                    double lat = Math.asin(Math.max(-1, Math.min(1, dy)));
                    float u = (float) ((lon / (2 * Math.PI) + 0.5) * pano.width);
                    float v = (float) ((0.5 - lat / Math.PI) * pano.height);
                    out.set(x0 + px, y0 + py, pano.sampleWrapU(u, v));
                }
            }
        }
        return out;
    }

    /** Face size for converting a panorama: a quarter of its width, at most 1024. */
    public static int faceSizeFor(int panoWidth) {
        int f = Math.min(1024, panoWidth / 4);
        return Math.max(16, f);
    }
}
