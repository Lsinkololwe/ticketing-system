package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.SendNotificationInput;
import com.pml.identity.web.graphql.dto.UpdateNotificationPreferencesInput;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.model.NotificationPreferences;
import com.pml.identity.service.NotificationPreferencesService;
import com.pml.identity.service.NotificationService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * GraphQL mutation resolver for notification-related mutations.
 * Provides endpoints for managing notifications and preferences.
 */
@Slf4j
@DgsComponent
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
        return notificationService.markAsRead(notificationId);
    }

    /**
     * Mutation to mark all notifications as read for the authenticated user.
     *
     * @param jwt the authenticated user's JWT token
     * @return Mono containing the count of notifications marked as read
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Integer> markAllNotificationsRead(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.debug("Marking all notifications as read for user {}", userId);
        return notificationService.markAllAsRead(userId);
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
        return notificationService.deleteNotification(notificationId);
    }

    /**
     * Mutation to update notification preferences for the authenticated user.
     *
     * @param input the preferences to update
     * @param jwt the authenticated user's JWT token
     * @return Mono containing the updated preferences
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<NotificationPreferences> updateNotificationPreferences(
        @InputArgument UpdateNotificationPreferencesInput input,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        log.debug("Updating notification preferences for user {}", userId);
        return preferencesService.updatePreferences(userId, input);
    }

    /**
     * Mutation to send a notification to a user (admin only).
     *
     * @param input notification details
     * @return Mono containing the created notification
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Notification> sendNotification(@InputArgument SendNotificationInput input) {
        log.debug("Admin sending notification to user {}", input.userId());
        return notificationService.createNotification(input);
    }

    /**
     * Mutation to send a bulk notification to multiple users (admin only).
     *
     * @param userIds list of user IDs to notify
     * @param input notification details
     * @return Mono containing the count of notifications sent
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Integer> sendBulkNotification(
        @InputArgument List<String> userIds,
        @InputArgument SendNotificationInput input
    ) {
        log.debug("Admin sending bulk notification to {} users", userIds.size());
        return notificationService.sendBulkNotification(userIds, input);
    }
}
