package com.pml.shared.security.tenancy;

import com.pml.shared.security.authorization.IdentityAuthorizationClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

    private final IdentityAuthorizationClient identity;

    public RemoteTenantMemberships(WebClient identity) {
        this.identity = new IdentityAuthorizationClient(identity);
    }

    @Override
    public Mono<Set<String>> activeOrganizationIdsOf(String subject) {
        if (subject == null || subject.isBlank()) {
            // Not a lookup failure: there is no principal to look up. Permits nothing.
            return Mono.just(Set.of());
        }
        return identity.userOrganizations(subject).map(answer -> answer.organizations() == null ? Set.<String>of()
                : answer.organizations().stream()
                        .filter(IdentityAuthorizationClient.Membership::isActive)
                        .map(IdentityAuthorizationClient.Membership::organizationId)
                        .filter(id -> id != null && !id.isBlank())
                        .collect(Collectors.toUnmodifiableSet()));
    }
}
