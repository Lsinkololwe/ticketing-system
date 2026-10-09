package com.pml.booking.infrastructure.messaging;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.event.ConsumerDispatch;
import com.pml.shared.event.ConsumerGuard;
import com.pml.shared.event.DeadLetter;
import com.pml.shared.event.DeadLetterDepth;
import com.pml.shared.event.EventType;
import com.pml.shared.event.RetryBudget;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Idempotency, retry and dead-lettering for the catalog subscription.
 *
 * <h2>Two answers to "has this delivery been handled"</h2>
 * {@link ConsumerGuard} keys Redis on the envelope's own {@code eventId}, which is unique per
 * message: a redelivery of one message is recognised, and a different message about the same catalog
 * event is not. Redis may be empty after a flush or an eviction, so a miss falls through to booking's
 * own state, asked per wire name:
 *
 * <ul>
 *   <li>{@code EventPublished} has been handled when an escrow exists for the event.</li>
 *   <li>{@code EventCompleted} has been handled when that escrow has left {@code ACTIVE}.</li>
 *   <li>Cancellation and reschedule are idempotent in their own writes, so the durable check answers
 *       "not handled" and lets them run again harmlessly.</li>
 * </ul>
 *
 * <p>The effect is the marker. An escrow row cannot disagree with whether the escrow was opened,
 * which a separate "seen" collection written beside the work could.</p>
 */
@Slf4j
@Configuration
public class CatalogConsumerConfig {

    /** Names this consumer in dedup keys, dead letters and metrics. */
    public static final String CONSUMER = "booking.catalogEvents";
    public static final String SUBSCRIPTION = "catalog-events/booking";

    /**
     * The per-wire-name durable check, over any way of finding an event's escrow.
     *
     * <p>Static so the routing test exercises this exact logic without a Spring context.</p>
     */
    public static ConsumerGuard.DurablyHandled durablyHandled(
            Function<String, Mono<EventEscrowAccount>> escrowForEvent) {

        return envelope -> {
            Object rawEventId = envelope.payload().get("eventId");
            if (rawEventId == null || rawEventId.toString().isBlank()) {
                // No id to check against. Answering "handled" would drop the message silently;
                // "not handled" lets the work run and fail loudly, which is what gets noticed.
                return Mono.just(false);
            }
            String eventId = rawEventId.toString();

            EventType type = EventType.ofWireName(envelope.eventType()).orElse(null);
            if (type == EventType.CATALOG_EVENT_PUBLISHED) {
                return escrowForEvent.apply(eventId).hasElement();
            }
            if (type == EventType.CATALOG_EVENT_COMPLETED) {
                return escrowForEvent.apply(eventId)
                        .map(escrow -> escrow.getStatus() != EscrowStatus.ACTIVE)
                        .defaultIfEmpty(false);
            }
            return Mono.just(false);
        };
    }

    @Bean
    public ConsumerGuard.DurablyHandled catalogDurablyHandled(EventEscrowAccountRepository escrowAccounts) {
        return durablyHandled(escrowAccounts::findByEventId);
    }

    @Bean
    public ConsumerGuard catalogConsumerGuard(ReactiveStringRedisTemplate redis,
                                              ConsumerGuard.DurablyHandled durablyHandled) {
        return new ConsumerGuard(redis, durablyHandled, CONSUMER);
    }

    /**
     * Where a message goes when the retry budget is spent.
     *
     * <p>The Azure receive context arrives on the message, one delivery at a time, and is not
     * reachable from here — so this sink records the give-up and lets the broker's max-delivery
     * count move the message. {@link DeadLetterDepth} is what makes it visible.</p>
     */
    @Bean
    public DeadLetter.Sink catalogDeadLetterSink() {
        return letter -> {
            log.error("Dead-lettering {} from {}: {} — {}",
                    letter.eventId(), letter.consumerName(), letter.reason(), letter.description());
            return Mono.empty();
        };
    }

    @Bean
    public ConsumerDispatch catalogConsumerDispatch(ConsumerGuard guard,
                                                    RetryBudget retryBudget,
                                                    DeadLetter.Sink sink,
                                                    MeterRegistry registry) {
        return new ConsumerDispatch(guard, retryBudget, sink,
                new DeadLetterDepth(registry), CONSUMER, SUBSCRIPTION);
    }
}
