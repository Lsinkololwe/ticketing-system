package com.pml.booking.domain.enums;

/**
 * Alert priority levels for notification routing.
 *
 * <p>Determines which channels receive the notification and how urgently
 * it should be handled by the recipient.</p>
 *
 * <h2>Channel Routing</h2>
 * <table border="1">
 *   <tr><th>Priority</th><th>Email</th><th>Slack</th><th>SMS</th></tr>
 *   <tr><td>LOW</td><td>✓</td><td></td><td></td></tr>
 *   <tr><td>MEDIUM</td><td>✓</td><td>✓</td><td></td></tr>
 *   <tr><td>HIGH</td><td>✓</td><td>✓</td><td></td></tr>
 *   <tr><td>CRITICAL</td><td>✓</td><td>✓</td><td>✓</td></tr>
 * </table>
 *
 * @since 1.0.0
 */
public enum AlertPriority {

    /**
     * Low priority - informational alerts.
     * Sent via email only, can be batched.
     */
    LOW("Low", "📋", false, false),

    /**
     * Medium priority - requires attention within business hours.
     * Sent via email and Slack.
     */
    MEDIUM("Medium", "⚠️", true, false),

    /**
     * High priority - requires prompt attention.
     * Sent via email and Slack with @channel mention.
     */
    HIGH("High", "🚨", true, false),

    /**
     * Critical priority - requires immediate attention.
     * Sent via email, Slack (@channel), and SMS to on-call.
     */
    CRITICAL("Critical", "🔥", true, true);

    private final String displayName;
    private final String emoji;
    private final boolean sendToSlack;
    private final boolean sendSms;

    AlertPriority(String displayName, String emoji, boolean sendToSlack, boolean sendSms) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.sendToSlack = sendToSlack;
        this.sendSms = sendSms;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public boolean shouldSendToSlack() {
        return sendToSlack;
    }

    public boolean shouldSendSms() {
        return sendSms;
    }
}
