package com.pml.catalog.web.graphql.federation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.Location;
import com.pml.catalog.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ============================================================================
 * EVENT FIELD RESOLVER - Federation Entity References
 * ============================================================================
 *
 * This resolver handles fields on the Event type that reference OTHER
 * federated entities (User from Identity Service).
 *
 * HOW ENTITY REFERENCES WORK:
 * ---------------------------
 *
 * When an Event has an "organizer" field that returns a User, we don't actually
 * need to fetch the full User from Identity Service ourselves. Instead:
 *
 * 1. We return a "stub" containing just the @key fields (id) and __typename
 * 2. Apollo Router sees this stub and recognizes it as a User reference
 * 3. If the client requested more User fields (like firstName, email), Router calls
 *    Identity Service's _entities query to fetch them
 * 4. Router merges our Event data with Identity's User data
 *
 * EXAMPLE QUERY FLOW:
 * -------------------
 *
 *   query {
 *     event(id: "123") {
 *       title                    <- Resolved by Catalog Service
 *       organizer {              <- This resolver returns User stub
 *         firstName              <- Router fetches from Identity Service
 *         email                  <- Router fetches from Identity Service
 *       }
 *     }
 *   }
 *
 * Step 1: Catalog Service resolves Event { title, organizer: {__typename: "User", id: "456"} }
 * Step 2: Router sees User reference, calls Identity Service: _entities(representations: [{__typename: "User", id: "456"}])
 * Step 3: Identity Service returns full User { firstName: "John", email: "john@example.com" }
 * Step 4: Router merges: Event { title: "...", organizer: { firstName: "John", email: "john@example.com" } }
 *
 * WHY WE INCLUDE organizerName IN THE STUB:
 * -----------------------------------------
 *
 * We could optionally use @provides to tell Router that we already have
 * some User fields cached (like the organizer's name). However, since
 * we use `resolvable: false` on the User stub type, we don't use @provides here.
 * The Identity Service is always the source of truth for User data.
 *
 * ============================================================================
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventFieldResolver {

    private final LocationService locationService;

    /**
     * ========================================================================
     * ORGANIZER FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: Event.organizer: User
     *
     * Returns a User "representation" (stub) that Apollo Router can use
     * to fetch the full User from Identity Service if needed.
     *
     * The representation MUST include:
     * - __typename: "User" (tells Router what type this is)
     * - id: The organizer's user ID (the @key field)
     *
     * Apollo Router will:
     * 1. Receive this stub
     * 2. Check if the client requested any User fields beyond id
     * 3. If yes, call Identity Service's _entities query to resolve the User
     * 4. Merge the User data with the Event data
     *
     * @param dfe The DataFetchingEnvironment containing the parent Event
     * @return Map representing a User entity reference (stub)
     */
    @DgsData(parentType = "Event", field = "organizer")
    public Map<String, Object> getEventOrganizer(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();

        // If no organizerId, return null (optional field)
        if (event.getOrganizerId() == null) {
            log.debug("Federation: Event.organizer is null for eventId={} (no organizerId)",
                    event.getId());
            return null;
        }

        log.debug("Federation: Resolving Event.organizer for eventId={}, organizerId={}",
                event.getId(), event.getOrganizerId());

        // Build the User representation (stub)
        // This is the minimum required for Apollo Router to resolve the User
        Map<String, Object> userRepresentation = new HashMap<>();

        // REQUIRED: These fields are necessary for federation to work
        // __typename tells Router this is a User entity
        userRepresentation.put("__typename", "User");

        // id is the @key field - Router uses this to fetch the full User
        userRepresentation.put("id", event.getOrganizerId());

        // NOTE: We could add @provides for cached fields like organizerName,
        // but since User is defined with resolvable: false, we let Identity
        // Service be the single source of truth for all User fields.

        return userRepresentation;
    }

    /**
     * ========================================================================
     * LOCATION FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: Event.location: Location
     *
     * Location is NOT a federated entity (no @key directive) - it's a simple
     * address type owned entirely by this service. Therefore, we must return
     * the actual Location object, not a federation stub.
     *
     * This resolver fetches the full Location from the database using the
     * event's locationId reference.
     *
     * @param dfe The DataFetchingEnvironment containing the parent Event
     * @return The full Location object, or null if not found
     */
    @DgsData(parentType = "Event", field = "location")
    public CompletableFuture<Location> getEventLocation(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();

        // If no locationId, return null (some events might be virtual)
        if (event.getLocationId() == null) {
            log.debug("Event.location is null for eventId={} (no locationId)", event.getId());
            return CompletableFuture.completedFuture(null);
        }

        log.debug("Resolving Event.location for eventId={}, locationId={}",
                event.getId(), event.getLocationId());

        // Fetch the actual Location object from the database
        // Location is NOT a federated entity, so we must return the full object
        return locationService.findById(event.getLocationId())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Location not found for id={}, returning null", event.getLocationId());
                    return Mono.empty();
                }))
                .toFuture();
    }
}
