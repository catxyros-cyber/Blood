#version 150

// BLOOD Client post-processing: shared full-screen vertex shader (same contract as vanilla's
// post programs: PostPass draws a quad from (0,0) to OutSize and sets ProjMat/InSize/OutSize).

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 InSize;
uniform vec2 OutSize;

out vec2 texCoord;
out vec2 oneTexel;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);
    oneTexel = 1.0 / InSize;
    texCoord = Position.xy / OutSize;
}
