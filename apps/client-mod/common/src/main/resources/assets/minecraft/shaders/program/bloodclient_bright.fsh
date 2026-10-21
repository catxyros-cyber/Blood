#version 150

// Glow, step 1: downsample the frame into a small target and keep only the bright parts.
// Four bilinear taps one texel apart average a 4x4 block, so small bright pixels don't sparkle.

uniform sampler2D DiffuseSampler;
uniform float GlowThreshold;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec2 o = oneTexel;
    vec3 c = texture(DiffuseSampler, texCoord + vec2(-o.x, -o.y)).rgb
           + texture(DiffuseSampler, texCoord + vec2( o.x, -o.y)).rgb
           + texture(DiffuseSampler, texCoord + vec2(-o.x,  o.y)).rgb
           + texture(DiffuseSampler, texCoord + vec2( o.x,  o.y)).rgb;
    c *= 0.25;
    float peak = max(c.r, max(c.g, c.b));
    // Soft knee: fades in over 0.2 above the threshold instead of switching on.
    float w = smoothstep(GlowThreshold, GlowThreshold + 0.2, peak);
    fragColor = vec4(c * w, 1.0);
}
