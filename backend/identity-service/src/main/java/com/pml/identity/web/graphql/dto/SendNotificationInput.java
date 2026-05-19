package com.pml.identity.web.graphql.dto;

import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.enums.NotificationType;

import java.util.List;
import java.util.Map;

/**
 * Input DTO for sending a notification to a user.
 * Used by admin mutations to create and send notifications.
 *
 * @param userId the ID of the user to notify
 * @param type the type/category of the notification
 * @param title the notification title
 * @param body the notification body content
 * @param data additional structured data (e.g., ticketId, eventId)
 * @param channels the delivery channels to use
 */
public record SendNotificationInput(
    String userId,
    NotificationType type,
    String title,
    String body,
    Map<String, Object> data,
    List<NotificationChannel> channels
) {}
