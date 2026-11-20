#version 330 core

layout(location = 0) in vec2 a0;
layout(location = 1) in vec2 a1;
layout(location = 2) in vec2 a2;
layout(location = 3) in vec4 a3;

uniform mat4 uProjection;

out vec2 vUv;
flat out vec2 vLayer;
flat out vec4 vTint;

void main() {
    vUv = a1;
    vLayer = a2;
    vTint = a3;
    gl_Position = uProjection * vec4(a0, 0.0, 1.0);
}
