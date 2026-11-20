#version 330 core

layout(location = 0) in vec2 a0;
layout(location = 1) in vec2 a1;

uniform mat4 uProjection;

out vec2 vLocal;

void main() {
    vLocal = a1;
    gl_Position = uProjection * vec4(a0, 0.0, 1.0);
}
