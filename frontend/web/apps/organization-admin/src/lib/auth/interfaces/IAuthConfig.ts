import 'server-only';

/**
 * Authentication configuration interface.
 *
 * This interface defines the required configuration values for the auth system,
 * including GraphQL endpoints, Keycloak settings, and application URLs.
 *
 * All properties are readonly to prevent accidental modification after initialization.
 *
 * @example
 * ```typescript
 * const config: IAuthConfig = {
 *   graphqlEndpoint: 'http://localhost:4000/graphql',
 *   keycloakIssuer: 'http://localhost:8084/realms/event-ticketing',
 *   keycloakClientId: 'event-ticketing-admin',
 *   appUrl: 'http://localhost:3030'
 * };
 * ```
 */
export interface IAuthConfig {
  /**
   * GraphQL API endpoint URL.
   *
   * This is the Apollo Router endpoint that federates all backend services.
   *
   * @example 'http://localhost:4000/graphql'
   */
  readonly graphqlEndpoint: string;

  /**
   * Keycloak issuer URL (realm endpoint).
   *
   * This is the base URL for the Keycloak realm used for authentication.
   *
   * @example 'http://localhost:8084/realms/event-ticketing'
   */
  readonly keycloakIssuer: string;

  /**
   * Keycloak client ID for this application.
   *
   * This identifies the application to Keycloak for OAuth2/OIDC flows.
   *
   * @example 'event-ticketing-admin'
   */
  readonly keycloakClientId: string;

  /**
   * Application base URL.
   *
   * This is used for constructing redirect URIs and logout URLs.
   *
   * @example 'http://localhost:3030'
   */
  readonly appUrl: string;

  /**
   * Keycloak client secret (optional, for confidential clients).
   *
   * Required for server-side token exchange operations.
   */
  readonly keycloakClientSecret?: string;

  /**
   * Token endpoint URL (optional override).
   *
   * If not provided, constructed from keycloakIssuer.
   *
   * @example 'http://localhost:8084/realms/event-ticketing/protocol/openid-connect/token'
   */
  readonly tokenEndpoint?: string;

  /**
   * Logout endpoint URL (optional override).
   *
   * If not provided, constructed from keycloakIssuer.
   *
   * @example 'http://localhost:8084/realms/event-ticketing/protocol/openid-connect/logout'
   */
  readonly logoutEndpoint?: string;

  /**
   * Session cookie name (optional override).
   *
   * @default 'better-auth.session_token'
   */
  readonly sessionCookieName?: string;

  /**
   * Whether to use secure cookies (HTTPS only).
   *
   * @default true in production, false in development
   */
  readonly secureCookies?: boolean;

  /**
   * Session timeout in seconds.
   *
   * @default 3600 (1 hour)
   */
  readonly sessionTimeout?: number;
}

/**
 * Factory function type for creating auth configuration.
 *
 * This allows for dynamic configuration based on environment variables.
 *
 * @example
 * ```typescript
 * const createConfig: AuthConfigFactory = () => ({
 *   graphqlEndpoint: process.env.GRAPHQL_ENDPOINT!,
 *   keycloakIssuer: process.env.KEYCLOAK_ISSUER!,
 *   keycloakClientId: process.env.KEYCLOAK_CLIENT_ID!,
 *   appUrl: process.env.APP_URL!,
 * });
 * ```
 */
export type AuthConfigFactory = () => IAuthConfig;

/**
 * Partial configuration for testing or overrides.
 */
export type PartialAuthConfig = Partial<IAuthConfig>;

/**
 * Required configuration keys that must be provided.
 */
export type RequiredAuthConfigKeys = Pick<
  IAuthConfig,
  'graphqlEndpoint' | 'keycloakIssuer' | 'keycloakClientId' | 'appUrl'
>;

/**
 * Validates that required configuration keys are present.
 *
 * @param config - Configuration object to validate
 * @throws {Error} When required keys are missing
 */
export function validateAuthConfig(config: PartialAuthConfig): asserts config is RequiredAuthConfigKeys {
  const required: (keyof RequiredAuthConfigKeys)[] = [
    'graphqlEndpoint',
    'keycloakIssuer',
    'keycloakClientId',
    'appUrl',
  ];

  for (const key of required) {
    if (!config[key]) {
      throw new Error(`Missing required auth configuration: ${key}`);
    }
  }
}
