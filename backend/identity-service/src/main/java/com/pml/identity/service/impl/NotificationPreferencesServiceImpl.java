package com.pml.identity.service.impl;

import com.pml.identity.service.QuietHours;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import org.springframework.dao.DuplicateKeyException;
import java.util.ArrayList;
import java.util.List;
import com.pml.identity.web.graphql.dto.UpdateNotificationPreferencesInput;
import com.pml.identity.domain.model.NotificationPreferences;
import com.pml.identity.repository.NotificationPreferencesRepository;
import com.pml.identity.service.NotificationPreferencesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementation of NotificationPreferencesService.
 * Manages user notification delivery preferences.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferencesServiceImpl implements NotificationPreferencesService {

    private final NotificationPreferencesRepository preferencesRepository;

    @Override
    public Mono<NotificationPreferences> findByUserId(String userId) {
        log.debug("Finding notification preferences for user {}", userId);
        return preferencesRepository.findByUserId(userId);
    }

    @Override
    public Mono<NotificationPreferences> updatePreferences(String userId, UpdateNotificationPreferencesInput input) {
        List<FieldViolation> violations = new ArrayList<>();
        essential(violations, "ticketNotifications", input.ticketNotifications());
        essential(violations, "paymentNotifications", input.paymentNotifications());
        essential(violations, "eventUpdates", input.eventUpdates());
        essential(violations, "teamNotifications", input.teamNotifications());
        essential(violations, "systemAnnouncements", input.systemAnnouncements());
        if (input.timezone() != null && !input.timezone().isBlank() && !QuietHours.isZone(input.timezone())) {
            violations.add(new FieldViolation("timezone", "must be an IANA time zone such as Africa/Lusaka"));
        }
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }

        return getOrCreateDefault(userId)
            .flatMap(prefs -> {
                if (input.emailEnabled() != null) prefs.setEmailEnabled(input.emailEnabled());
                if (input.smsEnabled() != null) prefs.setSmsEnabled(input.smsEnabled());
                if (input.whatsappEnabled() != null) prefs.setWhatsappEnabled(input.whatsappEnabled());
                if (input.pushEnabled() != null) prefs.setPushEnabled(input.pushEnabled());
                if (input.inAppEnabled() != null) prefs.setInAppEnabled(input.inAppEnabled());
                if (input.eventReminders() != null) prefs.setEventReminders(input.eventReminders());
                if (input.marketingEmails() != null) prefs.setMarketingEmails(input.marketingEmails());
                if (input.reminderHoursBefore() != null) prefs.setReminderHoursBefore(input.reminderHoursBefore());
                if (input.quietHoursStart() != null) prefs.setQuietHoursStart(blankToNull(input.quietHoursStart()));
                if (input.quietHoursEnd() != null) prefs.setQuietHoursEnd(blankToNull(input.quietHoursEnd()));
                if (input.timezone() != null) prefs.setTimezone(blankToNull(input.timezone()));
                if ((prefs.getQuietHoursStart() == null) != (prefs.getQuietHoursEnd() == null)) {
                    return Mono.error(new ValidationRefusal(List.of(new FieldViolation(
                            prefs.getQuietHoursStart() == null ? "quietHoursStart" : "quietHoursEnd",
                            "quiet hours need both a start and an end"))));
                }
                return preferencesRepository.save(prefs);
            });
    }

    /** An essential category cannot be switched off; saying so beats silently keeping it on. */
    private static void essential(List<FieldViolation> violations, String field, Boolean requested) {
        if (Boolean.FALSE.equals(requested)) {
            violations.add(new FieldViolation(field, "is always on: these messages are about the user's own tickets, money or account"));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public Mono<NotificationPreferences> getOrCreateDefault(String userId) {
        log.debug("Getting or creating default notification preferences for user {}", userId);

        return preferencesRepository.findByUserId(userId)
            .switchIfEmpty(Mono.defer(() -> {
                NotificationPreferences defaultPrefs = NotificationPreferences.defaultPreferences(userId);
                // Two first requests can race to create the row; userId is unique, so the loser reads
                // the winner's row instead of failing.
                return preferencesRepository.save(defaultPrefs)
                        .onErrorResume(DuplicateKeyException.class, raced -> preferencesRepository.findByUserId(userId));
            }));
    }
}
