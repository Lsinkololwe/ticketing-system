package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.SetEventReminderInput;
import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.service.EventReminderService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

/**
 * GraphQL mutation resolver for event reminder mutations.
 * Provides endpoints for managing event reminders.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventReminderMutationResolver {

    private final EventReminderService reminderService;

    /**
     * Mutation to set an event reminder for the authenticated user.
     *
     * @param input reminder details
     * @param jwt the authenticated user's JWT token
     * @return Mono containing the created/updated reminder
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<EventReminder> setEventReminder(
        @InputArgument SetEventReminderInput input,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        log.debug("Setting event reminder for user {} for ticket {}", userId, input.ticketId());
        return reminderService.setReminder(userId, input);
    }

    /**
     * Mutation to cancel an event reminder.
     *
     * @param reminderId the reminder ID
     * @return Mono containing true if cancelled successfully
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> cancelEventReminder(@InputArgument String reminderId) {
        log.debug("Cancelling event reminder {}", reminderId);
        return reminderService.cancelReminder(reminderId);
    }
}
