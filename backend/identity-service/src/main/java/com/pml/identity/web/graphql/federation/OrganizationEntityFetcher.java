package com.pml.identity.web.graphql.federation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Resolves {@code Organization} when the router follows a reference from another subgraph.
 *
 * <h2>What is missing without this</h2>
 * {@code Organization} is declared {@code @key(fields: "id")}, which tells the router it may be
 * referenced from anywhere and resolved here. Catalog holds an organization id on every event;
 * booking holds one on every payout. When a client asks for {@code event.organization.name} the
 * router sends an {@code _entities} query to this subgraph — and with no fetcher registered for
 * the type, the resolution yields <b>null</b>.
 *
 * <p>That is the failure worth naming: not an error, a null. The query succeeds, the response is
 * shaped correctly, and the organization is simply absent. A client cannot distinguish it from an
 * event whose organization was genuinely deleted, so the bug presents as missing data rather than
 * as a broken graph.</p>
 *
 * <h2>No authorization here</h2>
 * The caller is the <b>router</b>, resolving a reference the user already reached legitimately —
 * not the user. A check here fails the resolution of a field the user is entitled to, and the
 * symptom is again a null rather than a denial. Authorization belongs on the field, via the
 * schema's {@code @auth} directive, and entity fetchers never authorize.
 */
@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class OrganizationEntityFetcher {

    private final OrganizationService organizationService;

    /**
     * @param values the key the router sends, {@code {"__typename": "Organization", "id": ...}}
     * @return the organization, or empty when the id does not resolve — the router renders null,
     *         which is the correct answer for a reference to something that no longer exists
     */
    @DgsEntityFetcher(name = "Organization")
    public Mono<Organization> fetchOrganization(Map<String, Object> values) {
        String id = (String) values.get("id");
        log.debug("Federation: resolving Organization entity with id={}", id);
        return organizationService.findById(id);
    }
}
