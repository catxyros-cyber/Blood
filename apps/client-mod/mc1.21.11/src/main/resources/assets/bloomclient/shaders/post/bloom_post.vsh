#version 330

// BLOOD Client post-processing: full-screen quad already in clip space.

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
}
