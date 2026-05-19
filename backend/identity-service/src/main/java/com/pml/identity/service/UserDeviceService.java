package com.pml.identity.service;

import com.pml.identity.web.graphql.dto.RegisterDeviceInput;
import com.pml.identity.domain.model.UserDevice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing user devices for push notifications.
 */
public interface UserDeviceService {

    /**
     * Register a device for a user.
     *
     * @param userId the user ID
     * @param input device details
     * @return Mono containing the registered device
     */
    Mono<UserDevice> registerDevice(String userId, RegisterDeviceInput input);

    /**
     * Unregister a device.
     *
     * @param deviceId the device ID
     * @return Mono containing true if unregistered successfully
     */
    Mono<Boolean> unregisterDevice(String deviceId);

    /**
     * Find all devices for a user.
     *
     * @param userId the user ID
     * @return Flux of devices
     */
    Flux<UserDevice> findByUserId(String userId);

    /**
     * Update the last active timestamp for a device.
     *
     * @param deviceToken the device token
     * @return Mono containing the updated device
     */
    Mono<UserDevice> updateLastActive(String deviceToken);
}
