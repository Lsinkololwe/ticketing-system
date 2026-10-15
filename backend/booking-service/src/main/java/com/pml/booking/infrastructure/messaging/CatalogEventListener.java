package com.pml.booking.infrastructure.messaging;

import com.pml.booking.workflow.finance.EventFinanceProcess;
import com.pml.shared.event.ConsumerDispatch;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.EventTopology;
import com.pml.shared.event.EventTransportAutoConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * booking-service's subscription to {@code catalog-events}.
 *
 * <h2>The message is the shared event envelope</h2>
 * catalog stages {@link EventEnvelope}s in its outbox and its drain publishes them unchanged, so this
 * listener binds the envelope directly. Routing reads the wire name. Deduplication reads the
 * envelope's own {@code eventId}, which is unique per message; the catalog event's id names the
 * aggregate the message is about and repeats across that event's whole lifecycle, so it cannot be the
 * deduplication key.
 *
 * <h2>Acknowledged only when the outcome is final</h2>
 * The listener returns normally, and the message is acknowledged, for HANDLED and DUPLICATE. For
 * DEAD_LETTERED it throws {@link AmqpRejectAndDontRequeueException}: the container rejects the
 * message without requeueing, the queue's dead-letter exchange moves it to
 * {@code catalog-events.booking-sub.dlq}, and an operator can replay it from there.
 *
 * <h2>Why the dispatch is awaited here</h2>
 * The container hands this method to a thread of its own, one message at a time for this
 * subscription, so waiting on the result blocks nothing the reactive stack depends on. What waiting
 * buys is the contract above: the acknowledgement follows the outcome, and the next message, which may
 * depend on this one, is not delivered until this one has settled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventListener {

    /** How long one message may take, retries included, before the listener gives it up. */
    private static final Duration HANDLING_LIMIT = Duration.ofMinutes(2);

    private final EventFinanceProcess eventFinance;

    /** The deduplication guard, the retry budget and the dead-letter sink. */
    private final ConsumerDispatch dispatch;

    @RabbitListener(
            queues = EventTopology.CATALOG_EVENTS + ".booking-sub",
            containerFactory = EventTransportAutoConfiguration.ORDERED_LISTENER_FACTORY)
    public void onCatalogEvent(EventEnvelope envelope) {
        log.info("Received {} ({}) correlation={}",
                envelope.eventType(), envelope.eventId(), envelope.correlationId());

        ConsumerDispatch.Outcome outcome = dispatch
                .deliver(envelope, Mono.defer(() -> route(eventFinance, envelope)))
                .block(HANDLING_LIMIT);

        if (outcome == ConsumerDispatch.Outcome.DEAD_LETTERED) {
            // DeadLetterDepth has already counted the give-up; rejecting without a requeue is what
            // moves the message to the dead-letter queue.
            log.error("Catalog message {} ({}) dead-lettered after retries",
                    envelope.eventId(), envelope.eventType());
            throw new AmqpRejectAndDontRequeueException(
                    "dead-lettered " + envelope.eventType() + " " + envelope.eventId());
        }
    }

    /**
     * A catalog fact becomes a signal to the event's finance workflow; the listener does no business
     * work of its own. A registered event type booking does not consume yet, and a wire name this build
     * does not know, are acknowledged rather than retried: redelivering them cannot change the answer.
     */
    public static Mono<Void> route(EventFinanceProcess eventFinance, EventEnvelope envelope) {
        return eventFinance.deliver(envelope);
    }
}
