package dev.bloom.client.core.sky;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Other cubemap formats, converted to MOTION's 3×2 atlas ({@link CubeLayout}), plus atlas resizing
 * and direction sampling. Pure Java (no Minecraft classes), unit tested.
 *
 * <p>Cubemap faces follow the OpenGL convention (+X right, +Y up, +Z front), as used by most skybox
 * packs and cubemap crosses. Minecraft's north (-Z) is the cubemap's +Z ("front"), so a Minecraft
 * direction (x, y, z) samples the cubemap at (x, y, -z). This was checked against a sky supplied as
 * both a cross and a 360° panorama: the two match to within ~1/255.
 */
public final class CubeFormats {
    /** Cubemap faces in OpenGL order. */
    public static final String[] FACES = {"px", "nx", "py", "ny", "pz", "nz"};
    private static final String[] LABELS = {"right (+X)", "left (-X)", "top (+Y)", "bottom (-Y)", "front (+Z)", "back (-Z)"};

    /** Position (column, row) of each face in a horizontal cross (4×3 faces). */
    private static final int[][] CROSS = {{2, 1}, {0, 1}, {1, 0}, {1, 2}, {1, 1}, {3, 1}};

    private CubeFormats() {
    }

    // ------------------------------------------------------------------ detection

    /** A horizontal cubemap cross: 4 faces wide, 3 tall. */
    public static boolean looksLikeCross(int w, int h) {
        return w > 0 && w * 3 == h * 4 && w % 4 == 0 && w / 4 >= 16 && w / 4 <= 2048;
    }

    /** Face id ("px"...) for a file name, or null. Accepts px/posx/right, nx/negx/left, etc. */
    public static String faceFor(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        n = n.substring(slash + 1).replaceAll("\\.(png)$", "");
        String[][] names = {
                {"px", "posx", "positivex", "right", "rt", "east"},
                {"nx", "negx", "negativex", "left", "lf", "west"},
                {"py", "posy", "positivey", "top", "up", "tp"},
                {"ny", "negy", "negativey", "bottom", "down", "dn", "bt"},
                {"pz", "posz", "positivez", "front", "ft", "north"},
                {"nz", "negz", "negativez", "back", "bk", "south"}};
        for (int f = 0; f < 6; f++) {
            for (String alias : names[f]) {
                if (n.equals(alias) || n.endsWith("_" + alias) || n.endsWith("-" + alias) || n.endsWith(" " + alias)) return FACES[f];
            }
        }
        return null;
    }

    /** Problems with a set of six faces (missing, not square, mismatched sizes). Empty = usable. */
    public static List<String> checkFaces(Map<String, int[]> sizes) {
        List<String> problems = new ArrayList<>();
        int size = -1;
        for (int f = 0; f < 6; f++) {
            int[] wh = sizes.get(FACES[f]);
            if (wh == null) {
                problems.add("Missing the " + LABELS[f] + " face.");
                continue;
            }
            if (wh[0] != wh[1]) problems.add("The " + LABELS[f] + " face isn't square (" + wh[0] + "×" + wh[1] + ").");
            else if (wh[0] < 16 || wh[0] > 2048) problems.add("The " + LABELS[f] + " face must be 16–2048 px (it's " + wh[0] + ").");
            else if (size < 0) size = wh[0];
            else if (size != wh[0]) problems.add("All faces must be the same size (" + LABELS[f] + " is " + wh[0] + ", others " + size + ").");
        }
        return problems;
    }

    // ------------------------------------------------------------------ conversion

    /** Converts a horizontal cross into the 3×2 atlas, at the cross's own face size. */
    public static RgbaImage crossToAtlas(RgbaImage cross) {
        if (!looksLikeCross(cross.width, cross.height)) throw new IllegalArgumentException("not a 4×3 cross");
        int f = cross.width / 4;
        Map<String, RgbaImage> faces = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            RgbaImage face = new RgbaImage(f, f);
            int ox = CROSS[i][0] * f, oy = CROSS[i][1] * f;
            for (int y = 0; y < f; y++) System.arraycopy(cross.argb, (oy + y) * cross.width + ox, face.argb, y * f, f);
            faces.put(FACES[i], face);
        }
        return facesToAtlas(faces);
    }

    /** Converts six OpenGL-convention faces (see {@link #FACES}) into the 3×2 atlas. */
    public static RgbaImage facesToAtlas(Map<String, RgbaImage> faces) {
        Map<String, int[]> sizes = new LinkedHashMap<>();
        for (Map.Entry<String, RgbaImage> e : faces.entrySet()) sizes.put(e.getKey(), new int[]{e.getValue().width, e.getValue().height});
        List<String> problems = checkFaces(sizes);
        if (!problems.isEmpty()) throw new IllegalArgumentException(String.join(" ", problems));
        RgbaImage[] gl = new RgbaImage[6];
        for (int i = 0; i < 6; i++) gl[i] = faces.get(FACES[i]);
        int n = gl[0].width;
        RgbaImage out = new RgbaImage(n * 3, n * 2);
        float[] d = new float[3];
        for (int face = 0; face < 6; face++) {
            float[] uv = CubeLayout.UV[face];
            int x0 = Math.round(uv[0] * out.width), y0 = Math.round(uv[1] * out.height);
            for (int py = 0; py < n; py++) {
                for (int px = 0; px < n; px++) {
                    // Edge texels on the cube edges (the renderer insets faces by half a texel).
                    CubeLayout.direction(face, n == 1 ? 0.5f : px / (float) (n - 1), n == 1 ? 0.5f : py / (float) (n - 1), d);
                    out.set(x0 + px, y0 + py, sampleGl(gl, d[0], d[1], -d[2]));
                }
            }
        }
        return out;
    }

    /** Bilinear sample of OpenGL-convention faces by an OpenGL direction. */
    static int sampleGl(RgbaImage[] gl, float x, float y, float z) {
        float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        int face;
        float sc, tc, ma;
        if (ax >= ay && ax >= az) {
            ma = ax;
            if (x > 0) {
                face = 0;
                sc = -z;
            } else {
                face = 1;
                sc = z;
            }
            tc = -y;
        } else if (ay >= az) {
            ma = ay;
            sc = x;
            if (y > 0) {
                face = 2;
                tc = z;
            } else {
                face = 3;
                tc = -z;
            }
        } else {
            ma = az;
            tc = -y;
            if (z > 0) {
                face = 4;
                sc = x;
            } else {
                face = 5;
                sc = -x;
            }
        }
        RgbaImage img = gl[face];
        float s = (sc / ma + 1f) * 0.5f * img.width - 0.5f, t = (tc / ma + 1f) * 0.5f * img.height - 0.5f;
        return bilinear(img.argb, img.width, 0, 0, img.width, s, t);
    }

    // ------------------------------------------------------------------ sampling an atlas

    /**
     * Bilinear sample of a 3×2 atlas by a Minecraft direction, exactly as the renderer shows it
     * (half-texel face inset, no bleeding between faces). Works for any 4-channel packing.
     */
    public static int sampleAtlas(int[] px, int width, int height, float x, float y, float z) {
        int n = width / 3;
        float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        int face;
        float a, b;
        if (ay >= ax && ay >= az) {
            if (y < 0) {
                face = CubeLayout.BOTTOM;
                a = x / ay;
                b = z / ay;
            } else {
                face = CubeLayout.TOP;
                a = x / ay;
                b = -z / ay;
            }
        } else if (az >= ax) {
            if (z < 0) {
                face = CubeLayout.NORTH;
                a = x / az;
                b = -y / az;
            } else {
                face = CubeLayout.SOUTH;
                a = -x / az;
                b = -y / az;
            }
        } else if (x >= 0) {
            face = CubeLayout.EAST;
            a = z / ax;
            b = -y / ax;
        } else {
            face = CubeLayout.WEST;
            a = -z / ax;
            b = -y / ax;
        }
        float s = (a + 1f) * 0.5f * (n - 1), t = (b + 1f) * 0.5f * (n - 1);
        float[] uv = CubeLayout.UV[face];
        int ox = Math.round(uv[0] * width), oy = Math.round(uv[1] * height);
        return bilinear(px, width, ox, oy, n, s, t);
    }

    /** Bilinear filter inside one n×n square at (ox, oy) of an image, clamped to that square. */
    static int bilinear(int[] px, int stride, int ox, int oy, int n, float s, float t) {
        s = Math.max(0f, Math.min(n - 1f, s));
        t = Math.max(0f, Math.min(n - 1f, t));
        int x0 = (int) s, y0 = (int) t;
        int x1 = Math.min(x0 + 1, n - 1), y1 = Math.min(y0 + 1, n - 1);
        float fx = s - x0, fy = t - y0;
        int c00 = px[(oy + y0) * stride + ox + x0], c10 = px[(oy + y0) * stride + ox + x1];
        int c01 = px[(oy + y1) * stride + ox + x0], c11 = px[(oy + y1) * stride + ox + x1];
        int out = 0;
        for (int sh = 0; sh < 32; sh += 8) {
            float top = ((c00 >>> sh) & 255) * (1 - fx) + ((c10 >>> sh) & 255) * fx;
            float bot = ((c01 >>> sh) & 255) * (1 - fx) + ((c11 >>> sh) & 255) * fx;
            out |= (Math.round(top * (1 - fy) + bot * fy) & 255) << sh;
        }
        return out;
    }

    // ------------------------------------------------------------------ resizing

    /**
     * Shrinks a 3×2 atlas to {@code face} px faces with an area average, one face at a time (so faces
     * never bleed into each other). Returns the input unchanged if it's already that small.
     * Pixels are any 4×8-bit packing; the result has the same packing.
     */
    public static int[] shrinkAtlas(int[] px, int width, int height, int face, int[] sizeOut) {
        int n = width / 3;
        if (face >= n || face < 1) {
            sizeOut[0] = width;
            sizeOut[1] = height;
            return px;
        }
        int ow = face * 3, oh = face * 2;
        int[] out = new int[ow * oh];
        float scale = n / (float) face;
        for (int fy = 0; fy < 2; fy++) {
            for (int fx = 0; fx < 3; fx++) {
                for (int y = 0; y < face; y++) {
                    int sy0 = (int) (y * scale), sy1 = Math.max(sy0 + 1, (int) ((y + 1) * scale));
                    for (int x = 0; x < face; x++) {
                        int sx0 = (int) (x * scale), sx1 = Math.max(sx0 + 1, (int) ((x + 1) * scale));
                        long[] sum = new long[4];
                        int count = 0;
                        for (int sy = sy0; sy < sy1; sy++) {
                            int row = (fy * n + sy) * width + fx * n;
                            for (int sx = sx0; sx < sx1; sx++) {
                                int c = px[row + sx];
                                sum[0] += c & 255;
                                sum[1] += (c >>> 8) & 255;
                                sum[2] += (c >>> 16) & 255;
                                sum[3] += c >>> 24;
                                count++;
                            }
                        }
                        int c = 0;
                        for (int k = 0; k < 4; k++) c |= (int) ((sum[k] + count / 2) / count) << (8 * k);
                        out[(fy * face + y) * ow + fx * face + x] = c;
                    }
                }
            }
        }
        sizeOut[0] = ow;
        sizeOut[1] = oh;
        return out;
    }
}
