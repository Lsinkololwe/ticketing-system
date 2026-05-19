package com.pml.shared.graphql.auth;

/**
 * Authorization roles for GraphQL @auth directive.
 *
 * <p>These roles map to Keycloak realm roles and are extracted from JWT tokens
 * by {@link com.pml.shared.security.KeycloakJwtAuthenticationConverter}.</p>
 *
 * <h2>Role Hierarchy</h2>
 * <pre>
 *   PUBLIC           - No authentication required
 *       │
 *   AUTHENTICATED    - Any authenticated user
 *       │
 *   ├── CUSTOMER     - Regular ticket buyers
 *   │
 *   ├── ORGANIZER    - Event organizers (own organizations/events)
 *   │
 *   ├── ADMIN        - Platform administrators
 *   │
 *   └── INTERNAL     - Service-to-service calls only
 * </pre>
 *
 * @since 1.0.0
 */
public enum Role {

    /**
     * No authentication required. Field/operation is publicly accessible.
     */
    PUBLIC,

    /**
     * Any authenticated user can access.
     * Requires valid JWT token but no specific role.
     */
    AUTHENTICATED,

    /**
     * Customer role - regular ticket buyers.
     * Can purchase tickets, view own tickets, request refunds.
     */
    CUSTOMER,

    /**
     * Organizer role - event creators and managers.
     * Can create events, manage tickets for own events, validate tickets.
     */
    ORGANIZER,

    /**
     * Admin role - platform administrators.
     * Full access to all data and operations.
     */
    ADMIN,

    /**
     * Super Admin role - highest privilege level.
     * System-wide access including user management.
     */
    SUPER_ADMIN,

    /**
     * Finance role - financial operations.
     * Access to payouts, commissions, financial reports.
     */
    FINANCE,

    /**
     * Internal service role - service-to-service communication.
     * Used for internal API calls between microservices.
     */
    INTERNAL;

    /**
     * Get the Spring Security authority name for this role.
     *
     * @return Authority name with ROLE_ prefix (e.g., "ROLE_ADMIN")
     */
    public String toAuthority() {
        return "ROLE_" + this.name();
    }
}
