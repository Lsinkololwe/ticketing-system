package com.pml.catalog.security;

import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Who may add pictures for an organization: the same member who may create its events, resolved by
 * identity-service. The organization comes from that answer, never from anything the caller sends, so
 * a picture cannot be filed under an organization the caller is not in.
 */
@Component
@RequiredArgsConstructor
public class MediaAuthority {

    private final IdentityServiceClient identityServiceClient;

    /** The organization the caller may add pictures to, or an {@link AccessDeniedException}. */
    public Mono<String> organizationOf(String userId) {
        // Identity answers "may this user create events in THAT organization", so the organization has to be
        // named: the caller's own (the one they own, else the first they actively belong to).
        return identityServiceClient.getUserOrganizations(userId)
                .flatMap(memberships -> {
                    var active = memberships.organizations().stream()
                            .filter(IdentityServiceClient.OrganizationMembershipInfo::isActive).toList();
                    return active.stream().filter(m -> "OWNER".equals(m.role())).findFirst()
                            .or(() -> active.stream().findFirst())
                            .map(m -> Mono.just(m.organizationId()))
                            .orElseGet(() -> Mono.<String>error(new AccessDeniedException("pictures belong to an organization")));
                })
                .flatMap(organizationId -> identityServiceClient.checkAuthorization(AuthorizationRequest.builder()
                                .userId(userId)
                                .organizationId(organizationId)
                                .requiredPermission(Permission.EVENT_CREATE.code())
                                .build())
                        .flatMap(result -> result.isAuthorized()
                                ? Mono.just(organizationId)
                                : Mono.<String>error(new AccessDeniedException(result.getReason()))));
    }
}
