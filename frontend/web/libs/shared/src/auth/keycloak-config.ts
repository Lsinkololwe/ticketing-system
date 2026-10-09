/**
 * Keycloak realm constants shared by the apps (client ids and realm roles).
 * Connection settings live in the BFF config (`auth/bff/config.ts`).
 */

/** Known client IDs from the Keycloak realm configuration */
export const KEYCLOAK_CLIENTS = {
  /** Public ticketing web app */
  TICKETING_WEB: 'myticketzm-web',
  /** Admin portal */
  ADMIN: 'myticketzm-admin',
  /** Mobile app (public client with PKCE) */
  MOBILE: 'myticketzm-mobile',
  /** Organizer portal */
  ORGANIZER: 'myticketzm-organizer',
} as const;

/** User roles from the Keycloak realm configuration */
export const KEYCLOAK_ROLES = {
  CUSTOMER: 'CUSTOMER',
  ORGANIZER: 'ORGANIZER',
  SCANNER: 'SCANNER',
  FINANCE: 'FINANCE',
  FINANCE_LEAD: 'FINANCE_LEAD',
  ADMIN: 'ADMIN',
  SUPER_ADMIN: 'SUPER_ADMIN',
} as const;

export type KeycloakRole = (typeof KEYCLOAK_ROLES)[keyof typeof KEYCLOAK_ROLES];
