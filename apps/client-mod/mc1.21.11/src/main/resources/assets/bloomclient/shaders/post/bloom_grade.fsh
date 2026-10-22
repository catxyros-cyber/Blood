#version 330

// BLOOD Client colour grading. One full-screen pass:
//   sharpen -> glow -> exposure -> white balance -> colour filter -> saturation -> contrast
//   -> soft highlights -> vignette -> dither
// Every value at its neutral setting leaves the image unchanged (BLOOD skips the pass then).
// Parameters arrive in DynamicTransforms (see PostEffects.java):
//   ModelViewMat[0] = exposure, contrast, saturation, warmth
//   ModelViewMat[1] = tint r, g, b, tint strength
//   ModelViewMat[2] = vignette, sharpen, soft highlights, glow strength
//   ModelViewMat[3] = mood (rain/thunder), glow threshold
//   TextureMat[0]   = input size, output size

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;
uniform sampler2D BloomSampler;  // a 1x1 black texture when glow is off

in vec2 texCoord;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float shoulder(float x) {
    // Linear below 0.8, then an exponential roll-off that approaches 1.0 instead of clipping.
    const float k = 0.8;
    return x <= k ? x : k + (1.0 - k) * (1.0 - exp(-(x - k) / (1.0 - k)));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float Exposure = ModelViewMat[0].x;
    float Contrast = ModelViewMat[0].y;
    float Saturation = ModelViewMat[0].z;
    float Warmth = ModelViewMat[0].w;
    vec3 Tint = ModelViewMat[1].xyz;
    float TintStrength = ModelViewMat[1].w;
    float Vignette = ModelViewMat[2].x;
    float Sharpen = ModelViewMat[2].y;
    float SoftHighlights = ModelViewMat[2].z;
    float GlowStrength = ModelViewMat[2].w;
    float Mood = ModelViewMat[3].x;
    vec2 oneTexel = 1.0 / TextureMat[0].xy;
    vec2 OutSize = TextureMat[0].zw;

    vec4 src = texture(InSampler, texCoord);
    vec3 c = src.rgb;

    if (Sharpen > 0.001) {
        // Contrast-adaptive sharpening: strongest on soft detail, gentle on edges that are already
        // crisp, so textures get clearer without halos.
        vec3 n = texture(InSampler, texCoord + vec2(oneTexel.x, 0.0)).rgb;
        vec3 s = texture(InSampler, texCoord - vec2(oneTexel.x, 0.0)).rgb;
        vec3 e = texture(InSampler, texCoord + vec2(0.0, oneTexel.y)).rgb;
        vec3 w = texture(InSampler, texCoord - vec2(0.0, oneTexel.y)).rgb;
        vec3 mn = min(c, min(min(n, s), min(e, w)));
        vec3 mx = max(c, max(max(n, s), max(e, w)));
        vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, 1e-4), 0.0, 1.0));
        vec3 detail = c - (n + s + e + w) * 0.25;
        c = clamp(c + detail * amp * Sharpen * 2.2, 0.0, 1.0);
    }

    if (GlowStrength > 0.001) {
        vec3 glow = texture(BloomSampler, texCoord).rgb;
        // Screen-blend the glow so bright areas bloom without blowing out to flat white.
        c = 1.0 - (1.0 - c) * (1.0 - clamp(glow * GlowStrength, 0.0, 1.0));
    }

    c *= exp2(Exposure);

    float warm = Warmth - 0.25 * Mood;
    c *= vec3(1.0 + 0.10 * warm, 1.0 + 0.015 * warm, 1.0 - 0.12 * warm);

    if (TintStrength > 0.001) {
        // A colour filter that keeps brightness, acting mostly on shadows and mid-tones.
        vec3 filt = mix(vec3(1.0), Tint / max(dot(Tint, LUMA), 0.05), 0.5);
        float lum = dot(c, LUMA);
        float w = TintStrength * (1.0 - 0.6 * smoothstep(0.35, 0.95, lum));
        c = mix(c, c * filt, w);
    }

    float l = dot(c, LUMA);
    float sat = Saturation * (1.0 - 0.3 * Mood);
    // Vibrance-style saturation: muted colours gain more than already-saturated ones.
    float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    float satAmount = sat > 1.0 ? 1.0 + (sat - 1.0) * (1.0 - 0.5 * chroma) : sat;
    c = max(mix(vec3(l), c, satAmount), 0.0);

    float con = mix(Contrast, 1.0, 0.1 * Mood);
    c = (c - 0.5) * con + 0.5;

    if (SoftHighlights > 0.5) {
        c = vec3(shoulder(c.r), shoulder(c.g), shoulder(c.b));
    }

    if (Vignette > 0.001) {
        vec2 p = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
        float d = length(p) / length(vec2(OutSize.x / OutSize.y, 1.0) * 0.5);
        c *= 1.0 - Vignette * 0.65 * smoothstep(0.45, 1.05, d);
    }

    // Tiny noise against banding in smooth skies and fog.
    c += (hash(gl_FragCoord.xy) - 0.5) / 255.0;

    fragColor = vec4(clamp(c, 0.0, 1.0), src.a);
}
