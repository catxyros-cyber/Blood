#version 330

// Glow, step 1: downsample the frame into a small target and keep only the bright parts.
// Four bilinear taps one texel apart average a 4x4 block, so small bright pixels don't sparkle.
// Parameters arrive in DynamicTransforms (see PostEffects.java):
//   TextureMat[0] = (in width, in height, out width, out height), ModelViewMat[3].y = threshold.

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 o = 1.0 / TextureMat[0].xy;
    float threshold = ModelViewMat[3].y;
    vec3 c = texture(InSampler, texCoord + vec2(-o.x, -o.y)).rgb
           + texture(InSampler, texCoord + vec2( o.x, -o.y)).rgb
           + texture(InSampler, texCoord + vec2(-o.x,  o.y)).rgb
           + texture(InSampler, texCoord + vec2( o.x,  o.y)).rgb;
    c *= 0.25;
    float peak = max(c.r, max(c.g, c.b));
    // Soft knee: fades in over 0.2 above the threshold instead of switching on.
    float w = smoothstep(threshold, threshold + 0.2, peak);
    fragColor = vec4(c * w, 1.0);
}
