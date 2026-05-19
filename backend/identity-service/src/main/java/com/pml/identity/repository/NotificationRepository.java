package com.pml.identity.repository;

import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.enums.NotificationStatus;
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
     * Find notifications for a user with a specific status, ordered by creation date.
     *
     * @param userId the user ID
     * @param status the notification status
     * @param pageable pagination parameters
     * @return Flux of notifications
     */
    Flux<Notification> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, NotificationStatus status, Pageable pageable);

    /**
     * Count notifications for a user with a specific status.
     *
     * @param userId the user ID
     * @param status the notification status
     * @return Mono containing the count
     */
    Mono<Long> countByUserIdAndStatus(String userId, NotificationStatus status);

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
}
