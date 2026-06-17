/**
 * Authentication Container (Dependency Injection Root)
 *
 * SERVER-ONLY: Composes all auth services using DI.
 *
 * Java-style class that assembles Better Auth with all dependencies.
 * Accepts providers via constructor for testability.
 *
 * ## Native Features Preserved
 *
 * - Better Auth: betterAuth(), genericOAuth(), nextCookies()
 * - MongoDB: mongodbAdapter() with native driver
 * - Redis: redisStorage() with ioredis
 * - Keycloak: OIDC discovery, PKCE, backchannel logout
 *
 * ## Production Usage
 *
 * ```typescript
 * const container = AuthContainer.createDefault({
 *   appId: 'organization-admin',
 *   cookiePrefix: 'pml_org',
 * });
 *
 * const auth = container.getAuth();
 * const session = await auth.api.getSession({ headers });
 * ```
 *
 * ## Test Usage
 *
 * ```typescript
 * const container = new AuthContainer(
 *   config,
 *   new TestEnvProvider({ appUrl: 'http://test.local' }),
 *   mockDatabaseProvider,
 *   new InMemoryRedisProvider(),
 * );
 * ```
 *
 * @module libs/shared/src/auth/better-auth/container/AuthContainer
 */

import 'server-only';

import { betterAuth } from 'better-auth';
import { genericOAuth } from 'better-auth/plugins';
import { nextCookies } from 'better-auth/next-js';
import { mongodbAdapter } from '@better-auth/mongo-adapter';
import { redisStorage } from '@better-auth/redis-storage';

import type { IEnvironmentProvider } from '../interfaces/IEnvironmentProvider';
import type { IDatabaseProvider } from '../interfaces/IDatabaseProvider';
import type { IRedisProvider } from '../interfaces/IRedisProvider';
import type { IJtiBlacklistService } from '../interfaces/IJtiBlacklistService';
import type { IBackchannelLogoutHandler } from '../interfaces/IBackchannelLogoutHandler';
import type { AppAuthConfig } from '../types';

import { JtiBlacklistService } from '../services/JtiBlacklistService';
import { BackchannelLogoutHandler } from '../services/BackchannelLogoutHandler';

// =============================================================================
// GLOBAL SINGLETON CACHE
// =============================================================================

declare global {
  // eslint-disable-next-line no-var
  var _authContainers: Map<string, AuthContainer> | undefined;
}

// =============================================================================
// SESSION CONFIGURATION
// =============================================================================

const SESSION_CONFIG = {
  expiresIn: 60 * 60, // 1 hour - idle/absolute timeout (OWASP recommended)
  updateAge: 5 * 60, // 5 minutes - refresh on activity
  storeSessionInDatabase: true,
  cookieCache: { enabled: false },
} as const;

// =============================================================================
// AUTH CONTAINER
// =============================================================================

/**
 * Authentication Container
 *
 * Dependency injection root for Better Auth configuration.
 * Composes all services and provides access to auth instance.
 */
export class AuthContainer {
  // Using BetterAuthInstance to avoid complex type inference issues
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private readonly auth: any;
  private readonly jtiBlacklist: IJtiBlacklistService | null;
  private readonly backchannelHandler: IBackchannelLogoutHandler | null;
  private readonly config: AppAuthConfig;

  /**
   * Create auth container with injected providers
   *
   * @param config - Application auth configuration
   * @param envProvider - Environment configuration provider
   * @param databaseProvider - MongoDB database provider
   * @param redisProvider - Redis provider
   */
  constructor(
    config: AppAuthConfig,
    private readonly envProvider: IEnvironmentProvider,
    private readonly databaseProvider: IDatabaseProvider,
    private readonly redisProvider: IRedisProvider
  ) {
    this.config = config;

    // Build secondary storage if Redis enabled
    const secondaryStorage = this.buildSecondaryStorage();

    // Build JTI blacklist if Redis enabled
    this.jtiBlacklist = this.buildJtiBlacklist();

    // Create Better Auth instance (preserves native API)
    this.auth = this.buildBetterAuth(secondaryStorage);

    // Build backchannel logout handler if blacklist available
    this.backchannelHandler = this.buildBackchannelHandler();
  }

  /**
   * Factory method for production use
   *
   * Wires up all production providers automatically.
   * Uses singleton pattern to cache instances by appId.
   *
   * @param config - Application auth configuration
   * @returns AuthContainer instance
   */
  static createDefault(config: AppAuthConfig): AuthContainer {
    // Initialize global cache
    if (!global._authContainers) {
      global._authContainers = new Map();
    }

    // Return cached instance if exists
    if (global._authContainers.has(config.appId)) {
      return global._authContainers.get(config.appId)!;
    }

    // Import production providers
    const { ProcessEnvProvider } = require('../providers/ProcessEnvProvider');
    const { MongoDatabaseProvider } = require('../providers/MongoDatabaseProvider');
    const { RedisProvider } = require('../providers/RedisProvider');

    // Create providers
    const env = new ProcessEnvProvider();
    const db = MongoDatabaseProvider.getInstance(env);
    const redis = RedisProvider.getInstance(env);

    // Create container
    const container = new AuthContainer(config, env, db, redis);

    // Cache for future requests
    global._authContainers.set(config.appId, container);

    return container;
  }

  /**
   * Reset all cached containers
   *
   * Use in tests to ensure clean state.
   */
  static resetAll(): void {
    global._authContainers?.clear();
  }

  // ===========================================================================
  // PUBLIC GETTERS
  // ===========================================================================

  /**
   * Get Better Auth instance
   *
   * Returns the native Better Auth instance with full type inference.
   */
  getAuth() {
    return this.auth;
  }

  /**
   * Get database instance
   */
  getDb() {
    return this.databaseProvider.getDb();
  }

  /**
   * Get database provider
   */
  getDatabaseProvider(): IDatabaseProvider {
    return this.databaseProvider;
  }

  /**
   * Get Redis client (may be null)
   */
  getRedis() {
    return this.redisProvider.getClient();
  }

  /**
   * Get Redis provider
   */
  getRedisProvider(): IRedisProvider {
    return this.redisProvider;
  }

  /**
   * Get JTI blacklist service (may be null)
   */
  getJtiBlacklist(): IJtiBlacklistService | null {
    return this.jtiBlacklist;
  }

  /**
   * Get backchannel logout handler (may be null)
   */
  getBackchannelHandler(): IBackchannelLogoutHandler | null {
    return this.backchannelHandler;
  }

  /**
   * Get environment config (subset safe to expose)
   */
  getEnvConfig() {
    return {
      KEYCLOAK_ISSUER: this.envProvider.keycloakIssuer,
      KEYCLOAK_CLIENT_ID: this.envProvider.keycloakClientId,
      APP_URL: this.envProvider.appUrl,
    };
  }

  /**
   * Get app configuration
   */
  getConfig(): AppAuthConfig {
    return this.config;
  }

  // ===========================================================================
  // PRIVATE: Build Better Auth (Native API)
  // ===========================================================================

  private buildBetterAuth(
    secondaryStorage?: ReturnType<typeof redisStorage>
  ) {
    const env = this.envProvider;
    const db = this.databaseProvider.getDb();

    // Native Better Auth configuration
    return betterAuth({
      baseURL: env.appUrl,
      secret: env.authSecret,

      // Native MongoDB adapter
      database: mongodbAdapter(db),

      // Native plugins
      plugins: [
        // Native genericOAuth with Keycloak OIDC
        genericOAuth({
          config: [
            {
              providerId: 'keycloak',
              clientId: env.keycloakClientId,
              clientSecret: env.keycloakClientSecret,
              // Native OIDC discovery
              discoveryUrl: env.keycloakDiscoveryUrl,
              // Native PKCE support
              pkce: true,
              scopes: ['openid', 'profile', 'email', 'phone'],
              // Map Keycloak profile to Better Auth user
              mapProfileToUser: (profile) => ({
                id: profile.sub as string,
                email: (profile.email as string) || '',
                name:
                  (profile.name as string) ||
                  (profile.preferred_username as string) ||
                  '',
                emailVerified: (profile.email_verified as boolean) || false,
                image: (profile.picture as string) || null,
                phone: (profile.phone_number as string) || null,
                phoneVerified: (profile.phone_number_verified as boolean) || false,
              }),
            },
          ],
        }),
        // Native Next.js cookie handling
        nextCookies(),
      ],

      // Native Redis secondary storage
      ...(secondaryStorage && { secondaryStorage }),

      // Session configuration
      session: SESSION_CONFIG,

      // Cookie configuration
      advanced: {
        useSecureCookies: env.isProduction,
        cookiePrefix: this.config.cookiePrefix,
        defaultCookieAttributes: {
          httpOnly: true,
          secure: env.isProduction,
          sameSite: 'lax' as const,
          path: '/',
        },
      },

      // Account linking
      account: {
        accountLinking: {
          enabled: true,
          trustedProviders: ['keycloak'],
        },
      },

      // Custom user fields
      user: {
        additionalFields: {
          roles: { type: 'string[]', required: false },
          keycloakId: { type: 'string', required: false },
          phone: { type: 'string', required: false },
          phoneVerified: { type: 'boolean', required: false },
        },
      },

      // Trusted origins
      trustedOrigins: [
        env.appUrl,
        ...(env.isProduction
          ? []
          : ['http://localhost:3030', 'http://localhost:3031', 'http://localhost:3000']),
      ],
    });
  }

  private buildSecondaryStorage(): ReturnType<typeof redisStorage> | undefined {
    const redis = this.redisProvider.getClient();
    if (!redis) return undefined;

    // Native Better Auth Redis storage
    return redisStorage({
      client: redis,
      keyPrefix: this.config.redisKeyPrefix || `${this.config.appId}:`,
    });
  }

  private buildJtiBlacklist(): IJtiBlacklistService | null {
    const redis = this.redisProvider.getClient();
    if (!redis) return null;

    return new JtiBlacklistService(
      redis,
      this.config.redisKeyPrefix || `${this.config.appId}:`,
      86400 // 24 hour default TTL
    );
  }

  private buildBackchannelHandler(): IBackchannelLogoutHandler | null {
    if (!this.jtiBlacklist) return null;

    const db = this.databaseProvider.getDb();
    const env = this.envProvider;
    const appId = this.config.appId;
    const auth = this.auth;

    return new BackchannelLogoutHandler({
      keycloakJwksUrl: env.keycloakJwksUrl,
      keycloakIssuer: env.keycloakIssuer,
      clientId: env.keycloakClientId,
      jtiBlacklist: this.jtiBlacklist,
      // Use Better Auth's native session revocation API
      // Note: revokeSessionBySid is intentionally omitted because:
      // - Keycloak's `sid` is not stored in Better Auth sessions
      // - Better Auth has no native mapping between Keycloak sid and BA sessions
      // - Revoking ALL user sessions is the safe, reliable approach
      revokeUserSessions: async (keycloakUserId: string) => {
        const usersCollection = db.collection('users');

        // Find user by Keycloak ID or Better Auth ID
        const user = await usersCollection.findOne({
          $or: [{ keycloakId: keycloakUserId }, { id: keycloakUserId }],
        });

        if (user) {
          // Native Better Auth: Revoke all sessions for user
          await auth.api.revokeSessions({
            body: { userId: user.id },
          });
          console.log(
            `[Auth:${appId}] Backchannel logout: Revoked all sessions for user ${user.id} using Better Auth native API`
          );
        }
      },
    });
  }
}
