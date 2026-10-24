#version 330

// Glow: separable 9-tap Gaussian using 5 bilinear samples (run horizontally, then vertically).
//   ColorModulator.xy = direction, ColorModulator.z = radius, TextureMat[0].xy = input size.

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 step = ColorModulator.xy * (1.0 / TextureMat[0].xy) * ColorModulator.z;
    vec3 c = texture(InSampler, texCoord).rgb * 0.2270270270;
    c += texture(InSampler, texCoord + step * 1.3846153846).rgb * 0.3162162162;
    c += texture(InSampler, texCoord - step * 1.3846153846).rgb * 0.3162162162;
    c += texture(InSampler, texCoord + step * 3.2307692308).rgb * 0.0702702703;
    c += texture(InSampler, texCoord - step * 3.2307692308).rgb * 0.0702702703;
    fragColor = vec4(c, 1.0);
}
