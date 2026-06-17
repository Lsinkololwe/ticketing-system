package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.pml.identity.domain.model.UserDevice;
import com.pml.identity.service.UserDeviceService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
     * @return Flux of user devices
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<UserDevice> myDevices() {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("Fetching devices for user {}", userId))
                .flatMapMany(deviceService::findByUserId);
    }
}
