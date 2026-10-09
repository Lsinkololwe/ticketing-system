/**
 * Auth module exports (client-safe).
 *
 * Sessions are handled by the server-side BFF: import it from
 * `@pml.tickets/shared/auth/bff` (see auth/bff/MIGRATION.md). The browser never holds tokens.
 */
export { KEYCLOAK_CLIENTS, KEYCLOAK_ROLES, type KeycloakRole } from './keycloak-config';
