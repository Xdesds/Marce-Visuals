#version 330
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
in vec3 surface;
in vec3 surfaceNormal;
in vec4 tint;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    float mask = texture(Sampler0, texCoord).a;
    if (mask < 0.1) discard;
    float time = GameTime * 24000.0;
    float pattern;
    vec3 color = tint.rgb;
#if HIT_MODE == 0
    pattern = 0.55 + 0.30 * sin(surface.y * 11.0 - time * 14.0);
#elif HIT_MODE == 1
    float flow = sin(surface.y * 8.0 + sin(surface.x * 9.0 + time * 3.0) - time * 6.0);
    pattern = 0.35 + 0.55 * smoothstep(-0.8, 0.9, flow);
    color = mix(color, min(color.brg + 0.18, vec3(1.0)), flow * 0.35 + 0.35);
#elif HIT_MODE == 2
    float rings = sin(length(surface.xz) * 20.0 + surface.y * 23.0 - time * 20.0);
    pattern = 0.16 + 0.84 * pow(max(rings, 0.0), 5.0);
#else
    vec3 cell = abs(fract(surface * 11.0 + vec3(time * 0.6, -time * 0.9, 0.0)) - 0.5);
    float seams = 1.0 - smoothstep(0.035, 0.09, min(cell.x, min(cell.y, cell.z)));
    pattern = 0.22 + seams * 0.72;
    color = mix(color, vec3(1.0), seams * 0.45);
#endif
    float light = 0.85 + 0.15 * abs(dot(normalize(surfaceNormal), normalize(vec3(0.4, 1.0, 0.5))));
    fragColor = vec4(color * light, tint.a * pattern * mask);
}
