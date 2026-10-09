package com.pml.catalog.workflow.schedule;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.error.CatalogRefusalTranslator;
import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.catalog.service.EventService;
import com.pml.catalog.workflow.lifecycle.EventLifecycleProcess;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.ActivityImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Publishes a scheduled event when its time comes, through {@link EventLifecycleProcess} so the
 * lifecycle workflow that completes it starts with it. The activity re-reads the event first, so a
 * schedule that no longer applies publishes nothing. Runs on the worker's own thread, which may wait
 * on a reactive chain.
 */
@Slf4j
@Component
@ActivityImpl(taskQueues = TaskQueues.LIFECYCLE)
public class EventPublishScheduleActivitiesImpl implements EventPublishScheduleActivities {

    /** The actor recorded for a publication nobody pressed a button for. */
    static final String SYSTEM_ACTOR = "system:scheduled-publish";

    private static final Duration AWAIT = Duration.ofSeconds(50);

    private final EventService events;
    private final EventLifecycleProcess lifecycle;

    public EventPublishScheduleActivitiesImpl(EventService events, EventLifecycleProcess lifecycle) {
        this.events = events;
        this.lifecycle = lifecycle;
    }

    @Override
    public String publishDue(String eventId, long publishAtMillis) {
        Event event = await(events.findById(eventId));
        if (event == null || event.getStatus() != EventStatus.APPROVED || !event.isPublishScheduled()
                || event.getPublishAt() == null || event.getPublishAt().toEpochMilli() != publishAtMillis) {
            log.info("Scheduled publication of {} no longer applies", eventId);
            return "SKIPPED";
        }
        try {
            await(lifecycle.publish(eventId, SYSTEM_ACTOR));
            log.info("Event {} published on schedule", eventId);
            return "PUBLISHED";
        } catch (RuntimeException error) {
            if (isRefusal(error)) {
                // The event cannot be published (a rule changed since it was scheduled). Clear the
                // schedule so the organizer sees an approved event with no clock on it, not a wait
                // that has quietly ended.
                log.warn("Scheduled publication of {} refused: {}", eventId, error.getMessage());
                await(events.clearPublishSchedule(eventId));
                return "REFUSED";
            }
            throw Refusals.forActivity(error, new CatalogRefusalTranslator());
        }
    }

    private static boolean isRefusal(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DomainRefusal) {
                return true;
            }
        }
        return false;
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error, new CatalogRefusalTranslator());
        }
    }
}
