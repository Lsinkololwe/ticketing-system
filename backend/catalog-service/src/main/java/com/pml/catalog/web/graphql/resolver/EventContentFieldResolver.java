package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.service.OrganizerProfileService;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventContentFieldResolver {

    private static final String UNKNOWN = "Unknown";

    private final OrganizerProfileService organizers;
    private final IdentityServiceClient identity;
    private final ReactiveMongoTemplate mongo;

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

    /**
     * The name stored when the event was created. {@code Event.organizerName} is non-null in the
     * schema, and an event written before creation began denormalizing it has none: without this,
     * one such row fails the whole list it appears in, not just its own field.
     *
     * <p>Such an event is repaired the first time it is read: the name is asked of identity and
     * written back, so the lookup happens once per legacy event and not on every list. An identity
     * outage answers "Unknown" and writes nothing, so the next read tries again.
     */
    @DgsData(parentType = "Event", field = "organizerName")
    public Mono<String> organizerName(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        String stored = event.getOrganizerName();
        if (stored != null && !stored.isBlank()) {
            return Mono.just(stored);
        }
        String organizationId = event.getOrganizationId();
        if (organizationId == null || organizationId.isBlank()) {
            return Mono.just(UNKNOWN);
        }
        return identity.getOrganizationName(organizationId)
                .filter(name -> !name.isBlank())
                .flatMap(name -> mongo.updateFirst(
                                Query.query(Criteria.where("_id").is(event.getId())
                                        .orOperator(Criteria.where("organizerName").exists(false),
                                                Criteria.where("organizerName").is(null),
                                                Criteria.where("organizerName").is(""))),
                                Update.update("organizerName", name), Event.class)
                        .onErrorResume(error -> {
                            log.warn("Could not store organizer name for event {}: {}", event.getId(), error.toString());
                            return Mono.empty();
                        })
                        .thenReturn(name))
                .onErrorResume(error -> {
                    log.warn("Organizer name lookup failed for organization {}: {}", organizationId, error.toString());
                    return Mono.empty();
                })
                .defaultIfEmpty(UNKNOWN);
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
