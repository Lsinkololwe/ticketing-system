package com.pml.catalog.web.graphql.federation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * ============================================================================
 * EVENT ENTITY FETCHER - Federation Entity Resolution
 * ============================================================================
 *
 * This class is the ENTRY POINT for Apollo Router to resolve Event entities.
 *
 * WHAT IS AN ENTITY FETCHER?
 * --------------------------
 * In GraphQL Federation, when another service (like Booking Service) returns
 * an Event reference (stub with just {__typename: "Event", id: "123"}), Apollo
 * Router needs to "hydrate" that stub with actual Event data.
 *
 * This fetcher is called as part of the _entities query, which is a special
 * query that Apollo Router uses for entity resolution.
 *
 * HOW IT WORKS:
 * -------------
 * 1. Booking Service returns: Ticket { event: {__typename: "Event", id: "123"} }
 * 2. If client requested event.title, Router needs the full Event
 * 3. Router calls this service's _entities query with: [{__typename: "Event", id: "123"}]
 * 4. DGS framework routes this to our @DgsEntityFetcher based on __typename
 * 5. We fetch and return the full Event
 * 6. Router merges the Event data into the original response
 *
 * THE @key DIRECTIVE CONNECTION:
 * ------------------------------
 * In our schema, Event is defined with @key(fields: "id").
 * This tells Router that "id" is how we identify Events.
 * The Map<String, Object> values will contain the key fields: {"id": "123"}
 *
 * For composite keys like @key(fields: "orgId eventId"), values would be:
 * {"orgId": "abc", "eventId": "123"}
 *
 * BATCHING FOR PERFORMANCE:
 * -------------------------
 * For better performance with multiple entities, DGS supports batch fetching.
 * You can create a batch fetcher that receives List<Map<String, Object>> and
 * returns Map<Map<String, Object>, Event> for efficient database queries.
 *
 * ============================================================================
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventEntityFetcher {

    private final EventService eventService;

    /**
     * Resolve an Event entity by its key fields.
     *
     * This method is called by Apollo Router during federation query planning
     * when an Event entity needs to be resolved from its key.
     *
     * The @DgsEntityFetcher annotation tells DGS:
     * - "Event" is the GraphQL type name to handle
     * - When _entities is called with __typename: "Event", route here
     *
     * IMPORTANT: The returned Event MUST have its id field set to the same
     * value as the requested key. If it doesn't match, Router will fail
     * to merge the response correctly.
     *
     * @param values Map containing the @key fields defined in schema (id)
     * @return The resolved Event entity, or empty Mono if not found
     */
    @DgsEntityFetcher(name = "Event")
    public Mono<Event> fetchEvent(Map<String, Object> values) {
        String id = (String) values.get("id");
        log.debug("Federation: Resolving Event entity with id={}", id);
        return eventService.findById(id);
    }
}
