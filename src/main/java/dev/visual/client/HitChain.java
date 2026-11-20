package dev.visual.client;

import java.util.UUID;

final class HitChain {
    private static final int COMBO_WINDOW_TICKS = 24;
    enum Kind { NORMAL, COMBO, CRITICAL }
    private UUID target;
    private long lastTick;
    private int count;

    Kind record(UUID nextTarget, long tick, boolean critical, int threshold) {
        boolean continues = nextTarget.equals(target) && tick >= lastTick && tick - lastTick <= COMBO_WINDOW_TICKS;
        count = continues ? count + 1 : 1;
        target = nextTarget;
        lastTick = tick;
        return critical ? Kind.CRITICAL : count >= threshold ? Kind.COMBO : Kind.NORMAL;
    }

    void reset() {
        target = null;
        count = 0;
        lastTick = 0;
    }
}
