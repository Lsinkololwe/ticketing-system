package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.security.Permission;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * The one way booking asks identity whether a person holds a permission in an organization, and
 * the one place that decides what an unanswered question means: it means no. An identity outage,
 * an empty reply and a malformed identifier all deny, so a failure can never widen access.
 */
@Slf4j
final class OrganizationPermissionCheck {

    private OrganizationPermissionCheck() {
    }

    /**
     * @param eventId when not null the question is about that event, and an event grant decides
     *                alone if one exists
     * @return true only when identity answers that the person holds {@code permission}
     */
    static Mono<Boolean> holds(IdentityServiceClient identity, String userId, String organizationId,
                               String eventId, Permission permission) {
        if (userId == null || userId.isBlank() || organizationId == null || organizationId.isBlank()) {
            return Mono.just(false);
        }
        return identity.checkAuthorization(AuthorizationRequest.builder()
                        .userId(userId)
                        .organizationId(organizationId)
                        .eventId(eventId)
                        .requiredPermission(permission.code())
                        .build())
                .map(result -> result.isAuthorized())
                .onErrorResume(error -> {
                    log.error("{} check failed for organization {}: {}", permission.code(), organizationId, error.toString());
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }
}
