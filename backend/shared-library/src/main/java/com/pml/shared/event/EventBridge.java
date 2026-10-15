package com.pml.shared.event;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * The one place a message is handed to the broker.
 *
 * <h2>Why publishing has exactly one home</h2>
 * Scattered sends make two rules unenforceable. The first is that a send must never happen inside a
 * transaction — a message published from an uncommitted transaction announces something that may
 * never become true, and there is no unsend. The second is that every message carries the ordering
 * key its event type implies. Both are trivial to check at one call site and impossible to check at
 * fourteen.
 *
 * <h2>The Outbox is what calls this, not application code</h2>
 * A service stages an envelope inside its transaction and returns. The drain calls this
 * afterwards, outside any transaction, and marks the row sent only once this completes. That
 * ordering is the whole design: application code cannot publish, so it cannot publish early.
 *
 * <h2>A failed publish is an error, not a warning</h2>
 * The transport completes only when the broker has confirmed the message. A refusal or a timeout
 * becomes {@link EventPublicationFailed}, so the outbox row stays PENDING and the message stays owed.
 */
@Slf4j
public class EventBridge {

    /** {@code eventType} as a message header, which tooling that reads headers rather than routing keys uses. */
    public static final String EVENT_TYPE_HEADER = AmqpEventTransport.EVENT_TYPE_HEADER;

    /** The header that carries the key events of one aggregate are ordered on. */
    public static final String ORDERING_KEY_HEADER = AmqpEventTransport.ORDERING_KEY_HEADER;

    private final EventTransport transport;
    private final String exchange;

    public EventBridge(EventTransport transport, String exchange) {
        this.transport = transport;
        this.exchange = exchange;
    }

    /**
     * Publishes with the ordering key the event type's ordering implies.
     *
     * <p>Every message carries one, and which one is the whole point. An <b>ordered</b> row uses the
     * aggregate's id, so only that aggregate's events are held in order. A <b>commutative</b> row uses
     * its own {@code eventId}, which is unique, so it is its own key and constrains nothing.</p>
     */
    public Mono<Void> publish(EventEnvelope envelope) {
        Optional<String> orderingKey = EventType.ofWireName(envelope.eventType())
                .flatMap(EventType::orderingKey);

        if (orderingKey.isEmpty()) {
            // Commutative: its own id, unique, so it is its own key and blocks nothing.
            return publish(envelope, envelope.eventId());
        }

        Object aggregateId = envelope.payload().get(orderingKey.get());
        if (aggregateId == null || aggregateId.toString().isBlank()) {
            // Refused rather than sent. String.valueOf(null) is "null" — a perfectly valid key.
            // Every ordered message missing its key would then share one key called "null": ordered
            // against each other, and against nothing they are actually related to. The send succeeds
            // and the ordering guarantee is silently inverted, which is worse than not having it.
            return Mono.error(new MissingOrderingKey(envelope, orderingKey.get()));
        }

        return publish(envelope, aggregateId.toString());
    }

    /** Raised when an ordered event type arrives without the payload key that orders it. */
    public static class MissingOrderingKey extends RuntimeException implements PermanentFailure {
        public MissingOrderingKey(EventEnvelope envelope, String key) {
            super("%s is ordered on '%s' (ET-PLT-003 §4) but %s carries no such payload key"
                    .formatted(envelope.eventType(), key, envelope.eventId()));
        }
    }

    /** @param orderingKey the key the message is ordered on; never null */
    public Mono<Void> publish(EventEnvelope envelope, String orderingKey) {
        return transport.send(exchange, envelope.eventType(), envelope, orderingKey);
    }

    /** Raised when the broker refuses or does not confirm a message, so the outbox row stays PENDING. */
    public static class EventPublicationFailed extends RuntimeException {
        public EventPublicationFailed(String exchange, EventEnvelope envelope) {
            super("The broker refused %s (%s) on exchange %s"
                    .formatted(envelope.eventType(), envelope.eventId(), exchange));
        }

        public EventPublicationFailed(String exchange, EventEnvelope envelope, Throwable cause) {
            super("The broker did not confirm %s (%s) on exchange %s: %s"
                    .formatted(envelope.eventType(), envelope.eventId(), exchange, cause.getMessage()), cause);
        }
    }
}
