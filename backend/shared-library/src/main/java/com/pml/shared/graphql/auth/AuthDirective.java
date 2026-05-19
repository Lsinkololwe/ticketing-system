package com.pml.shared.graphql.auth;

import graphql.schema.*;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * GraphQL @auth directive implementation for field-level authorization.
 *
 * <h2>Overview</h2>
 * <p>
 * This directive enforces role-based access control on GraphQL fields and operations.
 * It integrates with Spring Security to extract roles from the JWT token and compare
 * them against the required role specified in the directive.
 * </p>
 *
 * <h2>Usage in Schema</h2>
 * <pre>
 * # Define directive (in auth.graphqls)
 * directive @auth(requires: Role = AUTHENTICATED) on FIELD_DEFINITION | OBJECT
 *
 * # Apply to mutations
 * type Mutation {
 *   createEvent(input: CreateEventInput!): Event @auth(requires: ORGANIZER)
 *   deleteUser(id: ID!): Boolean @auth(requires: ADMIN)
 *   purchaseTicket(input: PurchaseInput!): Ticket @auth(requires: CUSTOMER)
 * }
 *
 * # Apply to queries
 * type Query {
 *   adminDashboard: Dashboard @auth(requires: ADMIN)
 *   myTickets: [Ticket!]! @auth(requires: AUTHENTICATED)
 *   events: [Event!]!  # No @auth = PUBLIC access
 * }
 * </pre>
 *
 * <h2>Role Hierarchy</h2>
 * <pre>
 *   PUBLIC           - No authentication required (default if no @auth)
 *   AUTHENTICATED    - Any authenticated user
 *   CUSTOMER         - Customer role
 *   ORGANIZER        - Event organizers
 *   ADMIN            - Platform administrators
 *   SUPER_ADMIN      - Highest privilege
 *   FINANCE          - Financial operations
 *   INTERNAL         - Service-to-service only
 * </pre>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Enforces authorization at field level</li>
 *   <li>Defense in Depth: Works alongside Apollo Router limits</li>
 * </ul>
 *
 * @see Role
 * @see com.pml.shared.security.SecurityContextUtils
 * @since 1.0.0
 */
@Slf4j
public class AuthDirective implements SchemaDirectiveWiring {

    private static final String DIRECTIVE_NAME = "auth";
    private static final String REQUIRES_ARG = "requires";

    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
        GraphQLFieldDefinition field = environment.getElement();
        GraphQLFieldsContainer parentType = environment.getFieldsContainer();

        // Get the required role from directive argument
        GraphQLAppliedDirective directive = environment.getAppliedDirective(DIRECTIVE_NAME);
        if (directive == null) {
            return field;
        }

        Role requiredRole = getRequiredRole(directive);

        log.debug("Applying @auth directive to {}.{} requiring role: {}",
                parentType.getName(), field.getName(), requiredRole);

        // Create field coordinates for graphql-java 24.x API
        FieldCoordinates coordinates = FieldCoordinates.coordinates(
                parentType.getName(), field.getName());

        // Get the original data fetcher using FieldCoordinates (graphql-java 24.x API)
        GraphQLCodeRegistry.Builder codeRegistry = environment.getCodeRegistry();
        DataFetcher<?> originalFetcher = codeRegistry.getDataFetcher(coordinates, field);

        // Wrap with authorization check
        DataFetcher<?> authFetcher = createAuthDataFetcher(originalFetcher, requiredRole,
                parentType.getName(), field.getName());

        // Register the wrapped fetcher using FieldCoordinates
        codeRegistry.dataFetcher(coordinates, authFetcher);

        return field;
    }

    /**
     * Extract the required role from the directive argument.
     */
    private Role getRequiredRole(GraphQLAppliedDirective directive) {
        GraphQLAppliedDirectiveArgument requiresArg = directive.getArgument(REQUIRES_ARG);
        if (requiresArg == null || requiresArg.getValue() == null) {
            return Role.AUTHENTICATED; // Default to requiring authentication
        }

        Object value = requiresArg.getValue();
        if (value instanceof graphql.language.EnumValue enumValue) {
            return Role.valueOf(enumValue.getName());
        } else if (value instanceof String stringValue) {
            return Role.valueOf(stringValue);
        }

        return Role.AUTHENTICATED;
    }

    /**
     * Create a DataFetcher that wraps the original with authorization checks.
     */
    private DataFetcher<?> createAuthDataFetcher(DataFetcher<?> originalFetcher,
                                                   Role requiredRole,
                                                   String typeName,
                                                   String fieldName) {
        return environment -> {
            // PUBLIC role means no auth required
            if (requiredRole == Role.PUBLIC) {
                return originalFetcher.get(environment);
            }

            // Check authorization using reactive security context
            return checkAuthorization(requiredRole, typeName, fieldName)
                    .flatMap(authorized -> {
                        if (authorized) {
                            try {
                                Object result = originalFetcher.get(environment);
                                if (result instanceof Mono<?> mono) {
                                    return mono;
                                } else if (result instanceof CompletableFuture<?> future) {
                                    return Mono.fromFuture(future);
                                } else {
                                    return Mono.justOrEmpty(result);
                                }
                            } catch (Exception e) {
                                return Mono.error(e);
                            }
                        } else {
                            return Mono.error(new AccessDeniedException(
                                    String.format("Access denied to %s.%s. Required role: %s",
                                            typeName, fieldName, requiredRole)));
                        }
                    })
                    .toFuture();
        };
    }

    /**
     * Check if the current user has the required role.
     */
    private Mono<Boolean> checkAuthorization(Role requiredRole, String typeName, String fieldName) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(authentication -> {
                    Set<String> authorities = authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toSet());

                    boolean hasAccess = checkRoleHierarchy(requiredRole, authorities);

                    if (!hasAccess) {
                        log.warn("Authorization denied for {}.{} - required: {}, has: {}",
                                typeName, fieldName, requiredRole, authorities);
                    } else {
                        log.debug("Authorization granted for {}.{} - required: {}, has: {}",
                                typeName, fieldName, requiredRole, authorities);
                    }

                    return hasAccess;
                })
                .defaultIfEmpty(requiredRole == Role.PUBLIC); // Allow if PUBLIC, deny otherwise
    }

    /**
     * Check if user authorities satisfy the required role with hierarchy support.
     *
     * <p>Role hierarchy (higher roles include lower role permissions):</p>
     * <ul>
     *   <li>SUPER_ADMIN includes all roles</li>
     *   <li>ADMIN includes ORGANIZER, CUSTOMER, AUTHENTICATED</li>
     *   <li>FINANCE includes AUTHENTICATED</li>
     *   <li>ORGANIZER includes CUSTOMER, AUTHENTICATED</li>
     *   <li>CUSTOMER includes AUTHENTICATED</li>
     *   <li>INTERNAL is separate (service-to-service only)</li>
     * </ul>
     */
    private boolean checkRoleHierarchy(Role requiredRole, Set<String> authorities) {
        // SUPER_ADMIN has access to everything
        if (authorities.contains("ROLE_SUPER_ADMIN")) {
            return true;
        }

        // INTERNAL service has access to INTERNAL-marked operations
        if (requiredRole == Role.INTERNAL) {
            return authorities.contains("ROLE_INTERNAL") ||
                   authorities.contains("ROLE_INTERNAL_SERVICE") ||
                   authorities.contains("SCOPE_internal-read") ||
                   authorities.contains("SCOPE_internal-write");
        }

        // Check specific role requirements
        return switch (requiredRole) {
            case PUBLIC -> true; // Always allowed

            case AUTHENTICATED -> !authorities.isEmpty(); // Any authenticated user

            case CUSTOMER ->
                authorities.contains("ROLE_CUSTOMER") ||
                authorities.contains("ROLE_ORGANIZER") ||
                authorities.contains("ROLE_ADMIN") ||
                authorities.contains("ROLE_SUPER_ADMIN");

            case ORGANIZER ->
                authorities.contains("ROLE_ORGANIZER") ||
                authorities.contains("ROLE_ADMIN") ||
                authorities.contains("ROLE_SUPER_ADMIN");

            case ADMIN ->
                authorities.contains("ROLE_ADMIN") ||
                authorities.contains("ROLE_SUPER_ADMIN");

            case SUPER_ADMIN ->
                authorities.contains("ROLE_SUPER_ADMIN");

            case FINANCE ->
                authorities.contains("ROLE_FINANCE") ||
                authorities.contains("ROLE_ADMIN") ||
                authorities.contains("ROLE_SUPER_ADMIN");

            case INTERNAL -> false; // Handled above
        };
    }
}
