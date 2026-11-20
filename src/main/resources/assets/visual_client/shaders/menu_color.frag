#version 330 core

in vec2 vLocal;
uniform vec3 uHsv;
uniform vec3 uField;

out vec4 fragColor;

vec3 hueColor(float hue) {
    vec3 rgb = abs(fract(hue + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0;
    return clamp(rgb, 0.0, 1.0);
}

void main() {
    vec2 size = uField.xy;
    vec2 pixel = vLocal * size;
    float radius = uField.z > 0.5 ? size.y * 0.5 : 5.0;
    vec2 corner = abs(pixel - size * 0.5) - size * 0.5 + radius;
    float distanceToEdge = length(max(corner, 0.0)) + min(max(corner.x, corner.y), 0.0) - radius;
    float aa = max(fwidth(distanceToEdge) * 0.7, 0.01);
    float coverage = 1.0 - smoothstep(-aa, aa, distanceToEdge);

    vec3 color;
    vec2 marker;
    if (uField.z > 0.5) {
        color = hueColor(vLocal.x);
        marker = vec2(clamp(uHsv.x * size.x, 3.0, size.x - 3.0), size.y * 0.5);
    } else {
        color = mix(vec3(1.0), hueColor(uHsv.x), vLocal.x) * (1.0 - vLocal.y);
        marker = clamp(vec2(uHsv.y, 1.0 - uHsv.z) * size, vec2(3.5), size - 3.5);
    }
    float ring = length(pixel - marker);
    color = mix(color, vec3(0.08), 1.0 - smoothstep(3.0, 3.9, ring));
    color = mix(color, vec3(1.0), (1.0 - smoothstep(2.9, 3.5, ring)) * smoothstep(1.5, 2.3, ring));
    fragColor = vec4(color, coverage);
}
