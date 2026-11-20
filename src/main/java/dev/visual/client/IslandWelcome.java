package dev.visual.client;

import dev.visual.client.MarceLogo.Layer;

final class IslandWelcome {
    private static final float INTRO_WIDTH = 45;
    private static final float INTRO_HEIGHT = 36;
    private static final float TEXT_SIZE = 12;
    private static final String GREETING = "Hello, ";
    private static final MsdfDraw TYPE = MsdfDraw.INSTANCE;
    private static final MarceLogo LOGO = MarceLogo.INSTANCE;

    private boolean pending;
    private long started;
    private String name = "";

    void queue() {
        pending = true;
        started = 0;
    }

    void reset() {
        pending = false;
        started = 0;
        name = "";
    }

    Size render(int viewportWidth, float top, long now, String playerName, boolean ready,
                float compactWidth, int accent, float opacity, float stretch) {
        if (pending && ready) {
            name = playerName;
            started = now;
            pending = false;
        }
        if (started == 0) {
            return null;
        }

        float seconds = (now - started) / 1_000_000_000f;
        if (seconds >= 2.28f) {
            reset();
            return null;
        }

        float arrive = ease(progress(seconds, 0.02f, 0.34f));
        float orbit = progress(seconds, 0.29f, 0.70f);
        float slide = ease(progress(seconds, 0.69f, 1.01f));
        float reveal = ease(progress(seconds, 0.78f, 1.06f));
        float exit = ease(progress(seconds, 2.08f, 2.28f));

        float greetingWidth = TYPE.width(GREETING, TEXT_SIZE);
        float openWidth = Math.max(104, greetingWidth + TYPE.width(name, TEXT_SIZE) + 58);
        float width = mix(mix(INTRO_WIDTH, openWidth, slide), compactWidth, exit);
        float height = mix(INTRO_HEIGHT, DynamicIsland.COMPACT_HEIGHT, exit);
        float left = (viewportWidth - width) * 0.5f;
        float centerX = viewportWidth * 0.5f;
        float centerY = top + height * 0.5f + 0.5f;
        DynamicIsland.drawShell(left, top, width, height, stretch, 0, 0, 0);

        float logoX = mix(mix(centerX, left + width - 24, slide), centerX, exit);
        float logoWidth = mix(mix(27, 25, slide), DynamicIsland.LOGO_WIDTH, exit);
        if (orbit < 1) {
            float offset = 19 * (1 - arrive);
            float planetAlpha = ease(progress(seconds, 0, 0.12f)) * opacity;
            LOGO.layer(Layer.UPPER, logoX, centerY - offset, logoWidth, 0, planetAlpha, accent);
            LOGO.layer(Layer.LOWER, logoX, centerY + offset, logoWidth, 0, planetAlpha, accent);
            LOGO.layer(Layer.ORBIT, logoX, centerY, logoWidth, (float) (2 * Math.PI * ease(orbit)),
                ease(progress(seconds, 0.28f, 0.40f)) * opacity, accent);
        } else {
            LOGO.layer(Layer.FULL, logoX, centerY, logoWidth, 0, opacity, accent);
        }

        float greetingOpacity = reveal * (1 - ease(progress(seconds, 2.02f, 2.16f))) * opacity;
        if (greetingOpacity > 0) {
            float textX = left + 13 - (1 - reveal) * 4;
            TYPE.text(GREETING, textX, centerY, TEXT_SIZE, DynamicIsland.withOpacity(0xFFF4F1FF, greetingOpacity));
            TYPE.text(name, textX + greetingWidth, centerY, TEXT_SIZE, DynamicIsland.withOpacity(accent, greetingOpacity));
        }
        return new Size(width, height);
    }

    private static float progress(float time, float start, float end) {
        return Math.clamp((time - start) / (end - start), 0, 1);
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float mix(float from, float to, float t) {
        return from + (to - from) * t;
    }

    record Size(float width, float height) {}
}
