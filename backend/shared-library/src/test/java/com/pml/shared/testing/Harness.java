package com.pml.shared.testing;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

/**
 * Holds the {@link ReactiveMongoTemplate} the assertion helpers read through.
 *
 * <p>Tests call the helpers in the short form —
 * {@code Persistence.assertNothingPersisted("booking_reservations")},
 * {@code Inventory.assertConserved(tierId)}, {@code Ledger.assertBalanced()} —
 * so a test reads as the claim, not the plumbing. This is the plumbing, bound
 * once per test class.
 *
 * <p>Every helper also takes an explicit template, so a test that talks to two
 * databases never has to reach through this.
 */
public final class Harness {

    private static volatile ReactiveMongoTemplate template;

    private Harness() {
    }

    /** Bind the template the short-form assertions read. Call in {@code @BeforeEach}. */
    public static void bind(ReactiveMongoTemplate boundTemplate) {
        template = boundTemplate;
    }

    public static void unbind() {
        template = null;
    }

    static ReactiveMongoTemplate template() {
        ReactiveMongoTemplate bound = template;
        if (bound == null) {
            throw new IllegalStateException("""
                    No ReactiveMongoTemplate bound. Call Harness.bind(template) in @BeforeEach, \
                    or use the explicit two-argument form of the assertion.""");
        }
        return bound;
    }
}
