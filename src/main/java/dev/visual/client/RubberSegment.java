package dev.visual.client;

import java.util.ArrayDeque;

final class RubberSegment {
    private static final double DILATE_SECONDS = 0.15;
    private final Edge left = new Edge();
    private final Edge right = new Edge();
    private final ArrayDeque<Sample> history = new ArrayDeque<>();
    private int selected = -1;
    private int pressed;
    private boolean held;
    private boolean onThumb;
    private boolean dragging;
    private float downX;
    private float offset;
    private float dragWidth;
    private long lastFrame;
    private long settleAt;
    private long relaxAt;
    private float squash;
    private float stretchLeft;
    private float stretchRight;

    void update(int value, long now) {
        if (selected < 0) {
            selected = value;
            left.jump(value);
            right.jump(value + 1);
        } else if (!held && value != selected) {
            travel(value, now);
        }
        double dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000.0, 0.05);
        lastFrame = now;
        if (dragging || (held && onThumb)) {
            return;
        }
        if (settleAt > now) {
            left.step(stretchLeft, dt, 23, 0);
            right.step(stretchRight, dt, 23, 0);
        } else {
            if (relaxAt == 0 && settleAt != 0) {
                relaxAt = now + 170_000_000L;
            }
            float shift = now < relaxAt ? squash : 0;
            left.step(selected + Math.max(shift, 0), dt, 23, 8);
            right.step(selected + 1 + Math.min(shift, 0), dt, 23, 8);
        }
    }

    void press(float x, long now) {
        held = true;
        dragging = false;
        onThumb = x >= left.value && x <= right.value;
        downX = x;
        pressed = x < 1 ? 0 : 1;
        offset = x - (float) left.value;
        dragWidth = (float) (right.value - left.value);
        history.clear();
        remember(x, now);
    }

    void drag(float x, long now) {
        if (!held || !onThumb) return;
        remember(x, now);
        if (!dragging && Math.abs(x - downX) < 0.12f) return;
        dragging = true;
        float l = x - offset;
        float max = 2 - dragWidth;
        if (l < 0) {
            left.jump(0);
            right.jump(dragWidth - rubber(-l, dragWidth));
        } else if (l > max) {
            right.jump(2);
            left.jump(max + rubber(l - max, dragWidth));
        } else {
            left.jump(l);
            right.jump(l + dragWidth);
        }
    }

    int release(float x, long now) {
        if (!held) return selected;
        if (dragging) {
            remember(x, now);
            Sample first = history.peekFirst();
            Sample last = history.peekLast();
            float velocity = first == null || last.time - first.time < 8_000_000L ? 0
                : (last.x - first.x) * 1_000_000_000f / (last.time - first.time);
            float projected = (float) ((left.value + right.value) * 0.5) + velocity * 0.075f;
            selected = projected < 1 ? 0 : 1;
            settleAt = 0;
            relaxAt = now + 170_000_000L;
            squash = Math.signum(velocity) * 0.08f;
            left.velocity = right.velocity = Math.clamp(velocity, -12, 12);
        } else if (Math.abs(x - downX) <= 0.30f) {
            travel(pressed, now);
        }
        held = false;
        dragging = false;
        history.clear();
        return selected;
    }

    void cancel() {
        held = false;
        dragging = false;
        settleAt = 0;
        relaxAt = 0;
        squash = 0;
        left.velocity = right.velocity = 0;
        history.clear();
    }

    private void travel(int to, long now) {
        if (selected == to) return;
        int from = selected;
        selected = to;
        stretchLeft = Math.min(from, to);
        stretchRight = Math.max(from, to) + 1;
        squash = Math.signum(to - from) * 0.08f;
        settleAt = now + (long) (DILATE_SECONDS * 1_000_000_000L);
        relaxAt = 0;
    }

    float left() { return Math.clamp((float) left.value, 0, 2); }
    float right() { return Math.clamp((float) right.value, left(), 2); }
    private void remember(float x, long now) {
        history.addLast(new Sample(x, now));
        while (history.size() > 2 && now - history.peekFirst().time > 100_000_000L) {
            history.removeFirst();
        }
    }

    private static float rubber(float over, float dimension) {
        return over * dimension * 0.55f / (dimension + 0.55f * over);
    }

    private record Sample(float x, long time) {}

    private static final class Edge {
        double value;
        double velocity;

        void jump(double target) {
            value = target;
            velocity = 0;
        }

        void step(double target, double dt, double decay, double frequency) {
            double displacement = value - target;
            double exponential = Math.exp(-decay * dt);
            if (frequency == 0) {
                double impulse = velocity + decay * displacement;
                value = target + (displacement + impulse * dt) * exponential;
                velocity = (velocity - decay * impulse * dt) * exponential;
            } else {
                double swing = (velocity + decay * displacement) / frequency;
                double cosine = Math.cos(frequency * dt);
                double sine = Math.sin(frequency * dt);
                double wave = displacement * cosine + swing * sine;
                value = target + exponential * wave;
                velocity = exponential * (frequency * (swing * cosine - displacement * sine) - decay * wave);
            }
        }
    }
}
