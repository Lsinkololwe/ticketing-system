package com.pml.shared.event;

import reactor.core.publisher.Mono;

/**
 * A message the platform has stopped trying to handle, and why.
 *
 * <h2>The reason is the whole point</h2>
 * A dead letter carries <b>a reason and the failing consumer's name</b>, and the admin
 * transaction-recovery queue is what consumes them. A dead letter with no reason is a message an operator
 * can see, cannot triage, and will either replay blindly or leave alone — both of which are
 * worse than the failure that produced it.
 *
 * <p>The consumer name matters as much as the reason. One subscription carries several wire
 * names and several handlers; "deserialisation failed" identifies a bug only once you know
 * which of them said it.</p>
 *
 * @param eventId      the envelope's id — the key a replay is idempotent on
 * @param eventType    the {@link EventType} wire name
 * @param consumerName the handler that gave up, not the subscription it arrived on
 * @param subscription the Service Bus subscription, so depth can be attributed
 * @param reason       short and machine-readable; the Service Bus dead-letter *reason* field
 * @param description  the failure's own message, for a human reading the recovery queue
 */
public record DeadLetter(String eventId,
                         String eventType,
                         String consumerName,
                         String subscription,
                         String reason,
                         String description) {

    /** Reason codes, kept closed so the recovery queue can group by them. */
    public static final String RETRIES_EXHAUSTED = "RETRIES_EXHAUSTED";
    public static final String PERMANENT_FAILURE = "PERMANENT_FAILURE";
    /**
     * A consumer receiving an envelope whose {@code schemaVersion} is older than the N−1 it
     * supports dead-letters with this reason rather than partially applying the payload. It is a
     * dead-letter reason, not an {@code ErrorCode}: nothing crosses the API boundary here.
     */
    public static final String UNSUPPORTED_SCHEMA_VERSION = "UNSUPPORTED_SCHEMA_VERSION";

    public static DeadLetter from(EventEnvelope envelope,
                                  String consumerName,
                                  String subscription,
                                  Throwable failure) {
        boolean permanent = failure instanceof PermanentFailure;
        return new DeadLetter(
                envelope.eventId(),
                envelope.eventType(),
                consumerName,
                subscription,
                permanent ? PERMANENT_FAILURE : RETRIES_EXHAUSTED,
                // The class name as well as the message: a NullPointerException's message is
                // often null, and "null" alone tells an operator nothing at all.
                failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    /**
     * Where a dead letter goes.
     *
     * <p>An interface because the implementation calls {@code deadLetter(reason, description)} on
     * the Azure receive context, and {@code shared-library} holds no Azure SDK — broker specifics
     * stay in the service that binds to the broker.</p>
     */
    @FunctionalInterface
    public interface Sink {
        Mono<Void> accept(DeadLetter letter);
    }
}
