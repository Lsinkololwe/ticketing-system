package com.pml.identity.security;

import com.pml.identity.service.AuthorizationService;
import com.pml.shared.security.tenancy.TenantMemberships;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Identity's source of tenancy — read locally, since this service owns the memberships.
 *
 * <p>Catalog and booking reach identity over HTTP for this. Identity is identity: it queries
 * {@link AuthorizationService} directly, so there is no transport to fail and no outage to
 * distinguish from "belongs to nothing". A repository error still propagates rather than
 * becoming an empty set — {@link TenantMemberships} forbids that conflation for the same reason
 * in every service.
 *
 * <p>Inactive memberships are filtered here, so no resolver has to remember {@code isActive}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityTenantMemberships implements TenantMemberships {

    private final AuthorizationService authorizationService;

    @Override
    public Mono<Set<String>> activeOrganizationIdsOf(String subject) {
        if (subject == null || subject.isBlank()) {
            return Mono.just(Set.of());
        }
        return authorizationService.getUserOrganizations(subject)
                .filter(m -> m.isActive() && m.organizationId() != null && !m.organizationId().isBlank())
                .map(m -> m.organizationId())
                .collect(Collectors.toUnmodifiableSet())
                .doOnNext(ids -> log.debug("Resolved {} active membership(s) for {}", ids.size(), subject));
    }
}
