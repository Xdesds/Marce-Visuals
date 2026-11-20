#version 330 core

in vec2 vLocal;
uniform sampler2D uBackdrop;
uniform vec2 uCopyOrigin;
uniform vec2 uCopySize;
uniform vec2 uTextureSize;
uniform vec2 uRadiusPixels;
uniform vec2 uShapeSize;
uniform vec2 uCorners;
uniform vec3 uWave; // сила баса, сила удара, фаза
uniform float uOpacity;

out vec4 fragColor;

vec3 backdrop(vec2 pixel) {
    return texture(uBackdrop, clamp(pixel, vec2(0.5), uCopySize - 0.5) / uTextureSize).rgb;
}

void main() {
    vec2 halfSize = uShapeSize * 0.5;
    vec2 local = vLocal;
    if (uWave.x + uWave.y > 0.001) {
        float bass = uWave.x;
        float hit = uWave.y;
        float phase = uWave.z;
        float horizontal = (vLocal.x - halfSize.x) / halfSize.x;
        float side = sign(horizontal);
        float sideWeight = smoothstep(0.2, 0.92, abs(horizontal));
        float lower = smoothstep(0.0, halfSize.y, vLocal.y);
        float sideSwell = 1.45 + 1.15 * sin(vLocal.y * 0.18 + phase * 2.0 + side * 0.75);
        float sidePulse = 0.95 + 0.50 * sin(vLocal.y * 0.34 - phase * 3.0 + side);
        local.x -= side * sideWeight * lower * (bass * sideSwell + hit * sidePulse);

        float bottomSwell = 1.15 + 1.05 * sin(vLocal.x * 0.092 - phase * 2.0);
        float bottomPulse = 0.78 + 0.50 * cos(vLocal.x * 0.19 + phase * 3.0);
        local.y -= lower * (bass * bottomSwell + hit * bottomPulse);
    }
    float radius = local.y < halfSize.y ? uCorners.x : uCorners.y;
    vec2 corner = abs(local - halfSize) - halfSize + radius;
    float edge = length(max(corner, 0.0)) + min(max(corner.x, corner.y), 0.0) - radius;
    float aa = max(fwidth(edge) * 0.75, 0.01);
    float coverage = 1.0 - smoothstep(-aa, aa, edge);
    if (coverage <= 0.0) {
        discard;
    }

    vec2 p = gl_FragCoord.xy - uCopyOrigin;
    vec2 d = uRadiusPixels;
    vec3 blurred = backdrop(p) * 0.20;
    blurred += (backdrop(p + vec2(d.x, 0)) + backdrop(p - vec2(d.x, 0))
              + backdrop(p + vec2(0, d.y)) + backdrop(p - vec2(0, d.y))) * 0.12;
    blurred += (backdrop(p + d * 0.72) + backdrop(p - d * 0.72)
              + backdrop(p + vec2(d.x, -d.y) * 0.72)
              + backdrop(p + vec2(-d.x, d.y) * 0.72)) * 0.08;

    vec3 charcoal = vec3(0.058, 0.053, 0.071);
    fragColor = vec4(mix(charcoal, blurred, 0.20), coverage * uOpacity);
}
