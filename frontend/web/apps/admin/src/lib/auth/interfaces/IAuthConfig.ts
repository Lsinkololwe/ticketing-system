import 'server-only';

/**
 * Auth configuration for the admin app's service layer.
 *
 * @example
 * ```typescript
 * const config: IAuthConfig = {
 *   graphqlEndpoint: 'http://localhost:8080/graphql',
 *   keycloakIssuer: 'http://localhost:8084/realms/myticketzm-admin',
 *   keycloakClientId: 'myticketzm-admin',
 *   appUrl: 'http://localhost:3030',
 * };
 * ```
 */
export interface IAuthConfig {
  /** Apollo/GraphQL gateway endpoint. */
  readonly graphqlEndpoint: string;
  /** Keycloak realm issuer URL (the admin realm). */
  readonly keycloakIssuer: string;
  /** Keycloak client id for the admin app. */
  readonly keycloakClientId: string;
  /** Keycloak client secret (confidential client) — for server-side back-channel logout. */
  readonly keycloakClientSecret?: string;
  /** Admin app base URL (for redirects/logout). */
  readonly appUrl: string;
  /** Optional token endpoint override. */
  readonly tokenEndpoint?: string;
  /** Optional logout endpoint override. */
  readonly logoutEndpoint?: string;
}
