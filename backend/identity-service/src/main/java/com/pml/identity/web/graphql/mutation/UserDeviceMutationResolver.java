package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.RegisterDeviceInput;
import com.pml.identity.domain.model.UserDevice;
import com.pml.identity.service.UserDeviceService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

/**
 * GraphQL mutation resolver for user device mutations.
 * Provides endpoints for device registration and management.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class UserDeviceMutationResolver {

    private final UserDeviceService deviceService;

    /**
     * Mutation to register a device for push notifications.
     *
     * @param input device details
     * @param jwt the authenticated user's JWT token
     * @return Mono containing the registered device
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<UserDevice> registerDevice(
        @InputArgument RegisterDeviceInput input,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        log.debug("Registering device for user {}: platform={}", userId, input.platform());
        return deviceService.registerDevice(userId, input);
    }

    /**
     * Mutation to unregister a device.
     *
     * @param deviceId the device ID
     * @return Mono containing true if unregistered successfully
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> unregisterDevice(@InputArgument String deviceId) {
        log.debug("Unregistering device {}", deviceId);
        return deviceService.unregisterDevice(deviceId);
    }
}
