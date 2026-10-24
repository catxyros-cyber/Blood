package dev.bloom.client.core.avatar;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A 3D avatar built from coloured boxes and cylinders, with parts that animate like a player's.
 * Pure Java: shapes are authored in "pixels" (16 = one block) with y up, the ground at 0 and the
 * front facing -z; {@link #pose} turns them into Minecraft's player model space (blocks, y down,
 * feet at y = 1.5), ready to draw.
 */
public final class AvatarModel {
    public enum Part { BODY, HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /** One quad: 4 corners (x, y, z), a face normal and an ARGB colour. Triangles repeat the last corner. */
    record Quad(float[] v, float nx, float ny, float nz, int argb) {
    }

    private final String id;
    private final Map<Part, List<Quad>> parts = new EnumMap<>(Part.class);
    private final Map<Part, float[]> pivots = new EnumMap<>(Part.class);
    private final float height;

    AvatarModel(String id, float height) {
        this.id = id;
        this.height = height;
        for (Part p : Part.values()) {
            parts.put(p, new ArrayList<>());
            pivots.put(p, new float[]{0, 0, 0});
        }
    }

    public String id() {
        return id;
    }

    /** Total height in pixels (a player is about 32). */
    public float height() {
        return height;
    }

    public int quadCount() {
        int n = 0;
        for (List<Quad> l : parts.values()) n += l.size();
        return n;
    }

    // ------------------------------------------------------------------ authoring

    final class Builder {
        private final Part part;

        Builder(Part part, float px, float py, float pz) {
            this.part = part;
            pivots.put(part, new float[]{px, py, pz});
        }

        private void quad(float[] v, float nx, float ny, float nz, int argb) {
            parts.get(part).add(new Quad(v, nx, ny, nz, argb));
        }

        /** Axis-aligned box. {@code side} colours the four sides; top and bottom get {@code cap}. */
        Builder box(float x0, float y0, float z0, float x1, float y1, float z1, int side, int cap) {
            quad(new float[]{x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, 0, 1, 0, cap);      // top
            quad(new float[]{x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0}, 0, -1, 0, shade(cap, 0.7f)); // bottom
            quad(new float[]{x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0}, 0, 0, -1, side);    // front
            quad(new float[]{x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1}, 0, 0, 1, shade(side, 0.85f)); // back
            quad(new float[]{x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1}, -1, 0, 0, shade(side, 0.92f)); // -x
            quad(new float[]{x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0}, 1, 0, 0, shade(side, 0.92f)); // +x
            return this;
        }

        Builder box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
            return box(x0, y0, z0, x1, y1, z1, argb, argb);
        }

        /**
         * Vertical (frustum) cylinder: radius r0 at y0, r1 at y1, split into {@code bands} rings whose
         * colours come from {@code band.color(i)} (for bark rings, stripes…).
         */
        Builder cylinder(float cx, float cz, float r0, float r1, float y0, float y1, int segments, int bands, BandColor band, int capTop, int capBottom) {
            int n = Math.max(5, segments);
            for (int b = 0; b < bands; b++) {
                float ya = y0 + (y1 - y0) * b / bands, yb = y0 + (y1 - y0) * (b + 1) / bands;
                float ra = r0 + (r1 - r0) * b / bands, rb = r0 + (r1 - r0) * (b + 1) / bands;
                int col = band.color(b);
                for (int i = 0; i < n; i++) {
                    double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n, am = (a0 + a1) / 2;
                    float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                    quad(new float[]{
                            cx + c1 * ra, ya, cz + s1 * ra,
                            cx + c0 * ra, ya, cz + s0 * ra,
                            cx + c0 * rb, yb, cz + s0 * rb,
                            cx + c1 * rb, yb, cz + s1 * rb}, (float) Math.cos(am), 0, (float) Math.sin(am), col);
                }
            }
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                if (capTop != 0) quad(new float[]{cx, y1, cz, cx + c1 * r1, y1, cz + s1 * r1, cx + c0 * r1, y1, cz + s0 * r1, cx + c0 * r1, y1, cz + s0 * r1}, 0, 1, 0, capTop);
                if (capBottom != 0) quad(new float[]{cx, y0, cz, cx + c0 * r0, y0, cz + s0 * r0, cx + c1 * r0, y0, cz + s1 * r0, cx + c1 * r0, y0, cz + s1 * r0}, 0, -1, 0, capBottom);
            }
            return this;
        }

        Builder cylinder(float cx, float cz, float r, float y0, float y1, int segments, int argb) {
            return cylinder(cx, cz, r, r, y0, y1, segments, 1, i -> argb, argb, argb);
        }

        /** A thin limb between two points (square cross-section of width {@code w}), any direction. */
        Builder rod(float x0, float y0, float z0, float x1, float y1, float z1, float w, int argb) {
            float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-4f) return this;
            float ux = dx / len, uy = dy / len, uz = dz / len;
            // Two vectors perpendicular to the rod.
            float ax = Math.abs(uy) < 0.9f ? 0 : 1, ay = Math.abs(uy) < 0.9f ? 1 : 0, az = 0;
            float px = uy * az - uz * ay, py = uz * ax - ux * az, pz = ux * ay - uy * ax;
            float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
            px /= pl; py /= pl; pz /= pl;
            float qx = uy * pz - uz * py, qy = uz * px - ux * pz, qz = ux * py - uy * px;
            float h = w / 2;
            float[][] c = new float[8][];
            int k = 0;
            for (int end = 0; end < 2; end++) {
                float bx = end == 0 ? x0 : x1, by = end == 0 ? y0 : y1, bz = end == 0 ? z0 : z1;
                for (int s = 0; s < 4; s++) {
                    float sp = (s == 0 || s == 3) ? -h : h, sq = (s < 2) ? -h : h;
                    c[k++] = new float[]{bx + px * sp + qx * sq, by + py * sp + qy * sq, bz + pz * sp + qz * sq};
                }
            }
            int[][] faces = {{0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}, {3, 2, 1, 0}, {4, 5, 6, 7}};
            for (int[] f : faces) {
                float[] v = new float[12];
                for (int i = 0; i < 4; i++) System.arraycopy(c[f[i]], 0, v, i * 3, 3);
                float ex = v[3] - v[0], ey = v[4] - v[1], ez = v[5] - v[2];
                float fx = v[9] - v[0], fy = v[10] - v[1], fz = v[11] - v[2];
                float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
                float nl = (float) Math.max(1e-6, Math.sqrt(nx * nx + ny * ny + nz * nz));
                quad(v, nx / nl, ny / nl, nz / nl, argb);
            }
            return this;
        }
    }

    interface BandColor {
        int color(int band);
    }

    Builder part(Part p, float px, float py, float pz) {
        return new Builder(p, px, py, pz);
    }

    static int shade(int argb, float f) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (a << 24) | (Math.min(255, Math.round(r * f)) << 16) | (Math.min(255, Math.round(g * f)) << 8) | Math.min(255, Math.round(b * f));
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

    /**
     * Posed geometry in Minecraft's player model space. Each quad is 4 vertices of (x, y, z), then the
     * normal (x, y, z), then the colour as an int's bits: 16 floats per quad.
     */
    public float[] pose(Angles a) {
        float[] out = new float[quadCount() * 16];
        int k = 0;
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
            for (Quad q : parts.get(p)) {
                for (int i = 0; i < 4; i++) {
                    float x = q.v()[i * 3] - pv[0], y = q.v()[i * 3 + 1] - pv[1], z = q.v()[i * 3 + 2] - pv[2];
                    // Pitch (about x), then yaw (about y): like a head nodding, then turning.
                    float y1 = y * cx - z * sx, z1 = y * sx + z * cx;
                    float x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
                    float wx = x2 + pv[0], wy = y1 + pv[1] + bob, wz = z2 + pv[2];
                    out[k++] = wx / 16f;
                    out[k++] = 1.5f - wy / 16f;
                    out[k++] = wz / 16f;
                }
                float ny1 = q.ny() * cx - q.nz() * sx, nz1 = q.ny() * sx + q.nz() * cx;
                float nx2 = q.nx() * cy + nz1 * sy, nz2 = -q.nx() * sy + nz1 * cy;
                out[k++] = nx2;
                out[k++] = -ny1;
                out[k++] = nz2;
                out[k++] = Float.intBitsToFloat(q.argb());
            }
        }
        return out;
    }
}
