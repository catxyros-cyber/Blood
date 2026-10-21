package dev.bloom.client.core.sky;

/** A plain ARGB image (0xAARRGGBB per pixel), independent of Minecraft's NativeImage. */
public final class RgbaImage {
    public final int width;
    public final int height;
    public final int[] argb;

    public RgbaImage(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > 64L * 1024 * 1024) throw new IllegalArgumentException("Bad image size");
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
    }

    public int get(int x, int y) {
        return argb[y * width + x];
    }

    public void set(int x, int y, int c) {
        argb[y * width + x] = c;
    }

    /** Bilinear sample with u wrapping and v clamped (u, v in pixels). */
    public int sampleWrapU(float u, float v) {
        float fu = u - 0.5f, fv = Math.max(0, Math.min(height - 1, v - 0.5f));
        int x0 = (int) Math.floor(fu), y0 = (int) Math.floor(fv);
        float tx = fu - x0, ty = fv - y0;
        int x1 = x0 + 1, y1 = Math.min(height - 1, y0 + 1);
        x0 = Math.floorMod(x0, width);
        x1 = Math.floorMod(x1, width);
        int c00 = get(x0, y0), c10 = get(x1, y0), c01 = get(x0, y1), c11 = get(x1, y1);
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            float a = (c00 >>> shift) & 255, b = (c10 >>> shift) & 255, c = (c01 >>> shift) & 255, d = (c11 >>> shift) & 255;
            float top = a + (b - a) * tx, bot = c + (d - c) * tx;
            int v2 = Math.round(top + (bot - top) * ty);
            out |= (Math.max(0, Math.min(255, v2))) << shift;
        }
        return out;
    }
}
