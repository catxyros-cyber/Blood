package dev.bloom.client.core.sky;

/**
 * Reads a PNG's size from its header without decoding it, so oversized or fake images are
 * rejected before any decoder touches them.
 */
public final class PngInfo {
    public static final int MAX_DIMENSION = 8192;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public final int width;
    public final int height;

    private PngInfo(int w, int h) {
        width = w;
        height = h;
    }

    /** Returns null when the data isn't a PNG with sane dimensions. */
    public static PngInfo read(byte[] data) {
        if (data == null || data.length < 33) return null;
        for (int i = 0; i < 8; i++) if (data[i] != SIGNATURE[i]) return null;
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') return null;
        int w = readInt(data, 16), h = readInt(data, 20);
        if (w <= 0 || h <= 0 || w > MAX_DIMENSION || h > MAX_DIMENSION) return null;
        return new PngInfo(w, h);
    }

    private static int readInt(byte[] d, int o) {
        return ((d[o] & 255) << 24) | ((d[o + 1] & 255) << 16) | ((d[o + 2] & 255) << 8) | (d[o + 3] & 255);
    }
}
