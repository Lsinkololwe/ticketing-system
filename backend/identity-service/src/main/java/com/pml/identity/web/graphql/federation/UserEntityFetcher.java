package com.pml.identity.web.graphql.federation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.UserService;
import graphql.schema.DataFetchingEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * ============================================================================
 * USER ENTITY FETCHER - Federation Entity Resolution
 * ============================================================================
 *
 * This class is the ENTRY POINT for Apollo Router to resolve User entities.
 *
 * USER IS THE MOST REFERENCED ENTITY:
 * ------------------------------------
 * User is referenced by multiple services:
 * - Catalog Service: Event.organizer -> User
 * - Booking Service: Ticket.buyer -> User
 *
 * When any of these services return a User stub, and the client requests
 * User fields (firstName, email, etc.), Router calls this fetcher.
 *
 * EXAMPLE FEDERATED QUERY:
 * ------------------------
 *
 *   query {
 *     ticket(id: "t1") {
 *       ticketNumber            <- From Booking Service
 *       buyer {                 <- Stub from Booking, resolved HERE
 *         firstName             <- From this fetcher
 *         email                 <- From this fetcher
 *       }
 *       event {                 <- Stub from Booking, resolved by Catalog
 *         title                 <- From Catalog Service
 *         organizer {           <- Stub from Catalog, resolved HERE
 *           firstName           <- From this fetcher
 *         }
 *       }
 *     }
 *   }
 *
 * In this query, User fields are requested in two places (buyer, organizer).
 * Router batches these and makes a SINGLE call to our _entities query with
 * both User representations.
 *
 * EXTENDED FIELDS FROM BOOKING SERVICE:
 * -------------------------------------
 * Booking Service extends User with:
 * - purchasedTickets: [Ticket!]!
 * - totalSpent: BigDecimal!
 * - activeTicketCount: Int!
 *
 * When a query requests these fields, Router calls Booking Service AFTER
 * resolving the base User from this service.
 *
 * SECURITY CONSIDERATIONS:
 * ------------------------
 * This fetcher returns the full User object. Be careful about which fields
 * are exposed in the GraphQL schema. Sensitive fields like passwordHash
 * should NEVER be in the GraphQL schema.
 *
 * ============================================================================
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class UserEntityFetcher {

    private final UserService userService;

    /**
     * Resolve a User entity by its key fields.
     *
     * This method is called by Apollo Router during federation query planning
     * when a User entity needs to be resolved from its key.
     *
     * BATCH OPTIMIZATION NOTE:
     * If you see performance issues with many User resolutions, consider
     * implementing a batch fetcher that takes List<Map<String, Object>>
     * and fetches all users in a single database query.
     *
     * @param values Map containing the @key fields (id)
     * @return The resolved User entity, or empty Mono if not found
     */
    @DgsEntityFetcher(name = "User")
    public Mono<User> fetchUser(Map<String, Object> values) {
        String id = (String) values.get("id");
        log.debug("Federation: Resolving User entity with id={}", id);
        return userService.findById(id);
    }

    /**
     * Resolves the fullName computed field for User.
     * This also helps DGS properly initialize data fetcher wrapping
     * for the Spring GraphQL integration.
     */
    @DgsData(parentType = "User", field = "fullName")
    public String fullName(DataFetchingEnvironment env) {
        User user = env.getSource();
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }
}
