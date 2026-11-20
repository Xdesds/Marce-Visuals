package dev.visual.client;

record JellyAppearance(float pivotX, float pivotY, float scaleX, float scaleY, float offsetY) {
    static JellyAppearance at(float x, float y, long started, long now, boolean enabled) {
        float time = Math.max(0, (now - started) / 1_000_000_000f);
        if (!enabled || time >= 0.5f) {
            return new JellyAppearance(x, y, 1, 1, 0);
        }
        float settle = (float) (Math.exp(-12 * time) * Math.cos(23 * time));
        float wobble = (float) (Math.exp(-11 * time) * Math.sin(23 * time));
        return new JellyAppearance(x, y, 1 - 0.08f * settle + 0.025f * wobble,
            1 - 0.16f * settle - 0.04f * wobble, 5 * settle);
    }

    float x(float value) { return pivotX + (value - pivotX) * scaleX; }
    float y(float value) { return pivotY + (value - pivotY) * scaleY + offsetY; }
    double inverseX(double value) { return pivotX + (value - pivotX) / scaleX; }
    double inverseY(double value) { return pivotY + (value - pivotY - offsetY) / scaleY; }
    float contentScale() { return (float) Math.sqrt(scaleX * scaleY); }
}
