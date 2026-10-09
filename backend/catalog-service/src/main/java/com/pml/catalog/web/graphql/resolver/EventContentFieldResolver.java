package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.service.OrganizerProfileService;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * The fields of an event that depend on who is asking, and the organization fields catalog adds.
 *
 * <p>Sales totals are the organization's business: the owner and a platform administrator read them,
 * everyone else gets null. The check is on the field, not the query, because an event reaches a
 * buyer's screen through a dozen queries and every one of them selects the same type.
 */
@DgsComponent
@RequiredArgsConstructor
public class EventContentFieldResolver {

    private final OrganizerProfileService organizers;

    /** The reference identity resolves: {@code verified}, the name and the rest are its. */
    @DgsData(parentType = "Event", field = "organization")
    public Map<String, Object> organization(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        if (event.getOrganizationId() == null || event.getOrganizationId().isBlank()) {
            return null;
        }
        Map<String, Object> reference = new HashMap<>();
        reference.put("__typename", "Organization");
        reference.put("id", event.getOrganizationId());
        return reference;
    }

    @DgsData(parentType = "Event", field = "grossSales")
    public Mono<BigDecimal> grossSales(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        return ownerOnly(event).map(owner -> money(event.getGrossSales()));
    }

    @DgsData(parentType = "Event", field = "commissionAmount")
    public Mono<BigDecimal> commissionAmount(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        return ownerOnly(event).map(owner -> money(event.getCommissionAmount()));
    }

    @DgsData(parentType = "Event", field = "netSales")
    public Mono<BigDecimal> netSales(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        return ownerOnly(event).map(owner -> money(event.getGrossSales()).subtract(money(event.getCommissionAmount())));
    }

    @DgsData(parentType = "Organization", field = "publishedEventCount")
    public Mono<Integer> publishedEventCount(DgsDataFetchingEnvironment dfe) {
        return organizers.publishedEventCount(organizationIdOf(dfe.getSource()));
    }

    @DgsData(parentType = "Organization", field = "completedEventCount")
    public Mono<Integer> completedEventCount(DgsDataFetchingEnvironment dfe) {
        return organizers.completedEventCount(organizationIdOf(dfe.getSource()));
    }

    /** Emits once when the caller may see the event's money, and nothing otherwise. */
    private static Mono<Boolean> ownerOnly(Event event) {
        return CurrentTenantScope.get()
                .filter(scope -> scope.permits(event.getOrganizationId()))
                .map(scope -> true)
                .onErrorResume(error -> Mono.empty());
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @SuppressWarnings("unchecked")
    private static String organizationIdOf(Object source) {
        return source instanceof Map<?, ?> map ? String.valueOf(((Map<String, Object>) map).get("id")) : null;
    }
}
