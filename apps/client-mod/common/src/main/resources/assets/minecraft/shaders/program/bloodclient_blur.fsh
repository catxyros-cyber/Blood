#version 150

// Glow, steps 2-3: separable 9-tap Gaussian using 5 bilinear samples (run horizontally, then
// vertically). BlurDir picks the direction; BlurRadius widens the kernel for the second round.

uniform sampler2D DiffuseSampler;
uniform vec2 BlurDir;
uniform float BlurRadius;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec2 step = BlurDir * oneTexel * BlurRadius;
    vec3 c = texture(DiffuseSampler, texCoord).rgb * 0.2270270270;
    c += texture(DiffuseSampler, texCoord + step * 1.3846153846).rgb * 0.3162162162;
    c += texture(DiffuseSampler, texCoord - step * 1.3846153846).rgb * 0.3162162162;
    c += texture(DiffuseSampler, texCoord + step * 3.2307692308).rgb * 0.0702702703;
    c += texture(DiffuseSampler, texCoord - step * 3.2307692308).rgb * 0.0702702703;
    fragColor = vec4(c, 1.0);
}
