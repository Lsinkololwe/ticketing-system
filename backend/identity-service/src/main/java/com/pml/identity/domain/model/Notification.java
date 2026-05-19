package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.enums.NotificationStatus;
import com.pml.identity.domain.enums.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entity representing a notification sent to a user.
 * Supports multiple delivery channels (push, SMS, WhatsApp, email, in-app).
 * Tracks notification lifecycle from creation through delivery and read status.
 */
@Document(collection = "notifications")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    /**
     * Unique identifier for the notification
     */
    @Id
    private String id;

    /**
     * ID of the user receiving the notification
     */
    @Indexed
    private String userId;

    /**
     * Type/category of the notification
     */
    private NotificationType type;

    /**
     * Notification title (shown in push/email subject)
     */
    private String title;

    /**
     * Notification body content
     */
    private String body;

    /**
     * Additional structured data for the notification (e.g., ticketId, eventId)
     */
    private Map<String, Object> data;

    /**
     * Channels through which this notification should be delivered
     */
    private List<NotificationChannel> channels;

    /**
     * Current status of the notification
     */
    private NotificationStatus status;

    /**
     * Timestamp when notification was sent
     */
    private LocalDateTime sentAt;

    /**
     * Timestamp when notification was delivered
     */
    private LocalDateTime deliveredAt;

    /**
     * Timestamp when user read the notification
     */
    private LocalDateTime readAt;

    /**
     * Timestamp when notification was created
     */
    @CreatedDate
    private LocalDateTime createdAt;
}
