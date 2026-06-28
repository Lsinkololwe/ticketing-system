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
import { parsePhoneNumberFromString } from 'libphonenumber-js';

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
              // Request every scope whose claims we read from the token:
              // profile (preferred_username/given_name/family_name), email,
              // phone (phone_number/phone_number_verified) and roles
              // (realm_access.roles). `roles` is a Keycloak default client scope,
              // but we request it explicitly so the dependency is self-evident.
              scopes: ['openid', 'profile', 'email', 'phone', 'roles'],
              // Map Keycloak profile to Better Auth user.
              //
              // Better Auth owns ONLY its core fields here (email, name,
              // emailVerified). The Keycloak `sub` is carried as `keycloakId`
              // and promoted to the record `id` (= Mongo `_id`) by the
              // `databaseHooks.user.create.before` hook below, then stripped so
              // it is not persisted (the doc is already keyed by `_id` == sub).
              //
              // Native Better Auth claim mapping. `mapProfileToUser` is Better
              // Auth's native hook for turning OIDC token claims into the user
              // record — every value below comes directly from the Keycloak token
              // (the realm's protocol mappers emit all of these into the
              // userinfo/ID token: preferred_username, given_name, family_name,
              // email[_verified], phone_number[_verified], realm_access.roles).
              // Nothing is fabricated here.
              //
              // NOTE: `mapProfileToUser` cannot set the primary key — Better Auth
              // ignores any returned `id`. The Keycloak `sub` is carried as
              // `keycloakId` and promoted to the record `id` (= Mongo `_id`) by the
              // native `databaseHooks.user.create.before` hook below. The
              // keycloak-extensions sync remains the authoritative owner of these
              // business fields and re-asserts them on later Keycloak events
              // (upsert by `_id` == sub).
              mapProfileToUser: (profile) => {
                const emailVerified =
                  (profile.email_verified as boolean) || false;
                const phoneVerified =
                  (profile.phone_number_verified as boolean) || false;

                // The backend `users` $jsonSchema validator requires E.164 for
                // phoneNumber (^\+[1-9]\d{1,14}$). Keycloak may carry a local
                // Zambian format (e.g. "0969944454"), which is the SAME value the
                // user provided — just unformatted — so normalising it is not
                // fabrication. We use libphonenumber-js (the same algorithm as the
                // backend PhoneNumbers util and the PhoneNumberInput component) so
                // every phone write path agrees. Anything that cannot be parsed
                // into a valid number is written as `null` (phoneNumber is optional
                // in the schema) so a malformed value never trips the validator;
                // the keycloak-extensions sync remains the authoritative owner and
                // re-asserts the normalised number on later Keycloak events.
                const parsedPhone = (() => {
                  const raw = profile.phone_number;
                  if (typeof raw !== 'string' || raw.trim() === '') return null;
                  try {
                    const trimmed = raw.trim();
                    const p = trimmed.startsWith('+')
                      ? parsePhoneNumberFromString(trimmed)
                      : parsePhoneNumberFromString(trimmed, 'ZM');
                    return p && p.isValid() ? p : null;
                  } catch {
                    return null;
                  }
                })();
                const phoneNumber = parsedPhone ? parsedPhone.number : null;
                const phoneCountry = parsedPhone?.country ?? null;

                // realm_access.roles is Keycloak's source of truth for roles. Keep
                // only the application roles the backend `users` schema enum
                // accepts (Keycloak also emits infra roles like offline_access).
                const ALLOWED_ROLES = [
                  'CUSTOMER',
                  'ORGANIZER',
                  'ADMIN',
                  'SUPER_ADMIN',
                  'SCANNER',
                  'FINANCE',
                ];
                const roles = Array.from(
                  new Set(
                    ((profile.realm_access as { roles?: string[] })?.roles ?? [])
                      .map((r) => String(r).toUpperCase())
                      .filter((r) => ALLOWED_ROLES.includes(r))
                  )
                );

                return {
                  // Better Auth core fields (from token).
                  email: (profile.email as string) || '',
                  name: (profile.name as string) || '',
                  emailVerified,
                  // Keycloak sub → promoted to `_id` in the create hook, then
                  // stripped (NOT persisted — the doc is keyed by `_id` == sub).
                  keycloakId: profile.sub as string,
                  // Business fields (all from token claims).
                  username: profile.preferred_username as string,
                  firstName: profile.given_name as string,
                  lastName: profile.family_name as string,
                  phoneNumber,
                  phoneCountry,
                  phoneVerified,
                  roles,
                  // NOTE: `accountStatus` is intentionally NOT set here. It is not
                  // a token claim, and the keycloak-extensions sync owns it
                  // exclusively (the backend schema no longer requires it at
                  // insert).
                };
              },
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

      // Native solution for "MongoDB user id must equal the Keycloak id".
      //
      // Better Auth's `mapProfileToUser` cannot set the primary key, so we use a
      // create.before database hook to promote the Keycloak `sub` (carried as
      // `keycloakId`) to the record `id`. `createWithHooks` calls the adapter
      // with `forceAllowId: true`, so a provided string `id` is honoured as-is
      // and becomes the Mongo `_id`. This makes the Better Auth user document and
      // the backend `users` document share one `_id` == Keycloak sub.
      databaseHooks: {
        user: {
          create: {
            before: async (user) => {
              // The ONLY thing this native hook does is promote the Keycloak sub
              // (carried as `keycloakId` from the token) to the record `id`
              // (= Mongo `_id`), which `mapProfileToUser` cannot do. All other
              // field values come straight from the token via mapProfileToUser —
              // nothing is fabricated here. `createWithHooks` calls the adapter
              // with `forceAllowId: true`, so the provided string id is honoured
              // and becomes the `_id` shared with the backend `users` document.
              const { keycloakId, ...rest } = user as {
                keycloakId?: string;
              } & Record<string, unknown>;
              if (!keycloakId) {
                // No Keycloak sub (should not happen for OIDC sign-ups);
                // fall back to Better Auth's default id generation.
                return { data: user };
              }
              // Set `id` from the sub and DROP `keycloakId` from the persisted
              // data — the document is already keyed by `_id` == sub, so storing
              // the sub again under `keycloakId` would be redundant.
              return { data: { ...rest, id: keycloakId } };
            },
          },
        },
      },

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

      // Single shared user collection.
      //
      // `modelName: 'users'` points Better Auth at the SAME collection the
      // backend uses (`User.java` → @Document(collection = "users")), so there is
      // one `users` table referenced by both Better Auth and business logic.
      //
      // Business fields are declared here so they are part of Better Auth's schema
      // and returned in the typed session. They are marked `input: false` because
      // they are never accepted from client sign-up input — Better Auth seeds them
      // at create from the Keycloak TOKEN claims (mapProfileToUser + the create
      // hook above), and the keycloak-extensions sync is the authoritative owner
      // that overwrites them on subsequent Keycloak events. Seeding them here is
      // required so Better Auth's first insert satisfies the backend `users`
      // OWASP $jsonSchema validator.
      user: {
        modelName: 'users',
        additionalFields: {
          username: { type: 'string', required: false, input: false },
          firstName: { type: 'string', required: false, input: false },
          lastName: { type: 'string', required: false, input: false },
          phoneNumber: { type: 'string', required: false, input: false },
          phoneCountry: { type: 'string', required: false, input: false },
          phoneVerified: { type: 'boolean', required: false, input: false },
          roles: { type: 'string[]', required: false, input: false },
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
        const usersCollection = db.collection<{ _id: string }>('users');

        // The user document is keyed by `_id` == Keycloak sub.
        const user = await usersCollection.findOne({ _id: keycloakUserId });

        if (user) {
          // `_id` == Keycloak sub == the Better Auth user id used by revokeSessions.
          const userId = String(user._id ?? keycloakUserId);
          // Native Better Auth: Revoke all sessions for user
          await auth.api.revokeSessions({
            body: { userId },
          });
          console.log(
            `[Auth:${appId}] Backchannel logout: Revoked all sessions for user ${userId} using Better Auth native API`
          );
        }
      },
    });
  }
}
