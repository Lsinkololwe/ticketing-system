package com.pml.booking.service;

import com.pml.booking.domain.enums.AlertPriority;
import com.pml.booking.domain.enums.NotificationType;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Service for sending notifications across multiple channels.
 *
 * <p>Supports email, Slack webhooks, and SMS (for critical alerts).
 * Notifications are routed based on priority and recipient type.</p>
 *
 * <h2>Channel Routing</h2>
 * <ul>
 *   <li><b>Email</b>: All notifications (always sent)</li>
 *   <li><b>Slack</b>: MEDIUM, HIGH, CRITICAL priority</li>
 *   <li><b>SMS</b>: CRITICAL priority only (on-call team)</li>
 * </ul>
 *
 * <h2>Recipient Types</h2>
 * <ul>
 *   <li><b>Organizer</b>: Event organizer (email from their profile)</li>
 *   <li><b>Admin/Finance</b>: Platform team (configured recipients)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public interface NotificationService {

    // ========================================================================
    // ORGANIZER NOTIFICATIONS
    // ========================================================================

    /**
     * Sends a notification to an organizer.
     *
     * @param organizerId The organizer's ID (used to lookup email)
     * @param type The notification type
     * @param context Key-value pairs for template rendering
     * @return Mono completing when notification is sent
     */
    Mono<Void> sendOrganizerNotification(
            String organizerId,
            NotificationType type,
            Map<String, Object> context
    );

    /**
     * Sends a notification to an organizer with custom priority.
     *
     * @param organizerId The organizer's ID
     * @param type The notification type
     * @param priority Override the default priority
     * @param context Key-value pairs for template rendering
     * @return Mono completing when notification is sent
     */
    Mono<Void> sendOrganizerNotification(
            String organizerId,
            NotificationType type,
            AlertPriority priority,
            Map<String, Object> context
    );

    // ========================================================================
    // ADMIN/FINANCE TEAM NOTIFICATIONS
    // ========================================================================

    /**
     * Sends an alert to the admin/finance team.
     *
     * @param priority Alert priority (determines channels)
     * @param subject Alert subject/title
     * @param message Alert message body
     * @return Mono completing when notification is sent
     */
    Mono<Void> sendAdminAlert(
            AlertPriority priority,
            String subject,
            String message
    );

    /**
     * Sends a structured alert to the admin/finance team.
     *
     * @param type The notification type
     * @param context Key-value pairs for template rendering
     * @return Mono completing when notification is sent
     */
    Mono<Void> sendAdminAlert(
            NotificationType type,
            Map<String, Object> context
    );

    /**
     * Sends a reconciliation alert with multiple issues.
     *
     * @param runNumber The reconciliation run number
     * @param alerts List of alert messages
     * @param priority Alert priority
     * @return Mono completing when notification is sent
     */
    Mono<Void> sendReconciliationAlert(
            String runNumber,
            List<String> alerts,
            AlertPriority priority
    );

    // ========================================================================
    // DIRECT CHANNEL METHODS (for specific use cases)
    // ========================================================================

    /**
     * Sends an email directly.
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param body Email body (HTML supported)
     * @return Mono completing when email is sent
     */
    Mono<Void> sendEmail(String to, String subject, String body);

    /**
     * Sends a message to Slack.
     *
     * @param message The message text (supports Slack markdown)
     * @param priority Priority determines channel mention
     * @return Mono completing when message is sent
     */
    Mono<Void> sendSlackMessage(String message, AlertPriority priority);
}
