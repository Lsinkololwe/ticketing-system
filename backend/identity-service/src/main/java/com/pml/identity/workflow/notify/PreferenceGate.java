package com.pml.identity.workflow.notify;

import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.enums.NotificationType;
import com.pml.identity.domain.model.NotificationPreferences;
import com.pml.identity.service.QuietHours;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Which channels a message may use, given what its recipient chose.
 *
 * <p>Only optional categories consult preferences. An essential message — a ticket, a payment, a
 * change to an event someone holds a ticket for — goes out on the whole chain whatever the
 * recipient switched off, because not sending it is worse than an unwanted message.
 */
public final class PreferenceGate {

    /** The categories a recipient may switch off. Every other notification type is essential. */
    public static final Set<NotificationType> OPTIONAL = Set.of(NotificationType.EVENT_REMINDER);

    public static final String CATEGORY_DISABLED = "CATEGORY_DISABLED";
    public static final String QUIET_HOURS = "QUIET_HOURS";
    public static final String ALL_CHANNELS_DISABLED = "ALL_CHANNELS_DISABLED";

    /** The channels to try, or why none may be. */
    public record Verdict(List<NotificationChannel> channels, String suppressedBecause) {
        public boolean suppressed() {
            return suppressedBecause != null;
        }
    }

    private PreferenceGate() {
    }

    /** @param preferences the recipient's, or null for a recipient who never set any */
    public static Verdict decide(NotificationPreferences preferences, NotificationType type, Instant now) {
        if (!OPTIONAL.contains(type)) {
            return new Verdict(NotificationRules.CHAIN, null);
        }
        NotificationPreferences chosen = preferences != null ? preferences : NotificationPreferences.defaultPreferences(null);
        if (type == NotificationType.EVENT_REMINDER && !chosen.isEventReminders()) {
            return new Verdict(List.of(), CATEGORY_DISABLED);
        }
        if (QuietHours.contains(chosen.getQuietHoursStart(), chosen.getQuietHoursEnd(), chosen.getTimezone(), now)) {
            return new Verdict(List.of(), QUIET_HOURS);
        }
        List<NotificationChannel> channels = NotificationRules.CHAIN.stream()
                .filter(channel -> switch (channel) {
                    case WHATSAPP -> chosen.isWhatsappEnabled();
                    case SMS -> chosen.isSmsEnabled();
                    case EMAIL -> chosen.isEmailEnabled();
                    case PUSH -> chosen.isPushEnabled();
                    case IN_APP -> chosen.isInAppEnabled();
                })
                .toList();
        return channels.isEmpty() ? new Verdict(List.of(), ALL_CHANNELS_DISABLED) : new Verdict(channels, null);
    }
}
