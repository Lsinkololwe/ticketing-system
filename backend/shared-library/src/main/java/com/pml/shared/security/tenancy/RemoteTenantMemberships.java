package com.pml.shared.security.tenancy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A caller's active organizations, asked of identity-service, which owns memberships.
 *
 * <p>For every service except identity itself, which answers from its own collection. A failed
 * lookup is left to propagate: "identity is down" and "this user belongs to nothing" must not
 * produce the same answer, or an outage tells every organizer their own events do not exist —
 * see {@link TenantMemberships}.
 */
public class RemoteTenantMemberships implements TenantMemberships {

    static final String PATH = "/api/internal/authorization/user-organizations";

    private final WebClient identity;

    public RemoteTenantMemberships(WebClient identity) {
        this.identity = identity;
    }

    @Override
    public Mono<Set<String>> activeOrganizationIdsOf(String subject) {
        if (subject == null || subject.isBlank()) {
            // Not a lookup failure: there is no principal to look up. Permits nothing.
            return Mono.just(Set.of());
        }
        return identity.get()
                .uri(uri -> uri.path(PATH).queryParam("userId", subject).build())
                .retrieve()
                .bodyToMono(Memberships.class)
                .map(Memberships::activeOrganizationIds);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Memberships(List<Membership> organizations) {
        Set<String> activeOrganizationIds() {
            return organizations == null ? Set.of() : organizations.stream()
                    .filter(Membership::isActive)
                    .map(Membership::organizationId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Membership(String organizationId, boolean isActive) {
    }
}
