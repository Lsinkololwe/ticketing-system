package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.AlertPriority;
import com.pml.booking.domain.enums.NotificationType;
import com.pml.booking.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Notification Service Implementation
 *
 * <p>Handles multi-channel notifications including email, Slack webhooks,
 * and SMS for critical alerts. All operations are non-blocking.</p>
 *
 * <h2>Configuration</h2>
 * <pre>
 * notification:
 *   enabled: true
 *   email:
 *     enabled: true
 *     from: noreply@pml-events.com
 *     admin-recipients: finance@pml-events.com,admin@pml-events.com
 *   slack:
 *     enabled: true
 *     webhook-url: https://hooks.slack.com/services/xxx
 *     channel: #finance-alerts
 *   sms:
 *     enabled: true
 *     provider-url: https://api.africatalking.com/...
 *     on-call-numbers: +260971234567,+260972345678
 * </pre>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final Optional<JavaMailSender> mailSender;
    private final WebClient webClient;

    // ========================================================================
    // CONFIGURATION PROPERTIES
    // ========================================================================

    @Value("${notification.enabled:true}")
    private boolean notificationsEnabled;

    // Email configuration
    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${notification.email.from:noreply@pml-events.com}")
    private String emailFrom;

    @Value("${notification.email.admin-recipients:finance@pml-events.com}")
    private String adminEmailRecipients;

    // Slack configuration
    @Value("${notification.slack.enabled:false}")
    private boolean slackEnabled;

    @Value("${notification.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${notification.slack.channel:#finance-alerts}")
    private String slackChannel;

    // SMS configuration
    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${notification.sms.provider-url:}")
    private String smsProviderUrl;

    @Value("${notification.sms.api-key:}")
    private String smsApiKey;

    @Value("${notification.sms.on-call-numbers:}")
    private String onCallNumbers;

    // Platform info for branding
    @Value("${spring.application.name:PML Booking Service}")
    private String applicationName;

    public NotificationServiceImpl(
            Optional<JavaMailSender> mailSender,
            WebClient.Builder webClientBuilder
    ) {
        this.mailSender = mailSender;
        this.webClient = webClientBuilder.build();
    }

    // ========================================================================
    // ORGANIZER NOTIFICATIONS
    // ========================================================================

    @Override
    public Mono<Void> sendOrganizerNotification(
            String organizerId,
            NotificationType type,
            Map<String, Object> context
    ) {
        return sendOrganizerNotification(organizerId, type, type.getDefaultPriority(), context);
    }

    @Override
    public Mono<Void> sendOrganizerNotification(
            String organizerId,
            NotificationType type,
            AlertPriority priority,
            Map<String, Object> context
    ) {
        if (!notificationsEnabled) {
            log.debug("Notifications disabled - skipping organizer notification: {}", type);
            return Mono.empty();
        }

        log.info("Sending {} notification to organizer: {} (priority: {})",
                type.getDisplayName(), organizerId, priority);

        // Build notification content
        String subject = buildSubject(type, context);
        String body = buildOrganizerEmailBody(type, context);

        // For now, we'll send to admin recipients since we don't have organizer email lookup
        // In production, this would call IdentityService to get organizer's email
        String organizerEmail = context.getOrDefault("organizerEmail", adminEmailRecipients).toString();

        return sendEmail(organizerEmail, subject, body)
                .then(Mono.defer(() -> {
                    if (priority.shouldSendToSlack()) {
                        String slackMessage = buildSlackMessage(type, priority, context);
                        return sendSlackMessage(slackMessage, priority);
                    }
                    return Mono.empty();
                }))
                .doOnSuccess(v -> log.info("Organizer notification sent successfully: {} to {}",
                        type, organizerId))
                .doOnError(e -> log.error("Failed to send organizer notification: {} to {}",
                        type, organizerId, e));
    }

    // ========================================================================
    // ADMIN/FINANCE TEAM NOTIFICATIONS
    // ========================================================================

    @Override
    public Mono<Void> sendAdminAlert(
            AlertPriority priority,
            String subject,
            String message
    ) {
        if (!notificationsEnabled) {
            log.debug("Notifications disabled - skipping admin alert: {}", subject);
            return Mono.empty();
        }

        log.info("Sending admin alert: {} (priority: {})", subject, priority);

        String fullSubject = String.format("[%s] %s %s",
                priority.getDisplayName().toUpperCase(),
                priority.getEmoji(),
                subject);

        String emailBody = buildAdminEmailBody(priority, subject, message);

        // Send to all channels based on priority
        return sendEmail(adminEmailRecipients, fullSubject, emailBody)
                .then(Mono.defer(() -> {
                    if (priority.shouldSendToSlack()) {
                        return sendSlackMessage(buildSlackAlertMessage(priority, subject, message), priority);
                    }
                    return Mono.empty();
                }))
                .then(Mono.defer(() -> {
                    if (priority.shouldSendSms()) {
                        return sendSmsToOnCall(subject, message);
                    }
                    return Mono.empty();
                }))
                .doOnSuccess(v -> log.info("Admin alert sent successfully: {}", subject))
                .doOnError(e -> log.error("Failed to send admin alert: {}", subject, e));
    }

    @Override
    public Mono<Void> sendAdminAlert(NotificationType type, Map<String, Object> context) {
        String subject = buildSubject(type, context);
        String message = buildAlertMessage(type, context);
        return sendAdminAlert(type.getDefaultPriority(), subject, message);
    }

    @Override
    public Mono<Void> sendReconciliationAlert(
            String runNumber,
            List<String> alerts,
            AlertPriority priority
    ) {
        if (!notificationsEnabled) {
            log.debug("Notifications disabled - skipping reconciliation alert: {}", runNumber);
            return Mono.empty();
        }

        log.info("Sending reconciliation alert for run: {} with {} issues (priority: {})",
                runNumber, alerts.size(), priority);

        String subject = String.format("Reconciliation Alert: %s - %d issues found",
                runNumber, alerts.size());

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Reconciliation run ").append(runNumber)
                .append(" requires attention:\n\n");

        for (int i = 0; i < alerts.size(); i++) {
            messageBuilder.append(i + 1).append(". ").append(alerts.get(i)).append("\n");
        }

        messageBuilder.append("\nPlease review and resolve these discrepancies in the admin dashboard.");

        return sendAdminAlert(priority, subject, messageBuilder.toString());
    }

    // ========================================================================
    // DIRECT CHANNEL METHODS
    // ========================================================================

    @Override
    public Mono<Void> sendEmail(String to, String subject, String body) {
        if (!emailEnabled || mailSender.isEmpty()) {
            log.info("Email disabled or not configured - logging instead: TO={}, SUBJECT={}", to, subject);
            log.debug("Email body:\n{}", body);
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            try {
                MimeMessage message = mailSender.get().createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                helper.setFrom(emailFrom);
                helper.setTo(to.split(","));
                helper.setSubject(subject);
                helper.setText(body, true); // HTML enabled

                mailSender.get().send(message);
                log.info("Email sent successfully to: {}", to);
                return null;
            } catch (MessagingException e) {
                log.error("Failed to send email to {}: {}", to, e.getMessage());
                throw new RuntimeException("Email sending failed", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> sendSlackMessage(String message, AlertPriority priority) {
        if (!slackEnabled || slackWebhookUrl.isBlank()) {
            log.info("Slack disabled or not configured - logging instead: {}", message);
            return Mono.empty();
        }

        // Build Slack payload
        String channelMention = priority.ordinal() >= AlertPriority.HIGH.ordinal() ? "<!channel> " : "";
        String fullMessage = channelMention + message;

        Map<String, Object> payload = Map.of(
                "channel", slackChannel,
                "username", applicationName,
                "icon_emoji", priority.getEmoji(),
                "text", fullMessage
        );

        return webClient.post()
                .uri(slackWebhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Slack message sent successfully"))
                .doOnError(e -> log.error("Failed to send Slack message: {}", e.getMessage()))
                .then();
    }

    // ========================================================================
    // SMS SUPPORT
    // ========================================================================

    /**
     * Sends SMS to on-call team members for critical alerts.
     *
     * <p>Uses Africa's Talking or similar SMS provider common in Zambia.</p>
     */
    private Mono<Void> sendSmsToOnCall(String subject, String message) {
        if (!smsEnabled || onCallNumbers.isBlank()) {
            log.info("SMS disabled or no on-call numbers configured - skipping SMS");
            return Mono.empty();
        }

        String smsText = String.format("[CRITICAL] %s: %s",
                subject,
                message.length() > 140 ? message.substring(0, 137) + "..." : message);

        log.info("Sending SMS to on-call: {}", onCallNumbers);

        // Africa's Talking API format
        Map<String, Object> payload = Map.of(
                "username", "sandbox", // Replace with actual username
                "to", onCallNumbers,
                "message", smsText
        );

        if (smsProviderUrl.isBlank()) {
            log.info("SMS provider URL not configured - logging SMS: {}", smsText);
            return Mono.empty();
        }

        return webClient.post()
                .uri(smsProviderUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("apiKey", smsApiKey)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("SMS sent successfully to on-call team"))
                .doOnError(e -> log.error("Failed to send SMS: {}", e.getMessage()))
                .onErrorResume(e -> {
                    // SMS failure should not fail the entire notification
                    log.warn("SMS delivery failed but continuing: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    // ========================================================================
    // EMAIL BODY BUILDERS
    // ========================================================================

    private String buildSubject(NotificationType type, Map<String, Object> context) {
        String baseSubject = type.getDefaultSubject();

        // Add context-specific info to subject
        if (context.containsKey("chargebackId")) {
            return baseSubject + " - " + context.get("chargebackId");
        }
        if (context.containsKey("runNumber")) {
            return baseSubject + " - " + context.get("runNumber");
        }

        return baseSubject;
    }

    private String buildOrganizerEmailBody(NotificationType type, Map<String, Object> context) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".header { background: #2c3e50; color: white; padding: 20px; }");
        html.append(".content { padding: 20px; }");
        html.append(".alert { background: #fff3cd; border: 1px solid #ffc107; padding: 15px; margin: 10px 0; }");
        html.append(".critical { background: #f8d7da; border: 1px solid #dc3545; }");
        html.append(".detail { margin: 10px 0; }");
        html.append(".label { font-weight: bold; color: #666; }");
        html.append(".footer { background: #f8f9fa; padding: 15px; font-size: 12px; color: #666; }");
        html.append("</style></head><body>");

        html.append("<div class='header'>");
        html.append("<h2>").append(type.getEmoji()).append(" ").append(type.getDisplayName()).append("</h2>");
        html.append("</div>");

        html.append("<div class='content'>");

        switch (type) {
            case CHARGEBACK_RECEIVED -> buildChargebackReceivedBody(html, context);
            case CHARGEBACK_RESOLVED -> buildChargebackResolvedBody(html, context);
            case CHARGEBACK_WRITE_OFF -> buildChargebackWriteOffBody(html, context);
            case PAYOUT_APPROVED, PAYOUT_COMPLETED -> buildPayoutBody(html, context);
            default -> buildGenericBody(html, context);
        }

        html.append("</div>");

        html.append("<div class='footer'>");
        html.append("<p>This is an automated message from ").append(applicationName).append(".</p>");
        html.append("<p>Sent: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("</p>");
        html.append("</div>");

        html.append("</body></html>");
        return html.toString();
    }

    private void buildChargebackReceivedBody(StringBuilder html, Map<String, Object> context) {
        html.append("<div class='alert critical'>");
        html.append("<strong>A chargeback has been filed against a ticket sale.</strong>");
        html.append("</div>");

        html.append("<div class='detail'><span class='label'>Chargeback ID:</span> ")
                .append(context.getOrDefault("chargebackId", "N/A")).append("</div>");
        html.append("<div class='detail'><span class='label'>Amount:</span> K")
                .append(context.getOrDefault("amount", "0.00")).append("</div>");
        html.append("<div class='detail'><span class='label'>Reason:</span> ")
                .append(context.getOrDefault("reason", "Unknown")).append("</div>");
        html.append("<div class='detail'><span class='label'>Response Deadline:</span> ")
                .append(context.getOrDefault("deadline", "N/A")).append("</div>");

        html.append("<h3>What happens next?</h3>");
        html.append("<ol>");
        html.append("<li>Our team will review the chargeback and gather evidence</li>");
        html.append("<li>If disputed, we will submit documentation to the payment provider</li>");
        html.append("<li>If accepted, funds will be recovered from your escrow balance</li>");
        html.append("</ol>");

        html.append("<p><strong>Note:</strong> Chargeback fees (K")
                .append(context.getOrDefault("fee", "25.00"))
                .append(") apply regardless of outcome.</p>");
    }

    private void buildChargebackResolvedBody(StringBuilder html, Map<String, Object> context) {
        boolean disputeWon = Boolean.parseBoolean(context.getOrDefault("disputeWon", "false").toString());

        if (disputeWon) {
            html.append("<div class='alert' style='background: #d4edda; border-color: #28a745;'>");
            html.append("<strong>Good news! The chargeback dispute was won.</strong>");
            html.append("</div>");
        } else {
            html.append("<div class='alert'>");
            html.append("<strong>The chargeback has been processed.</strong>");
            html.append("</div>");
        }

        html.append("<div class='detail'><span class='label'>Chargeback ID:</span> ")
                .append(context.getOrDefault("chargebackId", "N/A")).append("</div>");
        html.append("<div class='detail'><span class='label'>Outcome:</span> ")
                .append(context.getOrDefault("outcome", "N/A")).append("</div>");
        html.append("<div class='detail'><span class='label'>Recovered Amount:</span> K")
                .append(context.getOrDefault("recoveredAmount", "0.00")).append("</div>");
        html.append("<div class='detail'><span class='label'>Recovery Source:</span> ")
                .append(context.getOrDefault("fundSource", "N/A")).append("</div>");
    }

    private void buildChargebackWriteOffBody(StringBuilder html, Map<String, Object> context) {
        html.append("<div class='alert critical'>");
        html.append("<strong>URGENT: Unrecoverable chargeback loss recorded.</strong>");
        html.append("</div>");

        html.append("<div class='detail'><span class='label'>Chargeback ID:</span> ")
                .append(context.getOrDefault("chargebackId", "N/A")).append("</div>");
        html.append("<div class='detail'><span class='label'>Write-off Amount:</span> K")
                .append(context.getOrDefault("writeOffAmount", "0.00")).append("</div>");
        html.append("<div class='detail'><span class='label'>Total Impact:</span> K")
                .append(context.getOrDefault("totalAmount", "0.00")).append("</div>");

        html.append("<p>This amount has been recorded as bad debt expense (account 5040).</p>");
    }

    private void buildPayoutBody(StringBuilder html, Map<String, Object> context) {
        html.append("<div class='detail'><span class='label'>Payout Amount:</span> K")
                .append(context.getOrDefault("amount", "0.00")).append("</div>");
        html.append("<div class='detail'><span class='label'>Bank Reference:</span> ")
                .append(context.getOrDefault("bankReference", "N/A")).append("</div>");
    }

    private void buildGenericBody(StringBuilder html, Map<String, Object> context) {
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            html.append("<div class='detail'><span class='label'>")
                    .append(formatLabel(entry.getKey())).append(":</span> ")
                    .append(entry.getValue()).append("</div>");
        }
    }

    private String buildAdminEmailBody(AlertPriority priority, String subject, String message) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; }");
        html.append(".priority-low { background: #d1ecf1; border-left: 4px solid #17a2b8; }");
        html.append(".priority-medium { background: #fff3cd; border-left: 4px solid #ffc107; }");
        html.append(".priority-high { background: #f8d7da; border-left: 4px solid #dc3545; }");
        html.append(".priority-critical { background: #721c24; color: white; border-left: 4px solid #f5c6cb; }");
        html.append(".alert-box { padding: 20px; margin: 20px 0; }");
        html.append("pre { background: #f8f9fa; padding: 10px; overflow-x: auto; }");
        html.append("</style></head><body>");

        String priorityClass = "priority-" + priority.name().toLowerCase();
        html.append("<div class='alert-box ").append(priorityClass).append("'>");
        html.append("<h2>").append(priority.getEmoji()).append(" ").append(subject).append("</h2>");
        html.append("<pre>").append(escapeHtml(message)).append("</pre>");
        html.append("</div>");

        html.append("<p><small>Generated by ").append(applicationName)
                .append(" at ").append(LocalDateTime.now()).append("</small></p>");

        html.append("</body></html>");
        return html.toString();
    }

    // ========================================================================
    // SLACK MESSAGE BUILDERS
    // ========================================================================

    private String buildSlackMessage(NotificationType type, AlertPriority priority,
                                      Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(type.getDisplayName()).append("*\n");

        switch (type) {
            case CHARGEBACK_RECEIVED -> {
                sb.append(":warning: New chargeback filed\n");
                sb.append("• Chargeback ID: `").append(context.getOrDefault("chargebackId", "N/A")).append("`\n");
                sb.append("• Amount: *K").append(context.getOrDefault("amount", "0.00")).append("*\n");
                sb.append("• Reason: ").append(context.getOrDefault("reason", "Unknown")).append("\n");
                sb.append("• Deadline: ").append(context.getOrDefault("deadline", "N/A"));
            }
            case CHARGEBACK_RESOLVED -> {
                boolean won = Boolean.parseBoolean(context.getOrDefault("disputeWon", "false").toString());
                sb.append(won ? ":white_check_mark: Dispute WON\n" : ":x: Chargeback processed\n");
                sb.append("• Recovered: K").append(context.getOrDefault("recoveredAmount", "0.00")).append("\n");
                sb.append("• Source: ").append(context.getOrDefault("fundSource", "N/A"));
            }
            case CHARGEBACK_WRITE_OFF -> {
                sb.append(":rotating_light: *WRITE-OFF ALERT*\n");
                sb.append("• Amount: *K").append(context.getOrDefault("writeOffAmount", "0.00")).append("*\n");
                sb.append("• This is an unrecoverable loss");
            }
            default -> {
                for (Map.Entry<String, Object> entry : context.entrySet()) {
                    sb.append("• ").append(formatLabel(entry.getKey())).append(": ")
                            .append(entry.getValue()).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private String buildSlackAlertMessage(AlertPriority priority, String subject, String message) {
        return String.format("*[%s] %s*\n```%s```",
                priority.getDisplayName().toUpperCase(),
                subject,
                message);
    }

    private String buildAlertMessage(NotificationType type, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getDefaultSubject()).append("\n\n");

        for (Map.Entry<String, Object> entry : context.entrySet()) {
            sb.append(formatLabel(entry.getKey())).append(": ").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    private String formatLabel(String camelCase) {
        return camelCase
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("_", " ")
                .substring(0, 1).toUpperCase() + camelCase
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("_", " ")
                .substring(1);
    }

    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
