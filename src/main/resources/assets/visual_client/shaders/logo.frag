#version 330 core

in vec2 vUv;
flat in vec2 vLayer;
flat in vec4 vTint;

uniform sampler2D uLogo;
out vec4 fragColor;

void main() {
    vec4 color = texture(uLogo, vUv);
    if (vLayer.x > 0.5) {
        // These curves separate the artwork along the gap and along the left ring's painted edge.
        vec2 p = vUv * 1254.0;
        float x = p.x - 250.0;
        float divider = 885.0 - 0.16 * x - 0.00029 * x * x;
        float leftEdge = 950.0 - 0.184 * x - 0.000735 * x * x;
        float rightEdge = min(950.0 + 0.72 * (p.y - 310.0), 1044.0 - 0.68 * (p.y - 440.0));
        float leftRing = 0.0;
        if (p.x < 165.0 && p.y > 745.0) {
            leftRing = 1.0;
        } else if (p.x < 425.0 && p.y > 875.0) {
            leftRing = 1.0 - smoothstep(leftEdge - 2.0, leftEdge + 2.0, p.y);
        }
        bool rightRing = p.x > max(950.0, rightEdge) && p.y < divider;
        float ring = rightRing ? 1.0 : leftRing;
        float upper = p.y < divider ? 1.0 : 0.0;
        float mask = vLayer.x < 1.5 ? upper * (1.0 - ring)
            : vLayer.x < 2.5 ? (1.0 - upper) * (1.0 - ring) : ring;
        color.a *= mask;
    }
    color.a *= vLayer.y;
    if (color.a <= 0.002) {
        discard;
    }
    if (vTint.a > 0.0) {
        float shade = dot(color.rgb, vec3(0.65, 0.30, 0.05));
        color.rgb = vTint.rgb * mix(0.32, 1.0, smoothstep(0.24, 0.76, shade));
    }
    fragColor = color;
}
