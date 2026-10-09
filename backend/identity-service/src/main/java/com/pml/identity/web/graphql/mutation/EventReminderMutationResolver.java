package com.pml.identity.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.workflow.reminder.ReminderProcess;
import com.pml.identity.web.graphql.dto.SetEventReminderInput;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * GraphQL mutation resolver for event reminder mutations.
 * Provides endpoints for managing event reminders.
 */
@Slf4j


@DgsComponent
@Validated
@RequiredArgsConstructor
public class EventReminderMutationResolver {

    /** A reminder is set and cancelled through its workflow, which owns the timers. */
    private final ReminderProcess reminderProcess;

    /**
     * Mutation to set an event reminder for the authenticated user.
     *
     * @param input reminder details
     * @return Mono containing the created/updated reminder
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<EventReminder> setEventReminder(
        @Valid @InputArgument SetEventReminderInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("Setting event reminder for user {} for ticket {}", userId, input.ticketId()))
                .flatMap(userId -> reminderProcess.set(userId, input));
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
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> reminderProcess.cancel(reminderId, userId));
    }
}
