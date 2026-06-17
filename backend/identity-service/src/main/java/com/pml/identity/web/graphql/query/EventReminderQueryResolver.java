package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.service.EventReminderService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;

/**
 * GraphQL Query Resolver for Event Reminder operations.
 * Handles reminder-related queries.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventReminderQueryResolver {

    private final EventReminderService reminderService;

    /**
     * Get my event reminders with optional event filter.
     * Schema: myEventReminders(eventId: ID): [EventReminder!]!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<EventReminder> myEventReminders(
            @InputArgument String eventId
    ) {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myEventReminders(userId={}, eventId={})", userId, eventId))
                .flatMapMany(userId -> {
                    if (eventId != null) {
                        return reminderService.findByUserIdAndEventId(userId, eventId);
                    }
                    return reminderService.findByUserId(userId);
                });
    }
}
