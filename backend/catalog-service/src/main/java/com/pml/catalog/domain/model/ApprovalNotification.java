package com.pml.catalog.domain.model;

import com.pml.catalog.domain.enums.ApprovalNotificationChannel;
import com.pml.catalog.domain.enums.ApprovalNotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * ApprovalNotification Model
 *
 * Tracks notifications sent as part of the approval workflow.
 * Supports both email and in-app notification delivery.
 * Includes retry tracking for failed deliveries.
 */
@Document(collection = "approval_notifications")
@CompoundIndex(name = "recipient_type_idx", def = "{'recipientId': 1, 'type': 1}")
@CompoundIndex(name = "event_type_idx", def = "{'eventId': 1, 'type': 1}")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalNotification {

    @Id
    private String id;

    /**
     * Event this notification is about
     */
    @Indexed
    private String eventId;

    /**
     * Denormalized event title for display
     */
    private String eventTitle;

    // ═══════════════════════════════════════════════════════════════════════════
    // NOTIFICATION DETAILS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Type of notification (determines content template)
     */
    @Indexed
    private ApprovalNotificationType type;

    /**
     * Channel through which to deliver the notification
     */
    private ApprovalNotificationChannel channel;

    /**
     * User ID of the notification recipient
     */
    @Indexed
    private String recipientId;

    /**
     * Email address for email notifications (null for in-app only)
     */
    private String recipientEmail;

    /**
     * Denormalized recipient name for display
     */
    private String recipientName;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Subject line (for email) or title (for in-app)
     */
    private String subject;

    /**
     * Message body
     */
    private String message;

    /**
     * Deep link URL to the relevant page in the application
     */
    private String actionUrl;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * When the notification was created
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * When the notification was sent (email sent or in-app created)
     */
    private LocalDateTime sentAt;

    /**
     * When the notification was confirmed delivered (email delivery confirmation)
     */
    private LocalDateTime deliveredAt;

    /**
     * When the user read/opened the notification
     */
    private LocalDateTime readAt;

    /**
     * When the last send attempt failed (null if no failure)
     */
    private LocalDateTime failedAt;

    /**
     * Reason for the last failure
     */
    private String failureReason;

    // ═══════════════════════════════════════════════════════════════════════════
    // RETRY TRACKING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Number of send retry attempts
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * Maximum number of retries allowed
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * When the next retry should be attempted
     */
    @Indexed
    private LocalDateTime nextRetryAt;

    /**
     * Last modified timestamp
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // DOMAIN METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Mark the notification as sent
     */
    public void markSent() {
        this.sentAt = LocalDateTime.now();
        this.failedAt = null;
        this.failureReason = null;
    }

    /**
     * Mark the notification as delivered
     */
    public void markDelivered() {
        this.deliveredAt = LocalDateTime.now();
    }

    /**
     * Mark the notification as read
     */
    public void markRead() {
        this.readAt = LocalDateTime.now();
    }

    /**
     * Record a send failure
     */
    public void recordFailure(String reason, int retryDelayMinutes) {
        this.failedAt = LocalDateTime.now();
        this.failureReason = reason;
        this.retryCount++;

        if (retryCount < maxRetries) {
            this.nextRetryAt = LocalDateTime.now().plusMinutes(retryDelayMinutes * (long) Math.pow(2, retryCount - 1));
        } else {
            this.nextRetryAt = null; // No more retries
        }
    }

    /**
     * Check if the notification should be retried
     */
    public boolean shouldRetry() {
        return failedAt != null && retryCount < maxRetries && nextRetryAt != null;
    }

    /**
     * Check if retry is due
     */
    public boolean isRetryDue() {
        return shouldRetry() && LocalDateTime.now().isAfter(nextRetryAt);
    }

    /**
     * Check if this is an email notification
     */
    public boolean isEmail() {
        return channel == ApprovalNotificationChannel.EMAIL ||
               channel == ApprovalNotificationChannel.BOTH;
    }

    /**
     * Check if this is an in-app notification
     */
    public boolean isInApp() {
        return channel == ApprovalNotificationChannel.IN_APP ||
               channel == ApprovalNotificationChannel.BOTH;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a notification for an organizer
     */
    public static ApprovalNotification forOrganizer(String eventId, String eventTitle,
                                                    ApprovalNotificationType type,
                                                    ApprovalNotificationChannel channel,
                                                    String organizerId, String organizerName,
                                                    String organizerEmail,
                                                    String subject, String message, String actionUrl) {
        return ApprovalNotification.builder()
                .eventId(eventId)
                .eventTitle(eventTitle)
                .type(type)
                .channel(channel)
                .recipientId(organizerId)
                .recipientName(organizerName)
                .recipientEmail(organizerEmail)
                .subject(subject)
                .message(message)
                .actionUrl(actionUrl)
                .build();
    }

    /**
     * Create a notification for an admin
     */
    public static ApprovalNotification forAdmin(String eventId, String eventTitle,
                                                ApprovalNotificationType type,
                                                ApprovalNotificationChannel channel,
                                                String adminId, String adminName,
                                                String adminEmail,
                                                String subject, String message, String actionUrl) {
        return ApprovalNotification.builder()
                .eventId(eventId)
                .eventTitle(eventTitle)
                .type(type)
                .channel(channel)
                .recipientId(adminId)
                .recipientName(adminName)
                .recipientEmail(adminEmail)
                .subject(subject)
                .message(message)
                .actionUrl(actionUrl)
                .build();
    }

    /**
     * Create a submission received notification for organizer
     */
    public static ApprovalNotification submissionReceived(String eventId, String eventTitle,
                                                          ApprovalNotificationChannel channel,
                                                          String organizerId, String organizerName,
                                                          String organizerEmail, String actionUrl) {
        return forOrganizer(eventId, eventTitle,
                ApprovalNotificationType.SUBMISSION_RECEIVED, channel,
                organizerId, organizerName, organizerEmail,
                "Event Submitted for Review",
                String.format("Your event \"%s\" has been submitted for review. We'll notify you once it's reviewed.", eventTitle),
                actionUrl);
    }

    /**
     * Create an approval granted notification for organizer
     */
    public static ApprovalNotification approvalGranted(String eventId, String eventTitle,
                                                       ApprovalNotificationChannel channel,
                                                       String organizerId, String organizerName,
                                                       String organizerEmail, String actionUrl) {
        return forOrganizer(eventId, eventTitle,
                ApprovalNotificationType.APPROVAL_GRANTED, channel,
                organizerId, organizerName, organizerEmail,
                "Event Approved!",
                String.format("Great news! Your event \"%s\" has been approved. You can now publish it.", eventTitle),
                actionUrl);
    }

    /**
     * Create a rejection notification for organizer
     */
    public static ApprovalNotification rejectionIssued(String eventId, String eventTitle,
                                                       ApprovalNotificationChannel channel,
                                                       String organizerId, String organizerName,
                                                       String organizerEmail, String actionUrl,
                                                       String reason) {
        return forOrganizer(eventId, eventTitle,
                ApprovalNotificationType.REJECTION_ISSUED, channel,
                organizerId, organizerName, organizerEmail,
                "Event Not Approved",
                String.format("Unfortunately, your event \"%s\" was not approved. Reason: %s", eventTitle, reason),
                actionUrl);
    }

    /**
     * Create an escalation notification for senior admin
     */
    public static ApprovalNotification escalationTriggered(String eventId, String eventTitle,
                                                           ApprovalNotificationChannel channel,
                                                           String adminId, String adminName,
                                                           String adminEmail, String actionUrl,
                                                           int hoursOverdue) {
        return forAdmin(eventId, eventTitle,
                ApprovalNotificationType.ESCALATION_TRIGGERED, channel,
                adminId, adminName, adminEmail,
                "URGENT: Event Approval Escalation",
                String.format("Event \"%s\" has been escalated. It is %d hours overdue for approval.", eventTitle, hoursOverdue),
                actionUrl);
    }
}
