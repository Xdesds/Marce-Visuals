package dev.visual.client;

import java.util.UUID;

/** Combo state is per target, with critical hits taking visual priority. */
final class HitChain {
    enum Kind { NORMAL, COMBO, CRITICAL }
    private UUID target;
    private long lastTick;
    private int count;

    Kind record(UUID nextTarget, long tick, boolean critical, int threshold) {
        count = nextTarget.equals(target) && tick >= lastTick && tick - lastTick <= 24 ? count + 1 : 1;
        target = nextTarget;
        lastTick = tick;
        return critical ? Kind.CRITICAL : count >= threshold ? Kind.COMBO : Kind.NORMAL;
    }

    void reset() { target = null; count = 0; lastTick = 0; }
}
