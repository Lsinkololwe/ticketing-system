package com.pml.identity.service;

import com.pml.identity.web.graphql.dto.UpdateNotificationPreferencesInput;
import com.pml.identity.domain.model.NotificationPreferences;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing user notification preferences.
 */
public interface NotificationPreferencesService {

    /**
     * Find notification preferences for a user.
     *
     * @param userId the user ID
     * @return Mono containing the preferences, or empty if not found
     */
    Mono<NotificationPreferences> findByUserId(String userId);

    /**
     * Update notification preferences for a user.
     *
     * @param userId the user ID
     * @param input the preferences to update
     * @return Mono containing the updated preferences
     */
    Mono<NotificationPreferences> updatePreferences(String userId, UpdateNotificationPreferencesInput input);

    /**
     * Get notification preferences for a user, creating default preferences if they don't exist.
     *
     * @param userId the user ID
     * @return Mono containing the preferences
     */
    Mono<NotificationPreferences> getOrCreateDefault(String userId);
}
