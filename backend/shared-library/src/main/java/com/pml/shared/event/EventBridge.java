package com.pml.shared.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

/**
 * The one place a message is handed to Azure Service Bus.
 *
 * <h2>Why publishing has exactly one home</h2>
 * Scattered {@code StreamBridge.send} calls make two rules unenforceable. The first is that a
 * send must never happen inside a transaction — a message published from an uncommitted
 * transaction announces something that may never become true, and there is no unsend. The
 * second is that the binding name must match a bound topic; a send to a name nothing is bound to returns
 * {@code false} and the message evaporates. Both are trivial to check at one call site and
 * impossible to check at fourteen.
 *
 * <h2>The Outbox is what calls this, not application code</h2>
 * A service stages an envelope inside its transaction and returns. The drain calls this
 * afterwards, outside any transaction, and marks the row sent only once this completes. That
 * ordering is the whole design: application code cannot publish, so it cannot publish early.
 *
 * <h2>{@code StreamBridge.send} is blocking</h2>
 * It returns a boolean, not a publisher, so it is wrapped on {@code boundedElastic} — the one
 * adapter the reactive stack allows for a genuinely blocking SDK, contained here rather than
 * scattered. A {@code false} return is turned into an error so the outbox row stays PENDING;
 * left as a boolean it is the {@code log.warn} that loses the message.
 */
@Slf4j
public class EventBridge {

    /** {@code eventType} as a message header, which is what subscription SQL filters read. */
    public static final String EVENT_TYPE_HEADER = "eventType";

    /** Service Bus session id, for the event types that require per-entity ordering. */
    /**
     * The header the Azure binder reads as the Service Bus session id. Any other name travels as a plain
     * application property, the message goes out with no session id, and a subscription that requires sessions
     * (all of ours do) refuses it: Azure with an error, the emulator by stalling the send until it times out.
     */
    public static final String SESSION_ID_HEADER = "azure_service_bus_session_id";

    private final StreamBridge streamBridge;
    private final String binding;

    public EventBridge(StreamBridge streamBridge, String binding) {
        this.streamBridge = streamBridge;
        this.binding = binding;
    }

    /**
     * Publishes with the session id the event type's ordering implies.
     *
     * <p>Every message carries one, and which one is the whole point. An <b>ordered</b> row uses
     * the aggregate's id, so Service Bus serialises that key and only that key. A
     * <b>commutative</b> row uses its own {@code eventId}, which is unique — so it is its own
     * session and blocks nothing.</p>
     *
     * <p>The alternative, omitting the header on commutative rows, does not work: a
     * session-enabled subscription rejects a message with no session id, and four of the six
     * subscriptions carry a mix of ordered and commutative rows. Sending a unique id
     * is what lets one subscription serve both without serialising the traffic that had no
     * reason to wait.</p>
     */
    public Mono<Void> publish(EventEnvelope envelope) {
        Optional<String> orderingKey = EventType.ofWireName(envelope.eventType())
                .flatMap(EventType::sessionKey);

        if (orderingKey.isEmpty()) {
            // Commutative: its own id, unique, so it is its own session and blocks nothing.
            return publish(envelope, envelope.eventId());
        }

        Object aggregateId = envelope.payload().get(orderingKey.get());
        if (aggregateId == null || aggregateId.toString().isBlank()) {
            // Refused rather than sent. String.valueOf(null) is "null" — a perfectly valid
            // session id, which Service Bus accepts. Every ordered message missing its key would
            // then share one session called "null": serialised against each other, and against
            // nothing they are actually related to. The send succeeds and the ordering guarantee
            // is silently inverted, which is worse than not having it.
            return Mono.error(new MissingSessionKey(envelope, orderingKey.get()));
        }

        return publish(envelope, aggregateId.toString());
    }

    /** Raised when an ordered event type arrives without the payload key that serialises it. */
    public static class MissingSessionKey extends RuntimeException implements PermanentFailure {
        public MissingSessionKey(EventEnvelope envelope, String key) {
            super("%s is ordered on '%s' (ET-PLT-003 §4) but %s carries no such payload key"
                    .formatted(envelope.eventType(), key, envelope.eventId()));
        }
    }

    /** @param sessionId the key Service Bus serialises on; never null on a session-enabled sub */
    public Mono<Void> publish(EventEnvelope envelope, String sessionId) {
        return Mono.fromCallable(() -> {
                    var message = MessageBuilder.withPayload(envelope)
                            .setHeader(EVENT_TYPE_HEADER, envelope.eventType())
                            .setHeader(SESSION_ID_HEADER, sessionId)
                            .build();
                    return streamBridge.send(binding, message);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(sent -> Boolean.TRUE.equals(sent)
                        ? Mono.empty()
                        // An error, not a warning. The outbox row is only marked SENT when this
                        // Mono completes, so failing here is what keeps the message owed.
                        : Mono.error(new EventPublicationFailed(binding, envelope)))
                .then();
    }

    /** Raised when the bus refuses a message, so the outbox row stays PENDING and is retried. */
    public static class EventPublicationFailed extends RuntimeException {
        public EventPublicationFailed(String binding, EventEnvelope envelope) {
            super("Service Bus refused %s (%s) on binding %s"
                    .formatted(envelope.eventType(), envelope.eventId(), binding));
        }
    }
}
