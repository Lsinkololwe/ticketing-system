package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.service.EventReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
            @InputArgument String eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            return Flux.empty();
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: myEventReminders(userId={}, eventId={})", userId, eventId);

        if (eventId != null) {
            return reminderService.findByUserIdAndEventId(userId, eventId);
        }
        return reminderService.findByUserId(userId);
    }
}
