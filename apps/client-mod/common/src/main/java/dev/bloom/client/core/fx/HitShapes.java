package dev.bloom.client.core.fx;

/**
 * Geometry for MOTION's hit effects and swing animations, free of Minecraft types so it can be
 * unit tested. Coordinates are offsets in blocks from the target; y is up.
 */
public final class HitShapes {
    /** How many ticks a hit effect lasts. */
    public static final int LIFETIME = 9;

    private HitShapes() {
    }

    /** Ring radius at a given age: grows fast at first, then slows. */
    public static double ringRadius(int age) {
        double t = Math.min(1.0, Math.max(0, age) / (double) (LIFETIME - 1));
        return 0.35 + 1.25 * (1 - (1 - t) * (1 - t));
    }

    /** {@code count} evenly spaced points on a horizontal circle; {@code phase} rotates them. */
    public static double[][] ring(double radius, int count, double phase) {
        int n = Math.max(3, count);
        double[][] out = new double[n][3];
        for (int i = 0; i < n; i++) {
            double a = phase + i * Math.PI * 2 / n;
            out[i][0] = Math.cos(a) * radius;
            out[i][1] = 0;
            out[i][2] = Math.sin(a) * radius;
        }
        return out;
    }

    /**
     * Points of a curved slash across the target, between {@code from} and {@code to} (0..1 along
     * the slash), drawn in the plane facing the attacker. The slash runs from upper-left to
     * lower-right as the attacker sees it. {@code yawDegrees} is the attacker's yaw (Minecraft
     * convention: facing direction is (-sin yaw, 0, cos yaw)).
     */
    public static double[][] slash(double yawDegrees, double from, double to, int steps, double size) {
        int n = Math.max(2, steps);
        double yaw = Math.toRadians(yawDegrees);
        // Right-hand vector as the attacker sees it, and the direction back toward the attacker.
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        double backX = Math.sin(yaw), backZ = -Math.cos(yaw);
        double[][] out = new double[n][3];
        for (int i = 0; i < n; i++) {
            double s = from + (to - from) * i / (n - 1.0);
            // An arc of ~200 degrees, tilted so it reads as a diagonal cut.
            double a = Math.toRadians(150 - 200 * s);
            double u = Math.cos(a) * size;             // left/right
            double v = Math.sin(a) * size * 0.55;      // up/down
            double tilt = Math.toRadians(-32);
            double uu = u * Math.cos(tilt) - v * Math.sin(tilt);
            double vv = u * Math.sin(tilt) + v * Math.cos(tilt);
            double bulge = Math.sin(s * Math.PI) * 0.18 * size; // curve toward the attacker
            out[i][0] = rightX * uu + backX * bulge;
            out[i][1] = vv;
            out[i][2] = rightZ * uu + backZ * bulge;
        }
        return out;
    }

    /** Smooth 0→1→0 ease for animations: in-out cubic over the swing. */
    public static float easeInOut(float p) {
        float t = Math.max(0f, Math.min(1f, p));
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    /** Degrees of extra rotation for the swing animation at swing progress {@code p} (0..1). */
    public static float swingDegrees(float p) {
        return 360f * easeInOut(p);
    }

    /** Fully saturated colour on the hue wheel, for the rainbow option. {@code t} in seconds. */
    public static int rainbow(double t) {
        double h = ((t * 0.25) % 1 + 1) % 1 * 6;
        int i = (int) Math.floor(h);
        double f = h - i;
        double q = 1 - f;
        double r, g, b;
        switch (i % 6) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }

    /** Mix an RGB colour toward white by {@code f} (0..1). */
    public static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += Math.round((255 - r) * f);
        g += Math.round((255 - g) * f);
        b += Math.round((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }
}
