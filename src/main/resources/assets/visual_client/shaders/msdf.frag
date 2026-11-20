#version 330 core

in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAtlas;
uniform vec2 uUnitRange;

out vec4 fragColor;

float median(float red, float green, float blue) {
    return max(min(red, green), min(max(red, green), blue));
}

void main() {
    vec3 distances = texture(uAtlas, vUv).rgb;
    vec2 screenTexels = 1.0 / max(fwidth(vUv), vec2(1e-6));
    float screenRange = max(0.5 * dot(uUnitRange, screenTexels), 1.0);
    float distance = median(distances.r, distances.g, distances.b) - 0.5;
    float coverage = clamp(distance * screenRange + 0.5, 0.0, 1.0);
    if (coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(vColor.rgb, coverage * vColor.a);
}
