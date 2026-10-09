package com.pml.identity.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.model.NotificationPreferences;
import com.pml.identity.service.NotificationPreferencesService;
import com.pml.identity.service.NotificationService;
import com.pml.identity.web.graphql.dto.UpdateNotificationPreferencesInput;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * GraphQL mutation resolver for notification-related mutations.
 * Provides endpoints for managing notifications and preferences.
 */
@Slf4j

@DgsComponent
@Validated
@RequiredArgsConstructor
public class NotificationMutationResolver {

    private final NotificationService notificationService;
    private final NotificationPreferencesService preferencesService;

    /**
     * Mutation to mark a notification as read.
     *
     * @param notificationId the notification ID
     * @return Mono containing the updated notification
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Notification> markNotificationRead(@InputArgument String notificationId) {
        log.debug("Marking notification {} as read", notificationId);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> notificationService.markAsRead(userId, notificationId));
    }

    /**
     * Mutation to mark all notifications as read for the authenticated user.
     *
     * @return Mono containing the count of notifications marked as read
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Integer> markAllNotificationsRead() {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("Marking all notifications as read for user {}", userId))
                .flatMap(notificationService::markAllAsRead);
    }

    /**
     * Mutation to delete a notification.
     *
     * @param notificationId the notification ID
     * @return Mono containing true if deleted successfully
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> deleteNotification(@InputArgument String notificationId) {
        log.debug("Deleting notification {}", notificationId);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> notificationService.deleteNotification(userId, notificationId));
    }

    /**
     * Mutation to update notification preferences for the authenticated user.
     *
     * @param input the preferences to update
     * @return Mono containing the updated preferences
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<NotificationPreferences> updateNotificationPreferences(
        @Valid @InputArgument UpdateNotificationPreferencesInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("Updating notification preferences for user {}", userId))
                .flatMap(userId -> preferencesService.updatePreferences(userId, input));
    }

}
