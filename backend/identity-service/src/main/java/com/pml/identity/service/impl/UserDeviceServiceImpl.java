package com.pml.identity.service.impl;

import com.pml.identity.web.graphql.dto.RegisterDeviceInput;
import com.pml.identity.domain.model.UserDevice;
import com.pml.identity.repository.UserDeviceRepository;
import com.pml.identity.service.UserDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Implementation of UserDeviceService.
 * Manages device registration for push notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeviceServiceImpl implements UserDeviceService {

    private final UserDeviceRepository deviceRepository;

    @Override
    public Mono<UserDevice> registerDevice(String userId, RegisterDeviceInput input) {
        log.debug("Registering device for user {}: token={}, platform={}",
            userId, input.deviceToken(), input.platform());

        // Check if device already exists
        return deviceRepository.findByUserIdAndDeviceToken(userId, input.deviceToken())
            .flatMap(existing -> {
                // Update existing device
                log.debug("Updating existing device {}", existing.getId());
                existing.setDeviceName(input.deviceName());
                existing.setPlatform(input.platform());
                existing.setActive(true);
                existing.setLastActiveAt(LocalDateTime.now());
                return deviceRepository.save(existing);
            })
            .switchIfEmpty(Mono.defer(() -> {
                // Create new device
                log.debug("Creating new device registration");
                UserDevice device = UserDevice.builder()
                    .userId(userId)
                    .deviceToken(input.deviceToken())
                    .platform(input.platform())
                    .deviceName(input.deviceName())
                    .isActive(true)
                    .lastActiveAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

                return deviceRepository.save(device);
            }));
    }

    @Override
    public Mono<Boolean> unregisterDevice(String deviceId) {
        log.debug("Unregistering device {}", deviceId);

        return deviceRepository.findById(deviceId)
            .flatMap(device -> {
                device.setActive(false);
                return deviceRepository.save(device);
            })
            .thenReturn(true)
            .onErrorReturn(false);
    }

    @Override
    public Flux<UserDevice> findByUserId(String userId) {
        log.debug("Finding devices for user {}", userId);
        return deviceRepository.findByUserId(userId);
    }

    @Override
    public Mono<UserDevice> updateLastActive(String deviceToken) {
        log.debug("Updating last active for device token {}", deviceToken);

        return deviceRepository.findByDeviceToken(deviceToken)
            .flatMap(device -> {
                device.setLastActiveAt(LocalDateTime.now());
                return deviceRepository.save(device);
            });
    }
}
