/**
 * Test Environment Provider
 *
 * Mock implementation of IEnvironmentProvider for testing.
 * Accepts all configuration via constructor - no process.env dependency.
 *
 * @example
 * ```typescript
 * const env = new TestEnvProvider({
 *   appUrl: 'http://test.local:3000',
 *   keycloakIssuer: 'http://keycloak.test/realms/test',
 * });
 *
 * const container = new AuthContainer(config, env, mockDb, mockRedis);
 * ```
 *
 * @module libs/shared/src/auth/better-auth/providers/__mocks__/TestEnvProvider
 */

import type {
  IEnvironmentProvider,
  PartialEnvConfig,
} from '../../interfaces/IEnvironmentProvider';

// =============================================================================
// DEFAULT TEST VALUES
// =============================================================================

/**
 * Required fields for testing (excludes optional Redis and computed fields)
 */
type RequiredTestEnvFields = Omit<
  IEnvironmentProvider,
  'redisEnabled' | 'keycloakJwksUrl' | 'keycloakDiscoveryUrl' | 'redisHost' | 'redisPort' | 'redisPassword'
>;

const TEST_DEFAULTS: Required<RequiredTestEnvFields> = {
  mongoUri: 'mongodb://localhost:27017',
  mongoDatabase: 'test_db',
  appUrl: 'http://localhost:3000',
  authSecret: 'test-secret-must-be-at-least-32-characters-long',
  keycloakClientId: 'test-client',
  keycloakClientSecret: 'test-secret',
  keycloakIssuer: 'http://localhost:8084/realms/test',
  isProduction: false,
};

// =============================================================================
// TEST ENVIRONMENT PROVIDER
// =============================================================================

/**
 * Test environment provider
 *
 * Provides configurable test values without environment dependencies.
 * Perfect for unit tests that need isolated, reproducible configuration.
 */
export class TestEnvProvider implements IEnvironmentProvider {
  readonly mongoUri: string;
  readonly mongoDatabase: string;
  readonly appUrl: string;
  readonly authSecret: string;
  readonly keycloakClientId: string;
  readonly keycloakClientSecret: string;
  readonly keycloakIssuer: string;
  readonly redisHost?: string;
  readonly redisPort?: number;
  readonly redisPassword?: string;
  readonly isProduction: boolean;
  readonly redisEnabled: boolean;
  readonly keycloakJwksUrl: string;
  readonly keycloakDiscoveryUrl: string;

  /**
   * Create test environment provider
   *
   * @param overrides - Values to override from defaults
   */
  constructor(overrides: PartialEnvConfig = {}) {
    this.mongoUri = overrides.mongoUri ?? TEST_DEFAULTS.mongoUri;
    this.mongoDatabase = overrides.mongoDatabase ?? TEST_DEFAULTS.mongoDatabase;
    this.appUrl = overrides.appUrl ?? TEST_DEFAULTS.appUrl;
    this.authSecret = overrides.authSecret ?? TEST_DEFAULTS.authSecret;
    this.keycloakClientId = overrides.keycloakClientId ?? TEST_DEFAULTS.keycloakClientId;
    this.keycloakClientSecret = overrides.keycloakClientSecret ?? TEST_DEFAULTS.keycloakClientSecret;
    this.keycloakIssuer = overrides.keycloakIssuer ?? TEST_DEFAULTS.keycloakIssuer;
    this.redisHost = overrides.redisHost;
    this.redisPort = overrides.redisPort;
    this.redisPassword = overrides.redisPassword;
    this.isProduction = overrides.isProduction ?? TEST_DEFAULTS.isProduction;

    // Computed properties
    this.redisEnabled = !!this.redisHost;
    this.keycloakJwksUrl = `${this.keycloakIssuer}/protocol/openid-connect/certs`;
    this.keycloakDiscoveryUrl = `${this.keycloakIssuer}/.well-known/openid-configuration`;
  }

  /**
   * Create provider with Redis enabled
   */
  static withRedis(overrides: PartialEnvConfig = {}): TestEnvProvider {
    return new TestEnvProvider({
      redisHost: 'localhost',
      redisPort: 6379,
      ...overrides,
    });
  }

  /**
   * Create provider simulating production
   */
  static production(overrides: PartialEnvConfig = {}): TestEnvProvider {
    return new TestEnvProvider({
      isProduction: true,
      appUrl: 'https://app.example.com',
      ...overrides,
    });
  }
}
