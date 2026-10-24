package dev.bloom.client.core.avatar;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A 3D avatar built from smooth shapes (ellipsoids, tubes, turned profiles) with per-vertex normals
 * and colours, and parts that animate like a player's. Pure Java: shapes are authored in "pixels"
 * (16 = one block) with y up, the ground at 0 and the front facing -z; {@link #pose} turns them into
 * Minecraft's player model space (blocks, y down, feet at y = 1.5), ready to draw.
 */
public final class AvatarModel {
    public enum Part { BODY, HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /** Floats per quad in {@link Posed#geometry()}: 4 vertices × (x, y, z, nx, ny, nz). */
    public static final int STRIDE = 24;

    /**
     * Posed geometry: {@link #STRIDE} floats per quad, and one ARGB colour per vertex (colours are
     * kept as ints: packed into floats, bright colours would be NaN bit patterns and get mangled).
     */
    public record Posed(float[] geometry, int[] colors) {
        public int quads() {
            return colors.length / 4;
        }
    }

    /** A stored quad: positions and normals, and its four vertex colours. */
    private record Quad(float[] v, int[] c) {
    }

    /** Colour of a surface point, given its position and normal (y-up pixel space). */
    public interface Shader {
        int color(float x, float y, float z, float nx, float ny, float nz);

        static Shader solid(int argb) {
            return (x, y, z, nx, ny, nz) -> argb;
        }
    }

    private final String id;
    private final Map<Part, List<Quad>> parts = new EnumMap<>(Part.class);
    private final Map<Part, float[]> pivots = new EnumMap<>(Part.class);

    AvatarModel(String id) {
        this.id = id;
        for (Part p : Part.values()) {
            parts.put(p, new ArrayList<>());
            pivots.put(p, new float[]{0, 0, 0});
        }
    }

    public String id() {
        return id;
    }

    public int quadCount() {
        int n = 0;
        for (List<Quad> l : parts.values()) n += l.size();
        return n;
    }

    // ------------------------------------------------------------------ colour helpers

    static int rgb(int hex) {
        return 0xFF000000 | hex;
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int shade(int argb, float f) {
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * f));
        int g = Math.min(255, Math.round(((argb >> 8) & 255) * f));
        int b = Math.min(255, Math.round((argb & 255) * f));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Cheap smooth pseudo-noise in [0,1] for grain, scales and mottling. */
    static float noise(float x, float y, float z) {
        double v = Math.sin(x * 1.7 + y * 0.31) * 0.5 + Math.sin(y * 2.3 + z * 1.1) * 0.3 + Math.sin(z * 1.9 + x * 0.7 + y * 0.2) * 0.2;
        return (float) (v * 0.5 + 0.5);
    }

    // ------------------------------------------------------------------ authoring

    final class Builder {
        private final Part part;

        Builder(Part part, float px, float py, float pz) {
            this.part = part;
            pivots.put(part, new float[]{px, py, pz});
        }

        private void vtx(float[] q, int i, float x, float y, float z, float nx, float ny, float nz) {
            int o = i * 6;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-6f) {
                nx = 0;
                ny = 1;
                nz = 0;
                l = 1;
            }
            q[o] = x;
            q[o + 1] = y;
            q[o + 2] = z;
            q[o + 3] = nx / l;
            q[o + 4] = ny / l;
            q[o + 5] = nz / l;
        }

        /** One quad from 4 points (each x,y,z,nx,ny,nz), coloured by the shader. */
        private void quad(float[][] p, Shader s) {
            float[] q = new float[STRIDE];
            int[] c = new int[4];
            for (int i = 0; i < 4; i++) {
                float[] v = p[i];
                vtx(q, i, v[0], v[1], v[2], v[3], v[4], v[5]);
                c[i] = s.color(v[0], v[1], v[2], v[3], v[4], v[5]);
            }
            parts.get(part).add(new Quad(q, c));
        }

        /** Axis-aligned box with flat faces (small details: teeth, soles, panels). */
        Builder box(float x0, float y0, float z0, float x1, float y1, float z1, Shader s) {
            float[][][] faces = {
                    {{x0, y1, z0, 0, 1, 0}, {x1, y1, z0, 0, 1, 0}, {x1, y1, z1, 0, 1, 0}, {x0, y1, z1, 0, 1, 0}},
                    {{x0, y0, z1, 0, -1, 0}, {x1, y0, z1, 0, -1, 0}, {x1, y0, z0, 0, -1, 0}, {x0, y0, z0, 0, -1, 0}},
                    {{x0, y0, z0, 0, 0, -1}, {x1, y0, z0, 0, 0, -1}, {x1, y1, z0, 0, 0, -1}, {x0, y1, z0, 0, 0, -1}},
                    {{x1, y0, z1, 0, 0, 1}, {x0, y0, z1, 0, 0, 1}, {x0, y1, z1, 0, 0, 1}, {x1, y1, z1, 0, 0, 1}},
                    {{x0, y0, z1, -1, 0, 0}, {x0, y0, z0, -1, 0, 0}, {x0, y1, z0, -1, 0, 0}, {x0, y1, z1, -1, 0, 0}},
                    {{x1, y0, z0, 1, 0, 0}, {x1, y0, z1, 1, 0, 0}, {x1, y1, z1, 1, 0, 0}, {x1, y1, z0, 1, 0, 0}},
            };
            for (float[][] f : faces) quad(f, s);
            return this;
        }

        Builder box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
            return box(x0, y0, z0, x1, y1, z1, Shader.solid(argb));
        }

        /** Smooth ellipsoid. */
        Builder ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, int stacks, int slices, Shader s) {
            for (int i = 0; i < stacks; i++) {
                double t0 = Math.PI * i / stacks - Math.PI / 2, t1 = Math.PI * (i + 1) / stacks - Math.PI / 2;
                for (int j = 0; j < slices; j++) {
                    double a0 = 2 * Math.PI * j / slices, a1 = 2 * Math.PI * (j + 1) / slices;
                    float[][] p = {e(cx, cy, cz, rx, ry, rz, t0, a1), e(cx, cy, cz, rx, ry, rz, t0, a0), e(cx, cy, cz, rx, ry, rz, t1, a0), e(cx, cy, cz, rx, ry, rz, t1, a1)};
                    quad(p, s);
                }
            }
            return this;
        }

        Builder ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, Shader s) {
            return ellipsoid(cx, cy, cz, rx, ry, rz, 10, 16, s);
        }

        Builder sphere(float cx, float cy, float cz, float r, int argb) {
            return ellipsoid(cx, cy, cz, r, r, r, 8, 12, Shader.solid(argb));
        }

        private float[] e(float cx, float cy, float cz, float rx, float ry, float rz, double lat, double lon) {
            float ux = (float) (Math.cos(lat) * Math.cos(lon)), uy = (float) Math.sin(lat), uz = (float) (Math.cos(lat) * Math.sin(lon));
            return new float[]{cx + ux * rx, cy + uy * ry, cz + uz * rz, ux / rx, uy / ry, uz / rz};
        }

        /**
         * A surface of revolution around the vertical line (cx, cz): the profile is radius {@code rs[i]}
         * at height {@code ys[i]}. Ends with a non-zero radius are closed with flat caps.
         */
        Builder lathe(float cx, float cz, float[] ys, float[] rs, int slices, Shader s) {
            int n = ys.length;
            float[] nr = new float[n], ny = new float[n];
            for (int i = 0; i < n; i++) {
                int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
                float dy = ys[b] - ys[a], dr = rs[b] - rs[a];
                float l = (float) Math.sqrt(dy * dy + dr * dr);
                nr[i] = l < 1e-6f ? 1 : dy / l;
                ny[i] = l < 1e-6f ? 0 : -dr / l;
            }
            for (int i = 0; i < n - 1; i++) {
                for (int j = 0; j < slices; j++) {
                    double a0 = 2 * Math.PI * j / slices, a1 = 2 * Math.PI * (j + 1) / slices;
                    float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                    float[][] p = {
                            {cx + c1 * rs[i], ys[i], cz + s1 * rs[i], c1 * nr[i], ny[i], s1 * nr[i]},
                            {cx + c0 * rs[i], ys[i], cz + s0 * rs[i], c0 * nr[i], ny[i], s0 * nr[i]},
                            {cx + c0 * rs[i + 1], ys[i + 1], cz + s0 * rs[i + 1], c0 * nr[i + 1], ny[i + 1], s0 * nr[i + 1]},
                            {cx + c1 * rs[i + 1], ys[i + 1], cz + s1 * rs[i + 1], c1 * nr[i + 1], ny[i + 1], s1 * nr[i + 1]}};
                    quad(p, s);
                }
            }
            if (rs[n - 1] > 0) cap(cx, cz, ys[n - 1], rs[n - 1], slices, 1, s);
            if (rs[0] > 0) cap(cx, cz, ys[0], rs[0], slices, -1, s);
            return this;
        }

        private void cap(float cx, float cz, float y, float r, int slices, int dir, Shader s) {
            int rings = 3;
            for (int k = 0; k < rings; k++) {
                float ra = r * k / rings, rb = r * (k + 1) / rings;
                for (int j = 0; j < slices; j++) {
                    double a0 = 2 * Math.PI * j / slices, a1 = 2 * Math.PI * (j + 1) / slices;
                    float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                    float[][] p = dir > 0
                            ? new float[][]{{cx + c0 * ra, y, cz + s0 * ra, 0, 1, 0}, {cx + c1 * ra, y, cz + s1 * ra, 0, 1, 0}, {cx + c1 * rb, y, cz + s1 * rb, 0, 1, 0}, {cx + c0 * rb, y, cz + s0 * rb, 0, 1, 0}}
                            : new float[][]{{cx + c1 * ra, y, cz + s1 * ra, 0, -1, 0}, {cx + c0 * ra, y, cz + s0 * ra, 0, -1, 0}, {cx + c0 * rb, y, cz + s0 * rb, 0, -1, 0}, {cx + c1 * rb, y, cz + s1 * rb, 0, -1, 0}};
                    quad(p, s);
                }
            }
        }

        /** A smooth tube through {@code pts} (x,y,z each) with a radius per point and rounded ends. */
        Builder tube(float[][] pts, float[] radii, int sides, Shader s) {
            int n = pts.length;
            float[] prevN = null;
            float[][] frames = new float[n][];
            for (int i = 0; i < n; i++) {
                float[] a = pts[Math.max(0, i - 1)], b = pts[Math.min(n - 1, i + 1)];
                float tx = b[0] - a[0], ty = b[1] - a[1], tz = b[2] - a[2];
                float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                tx /= tl; ty /= tl; tz /= tl;
                float rx = Math.abs(tz) < 0.9f ? 0 : 1, ry = 0, rz = Math.abs(tz) < 0.9f ? 1 : 0;
                float nx = ty * rz - tz * ry, ny = tz * rx - tx * rz, nz = tx * ry - ty * rx;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nl; ny /= nl; nz /= nl;
                if (prevN != null && nx * prevN[0] + ny * prevN[1] + nz * prevN[2] < 0) {
                    nx = -nx; ny = -ny; nz = -nz;
                }
                float bx = ty * nz - tz * ny, by = tz * nx - tx * nz, bz = tx * ny - ty * nx;
                prevN = new float[]{nx, ny, nz};
                frames[i] = new float[]{nx, ny, nz, bx, by, bz};
            }
            for (int i = 0; i < n - 1; i++) {
                for (int j = 0; j < sides; j++) {
                    float[][] p = new float[4][];
                    int[][] idx = {{i, j + 1}, {i, j}, {i + 1, j}, {i + 1, j + 1}};
                    for (int k = 0; k < 4; k++) {
                        int pi = idx[k][0];
                        double ang = 2 * Math.PI * idx[k][1] / sides;
                        float[] f = frames[pi];
                        float c = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                        float ox = f[0] * c + f[3] * sn, oy = f[1] * c + f[4] * sn, oz = f[2] * c + f[5] * sn;
                        float r = radii[pi];
                        p[k] = new float[]{pts[pi][0] + ox * r, pts[pi][1] + oy * r, pts[pi][2] + oz * r, ox, oy, oz};
                    }
                    quad(p, s);
                }
            }
            ellipsoid(pts[0][0], pts[0][1], pts[0][2], radii[0], radii[0], radii[0], 6, sides, s);
            ellipsoid(pts[n - 1][0], pts[n - 1][1], pts[n - 1][2], radii[n - 1], radii[n - 1], radii[n - 1], 6, sides, s);
            return this;
        }

        /** Straight rounded limb from a to b. */
        Builder limb(float x0, float y0, float z0, float x1, float y1, float z1, float r0, float r1, Shader s) {
            return tube(new float[][]{{x0, y0, z0}, {(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2}, {x1, y1, z1}}, new float[]{r0, (r0 + r1) / 2, r1}, 10, s);
        }

        /**
         * A flat shape (a fan from its first point) extruded by {@code thickness} along the normal of its
         * plane: fins, wings, teeth.
         */
        Builder slab(float[][] poly, float thickness, Shader s) {
            int n = poly.length;
            float ax = poly[1][0] - poly[0][0], ay = poly[1][1] - poly[0][1], az = poly[1][2] - poly[0][2];
            float bx = poly[2][0] - poly[0][0], by = poly[2][1] - poly[0][1], bz = poly[2][2] - poly[0][2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= nl; ny /= nl; nz /= nl;
            float h = thickness / 2;
            float[][] top = new float[n][], bot = new float[n][];
            for (int i = 0; i < n; i++) {
                top[i] = new float[]{poly[i][0] + nx * h, poly[i][1] + ny * h, poly[i][2] + nz * h};
                bot[i] = new float[]{poly[i][0] - nx * h, poly[i][1] - ny * h, poly[i][2] - nz * h};
            }
            for (int i = 1; i < n - 1; i++) {
                quad(new float[][]{v(top[0], nx, ny, nz), v(top[i], nx, ny, nz), v(top[i + 1], nx, ny, nz), v(top[i + 1], nx, ny, nz)}, s);
                quad(new float[][]{v(bot[0], -nx, -ny, -nz), v(bot[i + 1], -nx, -ny, -nz), v(bot[i], -nx, -ny, -nz), v(bot[i], -nx, -ny, -nz)}, s);
            }
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float ex = poly[j][0] - poly[i][0], ey = poly[j][1] - poly[i][1], ez = poly[j][2] - poly[i][2];
                float sx = ey * nz - ez * ny, sy = ez * nx - ex * nz, sz = ex * ny - ey * nx;
                quad(new float[][]{v(bot[i], sx, sy, sz), v(bot[j], sx, sy, sz), v(top[j], sx, sy, sz), v(top[i], sx, sy, sz)}, s);
            }
            return this;
        }

        private float[] v(float[] p, float nx, float ny, float nz) {
            return new float[]{p[0], p[1], p[2], nx, ny, nz};
        }
    }

    Builder part(Part p, float px, float py, float pz) {
        return new Builder(p, px, py, pz);
    }

    // ------------------------------------------------------------------ posing

    /**
     * Joint angles in radians, using Minecraft's model-space conventions (as HumanoidModel sets them):
     * head pitch/yaw, and an x rotation (swing) for each limb.
     */
    public record Angles(float headPitch, float headYaw, float rightArm, float leftArm, float rightLeg, float leftLeg, float bob) {
        public static final Angles REST = new Angles(0, 0, 0, 0, 0, 0, 0);

        /** Walking like a player: legs and arms swing in opposition; {@code attack} raises the right arm. */
        public static Angles walking(float walkPos, float walkSpeed, float headPitchDeg, float headYawDeg, float attack, float age) {
            float c = (float) Math.cos(walkPos * 0.6662f), o = (float) Math.cos(walkPos * 0.6662f + Math.PI);
            float speed = Math.min(1f, walkSpeed);
            float right = o * speed + (float) Math.sin(age * 0.067f) * 0.05f;
            right -= (float) Math.sin(Math.min(1f, Math.max(0f, attack)) * Math.PI) * 1.3f;
            return new Angles((float) Math.toRadians(headPitchDeg), (float) Math.toRadians(headYawDeg),
                    right, c * speed - (float) Math.sin(age * 0.067f) * 0.05f, c * 1.4f * speed, o * 1.4f * speed,
                    (float) Math.abs(Math.sin(walkPos * 0.6662f)) * speed * 0.6f);
        }
    }

    /** The model posed in Minecraft's player model space (blocks, y down, feet at y = 1.5). */
    public Posed pose(Angles a) {
        float[] out = new float[quadCount() * STRIDE];
        int[] colors = new int[quadCount() * 4];
        int k = 0, ci = 0;
        for (Part p : Part.values()) {
            float[] pv = pivots.get(p);
            // Model-space angles → our y-up space: both rotations change sign (the y axis is flipped).
            float rx = 0, ry = 0;
            switch (p) {
                case HEAD -> { rx = -a.headPitch(); ry = -a.headYaw(); }
                case RIGHT_ARM -> rx = -a.rightArm();
                case LEFT_ARM -> rx = -a.leftArm();
                case RIGHT_LEG -> rx = -a.rightLeg();
                case LEFT_LEG -> rx = -a.leftLeg();
                default -> { }
            }
            float bob = (p == Part.RIGHT_LEG || p == Part.LEFT_LEG) ? 0 : a.bob();
            float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx), cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
            for (Quad quad : parts.get(p)) {
                float[] q = quad.v();
                System.arraycopy(quad.c(), 0, colors, ci, 4);
                ci += 4;
                for (int i = 0; i < 4; i++) {
                    int o = i * 6;
                    float x = q[o] - pv[0], y = q[o + 1] - pv[1], z = q[o + 2] - pv[2];
                    // Pitch (about x), then yaw (about y): like a head nodding, then turning.
                    float y1 = y * cx - z * sx, z1 = y * sx + z * cx;
                    float x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
                    out[k++] = (x2 + pv[0]) / 16f;
                    out[k++] = 1.5f - (y1 + pv[1] + bob) / 16f;
                    out[k++] = (z2 + pv[2]) / 16f;
                    float nx = q[o + 3], ny = q[o + 4], nz = q[o + 5];
                    float ny1 = ny * cx - nz * sx, nz1 = ny * sx + nz * cx;
                    out[k++] = nx * cy + nz1 * sy;
                    out[k++] = -ny1;
                    out[k++] = -nx * sy + nz1 * cy;
                }
            }
        }
        return new Posed(out, colors);
    }
}
