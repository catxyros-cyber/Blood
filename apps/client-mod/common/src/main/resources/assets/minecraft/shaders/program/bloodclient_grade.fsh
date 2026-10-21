#version 150

// BLOOD Client colour grading. One full-screen pass:
//   sharpen -> glow -> exposure -> white balance -> colour filter -> saturation -> contrast
//   -> soft highlights -> vignette -> dither
// Every uniform at its neutral value leaves the image unchanged (BLOOD skips the whole pass then).

uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;  // only bound by the glow chains; GlowStrength is 0 otherwise

uniform vec2 OutSize;
uniform float Exposure;      // stops, -1..1
uniform float Contrast;      // 1 = neutral
uniform float Saturation;    // 1 = neutral
uniform float Warmth;        // -1 cool .. 1 warm
uniform float TintR;
uniform float TintG;
uniform float TintB;
uniform float TintStrength;  // 0..1
uniform float Vignette;      // 0..1
uniform float Sharpen;       // 0..1
uniform float SoftHighlights;// 0 or 1
uniform float GlowStrength;  // 0..1.5
uniform float Mood;          // 0..1, rain/thunder

in vec2 texCoord;
in vec2 oneTexel;

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
    vec4 src = texture(DiffuseSampler, texCoord);
    vec3 c = src.rgb;

    if (Sharpen > 0.001) {
        vec3 n = texture(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0)).rgb
               + texture(DiffuseSampler, texCoord - vec2(oneTexel.x, 0.0)).rgb
               + texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y)).rgb
               + texture(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y)).rgb;
        vec3 detail = c - n * 0.25;
        // Limit the boost so edges get crisper without bright halos.
        c = clamp(c + clamp(detail, -0.08, 0.08) * Sharpen * 2.0, 0.0, 1.0);
    }

    if (GlowStrength > 0.001) {
        c += texture(BloomSampler, texCoord).rgb * GlowStrength;
    }

    c *= exp2(Exposure);

    float warm = Warmth - 0.25 * Mood;
    c *= vec3(1.0 + 0.10 * warm, 1.0 + 0.015 * warm, 1.0 - 0.12 * warm);

    if (TintStrength > 0.001) {
        // A colour filter that keeps brightness: multiply by the tint normalised to luminance 1
        // (softened, so strong colours like crimson don't swamp the image). It acts mostly on
        // shadows and mid-tones, the way colourists tint, so skies and highlights stay clean.
        vec3 tint = vec3(TintR, TintG, TintB);
        vec3 filt = mix(vec3(1.0), tint / max(dot(tint, LUMA), 0.05), 0.5);
        float lum = dot(c, LUMA);
        float w = TintStrength * (1.0 - 0.6 * smoothstep(0.35, 0.95, lum));
        c = mix(c, c * filt, w);
    }

    float l = dot(c, LUMA);
    float sat = Saturation * (1.0 - 0.3 * Mood);
    c = max(mix(vec3(l), c, sat), 0.0);

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
