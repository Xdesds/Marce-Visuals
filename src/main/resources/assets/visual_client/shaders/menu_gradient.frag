#version 330 core

in vec2 vLocal;
uniform vec3 uPanel;
uniform vec3 uAccent;
uniform vec3 uMotion;
uniform vec2 uMouse;
uniform vec3 uClick;
uniform vec2 uStyle;
out vec4 fragColor;

vec2 hash2(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.103, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.xx + q.yz) * q.zy) * 2.0 - 1.0;
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = dot(hash2(i), f);
    float b = dot(hash2(i + vec2(1, 0)), f - vec2(1, 0));
    float c = dot(hash2(i + vec2(0, 1)), f - vec2(0, 1));
    float d = dot(hash2(i + vec2(1)), f - vec2(1));
    return 0.5 + 0.5 * mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

vec2 drift(vec2 p, float t) {
    vec2 w1 = vec2(
        noise(p * 0.85 + vec2(t * 0.11, t * 0.07)),
        noise(p * 0.85 + vec2(4.1, 1.7) - vec2(t * 0.08, t * 0.12))
    );
    vec2 w2 = vec2(
        noise(p * 1.55 + w1 * 1.8 + vec2(1.3, 7.1) + t * 0.05),
        noise(p * 1.55 + w1 * 1.8 + vec2(6.4, 2.2) - t * 0.06)
    );
    return (w1 - 0.5) * 1.1 + (w2 - 0.5) * 0.55;
}

float field(vec2 p) {
    vec2 direction = vec2(0.573576, 0.819152); // 55 градусов
    vec2 flow = vec2(-direction.y, direction.x) * uMotion.x * 0.45;
    vec2 w = p + drift(p + flow + vec2(1.3, 3.4), uMotion.x) * 0.8;
    return clamp(dot(w, direction) / 1.25 + 0.5, 0.0, 1.0);
}

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

void main() {
    vec2 size = uPanel.xy;
    vec2 pixel = vLocal * size;
    vec2 corner = abs(pixel - size * 0.5) - size * 0.5 + uPanel.z;
    float edge = length(max(corner, 0.0)) + min(max(corner.x, corner.y), 0.0) - uPanel.z;
    float coverage = 1.0 - smoothstep(-max(fwidth(edge) * 0.7, 0.01), max(fwidth(edge) * 0.7, 0.01), edge);
    if (coverage <= 0.0) discard;

    vec2 aspect = vec2(size.x / size.y, 1.0);
    vec2 p = (vLocal - 0.5) * aspect;
    float ripple = 0.0;
    vec2 pointer = (uMouse - 0.5) * aspect;
    vec2 delta = p - pointer;
    p += vec2(-delta.y, delta.x) * exp(-dot(delta, delta) / 0.18) * 0.24 * uMotion.z;
    if (uClick.z >= 0.0 && uClick.z < 2.6) {
        vec2 pivot = (uClick.xy - 0.5) * aspect;
        vec2 d = p - pivot;
        float distanceFromClick = length((vLocal - uClick.xy) * aspect);
        float front = distanceFromClick - uClick.z * 1.8;
        float envelope = smoothstep(0.0, 0.045, uClick.z) * exp(-uClick.z * 3.8);
        ripple = exp(-front * front / 0.00065) * envelope;
        float wake = sin(front * 38.0) * exp(-front * front / 0.018) * envelope;
        p += d / max(length(d), 0.001) * wake * 0.035;
    }

    vec3 hue = uAccent / max(max(uAccent.r, uAccent.g), max(uAccent.b, 0.01));
    vec3 ramp[5];
    ramp[0] = mix(vec3(0.070, 0.058, 0.082), hue * 0.10, 0.25);
    ramp[1] = hue * 0.16;
    ramp[2] = hue * 0.43;
    ramp[3] = mix(hue, uAccent, 0.25);
    ramp[4] = mix(uAccent, vec3(1.0), 0.55);
    float raw = field(p);
    float scaled = raw * 4.0;
    int index = min(int(floor(scaled)), 3);
    float fraction = smoothstep(0.0, 1.0, scaled - float(index));
    vec3 color = mix(ramp[index], ramp[index + 1], fraction);

    vec2 gradient = vec2(field(p + vec2(0.004, 0)) - raw, field(p + vec2(0, 0.004)) - raw) / 0.004;
    vec3 normal = normalize(vec3(-gradient * sin(3.141593 * raw) * 1.4, 1.0));
    color *= 1.0 + 0.35 * (dot(normal, normalize(vec3(-0.82, 0.57, 1.2))) - 0.77);
    float reveal = uMotion.z > 0.5 ? smoothstep(0.0, 1.6, uMotion.x) : 1.0;
    color = mix(ramp[0], color, reveal);
    vec2 grainPixel = gl_FragCoord.xy / 1.2 + floor(uMotion.x * 24.0) * vec2(17.0, 59.0);
    float grain = hash(grainPixel) + hash(grainPixel + 7.13) - 1.0;
    color += grain * uMotion.y * 0.30;
    // Приглушаем градиент, чтобы он не мешал читать текст.
    color = mix(vec3(0.045, 0.040, 0.052), color, 0.34);
    color = mix(vec3(0.067, 0.067, 0.082), color, uStyle.x);
    color += uAccent * ripple * 0.19;
    fragColor = vec4(clamp(color, 0.0, 1.0), coverage * mix(0.882, 0.90, uStyle.x));
}
