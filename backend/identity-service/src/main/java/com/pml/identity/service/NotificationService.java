package com.pml.identity.service;

import com.pml.identity.web.graphql.dto.SendNotificationInput;
import com.pml.identity.domain.model.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service interface for managing notifications.
 * Handles notification creation, delivery, and lifecycle management.
 */
public interface NotificationService {

    /**
     * Create and send a notification to a user.
     *
     * @param input notification details
     * @return Mono containing the created notification
     */
    Mono<Notification> createNotification(SendNotificationInput input);

    /**
     * Send a bulk notification to multiple users.
     *
     * @param userIds list of user IDs to notify
     * @param input notification details
     * @return Mono containing the count of notifications sent
     */
    Mono<Integer> sendBulkNotification(List<String> userIds, SendNotificationInput input);

    /**
     * Find notifications for a user with pagination.
     *
     * @param userId the user ID
     * @param limit maximum number of notifications to return
     * @param offset number of notifications to skip
     * @param unreadOnly if true, only return unread notifications
     * @return Flux of notifications
     */
    Flux<Notification> findByUserId(String userId, int limit, int offset, boolean unreadOnly);

    /**
     * Find all notifications for a user.
     *
     * @param userId the user ID
     * @return Flux of all notifications for the user
     */
    Flux<Notification> findByUserId(String userId);

    /**
     * Count unread notifications for a user.
     *
     * @param userId the user ID
     * @return Mono containing the count of unread notifications
     */
    Mono<Long> countUnread(String userId);

    /**
     * Find a notification by ID.
     *
     * @param id the notification ID
     * @return Mono containing the notification, or empty if not found
     */
    Mono<Notification> findById(String id);

    /**
     * Mark a notification as read.
     *
     * @param notificationId the notification ID
     * @return Mono containing the updated notification
     */
    Mono<Notification> markAsRead(String notificationId);

    /**
     * Mark all notifications as read for a user.
     *
     * @param userId the user ID
     * @return Mono containing the count of notifications marked as read
     */
    Mono<Integer> markAllAsRead(String userId);

    /**
     * Delete a notification.
     *
     * @param notificationId the notification ID
     * @return Mono containing true if deleted successfully
     */
    Mono<Boolean> deleteNotification(String notificationId);
}
