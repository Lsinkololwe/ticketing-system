/**
 * Shared Better Auth Server Configuration
 *
 * This module provides a factory function to create Better Auth instances
 * with consistent configuration across all applications.
 *
 * ## Architecture Overview
 *
 * ```
 * Application (admin, organization-admin, ticketing)
 *     ↓
 * getBetterAuth({ appId, cookiePrefix, ... })
 *     ↓
 * createBetterAuth() ← validateEnv(), getMongoClientPromise(), getRedisClient()
 *     ↓
 * betterAuth({ database, plugins, session, databaseHooks, ... })
 *     ↓
 * BetterAuthInstance + JtiBlacklistService
 * ```
 *
 * ## Storage Architecture
 *
 * - **Primary Storage**: MongoDB (via official @better-auth/mongo-adapter)
 *   - Stores: users, sessions, accounts
 *   - Source of truth for all auth data
 *
 * - **Secondary Storage**: Redis (via official @better-auth/redis-storage)
 *   - Caches: active sessions
 *   - Enables: fast session lookups, rate limiting, JTI blacklist
 *   - Optional but recommended for production
 *
 * ## Session Management
 *
 * Sessions are managed with OWASP-compliant settings:
 * - 8-hour absolute timeout
 * - 5-minute update interval (extends on activity)
 * - Cookie cache with HMAC signatures (5-minute cache)
 *
 * ## JTI Blacklist (Backchannel Logout Support)
 *
 * When Redis is enabled, JTI blacklisting prevents session creation
 * with tokens that have been revoked via Keycloak backchannel logout:
 * - Blacklisted JTIs are stored in Redis with TTL matching token expiry
 * - Session creation is blocked if the token's JTI is blacklisted
 *
 * ## Used By
 *
 * - `apps/admin/src/lib/auth/index.ts` - Admin app auth
 * - `apps/organization-admin/src/lib/auth/index.ts` - Organizer app auth
 *
 * @see https://better-auth.com/docs/reference/options
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 * @module libs/shared/src/auth/better-auth/config
 */

import { betterAuth } from 'better-auth';
import { genericOAuth } from 'better-auth/plugins';
import { mongodbAdapter } from '@better-auth/mongo-adapter';
import { redisStorage } from '@better-auth/redis-storage';
import { MongoClient, Db } from 'mongodb';
import { Redis } from 'ioredis';

import type {
  AppAuthConfig,
  EnvValidationResult,
} from './types';
import { createJtiBlacklist, type JtiBlacklistService } from './jti-blacklist';
import { createBackchannelLogoutHandler } from './backchannel-logout';

// =============================================================================
// ENVIRONMENT VALIDATION
// =============================================================================

/**
 * Validate required environment variables for Better Auth
 *
 * Checks that all required variables are set before creating auth instance.
 * Returns either validated env object or list of missing variables.
 *
 * @returns Validation result with env values or errors
 *
 * @used-by createBetterAuth() - Called during initialization
 *
 * @example
 * ```typescript
 * const result = validateEnv();
 * if (!result.valid) {
 *   console.error('Missing:', result.errors);
 * }
 * ```
 */
export function validateEnv(): EnvValidationResult {
  const errors: string[] = [];

  // Required variables
  const MONGODB_URI = process.env.MONGODB_URI;
  const MONGODB_DATABASE = process.env.MONGODB_DATABASE;
  const APP_URL = process.env.NEXT_PUBLIC_APP_URL;
  const AUTH_SECRET = process.env.AUTH_SECRET;
  const KEYCLOAK_CLIENT_ID = process.env.AUTH_KEYCLOAK_ID;
  const KEYCLOAK_CLIENT_SECRET = process.env.AUTH_KEYCLOAK_SECRET;
  const KEYCLOAK_ISSUER = process.env.AUTH_KEYCLOAK_ISSUER;

  // Validate required variables
  if (!MONGODB_URI) errors.push('MONGODB_URI is required');
  if (!MONGODB_DATABASE) errors.push('MONGODB_DATABASE is required');
  if (!APP_URL) errors.push('NEXT_PUBLIC_APP_URL is required');
  if (!AUTH_SECRET) errors.push('AUTH_SECRET is required');
  if (!KEYCLOAK_CLIENT_ID) errors.push('AUTH_KEYCLOAK_ID is required');
  if (!KEYCLOAK_CLIENT_SECRET) errors.push('AUTH_KEYCLOAK_SECRET is required');
  if (!KEYCLOAK_ISSUER) errors.push('AUTH_KEYCLOAK_ISSUER is required');

  if (errors.length > 0) {
    return { valid: false, errors };
  }

  return {
    valid: true,
    env: {
      MONGODB_URI: MONGODB_URI!,
      MONGODB_DATABASE: MONGODB_DATABASE!,
      APP_URL: APP_URL!,
      AUTH_SECRET: AUTH_SECRET!,
      KEYCLOAK_CLIENT_ID: KEYCLOAK_CLIENT_ID!,
      KEYCLOAK_CLIENT_SECRET: KEYCLOAK_CLIENT_SECRET!,
      KEYCLOAK_ISSUER: KEYCLOAK_ISSUER!,
      REDIS_HOST: process.env.REDIS_HOST,
      REDIS_PORT: process.env.REDIS_PORT,
      REDIS_PASSWORD: process.env.REDIS_PASSWORD,
    },
  };
}

// =============================================================================
// MONGODB CLIENT (Singleton with Hot Reload Support)
// =============================================================================

/**
 * Global MongoDB client reference (survives hot reload in development)
 */
declare global {
  // eslint-disable-next-line no-var
  var _betterAuthMongoClient: MongoClient | undefined;
  // eslint-disable-next-line no-var
  var _betterAuthMongoClientPromise: Promise<MongoClient> | undefined;
}

/**
 * Get MongoDB client promise with connection pooling
 *
 * In development, the client is stored globally to survive hot reloads.
 * In production, a new client is created (managed by container lifecycle).
 *
 * @param uri - MongoDB connection URI
 * @returns Promise resolving to connected MongoClient
 *
 * @used-by createBetterAuth() - For database adapter
 */
function getMongoClientPromise(uri: string): Promise<MongoClient> {
  if (process.env.NODE_ENV === 'development') {
    // Development: Reuse client across hot reloads
    if (!global._betterAuthMongoClientPromise) {
      global._betterAuthMongoClient = new MongoClient(uri, {
        maxPoolSize: 10,
        minPoolSize: 2,
        maxIdleTimeMS: 60000,
        connectTimeoutMS: 10000,
        serverSelectionTimeoutMS: 30000,
        retryWrites: true,
        retryReads: true,
      });
      global._betterAuthMongoClientPromise = global._betterAuthMongoClient.connect();
    }
    return global._betterAuthMongoClientPromise;
  }

  // Production: Create new client (managed by container lifecycle)
  const client = new MongoClient(uri, {
    maxPoolSize: 50,
    minPoolSize: 10,
    connectTimeoutMS: 10000,
    retryWrites: true,
    retryReads: true,
  });
  return client.connect();
}

// =============================================================================
// REDIS CLIENT (Singleton)
// =============================================================================

/**
 * Global Redis client reference (survives hot reload in development)
 */
declare global {
  // eslint-disable-next-line no-var
  var _betterAuthRedisClient: Redis | undefined;
}

/**
 * Get Redis client for secondary storage
 *
 * Provides fast session caching and rate limiting.
 * In development, client is stored globally to survive hot reloads.
 *
 * @param host - Redis server host
 * @param port - Redis server port
 * @param password - Optional Redis password
 * @returns Connected Redis client
 *
 * @used-by createBetterAuth() - For secondary storage
 */
function getRedisClient(host: string, port: number, password?: string): Redis {
  if (process.env.NODE_ENV === 'development' && global._betterAuthRedisClient) {
    return global._betterAuthRedisClient;
  }

  const client = new Redis({
    host,
    port,
    password,
    maxRetriesPerRequest: 3,
    retryStrategy: (times) => {
      if (times > 5) return null;
      return Math.min(times * 200, 5000);
    },
    connectTimeout: 10000,
    commandTimeout: 5000,
    lazyConnect: false,
    enableOfflineQueue: true,
    enableReadyCheck: true,
  });

  client.on('error', (err) => {
    console.error('[Redis] Connection error:', err.message);
  });

  client.on('connect', () => {
    console.log('[Redis] Connected for Better Auth session storage');
  });

  if (process.env.NODE_ENV === 'development') {
    global._betterAuthRedisClient = client;
  }

  return client;
}

// =============================================================================
// SESSION CONFIGURATION (OWASP Compliant)
// =============================================================================

/**
 * Production-grade session configuration following OWASP guidelines
 *
 * @see https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
 */
const SESSION_CONFIG = {
  /**
   * Absolute session timeout: 8 hours
   * OWASP recommended maximum for high-risk applications
   */
  expiresIn: 8 * 60 * 60, // 8 hours in seconds

  /**
   * Update session timestamp every 5 minutes
   * Extends session on activity, provides sliding window behavior
   */
  updateAge: 5 * 60, // 5 minutes

  /**
   * Store sessions in database
   * MongoDB is source of truth for all session data
   */
  storeSessionInDatabase: true,

  /**
   * Cookie cache configuration
   * Uses HMAC signature for integrity - secure by design
   * Reduces database lookups for session validation
   */
  cookieCache: {
    enabled: true,
    maxAge: 5 * 60, // 5 minutes
  },
};

// =============================================================================
// BETTER AUTH FACTORY
// =============================================================================

/**
 * Result of creating a Better Auth instance
 *
 * Includes both the auth instance and supporting services like JTI blacklist.
 */
export interface BetterAuthResult {
  /** The Better Auth instance */
  auth: ReturnType<typeof betterAuth>;
  /** JTI blacklist service (only available if Redis is enabled) */
  jtiBlacklist: JtiBlacklistService | null;
  /** Backchannel logout handler (only available if Redis is enabled) */
  handleBackchannelLogout: ((logoutToken: string) => Promise<{ success: boolean; error?: string }>) | null;
  /** MongoDB database instance */
  mongoDb: Db;
  /** Redis client (only available if Redis is enabled) */
  redis: Redis | null;
  /** Environment configuration */
  env: {
    KEYCLOAK_ISSUER: string;
    KEYCLOAK_CLIENT_ID: string;
    APP_URL: string;
  };
}

/**
 * Create a Better Auth instance with the given configuration
 *
 * This is the main factory function that:
 * 1. Validates environment variables
 * 2. Connects to MongoDB
 * 3. Optionally connects to Redis
 * 4. Configures Keycloak OAuth
 * 5. Sets up session management
 * 6. Creates JTI blacklist service (if Redis enabled)
 * 7. Creates backchannel logout handler (if Redis enabled)
 *
 * @param config - Application-specific configuration
 * @returns Promise resolving to the Better Auth result with auth instance and services
 *
 * @used-by getBetterAuth() - Singleton wrapper
 *
 * @example
 * ```typescript
 * const { auth, jtiBlacklist, handleBackchannelLogout } = await createBetterAuth({
 *   appId: 'admin',
 *   cookiePrefix: 'pml_admin',
 *   redisKeyPrefix: 'pml-admin:',
 *   enableRedis: true,
 * });
 * ```
 */
export async function createBetterAuth(config: AppAuthConfig): Promise<BetterAuthResult> {
  const { appId, cookiePrefix, redisKeyPrefix, enableRedis = true, debug = false } = config;

  // Step 1: Validate environment
  const envResult = validateEnv();
  if (!envResult.valid) {
    const errorMessage = `[Auth:${appId}] Missing required environment variables:\n  - ${envResult.errors.join('\n  - ')}`;
    console.error(errorMessage);
    throw new Error(errorMessage);
  }

  const env = envResult.env;
  const isProduction = process.env.NODE_ENV === 'production';

  // Step 2: Connect to MongoDB
  const mongoClient = await getMongoClientPromise(env.MONGODB_URI);
  const mongoDb = mongoClient.db(env.MONGODB_DATABASE);

  if (debug) {
    console.log(`[Auth:${appId}] MongoDB connected`);
  }

  // Step 3: Setup Redis secondary storage and JTI blacklist (optional but recommended)
  let secondaryStorage: ReturnType<typeof redisStorage> | undefined;
  let jtiBlacklist: JtiBlacklistService | null = null;
  let redis: Redis | null = null;

  if (enableRedis && env.REDIS_HOST) {
    try {
      redis = getRedisClient(
        env.REDIS_HOST,
        parseInt(env.REDIS_PORT || '6379', 10),
        env.REDIS_PASSWORD
      );

      secondaryStorage = redisStorage({
        client: redis,
        keyPrefix: redisKeyPrefix,
      });

      // Create JTI blacklist service
      jtiBlacklist = createJtiBlacklist({
        redis,
        keyPrefix: redisKeyPrefix,
        defaultTtlSeconds: 86400, // 24 hours
      });

      if (debug) {
        console.log(`[Auth:${appId}] Redis secondary storage and JTI blacklist enabled`);
      }
    } catch (error) {
      console.warn(
        `[Auth:${appId}] Redis connection failed, continuing without secondary storage:`,
        error instanceof Error ? error.message : String(error)
      );
    }
  }

  // Step 4: Create Better Auth instance
  const auth = betterAuth({
    // Application base URL (used for callbacks)
    baseURL: env.APP_URL,

    // Secret for signing tokens (min 32 chars)
    secret: env.AUTH_SECRET,

    // ==========================================================================
    // OFFICIAL MONGODB ADAPTER
    // ==========================================================================
    database: mongodbAdapter(mongoDb),

    // ==========================================================================
    // KEYCLOAK OIDC PROVIDER
    // ==========================================================================
    plugins: [
      genericOAuth({
        config: [
          {
            providerId: 'keycloak',
            clientId: env.KEYCLOAK_CLIENT_ID,
            clientSecret: env.KEYCLOAK_CLIENT_SECRET,
            discoveryUrl: `${env.KEYCLOAK_ISSUER}/.well-known/openid-configuration`,
            pkce: true,
            scopes: ['openid', 'profile', 'email'],
            mapProfileToUser: (profile) => ({
              id: profile.sub as string,
              email: (profile.email as string) || '',
              name: (profile.name as string) || (profile.preferred_username as string) || '',
              emailVerified: (profile.email_verified as boolean) || false,
              image: (profile.picture as string) || null,
            }),
          },
        ],
      }),
    ],

    // ==========================================================================
    // OFFICIAL REDIS SECONDARY STORAGE (Optional)
    // ==========================================================================
    ...(secondaryStorage && { secondaryStorage }),

    // ==========================================================================
    // SESSION CONFIGURATION (OWASP Compliant)
    // ==========================================================================
    session: SESSION_CONFIG,

    // ==========================================================================
    // SECURITY CONFIGURATION
    // ==========================================================================
    advanced: {
      // Track IP addresses for session binding
      ipAddress: {
        ipAddressHeaders: ['x-forwarded-for', 'x-real-ip', 'cf-connecting-ip'],
        disableIpTracking: false,
      },

      // Use secure cookies in production
      useSecureCookies: isProduction,

      // CSRF and origin protection enabled
      disableCSRFCheck: false,
      disableOriginCheck: false,

      // Cookie attributes
      defaultCookieAttributes: {
        httpOnly: true,
        secure: isProduction,
        sameSite: 'lax' as const,
        path: '/',
      },

      // Application-specific cookie prefix
      cookiePrefix,
    },

    // ==========================================================================
    // ACCOUNT SETTINGS
    // ==========================================================================
    account: {
      accountLinking: {
        enabled: true,
        trustedProviders: ['keycloak'],
      },
    },

    // ==========================================================================
    // USER CONFIGURATION
    // ==========================================================================
    user: {
      additionalFields: {
        roles: {
          type: 'string[]',
          required: false,
        },
        keycloakId: {
          type: 'string',
          required: false,
        },
      },
    },

    // ==========================================================================
    // TRUSTED ORIGINS
    // ==========================================================================
    trustedOrigins: [
      env.APP_URL,
      ...(isProduction ? [] : ['http://localhost:3030', 'http://localhost:3031', 'http://localhost:3000']),
    ],

    // ==========================================================================
    // DATABASE HOOKS (JTI Blacklist Integration)
    // ==========================================================================
    databaseHooks: {
      session: {
        create: {
          /**
           * Before session creation hook
           *
           * Stores Keycloak session metadata in the Better Auth session
           * for later reference during backchannel logout.
           *
           * Note: JTI blacklist checking is done at the OAuth callback level,
           * not here, because we need access to the original tokens.
           *
           * @used-by Better Auth session creation
           */
          before: async (session) => {
            // Add creation timestamp for audit purposes
            return {
              data: {
                ...session,
                // Store creation time in ISO format for debugging
                createdAt: new Date().toISOString(),
              },
            };
          },
          after: async (session) => {
            if (debug) {
              console.log(`[Auth:${appId}] Session created for user: ${session.userId}`);
            }
          },
        },
        delete: {
          after: async (session) => {
            if (debug) {
              console.log(`[Auth:${appId}] Session deleted: ${session.token?.substring(0, 8)}...`);
            }
          },
        },
      },
    },
  });

  // Step 5: Create backchannel logout handler (if Redis/JTI blacklist enabled)
  let handleBackchannelLogout: BetterAuthResult['handleBackchannelLogout'] = null;

  if (jtiBlacklist) {
    handleBackchannelLogout = createBackchannelLogoutHandler({
      keycloakJwksUrl: `${env.KEYCLOAK_ISSUER}/protocol/openid-connect/certs`,
      keycloakIssuer: env.KEYCLOAK_ISSUER,
      clientId: env.KEYCLOAK_CLIENT_ID,
      jtiBlacklist,
      /**
       * Revoke all Better Auth sessions for a user
       *
       * Called during backchannel logout to invalidate all sessions
       * for the user being logged out.
       *
       * @param keycloakUserId - The Keycloak user ID (sub claim)
       */
      revokeUserSessions: async (keycloakUserId: string) => {
        try {
          // Find user by Keycloak ID and revoke their sessions
          const sessionsCollection = mongoDb.collection('sessions');
          const usersCollection = mongoDb.collection('users');

          // First, find the Better Auth user by Keycloak ID
          // The user might be stored with keycloakId field or the id might match
          const user = await usersCollection.findOne({
            $or: [
              { keycloakId: keycloakUserId },
              { id: keycloakUserId },
            ],
          });

          if (user) {
            // Delete all sessions for this user
            const result = await sessionsCollection.deleteMany({
              userId: user.id,
            });

            console.log(`[Auth:${appId}] Backchannel logout: Revoked ${result.deletedCount} sessions for user ${keycloakUserId}`);

            // Also clear from Redis if available
            if (redis) {
              const keys = await redis.keys(`${redisKeyPrefix}session:*`);
              for (const key of keys) {
                const sessionData = await redis.get(key);
                if (sessionData) {
                  try {
                    const parsed = JSON.parse(sessionData);
                    if (parsed.userId === user.id) {
                      await redis.del(key);
                    }
                  } catch {
                    // Skip invalid session data
                  }
                }
              }
            }
          } else {
            console.warn(`[Auth:${appId}] Backchannel logout: User not found for Keycloak ID ${keycloakUserId}`);
          }
        } catch (error) {
          console.error(`[Auth:${appId}] Backchannel logout: Failed to revoke sessions:`, error);
          throw error;
        }
      },
    });

    if (debug) {
      console.log(`[Auth:${appId}] Backchannel logout handler configured`);
    }
  }

  // Step 6: Return complete result
  return {
    auth,
    jtiBlacklist,
    handleBackchannelLogout,
    mongoDb,
    redis,
    env: {
      KEYCLOAK_ISSUER: env.KEYCLOAK_ISSUER,
      KEYCLOAK_CLIENT_ID: env.KEYCLOAK_CLIENT_ID,
      APP_URL: env.APP_URL,
    },
  };
}

// =============================================================================
// AUTH INSTANCE TYPE
// =============================================================================

/**
 * Type of the Better Auth instance (the auth object only)
 *
 * Use this for typing variables that hold just the auth instance.
 *
 * @used-by
 * - Route handlers that only need the auth object
 * - Server utilities
 */
export type BetterAuthInstance = BetterAuthResult['auth'];

// =============================================================================
// SINGLETON FACTORY
// =============================================================================

/**
 * Cache for auth results per app
 * Ensures only one instance is created per application
 */
const authResults = new Map<string, Promise<BetterAuthResult>>();

/**
 * Get or create a Better Auth result for the given app
 *
 * This ensures only one instance is created per application,
 * even if called multiple times. The instance is cached by appId.
 *
 * Returns the complete result including auth instance, JTI blacklist,
 * and backchannel logout handler.
 *
 * @param config - Application-specific configuration
 * @returns Promise resolving to the Better Auth result
 *
 * @used-by
 * - `apps/admin/src/lib/auth/index.ts`
 * - `apps/organization-admin/src/lib/auth/index.ts`
 *
 * @example
 * ```typescript
 * // In app's auth/index.ts
 * export const authResultPromise = getBetterAuth({
 *   appId: 'admin',
 *   cookiePrefix: 'pml_admin',
 *   redisKeyPrefix: 'pml-admin:',
 * });
 *
 * // Get just the auth instance
 * export const authPromise = authResultPromise.then(r => r.auth);
 *
 * // Get backchannel logout handler (for route handlers)
 * export const getBackchannelHandler = async () => {
 *   const result = await authResultPromise;
 *   return result.handleBackchannelLogout;
 * };
 * ```
 */
export function getBetterAuth(config: AppAuthConfig): Promise<BetterAuthResult> {
  const key = config.appId;

  if (!authResults.has(key)) {
    authResults.set(key, createBetterAuth(config));
  }

  return authResults.get(key)!;
}

/**
 * Get just the auth instance (convenience wrapper)
 *
 * @param config - Application-specific configuration
 * @returns Promise resolving to the Better Auth instance
 *
 * @used-by
 * - Route handlers that only need the auth object
 */
export async function getBetterAuthInstance(config: AppAuthConfig): Promise<BetterAuthInstance> {
  const result = await getBetterAuth(config);
  return result.auth;
}
