package com.pml.booking.config;

import com.azure.spring.messaging.checkpoint.Checkpointer;
import com.pml.booking.workflow.finance.EventFinanceProcess;
import com.pml.shared.event.ConsumerDispatch;
import com.pml.shared.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import static com.azure.spring.messaging.AzureHeaders.CHECKPOINTER;

/**
 * booking-service's subscription to {@code catalog-events}.
 *
 * <h2>The message is the shared event envelope</h2>
 * catalog stages {@link EventEnvelope}s in its outbox and its drain publishes them unchanged, so this
 * consumer binds the envelope directly. Routing reads the wire name. Deduplication reads the
 * envelope's own {@code eventId}, which is unique per message; the catalog event's id names the
 * aggregate the message is about and repeats across that event's whole lifecycle, so it cannot be
 * the deduplication key.
 *
 * <h2>Checkpointed only when the outcome is final</h2>
 * HANDLED and DUPLICATE acknowledge the message. DEAD_LETTERED leaves it unacknowledged, so the
 * broker's max-delivery count moves it to the dead-letter queue, where an operator can replay it.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AzureServiceBusConfig {

    private final EventFinanceProcess eventFinance;

    /** The deduplication guard, the retry budget and the dead-letter sink. */
    private final ConsumerDispatch dispatch;

    @Bean
    public Consumer<Message<EventEnvelope>> catalogEventConsumer() {
        return message -> {
            EventEnvelope envelope = message.getPayload();
            Checkpointer checkpointer = (Checkpointer) message.getHeaders().get(CHECKPOINTER);

            log.info("Received {} ({}) correlation={}",
                    envelope.eventType(), envelope.eventId(), envelope.correlationId());

            dispatch.deliver(envelope, Mono.defer(() -> route(eventFinance, envelope)))
                    .subscribe(outcome -> {
                        switch (outcome) {
                            case HANDLED, DUPLICATE -> checkpoint(checkpointer, envelope);
                            case DEAD_LETTERED ->
                                    // Not checkpointed: the broker's max-delivery count moves it,
                                    // and DeadLetterDepth has already counted the give-up.
                                    log.error("Catalog message {} ({}) dead-lettered after retries",
                                            envelope.eventId(), envelope.eventType());
                        }
                    }, failure ->
                            // ConsumerDispatch promises never to signal an error. If one arrives the
                            // contract is broken, and swallowing it here would hide that.
                            log.error("Catalog dispatch signalled an error, which it must not: {}",
                                    failure.getMessage(), failure));
        };
    }

    /**
     * Routes an envelope to booking's handler for its wire name.
     *
     * <p>A registered event type booking does not consume yet, and a wire name this build does not know, are both
     * acknowledged rather than retried: redelivering them cannot change the answer.</p>
     */
    /**
     * A catalog fact becomes a signal to the event's finance workflow; the consumer does no business
     * work of its own.
     */
    public static Mono<Void> route(EventFinanceProcess eventFinance, EventEnvelope envelope) {
        return eventFinance.deliver(envelope);
    }

    private void checkpoint(Checkpointer checkpointer, EventEnvelope envelope) {
        if (checkpointer == null) {
            log.warn("No checkpointer available for {} ({})", envelope.eventType(), envelope.eventId());
            return;
        }
        checkpointer.success()
                .subscribe(done -> { },
                        error -> log.error("Failed to checkpoint {} ({}): {}",
                                envelope.eventType(), envelope.eventId(), error.getMessage()),
                        () -> log.debug("Checkpointed {} ({})", envelope.eventType(), envelope.eventId()));
    }
}
