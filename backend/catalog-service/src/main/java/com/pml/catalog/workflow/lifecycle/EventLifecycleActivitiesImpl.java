package com.pml.catalog.workflow.lifecycle;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.infrastructure.client.BookingServiceClient;
import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.catalog.service.EventService;
import com.pml.catalog.error.CatalogRefusalTranslator;
import com.pml.shared.workflow.Refusals;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.View;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The lifecycle activities, adapting {@link EventService} to Temporal.
 *
 * <p>An activity method is synchronous by contract and runs on the worker's activity executor, so
 * the reactive chain is awaited here. The await is shorter than the activity's
 * start-to-close timeout, so a stalled database surfaces as a retried attempt rather than an
 * abandoned one.
 *
 * <p>{@link #unpublish} and {@link #cancel} each ask {@link BookingServiceClient} a question
 * catalog cannot answer from its own collections before writing: whether anything is sold, and
 * whether a payout is in flight. Both calls happen here rather than in {@link EventService}, which
 * stays free of outbound HTTP and takes the verified answer as a parameter.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.LIFECYCLE)
public class EventLifecycleActivitiesImpl implements EventLifecycleActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final EventService events;
    private final BookingServiceClient booking;

    public EventLifecycleActivitiesImpl(EventService events, BookingServiceClient booking) {
        this.events = events;
        this.booking = booking;
    }

    @Override
    public View current(String eventId) {
        return await(events.findById(eventId).map(EventLifecycleActivitiesImpl::view));
    }

    @Override
    public View publish(String eventId) {
        return await(events.publishEvent(eventId).map(EventLifecycleActivitiesImpl::view));
    }

    @Override
    public View reschedule(String eventId, long newStartsAtMillis, String reason) {
        return await(events.rescheduleEvent(eventId, Instant.ofEpochMilli(newStartsAtMillis), reason)
                .map(EventLifecycleActivitiesImpl::view));
    }

    /**
     * A cancellation with an in-flight payout is refused until that payout
     * resolves, so a settlement and a cancellation's refunds never race for the same escrow
     * balance.
     */
    @Override
    public View cancel(String eventId, String reason) {
        return await(booking.hasOpenPayoutRequest(eventId)
                .flatMap(open -> open
                        ? Mono.<Event>error(new TranslatedRefusal(ErrorCode.EVENT_STATE_INVALID,
                                "the event has a payout in flight and cannot be cancelled until it resolves",
                                Map.of("eventId", eventId)))
                        : events.cancelEventWithReason(eventId, reason))
                .map(EventLifecycleActivitiesImpl::view));
    }

    @Override
    public View unpublish(String eventId) {
        return await(booking.soldTicketCount(eventId)
                .flatMap(soldCount -> events.unpublishEvent(eventId, soldCount))
                .map(EventLifecycleActivitiesImpl::view));
    }

    @Override
    public View complete(String eventId) {
        return await(events.completeEvent(eventId).map(EventLifecycleActivitiesImpl::view));
    }

    static View view(Event event) {
        Instant startsAt = event.getEventDateTime();
        long endsAtMillis = startsAt == null && event.getEndDateTime() == null
                ? 0L
                : LifecycleRules.endsAt(startsAt, event.getEndDateTime()).toEpochMilli();
        return new View(event.getId(), event.getStatus(), startsAt != null ? startsAt.toEpochMilli() : 0L,
                endsAtMillis, event.getRescheduleCount());
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error, new CatalogRefusalTranslator());
        }
    }
}
