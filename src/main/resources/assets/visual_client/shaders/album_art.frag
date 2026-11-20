#version 330 core

in vec2 vLocal;
uniform sampler2D uCover;
uniform vec2 uCrop;
uniform float uOpacity;

out vec4 fragColor;

void main() {
    vec2 centered = vLocal - 0.5;
    vec2 corner = abs(centered) - 0.26;
    float edge = length(max(corner, 0.0)) + min(max(corner.x, corner.y), 0.0) - 0.24;
    float aa = max(fwidth(edge) * 0.75, 0.001);
    float coverage = 1.0 - smoothstep(-aa, aa, edge);
    vec4 picture = texture(uCover, 0.5 + centered * uCrop);
    fragColor = vec4(picture.rgb, picture.a * coverage * uOpacity);
}
