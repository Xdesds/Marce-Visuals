package dev.visual.client;

import java.util.UUID;

public final class HitChainCheck {
    public static void main(String[] args) {
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        HitChain chain = new HitChain();
        expect(chain.record(a, 0, false, 3), HitChain.Kind.NORMAL);
        expect(chain.record(a, 20, false, 3), HitChain.Kind.NORMAL);
        expect(chain.record(a, 44, false, 3), HitChain.Kind.COMBO);
        expect(chain.record(a, 64, true, 3), HitChain.Kind.CRITICAL);
        expect(chain.record(a, 84, false, 3), HitChain.Kind.COMBO);
        expect(chain.record(b, 85, false, 3), HitChain.Kind.NORMAL);
        expect(chain.record(b, 110, false, 3), HitChain.Kind.NORMAL);
        expect(chain.record(b, 120, false, 3), HitChain.Kind.NORMAL);
        chain.reset();
        expect(chain.record(b, 121, false, 2), HitChain.Kind.NORMAL);
        expect(chain.record(b, 130, false, 2), HitChain.Kind.COMBO);
        expect(chain.record(b, 20, false, 2), HitChain.Kind.NORMAL);
        expect(chain.record(a, 21, true, 5), HitChain.Kind.CRITICAL);
        System.out.println("Hit chain checks passed: threshold, timeout, target change, critical priority, reset and time rollback.");
    }

    private static void expect(HitChain.Kind actual, HitChain.Kind expected) {
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
