package com.pml.shared.event;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * The one path a consumed message takes: guarded, retried, and dead-lettered rather than lost.
 *
 * <h2>What a handler is spared</h2>
 * A consumer written against this supplies only its effect. Duplicate suppression, the
 * retry budget, the decision to give up, the reason recorded when it does, and the metric an
 * operator is paged on all happen here — once — rather than being reimplemented by fourteen
 * handlers with fourteen slightly different opinions about what counts as transient.
 *
 * <h2>Why exhaustion dead-letters explicitly instead of rethrowing</h2>
 * No bus consumer rethrows a transient failure. Letting the
 * exception reach the binder would dead-letter the message eventually, after
 * {@code maxDeliveryCount} redeliveries — but with the broker's own reason,
 * {@code MaxDeliveryCountExceeded}, which names no consumer and describes no failure. So the
 * error is caught, converted to a {@link DeadLetter} carrying the failure that actually
 * happened, and the message is settled deliberately.
 *
 * <h2>The two retry layers are not redundant</h2>
 * This one covers a failure the process can see: a provider blip, a lock contention, a
 * reconnect. The broker's {@code maxDeliveryCount} covers the failure it cannot — the process
 * dying mid-handler, where nothing settles the message and only redelivery recovers it. Removing
 * either leaves a real gap, which is why both are explicit.
 *
 * <h2>Dead-lettering is not the same as handling</h2>
 * The idempotency marker is written only when the work succeeds. A dead letter that transaction
 * recovery later replays must run the work, not be skipped as already seen — and since the marker's
 * authority is the consumer's own state, a replay after a partial effect still converges.
 */
@Slf4j
public final class ConsumerDispatch {

    /** What became of one delivery. Returned rather than logged so tests can assert on it. */
    public enum Outcome {
        /** The work ran and the durable marker was written. */
        HANDLED,
        /** A redelivery of something this consumer had already done. The work did not run. */
        DUPLICATE,
        /** Gave up. The message is in the dead-letter queue with a reason. */
        DEAD_LETTERED
    }

    private final ConsumerGuard guard;
    private final RetryBudget budget;
    private final DeadLetter.Sink deadLetters;
    private final DeadLetterDepth depth;
    private final String consumerName;
    private final String subscription;

    public ConsumerDispatch(ConsumerGuard guard,
                            RetryBudget budget,
                            DeadLetter.Sink deadLetters,
                            DeadLetterDepth depth,
                            String consumerName,
                            String subscription) {
        this.guard = guard;
        this.budget = budget;
        this.deadLetters = deadLetters;
        this.depth = depth;
        this.consumerName = consumerName;
        this.subscription = subscription;
    }

    /**
     * Delivers one envelope to {@code work}.
     *
     * <p>Never signals an error: every failure this can reach has already been recorded as a
     * dead letter, and an error escaping here is exactly the rethrow a bus consumer must not make.</p>
     */
    public Mono<Outcome> deliver(EventEnvelope envelope, Mono<Void> work) {
        return guard.runOnce(envelope, work.retryWhen(budget.toRetry()))
                .map(ran -> ran ? Outcome.HANDLED : Outcome.DUPLICATE)
                .onErrorResume(failure -> deadLetter(envelope, failure));
    }

    private Mono<Outcome> deadLetter(EventEnvelope envelope, Throwable failure) {
        DeadLetter letter = DeadLetter.from(envelope, consumerName, subscription, failure);

        log.error("[{}] giving up on {} ({}) — {}: {}",
                consumerName, envelope.eventType(), envelope.eventId(),
                letter.reason(), letter.description());

        return deadLetters.accept(letter)
                .doOnSuccess(ignored -> depth.produced(letter))
                .onErrorResume(sinkFailure -> {
                    // The sink itself failed. Do not rethrow: an error here would be redelivered
                    // and fail the same way. Leaving it unsettled is what the broker's
                    // maxDeliveryCount is for, and the counter still records that we gave up.
                    log.error("[{}] could not dead-letter {} — leaving it unsettled for redelivery",
                            consumerName, envelope.eventId(), sinkFailure);
                    depth.produced(letter);
                    return Mono.empty();
                })
                .thenReturn(Outcome.DEAD_LETTERED);
    }
}
