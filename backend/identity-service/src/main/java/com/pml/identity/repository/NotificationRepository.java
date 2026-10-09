package com.pml.identity.repository;

import com.pml.identity.domain.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for managing Notification entities in MongoDB.
 * Provides reactive queries for notification retrieval and status tracking.
 */
@Repository
public interface NotificationRepository extends ReactiveMongoRepository<Notification, String> {

    /**
     * Find notifications for a user, ordered by creation date (newest first).
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return Flux of notifications
     */
    Flux<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Find unread notifications for a user.
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return Flux of unread notifications
     */
    Flux<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Count unread notifications for a user.
     *
     * @param userId the user ID
     * @return Mono containing the count of unread notifications
     */
    @Query("{ 'userId': ?0, 'readAt': null }")
    Mono<Long> countUnreadByUserId(String userId);

    /**
     * Find all notifications for a user, ordered by creation date (newest first).
     *
     * @param userId the user ID
     * @return Flux of all notifications for the user
     */
    Flux<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    /** A notification only if it belongs to the user; another user's id answers empty, like an unknown one. */
    Mono<Notification> findByIdAndUserId(String id, String userId);

    /** Deletes the user's own notification; the count is zero for an unknown id and for someone else's. */
    Mono<Long> deleteByIdAndUserId(String id, String userId);
}
