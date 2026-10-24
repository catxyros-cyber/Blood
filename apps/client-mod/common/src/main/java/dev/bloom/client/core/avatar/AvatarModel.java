package dev.bloom.client.core.avatar;

import java.util.ArrayList;
import java.util.List;

/**
 * A 3D avatar built from smooth parametric surfaces (turned profiles, lofted curves, blades and
 * ellipsoids) with per-vertex normals and colours, baked ambient occlusion, and parts that animate
 * like a player's. Pure Java: shapes are authored in "pixels" (16 = one block) with y up, the ground
 * at 0 and the front facing -z (the right arm is at -x); {@link #pose} turns them into Minecraft's
 * player model space (blocks, y down, feet at y = 1.5), ready to draw.
 */
public final class AvatarModel {
    /**
     * What drives a group of shapes. BODY, HEAD, arms and legs move like a player's; TAIL sways
     * side to side; PROP spins about the z axis (propellers); SPINE bends a long body smoothly,
     * the front following the head's yaw and the back swaying like a tail.
     */
    public enum Part { BODY, HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, TAIL, PROP, SPINE }

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

    /** A stored quad: positions and normals, its four vertex colours, and the shape it came from. */
    private record Quad(float[] v, int[] c, int shape) {
    }

    /** A rough sphere standing in for a shape when baking ambient occlusion. */
    private record Occluder(float x, float y, float z, float r, int shape) {
    }

    /** Colour of a surface point, given its position and normal (y-up pixel space). */
    public interface Shader {
        int color(float x, float y, float z, float nx, float ny, float nz);

        static Shader solid(int argb) {
            return (x, y, z, nx, ny, nz) -> argb;
        }
    }

    /** A parametric surface: u runs around, v along; writes x, y, z into {@code out}. */
    interface Surf {
        void at(float u, float v, float[] out);
    }

    /** Radius multiplier at an angle around a shape and a position along it (grooves, ruffles, scales). */
    interface Wobble {
        float at(float angle, float t);
    }

    private static final class Segment {
        final Part part;
        final float[] pivot;
        final List<Quad> quads = new ArrayList<>();
        /** For TAIL: shapes behind pivot z + rampLen bend fully. For SPINE: front and rear bend lengths. */
        float rampFront, rampRear, frontZ, rearZ, frontGain = 0.35f;

        Segment(Part part, float[] pivot) {
            this.part = part;
            this.pivot = pivot;
        }
    }

    private final String id;
    private final List<Segment> segments = new ArrayList<>();
    private final List<Occluder> occluders = new ArrayList<>();
    private int shapes;
    private int quadCount;
    private float hover;

    AvatarModel(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public int quadCount() {
        return quadCount;
    }

    /** True for avatars that float above the ground (they bob gently in place). */
    public boolean hovers() {
        return hover > 0;
    }

    /** Makes the avatar float, bobbing by {@code amplitude} pixels. */
    AvatarModel hover(float amplitude) {
        hover = amplitude;
        return this;
    }

    /** Moves everything built so far by (dx, dy, dz) pixels. */
    AvatarModel shift(float dx, float dy, float dz) {
        for (Segment seg : segments) {
            seg.pivot[0] += dx;
            seg.pivot[1] += dy;
            seg.pivot[2] += dz;
            seg.frontZ += dz;
            seg.rearZ += dz;
            for (Quad q : seg.quads) {
                for (int i = 0; i < 4; i++) {
                    q.v()[i * 6] += dx;
                    q.v()[i * 6 + 1] += dy;
                    q.v()[i * 6 + 2] += dz;
                }
            }
        }
        List<Occluder> moved = new ArrayList<>();
        for (Occluder o : occluders) moved.add(new Occluder(o.x() + dx, o.y() + dy, o.z() + dz, o.r(), o.shape()));
        occluders.clear();
        occluders.addAll(moved);
        return this;
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
        int r = Math.max(0, Math.min(255, Math.round(((argb >> 16) & 255) * f)));
        int g = Math.max(0, Math.min(255, Math.round(((argb >> 8) & 255) * f)));
        int b = Math.max(0, Math.min(255, Math.round((argb & 255) * f)));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Cheap smooth pseudo-noise in [0,1] for grain, scales and mottling. */
    static float noise(float x, float y, float z) {
        double v = Math.sin(x * 1.7 + y * 0.31) * 0.5 + Math.sin(y * 2.3 + z * 1.1) * 0.3 + Math.sin(z * 1.9 + x * 0.7 + y * 0.2) * 0.2;
        return (float) (v * 0.5 + 0.5);
    }

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** Catmull-Rom through {@code pts} (each of the same length), at t in [0, pts.length - 1]. */
    static float[] spline(float[][] pts, float t) {
        int n = pts.length;
        if (n == 1) return pts[0].clone();
        t = Math.max(0, Math.min(n - 1, t));
        int i = Math.min(n - 2, (int) Math.floor(t));
        float f = t - i;
        float[] p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(n - 1, i + 2)];
        float[] o = new float[p1.length];
        float f2 = f * f, f3 = f2 * f;
        for (int k = 0; k < o.length; k++) {
            o[k] = 0.5f * (2 * p1[k] + (-p0[k] + p2[k]) * f + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * f2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * f3);
        }
        return o;
    }

    static float spline(float[] vals, float t) {
        float[][] p = new float[vals.length][];
        for (int i = 0; i < vals.length; i++) p[i] = new float[]{vals[i]};
        return spline(p, t)[0];
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] norm(float[] a) {
        float l = (float) Math.sqrt(dot(a, a));
        return l < 1e-9f ? new float[]{0, 1, 0} : new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    // ------------------------------------------------------------------ authoring

    final class Builder {
        private final Segment seg;

        Builder(Segment seg) {
            this.seg = seg;
        }

        private void add(float[] q, int[] c, int shape) {
            seg.quads.add(new Quad(q, c, shape));
            quadCount++;
        }

        private void occluder(float x, float y, float z, float r, int shape) {
            if (r >= 0.8f) occluders.add(new Occluder(x, y, z, r, shape));
        }

        /**
         * Tessellates a parametric surface. Normals come from the surface itself and are turned to
         * face outwards; colours from the shader at each grid point, so shared vertices match.
         */
        private int surface(Surf f, int nu, int nv, boolean closedU, Shader s) {
            int shape = shapes++;
            int w = nu + 1, h = nv + 1;
            float[][] p = new float[w * h][3], n = new float[w * h][];
            float cx = 0, cy = 0, cz = 0;
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    f.at((float) i / nu, (float) j / nv, p[j * w + i]);
                    cx += p[j * w + i][0];
                    cy += p[j * w + i][1];
                    cz += p[j * w + i][2];
                }
            }
            float[] centre = {cx / (w * h), cy / (w * h), cz / (w * h)};
            float facing = 0;
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    float u = (float) i / nu, v = (float) j / nv;
                    float[] nn = normalAt(f, u, v, closedU);
                    if (nn == null) nn = normalAt(f, u, v < 0.5f ? v + 0.03f : v - 0.03f, closedU);
                    if (nn == null) nn = norm(sub(p[j * w + i], centre));
                    n[j * w + i] = nn;
                    facing += dot(nn, sub(p[j * w + i], centre));
                }
            }
            if (facing < 0) for (float[] nn : n) { nn[0] = -nn[0]; nn[1] = -nn[1]; nn[2] = -nn[2]; }
            int[] col = new int[w * h];
            for (int k = 0; k < w * h; k++) col[k] = s.color(p[k][0], p[k][1], p[k][2], n[k][0], n[k][1], n[k][2]);
            for (int j = 0; j < nv; j++) {
                for (int i = 0; i < nu; i++) {
                    int[] idx = {j * w + i, j * w + i + 1, (j + 1) * w + i + 1, (j + 1) * w + i};
                    float[] q = new float[STRIDE];
                    int[] c = new int[4];
                    for (int k = 0; k < 4; k++) {
                        float[] pp = p[idx[k]], nn = n[idx[k]];
                        System.arraycopy(pp, 0, q, k * 6, 3);
                        System.arraycopy(nn, 0, q, k * 6 + 3, 3);
                        c[k] = col[idx[k]];
                    }
                    add(q, c, shape);
                }
            }
            return shape;
        }

        private float[] normalAt(Surf f, float u, float v, boolean closedU) {
            float e = 1e-3f;
            float u0 = closedU ? u - e : Math.max(0, u - e), u1 = closedU ? u + e : Math.min(1, u + e);
            float v0 = Math.max(0, v - e), v1 = Math.min(1, v + e);
            float[] a = new float[3], b = new float[3], c = new float[3], d = new float[3];
            f.at(u0, v, a);
            f.at(u1, v, b);
            f.at(u, v0, c);
            f.at(u, v1, d);
            float[] n = cross(sub(b, a), sub(d, c));
            float l = (float) Math.sqrt(dot(n, n));
            if (l < 1e-10f) return null;
            return new float[]{n[0] / l, n[1] / l, n[2] / l};
        }

        // -------------------------------------------------------------- shapes

        /** Axis-aligned box with flat faces (small hard details: soles, panels, teeth). */
        Builder box(float x0, float y0, float z0, float x1, float y1, float z1, Shader s) {
            int shape = shapes++;
            float[][][] faces = {
                    {{x0, y1, z0, 0, 1, 0}, {x1, y1, z0, 0, 1, 0}, {x1, y1, z1, 0, 1, 0}, {x0, y1, z1, 0, 1, 0}},
                    {{x0, y0, z1, 0, -1, 0}, {x1, y0, z1, 0, -1, 0}, {x1, y0, z0, 0, -1, 0}, {x0, y0, z0, 0, -1, 0}},
                    {{x0, y0, z0, 0, 0, -1}, {x1, y0, z0, 0, 0, -1}, {x1, y1, z0, 0, 0, -1}, {x0, y1, z0, 0, 0, -1}},
                    {{x1, y0, z1, 0, 0, 1}, {x0, y0, z1, 0, 0, 1}, {x0, y1, z1, 0, 0, 1}, {x1, y1, z1, 0, 0, 1}},
                    {{x0, y0, z1, -1, 0, 0}, {x0, y0, z0, -1, 0, 0}, {x0, y1, z0, -1, 0, 0}, {x0, y1, z1, -1, 0, 0}},
                    {{x1, y0, z0, 1, 0, 0}, {x1, y0, z1, 1, 0, 0}, {x1, y1, z1, 1, 0, 0}, {x1, y1, z0, 1, 0, 0}},
            };
            for (float[][] f : faces) {
                float[] q = new float[STRIDE];
                int[] c = new int[4];
                for (int k = 0; k < 4; k++) {
                    System.arraycopy(f[k], 0, q, k * 6, 6);
                    c[k] = s.color(f[k][0], f[k][1], f[k][2], f[k][3], f[k][4], f[k][5]);
                }
                add(q, c, shape);
            }
            return this;
        }

        Builder box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
            return box(x0, y0, z0, x1, y1, z1, Shader.solid(argb));
        }

        /**
         * An ellipsoid with its own axes: radius {@code ra} along {@code a}, {@code rb} along
         * {@code b} and {@code rc} along {@code a × b}, from latitude {@code lat0} to {@code lat1}
         * (degrees, -90..90 about the c axis) so caps, lids and domes come from the same call.
         */
        Builder blob(float[] c, float[] a, float[] b, float ra, float rb, float rc, float lat0, float lat1, int stacks, int slices, Shader s) {
            float[] ax = norm(a);
            float[] bx = norm(sub(b, scale(ax, dot(b, ax))));
            float[] cx = cross(ax, bx);
            double l0 = Math.toRadians(lat0), l1 = Math.toRadians(lat1);
            int shape = surface((u, v, out) -> {
                double lat = l0 + (l1 - l0) * v, lon = 2 * Math.PI * u;
                float ca = (float) (Math.cos(lat) * Math.cos(lon)) * ra, cb = (float) (Math.cos(lat) * Math.sin(lon)) * rb, cc = (float) Math.sin(lat) * rc;
                out[0] = c[0] + ax[0] * ca + bx[0] * cb + cx[0] * cc;
                out[1] = c[1] + ax[1] * ca + bx[1] * cb + cx[1] * cc;
                out[2] = c[2] + ax[2] * ca + bx[2] * cb + cx[2] * cc;
            }, slices, stacks, true, s);
            if (lat1 - lat0 > 120) ellipsoidOccluders(c, new float[][]{ax, bx, cx}, new float[]{ra, rb, rc}, shape);
            return this;
        }

        private void ellipsoidOccluders(float[] c, float[][] axes, float[] r, int shape) {
            int big = 0;
            for (int i = 1; i < 3; i++) if (r[i] > r[big]) big = i;
            float o1 = r[(big + 1) % 3], o2 = r[(big + 2) % 3];
            float rest = (o1 + o2) / 2, small = Math.min(o1, o2);
            int k = Math.max(1, Math.round(r[big] / Math.max(0.5f, small)));
            for (int i = 0; i < k; i++) {
                float t = k == 1 ? 0 : -1 + (2 * i + 1f) / k;
                float rr = rest * (float) Math.sqrt(Math.max(0.05, 1 - t * t * 0.85));
                occluder(c[0] + axes[big][0] * t * r[big], c[1] + axes[big][1] * t * r[big], c[2] + axes[big][2] * t * r[big], rr, shape);
            }
        }

        /** Axis-aligned ellipsoid. */
        Builder ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, int stacks, int slices, Shader s) {
            return blob(new float[]{cx, cy, cz}, new float[]{1, 0, 0}, new float[]{0, 0, 1}, rx, rz, ry, -90, 90, stacks, slices, s);
        }

        Builder ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, Shader s) {
            return ellipsoid(cx, cy, cz, rx, ry, rz, 14, 22, s);
        }

        Builder sphere(float cx, float cy, float cz, float r, int argb) {
            int seg = r < 0.6f ? 8 : r < 1.5f ? 12 : 18;
            return ellipsoid(cx, cy, cz, r, r, r, seg * 2 / 3, seg, Shader.solid(argb));
        }

        Builder sphere(float cx, float cy, float cz, float r, Shader s) {
            int seg = r < 0.6f ? 8 : r < 1.5f ? 12 : 18;
            return ellipsoid(cx, cy, cz, r, r, r, seg * 2 / 3, seg, s);
        }

        /**
         * A turned shape around the vertical line (cx, cz): a smooth (Catmull-Rom) profile of radius
         * {@code rs[i]} at height {@code ys[i]}, optionally wobbled. Ends with a radius are capped flat.
         */
        Builder lathe(float cx, float cz, float[] ys, float[] rs, int slices, int rings, Wobble wob, Shader s) {
            float[][] prof = new float[ys.length][];
            for (int i = 0; i < ys.length; i++) prof[i] = new float[]{ys[i], rs[i]};
            int last = ys.length - 1;
            int shape = surface((u, v, out) -> {
                float[] pr = spline(prof, v * last);
                double a = 2 * Math.PI * u;
                float r = Math.max(0, pr[1]) * (wob == null ? 1 : wob.at((float) a, pr[0]));
                out[0] = cx + (float) Math.cos(a) * r;
                out[1] = pr[0];
                out[2] = cz + (float) Math.sin(a) * r;
            }, slices, rings, true, s);
            for (int i = 0; i <= rings; i += 2) {
                float[] pr = spline(prof, (float) i / rings * last);
                occluder(cx, pr[0], cz, pr[1] * 0.95f, shape);
            }
            if (rs[last] > 0.02f) cap(cx, cz, ys[last], rs[last], slices, 1, wob, s, shape);
            if (rs[0] > 0.02f) cap(cx, cz, ys[0], rs[0], slices, -1, wob, s, shape);
            return this;
        }

        Builder lathe(float cx, float cz, float[] ys, float[] rs, int slices, Shader s) {
            return lathe(cx, cz, ys, rs, slices, Math.max(8, ys.length * 4), null, s);
        }

        private void cap(float cx, float cz, float y, float r, int slices, int dir, Wobble wob, Shader s, int shape) {
            int rings = 4;
            for (int k = 0; k < rings; k++) {
                float ra = r * k / rings, rb = r * (k + 1) / rings;
                for (int j = 0; j < slices; j++) {
                    double a0 = 2 * Math.PI * j / slices, a1 = 2 * Math.PI * (j + 1) / slices;
                    float w0 = wob == null ? 1 : wob.at((float) a0, y), w1 = wob == null ? 1 : wob.at((float) a1, y);
                    float c0 = (float) Math.cos(a0) * w0, s0 = (float) Math.sin(a0) * w0, c1 = (float) Math.cos(a1) * w1, s1 = (float) Math.sin(a1) * w1;
                    float[][] p = {{cx + c0 * ra, y, cz + s0 * ra}, {cx + c1 * ra, y, cz + s1 * ra}, {cx + c1 * rb, y, cz + s1 * rb}, {cx + c0 * rb, y, cz + s0 * rb}};
                    float[] q = new float[STRIDE];
                    int[] c = new int[4];
                    for (int i = 0; i < 4; i++) {
                        q[i * 6] = p[i][0];
                        q[i * 6 + 1] = p[i][1];
                        q[i * 6 + 2] = p[i][2];
                        q[i * 6 + 4] = dir;
                        c[i] = s.color(p[i][0], p[i][1], p[i][2], 0, dir, 0);
                    }
                    add(q, c, shape);
                }
            }
        }

        /**
         * A lofted shape: a smooth curve through {@code pts} with an elliptical cross-section
         * {@code rw} wide and {@code rh} high at each point (optionally wobbled), with domed ends.
         */
        Builder loft(float[][] pts, float[] rw, float[] rh, int sides, int rings, Wobble wob, Shader s) {
            int last = pts.length - 1;
            int samples = 64;
            float[][] c = new float[samples + 1][], side = new float[samples + 1][], up = new float[samples + 1][];
            float[] w = new float[samples + 1], h = new float[samples + 1];
            float[] prevSide = null;
            for (int i = 0; i <= samples; i++) {
                float t = (float) i / samples * last;
                c[i] = spline(pts, t);
                w[i] = Math.max(0.01f, spline(rw, t));
                h[i] = Math.max(0.01f, spline(rh, t));
                float[] tan = norm(sub(spline(pts, Math.min(last, t + 0.01f)), spline(pts, Math.max(0, t - 0.01f))));
                float[] sd;
                if (prevSide == null) {
                    float[] ref = Math.abs(tan[1]) > 0.85f ? new float[]{0, 0, -1} : new float[]{0, 1, 0};
                    sd = norm(cross(tan, ref));
                    if (Math.abs(tan[1]) > 0.85f && sd[0] < 0) sd = scale(sd, -1);
                } else {
                    sd = norm(sub(prevSide, scale(tan, dot(prevSide, tan))));
                }
                prevSide = sd;
                side[i] = sd;
                up[i] = norm(cross(sd, tan));
            }
            int shape = surface((u, v, out) -> {
                float fi = v * samples;
                int i = Math.min(samples - 1, (int) fi);
                float f = fi - i;
                double a = 2 * Math.PI * u;
                float m = wob == null ? 1 : wob.at((float) a, v);
                float cw = (float) Math.cos(a) * (w[i] + (w[i + 1] - w[i]) * f) * m, ch = (float) Math.sin(a) * (h[i] + (h[i + 1] - h[i]) * f) * m;
                for (int k = 0; k < 3; k++) {
                    float cc = c[i][k] + (c[i + 1][k] - c[i][k]) * f;
                    float sk = side[i][k] + (side[i + 1][k] - side[i][k]) * f, uk = up[i][k] + (up[i + 1][k] - up[i][k]) * f;
                    out[k] = cc + sk * cw + uk * ch;
                }
            }, sides, rings, true, s);
            for (int i = 0; i <= samples; i += 4) occluder(c[i][0], c[i][1], c[i][2], (float) Math.sqrt(w[i] * h[i]) * 0.95f, shape);
            // Domed ends: half-ellipsoids that continue the cross-section past each end.
            float[] t0 = norm(sub(c[0], c[1])), t1 = norm(sub(c[samples], c[samples - 1]));
            dome(c[0], side[0], up[0], t0, w[0], h[0], Math.min(w[0], h[0]), sides, s);
            dome(c[samples], side[samples], up[samples], t1, w[samples], h[samples], Math.min(w[samples], h[samples]), sides, s);
            return this;
        }

        Builder loft(float[][] pts, float[] rw, float[] rh, int sides, Shader s) {
            return loft(pts, rw, rh, sides, Math.max(10, pts.length * 6), null, s);
        }

        /** A smooth round tube through {@code pts} with a radius per point and rounded ends. */
        Builder tube(float[][] pts, float[] radii, int sides, Shader s) {
            return loft(pts, radii, radii, sides, Math.max(6, pts.length * 4), null, s);
        }

        /** Straight rounded limb from a to b. */
        Builder limb(float x0, float y0, float z0, float x1, float y1, float z1, float r0, float r1, Shader s) {
            return tube(new float[][]{{x0, y0, z0}, {x1, y1, z1}}, new float[]{r0, r1}, 10, s);
        }

        private void dome(float[] c, float[] sd, float[] up, float[] out, float w, float h, float len, int sides, Shader s) {
            if (w < 0.03f && h < 0.03f) return;
            int shape = shapes++;
            int stacks = 4;
            for (int k = 0; k < stacks; k++) {
                double la = Math.PI / 2 * k / stacks, lb = Math.PI / 2 * (k + 1) / stacks;
                for (int j = 0; j < sides; j++) {
                    double a0 = 2 * Math.PI * j / sides, a1 = 2 * Math.PI * (j + 1) / sides;
                    double[][] ll = {{la, a1}, {la, a0}, {lb, a0}, {lb, a1}};
                    float[] q = new float[STRIDE];
                    int[] col = new int[4];
                    for (int i = 0; i < 4; i++) {
                        float ca = (float) Math.cos(ll[i][0]), sa = (float) Math.sin(ll[i][0]);
                        float co = (float) Math.cos(ll[i][1]), so = (float) Math.sin(ll[i][1]);
                        float[] p = new float[3], n = new float[3];
                        for (int d = 0; d < 3; d++) {
                            p[d] = c[d] + sd[d] * ca * co * w + up[d] * ca * so * h + out[d] * sa * len;
                            n[d] = sd[d] * ca * co / w + up[d] * ca * so / h + out[d] * sa / len;
                        }
                        n = norm(n);
                        System.arraycopy(p, 0, q, i * 6, 3);
                        System.arraycopy(n, 0, q, i * 6 + 3, 3);
                        col[i] = s.color(p[0], p[1], p[2], n[0], n[1], n[2]);
                    }
                    add(q, col, shape);
                }
            }
        }

        /**
         * A smooth blade (fin, wing, ear, propeller): leading and trailing edges run from root to tip
         * (the same number of points, meeting at the tip for a point), with a rounded, tapering
         * thickness between them.
         */
        Builder blade(float[][] lead, float[][] trail, float thick, int around, int along, Shader s) {
            int last = lead.length - 1;
            surface((u, v, out) -> {
                float t = v * last;
                float[] l = spline(lead, t), tr = spline(trail, t);
                float[] chord = sub(tr, l);
                float tn = Math.min(t, last * 0.8f);
                float[] spanA = mid(spline(lead, Math.max(0, tn - 0.05f)), spline(trail, Math.max(0, tn - 0.05f)));
                float[] spanB = mid(spline(lead, tn + 0.05f), spline(trail, tn + 0.05f));
                float[] chordN = sub(spline(trail, tn), spline(lead, tn));
                float[] nrm = norm(cross(chordN, sub(spanB, spanA)));
                double a = 2 * Math.PI * u;
                float sPos = (float) (0.5 - 0.5 * Math.cos(a));
                float off = (float) Math.sin(a) * thick * 0.5f * (1 - 0.6f * v);
                for (int k = 0; k < 3; k++) out[k] = l[k] + chord[k] * sPos + nrm[k] * off;
            }, around, along, true, s);
            return this;
        }

        /** Flat polygon extruded by {@code thickness} (crisp details: teeth, claws, panels). */
        Builder slab(float[][] poly, float thickness, Shader s) {
            int shape = shapes++;
            int n = poly.length;
            float[] nn = norm(cross(sub(poly[1], poly[0]), sub(poly[2], poly[0])));
            float hh = thickness / 2;
            float[][] top = new float[n][], bot = new float[n][];
            for (int i = 0; i < n; i++) {
                top[i] = new float[]{poly[i][0] + nn[0] * hh, poly[i][1] + nn[1] * hh, poly[i][2] + nn[2] * hh};
                bot[i] = new float[]{poly[i][0] - nn[0] * hh, poly[i][1] - nn[1] * hh, poly[i][2] - nn[2] * hh};
            }
            for (int i = 1; i < n - 1; i++) {
                flat(new float[][]{top[0], top[i], top[i + 1], top[i + 1]}, nn, s, shape);
                flat(new float[][]{bot[0], bot[i + 1], bot[i], bot[i]}, scale(nn, -1), s, shape);
            }
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float[] sn = norm(cross(sub(poly[j], poly[i]), nn));
                flat(new float[][]{bot[i], bot[j], top[j], top[i]}, sn, s, shape);
            }
            return this;
        }

        private void flat(float[][] p, float[] n, Shader s, int shape) {
            float[] q = new float[STRIDE];
            int[] c = new int[4];
            for (int i = 0; i < 4; i++) {
                System.arraycopy(p[i], 0, q, i * 6, 3);
                System.arraycopy(n, 0, q, i * 6 + 3, 3);
                c[i] = s.color(p[i][0], p[i][1], p[i][2], n[0], n[1], n[2]);
            }
            add(q, c, shape);
        }
    }

    private static float[] scale(float[] a, float f) {
        return new float[]{a[0] * f, a[1] * f, a[2] * f};
    }

    private static float[] mid(float[] a, float[] b) {
        return new float[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
    }

    /** Starts a group of shapes driven by {@code p}, rotating about the pivot (pixels). */
    Builder part(Part p, float px, float py, float pz) {
        Segment s = new Segment(p, new float[]{px, py, pz});
        segments.add(s);
        return new Builder(s);
    }

    /** A TAIL group whose sway grows from nothing at the pivot to full {@code length} pixels behind it. */
    Builder tail(float px, float py, float pz, float length) {
        Segment s = new Segment(Part.TAIL, new float[]{px, py, pz});
        s.rampRear = length;
        segments.add(s);
        return new Builder(s);
    }

    /**
     * A SPINE group: shapes in front of {@code frontZ} bend towards the head's yaw (fully
     * {@code front} pixels further forward), shapes behind {@code rearZ} sway (fully {@code rear}
     * pixels further back); what lies between stays put.
     */
    Builder spine(float px, float py, float frontZ, float front, float rearZ, float rear) {
        Segment s = new Segment(Part.SPINE, new float[]{px, py, (frontZ + rearZ) / 2});
        s.frontZ = frontZ;
        s.rearZ = rearZ;
        s.rampFront = front;
        s.rampRear = rear;
        segments.add(s);
        return new Builder(s);
    }

    // ------------------------------------------------------------------ baking

    /**
     * Bakes ambient occlusion into the vertex colours: every shape darkens what it sits close to
     * (creases, joints, the underside of a chin) and the ground darkens what is near it. Call once,
     * after building.
     */
    AvatarModel bake(float strength) {
        for (Segment seg : segments) {
            for (int qi = 0; qi < seg.quads.size(); qi++) {
                Quad quad = seg.quads.get(qi);
                float[] v = quad.v();
                int[] c = quad.c().clone();
                for (int k = 0; k < 4; k++) {
                    int o = k * 6;
                    float x = v[o], y = v[o + 1], z = v[o + 2], nx = v[o + 3], ny = v[o + 4], nz = v[o + 5];
                    float occ = 0;
                    for (Occluder oc : occluders) {
                        if (oc.shape() == quad.shape()) continue;
                        float dx = oc.x() - x, dy = oc.y() - y, dz = oc.z() - z;
                        float d2 = dx * dx + dy * dy + dz * dz;
                        float d = (float) Math.sqrt(d2);
                        if (d < 1e-4f) continue;
                        float cos = (dx * nx + dy * ny + dz * nz) / d;
                        if (cos <= 0) continue;
                        float r2 = oc.r() * oc.r();
                        occ += d2 <= r2 ? cos : cos * r2 / d2;
                    }
                    float ao = Math.max(0.42f, 1 - strength * occ);
                    // Contact shading from the ground.
                    ao *= 1 - 0.32f * (0.5f - 0.5f * ny) * (float) Math.exp(-Math.max(0, y) / 2.2f);
                    c[k] = shade(c[k], ao);
                }
                seg.quads.set(qi, new Quad(v, c, quad.shape()));
            }
        }
        occluders.clear();
        return this;
    }

    // ------------------------------------------------------------------ posing

    /**
     * Joint angles in radians, using Minecraft's model-space conventions (as HumanoidModel sets them):
     * head pitch/yaw, an x rotation (swing) for each limb, a body bob in pixels, a tail sway and a
     * propeller spin.
     */
    public record Angles(float headPitch, float headYaw, float rightArm, float leftArm, float rightLeg, float leftLeg,
                         float bob, float sway, float spin, float age) {
        public static final Angles REST = new Angles(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        public Angles(float headPitch, float headYaw, float rightArm, float leftArm, float rightLeg, float leftLeg, float bob) {
            this(headPitch, headYaw, rightArm, leftArm, rightLeg, leftLeg, bob, 0, 0, 0);
        }

        /** Walking like a player: legs and arms swing in opposition; {@code attack} raises the right arm. */
        public static Angles walking(float walkPos, float walkSpeed, float headPitchDeg, float headYawDeg, float attack, float age) {
            float c = (float) Math.cos(walkPos * 0.6662f), o = (float) Math.cos(walkPos * 0.6662f + Math.PI);
            float speed = Math.min(1f, walkSpeed);
            float right = o * speed + (float) Math.sin(age * 0.067f) * 0.05f;
            right -= (float) Math.sin(Math.min(1f, Math.max(0f, attack)) * Math.PI) * 1.3f;
            float sway = (float) Math.sin(walkPos * 0.6662f) * speed * 0.45f + (float) Math.sin(age * 0.11f) * 0.12f;
            return new Angles((float) Math.toRadians(headPitchDeg), (float) Math.toRadians(headYawDeg),
                    right, c * speed - (float) Math.sin(age * 0.067f) * 0.05f, c * 1.4f * speed, o * 1.4f * speed,
                    (float) Math.abs(Math.sin(walkPos * 0.6662f)) * speed * 0.6f, sway, age * 1.1f, age);
        }
    }

    /** The model posed in Minecraft's player model space (blocks, y down, feet at y = 1.5). */
    public Posed pose(Angles a) {
        float[] out = new float[quadCount * STRIDE];
        int[] colors = new int[quadCount * 4];
        int k = 0, ci = 0;
        for (Segment seg : segments) {
            Part p = seg.part;
            float[] pv = seg.pivot;
            // Model-space angles → our y-up space: both rotations change sign (the y axis is flipped).
            float rx = 0, ry = 0, rz = 0;
            switch (p) {
                case HEAD -> { rx = -a.headPitch(); ry = -a.headYaw(); }
                case RIGHT_ARM -> rx = -a.rightArm();
                case LEFT_ARM -> rx = -a.leftArm();
                case RIGHT_LEG -> rx = -a.rightLeg();
                case LEFT_LEG -> rx = -a.leftLeg();
                case TAIL -> ry = a.sway();
                case PROP -> rz = a.spin();
                default -> { }
            }
            boolean ramped = p == Part.SPINE || (p == Part.TAIL && seg.rampRear > 0);
            float bob = (p == Part.RIGHT_LEG || p == Part.LEFT_LEG) && hover == 0 ? 0 : a.bob();
            if (hover > 0) bob += hover * (float) Math.sin(a.age() * 0.09f);
            float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx), cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
            float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);
            for (Quad quad : seg.quads) {
                float[] q = quad.v();
                System.arraycopy(quad.c(), 0, colors, ci, 4);
                ci += 4;
                for (int i = 0; i < 4; i++) {
                    int o = i * 6;
                    float pz = pv[2];
                    float vcy = cy, vsy = sy;
                    if (ramped) {
                        float zz = q[o + 2], yaw = 0;
                        if (p == Part.TAIL) {
                            yaw = ry * Math.max(0, Math.min(1, (zz - pz) / seg.rampRear));
                        } else if (zz < seg.frontZ) {
                            pz = seg.frontZ;
                            yaw = -a.headYaw() * seg.frontGain * Math.min(1, (seg.frontZ - zz) / seg.rampFront);
                        } else if (zz > seg.rearZ) {
                            pz = seg.rearZ;
                            yaw = a.sway() * Math.min(1, (zz - seg.rearZ) / seg.rampRear);
                        }
                        vcy = (float) Math.cos(yaw);
                        vsy = (float) Math.sin(yaw);
                    }
                    float x = q[o] - pv[0], y = q[o + 1] - pv[1], z = q[o + 2] - pz;
                    float nx = q[o + 3], ny = q[o + 4], nz = q[o + 5];
                    // Spin (about z), then pitch (about x), then yaw (about y).
                    float x0 = x * cz - y * sz, y0 = x * sz + y * cz;
                    float y1 = y0 * cx - z * sx, z1 = y0 * sx + z * cx;
                    float x2 = x0 * vcy + z1 * vsy, z2 = -x0 * vsy + z1 * vcy;
                    out[k++] = (x2 + pv[0]) / 16f;
                    out[k++] = 1.5f - (y1 + pv[1] + bob) / 16f;
                    out[k++] = (z2 + pz) / 16f;
                    float nx0 = nx * cz - ny * sz, ny0 = nx * sz + ny * cz;
                    float ny1 = ny0 * cx - nz * sx, nz1 = ny0 * sx + nz * cx;
                    out[k++] = nx0 * vcy + nz1 * vsy;
                    out[k++] = -ny1;
                    out[k++] = -nx0 * vsy + nz1 * vcy;
                }
            }
        }
        return new Posed(out, colors);
    }
}
