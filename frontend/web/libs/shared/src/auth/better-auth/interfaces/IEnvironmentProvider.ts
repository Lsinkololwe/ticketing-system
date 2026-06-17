/**
 * Environment Configuration Provider Interface
 *
 * Abstracts environment variable access for testability.
 * Production implementations read from process.env,
 * test implementations accept mock values via constructor.
 *
 * @example
 * ```typescript
 * // Production
 * const env = new ProcessEnvProvider();
 *
 * // Testing
 * const env = new TestEnvProvider({
 *   appUrl: 'http://test.local:3000',
 *   keycloakIssuer: 'http://keycloak.test/realms/test',
 * });
 * ```
 *
 * @module libs/shared/src/auth/better-auth/interfaces/IEnvironmentProvider
 */

// =============================================================================
// ENVIRONMENT CONFIGURATION INTERFACE
// =============================================================================

/**
 * Environment configuration provider contract
 *
 * Implementations must provide all required configuration values
 * for Better Auth, MongoDB, Redis, and Keycloak integration.
 */
export interface IEnvironmentProvider {
  // ===========================================================================
  // MongoDB Configuration
  // ===========================================================================

  /** MongoDB connection URI (e.g., mongodb://localhost:27017) */
  readonly mongoUri: string;

  /** MongoDB database name */
  readonly mongoDatabase: string;

  // ===========================================================================
  // Application Configuration
  // ===========================================================================

  /** Application base URL (e.g., http://localhost:3030) */
  readonly appUrl: string;

  /** Better Auth secret for signing sessions/tokens (min 32 chars) */
  readonly authSecret: string;

  /** Whether running in production mode */
  readonly isProduction: boolean;

  // ===========================================================================
  // Keycloak OIDC Configuration
  // ===========================================================================

  /** Keycloak OAuth client ID */
  readonly keycloakClientId: string;

  /** Keycloak OAuth client secret */
  readonly keycloakClientSecret: string;

  /** Keycloak realm issuer URL (e.g., http://localhost:8084/realms/event-ticketing) */
  readonly keycloakIssuer: string;

  // ===========================================================================
  // Redis Configuration (Optional)
  // ===========================================================================

  /** Redis host (optional, disables Redis features if not provided) */
  readonly redisHost?: string;

  /** Redis port (defaults to 6379) */
  readonly redisPort?: number;

  /** Redis password (optional) */
  readonly redisPassword?: string;

  // ===========================================================================
  // Computed Properties
  // ===========================================================================

  /** Whether Redis is configured and should be enabled */
  readonly redisEnabled: boolean;

  /** Keycloak JWKS URL for token verification */
  readonly keycloakJwksUrl: string;

  /** Keycloak OIDC discovery URL */
  readonly keycloakDiscoveryUrl: string;
}

// =============================================================================
// PARTIAL CONFIG FOR TESTING
// =============================================================================

/**
 * Partial environment config for test overrides
 *
 * All fields optional - test provider fills defaults.
 */
export type PartialEnvConfig = Partial<Omit<IEnvironmentProvider, 'redisEnabled' | 'keycloakJwksUrl' | 'keycloakDiscoveryUrl'>>;
