package com.pml.identity.repository;

import com.pml.identity.domain.model.UserDevice;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for managing UserDevice entities in MongoDB.
 * Provides reactive queries for device registration and push notification targeting.
 */
@Repository
public interface UserDeviceRepository extends ReactiveMongoRepository<UserDevice, String> {

    /**
     * Find devices for a user filtered by active status.
     *
     * @param userId the user ID
     * @param isActive whether to find active or inactive devices
     * @return Flux of devices
     */
    Flux<UserDevice> findByUserIdAndIsActive(String userId, boolean isActive);

    /**
     * Find a device by its push notification token.
     *
     * @param deviceToken the device token (FCM/APNS)
     * @return Mono containing the device, or empty if not found
     */
    Mono<UserDevice> findByDeviceToken(String deviceToken);

    /**
     * Find a device by user ID and device token.
     * Used to check if a device is already registered before creating a new entry.
     *
     * @param userId the user ID
     * @param deviceToken the device token
     * @return Mono containing the device, or empty if not found
     */
    Mono<UserDevice> findByUserIdAndDeviceToken(String userId, String deviceToken);

    /**
     * Find all devices for a user.
     *
     * @param userId the user ID
     * @return Flux of all user devices
     */
    Flux<UserDevice> findByUserId(String userId);
}
