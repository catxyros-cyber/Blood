package dev.bloom.client.core.visual;

/**
 * Pure decision logic for MOTION's post-processing, kept free of Minecraft classes so it can be
 * unit tested: which shader chain to run (if any) and the uniform values to feed it.
 */
public final class PostParams {
    /** The shader chains in assets/bloomclient/shaders/post/. */
    public enum Chain {
        GRADE("grade"),
        GLOW_MEDIUM("grade_glow_medium"),
        GLOW_HIGH("grade_glow_high");

        public final String file;

        Chain(String file) {
            this.file = file;
        }

        public boolean glow() {
            return this != GRADE;
        }
    }

    /** Uniform order in {@link #uniforms}. */
    public static final String[] UNIFORMS = {
            "Exposure", "Contrast", "Saturation", "Warmth", "TintR", "TintG", "TintB", "TintStrength",
            "Vignette", "Sharpen", "SoftHighlights", "GlowStrength", "Mood", "GlowThreshold"};
    public static final int EXPOSURE = 0, CONTRAST = 1, SATURATION = 2, WARMTH = 3, TINT_R = 4, TINT_G = 5, TINT_B = 6,
            TINT_STRENGTH = 7, VIGNETTE = 8, SHARPEN = 9, SOFT_HIGHLIGHTS = 10, GLOW_STRENGTH = 11, MOOD = 12, GLOW_THRESHOLD = 13;

    /** Everything the decision depends on (settings + game state). */
    public record Input(
            boolean enabled, float exposure, float contrast, float saturation, float warmth, int tintRgb, float tintStrength,
            float vignette, float sharpen, boolean softHighlights, boolean glow, float glowStrength, float glowThreshold,
            boolean weatherMood, float rain, float thunder, String quality, boolean lite, boolean adaptiveDowngrade) {
    }

    private PostParams() {
    }

    /** Which chain to run, or null to run none (also when every value is neutral: zero cost). */
    public static Chain chain(Input in) {
        if (!in.enabled()) return null;
        String q = effectiveQuality(in);
        boolean glow = in.glow() && in.glowStrength() > 0.001f && !in.lite() && !"low".equals(q);
        if (!glow) return neutral(in) ? null : Chain.GRADE;
        return "high".equals(q) ? Chain.GLOW_HIGH : Chain.GLOW_MEDIUM;
    }

    /** "Reduce effects when FPS drops" steps the shader quality down one level while FPS is low. */
    public static String effectiveQuality(Input in) {
        String q = in.quality() == null ? "medium" : in.quality();
        if (!in.adaptiveDowngrade()) return q;
        return switch (q) {
            case "high" -> "medium";
            default -> "low";
        };
    }

    /** True when the grade pass would leave the image unchanged. */
    static boolean neutral(Input in) {
        // Soft highlights alone don't count: they only change pixels that are already near white.
        return Math.abs(in.exposure()) < 1e-3f && Math.abs(in.contrast() - 1f) < 1e-3f && Math.abs(in.saturation() - 1f) < 1e-3f
                && Math.abs(in.warmth()) < 1e-3f && in.tintStrength() < 1e-3f && in.vignette() < 1e-3f
                && effectiveSharpen(in) < 1e-3f && mood(in) < 1e-3f;
    }

    /** Performance-friendly mode drops sharpening (four extra texture reads per pixel). */
    static float effectiveSharpen(Input in) {
        return in.lite() ? 0f : in.sharpen();
    }

    static float mood(Input in) {
        if (!in.weatherMood()) return 0f;
        return Math.min(1f, Math.max(in.rain(), 0f) * 0.7f + Math.max(in.thunder(), 0f) * 0.3f);
    }

    /** Uniform values in {@link #UNIFORMS} order, written into {@code out}. */
    public static float[] uniforms(Input in, Chain chain, float[] out) {
        out[EXPOSURE] = in.exposure();
        out[CONTRAST] = in.contrast();
        out[SATURATION] = in.saturation();
        out[WARMTH] = in.warmth();
        out[TINT_R] = ((in.tintRgb() >> 16) & 255) / 255f;
        out[TINT_G] = ((in.tintRgb() >> 8) & 255) / 255f;
        out[TINT_B] = (in.tintRgb() & 255) / 255f;
        out[TINT_STRENGTH] = in.tintStrength();
        out[VIGNETTE] = in.vignette();
        out[SHARPEN] = effectiveSharpen(in);
        out[SOFT_HIGHLIGHTS] = in.softHighlights() ? 1f : 0f;
        out[GLOW_STRENGTH] = chain != null && chain.glow() ? in.glowStrength() : 0f;
        out[MOOD] = mood(in);
        out[GLOW_THRESHOLD] = in.glowThreshold();
        return out;
    }
}
