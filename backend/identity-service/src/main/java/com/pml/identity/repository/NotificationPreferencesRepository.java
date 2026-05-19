package com.pml.identity.repository;

import com.pml.identity.domain.model.NotificationPreferences;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repository for managing NotificationPreferences entities in MongoDB.
 * Provides reactive queries for user notification preference management.
 */
@Repository
public interface NotificationPreferencesRepository extends ReactiveMongoRepository<NotificationPreferences, String> {

    /**
     * Find notification preferences by user ID.
     *
     * @param userId the user ID
     * @return Mono containing the user's notification preferences, or empty if not found
     */
    Mono<NotificationPreferences> findByUserId(String userId);
}
