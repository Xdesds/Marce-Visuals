package dev.visual.client;

final class IslandDock {
    private static final double DECAY = 11.5;
    private static final double FREQUENCY = 18.5;
    private double position;
    private double velocity;

    float update(float target, double seconds) {
        double displacement = position - target;
        double swing = (velocity + DECAY * displacement) / FREQUENCY;
        double sin = Math.sin(FREQUENCY * seconds);
        double cos = Math.cos(FREQUENCY * seconds);
        double decay = Math.exp(-DECAY * seconds);

        double wave = displacement * cos + swing * sin;
        position = target + decay * wave;
        velocity = decay * (FREQUENCY * (swing * cos - displacement * sin) - DECAY * wave);

        if (position < 0) {
            reset();
        } else if (Math.abs(position - target) < 0.05 && Math.abs(velocity) < 0.5) {
            position = target;
            velocity = 0;
        }
        return (float) position;
    }

    void reset() {
        position = 0;
        velocity = 0;
    }

    float stretch() {
        return (float) Math.min(Math.abs(velocity) / 300, 1);
    }
}
