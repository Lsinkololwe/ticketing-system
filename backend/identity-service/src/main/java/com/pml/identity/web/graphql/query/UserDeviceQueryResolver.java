package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.pml.identity.domain.model.UserDevice;
import com.pml.identity.service.UserDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;

/**
 * GraphQL query resolver for user device queries.
 * Provides endpoints for fetching registered devices.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class UserDeviceQueryResolver {

    private final UserDeviceService deviceService;

    /**
     * Query to fetch the authenticated user's registered devices.
     *
     * @param jwt the authenticated user's JWT token
     * @return Flux of user devices
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<UserDevice> myDevices(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.debug("Fetching devices for user {}", userId);
        return deviceService.findByUserId(userId);
    }
}
