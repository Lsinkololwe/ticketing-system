package com.pml.identity.web.graphql.federation;

import com.pml.identity.security.IdentityTenantReads;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.pml.identity.domain.model.EventAccessGrant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Resolves {@code EventAccessGrant} when the router follows a reference from another subgraph.
 *
 * <h2>Why this one matters more than most</h2>
 * An event access grant is what says a given user may validate tickets at a given event —
 * booking's validation path is its main consumer, and the cross-service events carry
 * {@code identity.EventAccessGranted} and {@code identity.EventAccessRevoked} specifically to
 * keep booking's copy current.
 *
 * <p>The type is declared {@code @key(fields: "id")}; without a fetcher, any federated
 * reference to a grant resolves to null. For an authorization-adjacent type, a null that means
 * "could not resolve" is easy to read as "no grant exists" — a denial the platform never
 * decided to make.</p>
 *
 * <h2>No authorization here</h2>
 * Deliberately none: the router is the caller, resolving a reference already reached
 * legitimately. Whether the requesting user may see this grant is a question for the field,
 * under the schema's {@code @auth} directive — and answering it here would turn a permitted read
 * into a silent null.
 */
@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class EventAccessGrantEntityFetcher {

    private final IdentityTenantReads reads;

    /**
     * @param values the key the router sends, {@code {"__typename": "EventAccessGrant", "id": ...}}
     * @return the grant, or empty when the id does not resolve
     */
    @DgsEntityFetcher(name = "EventAccessGrant")
    public Mono<EventAccessGrant> fetchEventAccessGrant(Map<String, Object> values) {
        String id = (String) values.get("id");
        log.debug("Federation: resolving EventAccessGrant entity with id={}", id);
        return reads.grantForCaller(id);
    }
}
