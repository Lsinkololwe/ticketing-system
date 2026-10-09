package com.pml.booking.infrastructure.temporal;

/**
 * The task queues booking's worker polls.
 *
 * <p>Every value here is a declared platform task queue and an entry under {@code spring.temporal.workers} in
 * this service's {@code application.yml}; {@code TemporalRegistryLintTest} holds the three together.
 * Annotations name these constants, never a literal, so a renamed queue cannot leave a worker
 * polling a name nothing starts work on.
 */
public final class TaskQueues {

    /** Purchase workflows and the checkout activities; latency-sensitive. */
    public static final String CHECKOUT = "booking-checkout";

    /** Every PawaPay call, behind one server-enforced rate limit. */
    public static final String PROVIDER = "booking-provider";

    /** Escrow, payouts, refunds and chargebacks. */
    public static final String FINANCE = "booking-finance";

    /** Reconciliation; few slots, so it never contends with the write path. */
    public static final String RECON = "booking-recon";

    private TaskQueues() {
    }
}
