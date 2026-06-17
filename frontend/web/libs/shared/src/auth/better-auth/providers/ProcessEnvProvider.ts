/**
 * Process Environment Provider
 *
 * SERVER-ONLY: Reads configuration from process.env.
 *
 * Production implementation of IEnvironmentProvider.
 * Validates required environment variables on construction.
 *
 * @example
 * ```typescript
 * const env = new ProcessEnvProvider();
 * console.log(env.appUrl); // http://localhost:3030
 * console.log(env.keycloakIssuer); // http://localhost:8084/realms/event-ticketing
 * ```
 *
 * @module libs/shared/src/auth/better-auth/providers/ProcessEnvProvider
 */

import 'server-only';

import type { IEnvironmentProvider } from '../interfaces/IEnvironmentProvider';

// =============================================================================
// PROCESS ENVIRONMENT PROVIDER
// =============================================================================

/**
 * Production environment provider
 *
 * Reads all configuration from process.env.
 * Throws Error if required variables are missing.
 */
export class ProcessEnvProvider implements IEnvironmentProvider {
  // ===========================================================================
  // MongoDB Configuration
  // ===========================================================================

  readonly mongoUri: string;
  readonly mongoDatabase: string;

  // ===========================================================================
  // Application Configuration
  // ===========================================================================

  readonly appUrl: string;
  readonly authSecret: string;
  readonly isProduction: boolean;

  // ===========================================================================
  // Keycloak OIDC Configuration
  // ===========================================================================

  readonly keycloakClientId: string;
  readonly keycloakClientSecret: string;
  readonly keycloakIssuer: string;

  // ===========================================================================
  // Redis Configuration
  // ===========================================================================

  readonly redisHost?: string;
  readonly redisPort?: number;
  readonly redisPassword?: string;

  // ===========================================================================
  // Computed Properties
  // ===========================================================================

  readonly redisEnabled: boolean;
  readonly keycloakJwksUrl: string;
  readonly keycloakDiscoveryUrl: string;

  /**
   * Create environment provider from process.env
   *
   * @throws Error if required environment variables are missing
   */
  constructor() {
    const missing: string[] = [];

    // Helper to read and track missing required vars
    const getRequired = (key: string): string => {
      const value = process.env[key];
      if (!value) {
        missing.push(key);
        return '';
      }
      return value;
    };

    // Helper to read optional vars
    const getOptional = (key: string): string | undefined => {
      return process.env[key] || undefined;
    };

    // ==========================================================================
    // Read Required Variables
    // ==========================================================================

    this.mongoUri = getRequired('MONGODB_URI');
    this.mongoDatabase = getRequired('MONGODB_DATABASE');
    this.appUrl = getRequired('NEXT_PUBLIC_APP_URL');
    this.authSecret = getRequired('AUTH_SECRET');
    this.keycloakClientId = getRequired('AUTH_KEYCLOAK_ID');
    this.keycloakClientSecret = getRequired('AUTH_KEYCLOAK_SECRET');
    this.keycloakIssuer = getRequired('AUTH_KEYCLOAK_ISSUER');

    // ==========================================================================
    // Read Optional Variables
    // ==========================================================================

    this.redisHost = getOptional('REDIS_HOST');
    this.redisPort = process.env.REDIS_PORT
      ? parseInt(process.env.REDIS_PORT, 10)
      : undefined;
    this.redisPassword = getOptional('REDIS_PASSWORD');

    // ==========================================================================
    // Compute Derived Properties
    // ==========================================================================

    this.isProduction = process.env.NODE_ENV === 'production';
    this.redisEnabled = !!this.redisHost;
    this.keycloakJwksUrl = `${this.keycloakIssuer}/protocol/openid-connect/certs`;
    this.keycloakDiscoveryUrl = `${this.keycloakIssuer}/.well-known/openid-configuration`;

    // ==========================================================================
    // Validate
    // ==========================================================================

    if (missing.length > 0) {
      throw new Error(
        `[ProcessEnvProvider] Missing required environment variables: ${missing.join(', ')}`
      );
    }

    // Validate AUTH_SECRET length
    if (this.authSecret.length < 32) {
      throw new Error(
        '[ProcessEnvProvider] AUTH_SECRET must be at least 32 characters'
      );
    }
  }

  /**
   * Create a string representation for debugging
   *
   * Masks sensitive values.
   */
  toString(): string {
    return JSON.stringify(
      {
        mongoUri: this.maskConnectionString(this.mongoUri),
        mongoDatabase: this.mongoDatabase,
        appUrl: this.appUrl,
        authSecret: '********',
        keycloakClientId: this.keycloakClientId,
        keycloakClientSecret: '********',
        keycloakIssuer: this.keycloakIssuer,
        redisHost: this.redisHost,
        redisPort: this.redisPort,
        redisEnabled: this.redisEnabled,
        isProduction: this.isProduction,
      },
      null,
      2
    );
  }

  /**
   * Mask sensitive parts of connection string
   */
  private maskConnectionString(uri: string): string {
    try {
      const url = new URL(uri);
      if (url.password) {
        url.password = '********';
      }
      return url.toString();
    } catch {
      return '********';
    }
  }
}
