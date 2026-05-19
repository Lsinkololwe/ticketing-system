package com.pml.identity.service.impl;

import com.pml.identity.web.graphql.dto.UpdateNotificationPreferencesInput;
import com.pml.identity.domain.model.NotificationPreferences;
import com.pml.identity.repository.NotificationPreferencesRepository;
import com.pml.identity.service.NotificationPreferencesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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
        log.debug("Updating notification preferences for user {}", userId);

        return getOrCreateDefault(userId)
            .flatMap(prefs -> {
                // Update only provided fields
                if (input.emailEnabled() != null) {
                    prefs.setEmailEnabled(input.emailEnabled());
                }
                if (input.smsEnabled() != null) {
                    prefs.setSmsEnabled(input.smsEnabled());
                }
                if (input.whatsappEnabled() != null) {
                    prefs.setWhatsappEnabled(input.whatsappEnabled());
                }
                if (input.pushEnabled() != null) {
                    prefs.setPushEnabled(input.pushEnabled());
                }
                if (input.eventReminders() != null) {
                    prefs.setEventReminders(input.eventReminders());
                }
                if (input.marketingEmails() != null) {
                    prefs.setMarketingEmails(input.marketingEmails());
                }
                if (input.reminderHoursBefore() != null) {
                    prefs.setReminderHoursBefore(input.reminderHoursBefore());
                }

                prefs.setUpdatedAt(LocalDateTime.now());
                return preferencesRepository.save(prefs);
            });
    }

    @Override
    public Mono<NotificationPreferences> getOrCreateDefault(String userId) {
        log.debug("Getting or creating default notification preferences for user {}", userId);

        return preferencesRepository.findByUserId(userId)
            .switchIfEmpty(Mono.defer(() -> {
                NotificationPreferences defaultPrefs = NotificationPreferences.defaultPreferences(userId);
                return preferencesRepository.save(defaultPrefs);
            }));
    }
}
