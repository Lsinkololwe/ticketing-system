/**
 * Better Auth Configuration with Keycloak OIDC + MongoDB + Redis
 *
 * PRODUCTION-GRADE SESSION MANAGEMENT
 * ====================================
 * Based on Better Auth official documentation:
 * @see https://better-auth.com/docs/concepts/session-management
 * @see https://better-auth.com/docs/concepts/database
 *
 * Architecture:
 * - MongoDB: Primary database for users, sessions, accounts (persistent)
 * - Redis: Secondary storage for fast session lookups (optional caching layer)
 * - Cookie Cache: Signed cookie for fast session validation (reduces DB hits)
 * - Keycloak: OIDC provider for authentication
 *
 * Session Storage Strategy:
 * 1. Sessions are ALWAYS stored in MongoDB (storeSessionInDatabase: true)
 * 2. Redis is used as secondary storage for fast lookups
 * 3. Cookie cache provides fastest validation (no DB/Redis hit for cached sessions)
 *
 * OWASP Security Best Practices Applied:
 * - Session IDs stored server-side (MongoDB + Redis), only session token in cookie
 * - HttpOnly, Secure, SameSite cookies to prevent XSS and CSRF
 * - Session expiration and idle timeout
 * - Automatic session invalidation
 * - IP address tracking for session binding
 *
 * @see https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
 */

import { betterAuth } from 'better-auth';
import {
  createEnhancedRedisStorage,
  SessionManager,
} from './session-storage';
import { genericOAuth } from 'better-auth/plugins';
import { keycloak } from 'better-auth/plugins/generic-oauth';
import { Redis } from 'ioredis';
import { MongoClient } from 'mongodb';
import { keycloakMongoAdapter } from './keycloak-mongo-adapter';

// =============================================================================
// ENVIRONMENT CONFIGURATION
// =============================================================================

const isProduction = process.env.NODE_ENV === 'production';

// Keycloak configuration - Organization Portal Client
const KEYCLOAK_CLIENT_ID = process.env.AUTH_KEYCLOAK_ID;
const KEYCLOAK_CLIENT_SECRET = process.env.AUTH_KEYCLOAK_SECRET;
const KEYCLOAK_ISSUER = process.env.AUTH_KEYCLOAK_ISSUER;

// Redis configuration
const REDIS_HOST = process.env.REDIS_HOST;
const REDIS_PORT = parseInt(process.env.REDIS_PORT || '6379', 10);
const REDIS_PASSWORD = process.env.REDIS_PASSWORD;

// MongoDB configuration
const MONGODB_URI = process.env.MONGODB_URI;
const MONGODB_DATABASE = process.env.MONGODB_DATABASE;

// App configuration
const APP_URL = process.env.NEXT_PUBLIC_APP_URL;
const AUTH_SECRET = process.env.AUTH_SECRET;

// Feature flags
const ENABLE_REDIS_SECONDARY_STORAGE = process.env.ENABLE_REDIS_SECONDARY_STORAGE === 'true';

// =============================================================================
// MONGODB CLIENT (Singleton with Connection Pooling)
// =============================================================================

declare global {
  var _mongoClientOrganizer: MongoClient | undefined;
  var _mongoClientPromiseOrganizer: Promise<MongoClient> | undefined;
}

function getMongoClientPromise(): Promise<MongoClient> {
  if (process.env.NODE_ENV === 'development') {
    if (!global._mongoClientPromiseOrganizer) {
      global._mongoClientOrganizer = new MongoClient(MONGODB_URI as string, {
        maxPoolSize: 10,
        minPoolSize: 2,
        maxIdleTimeMS: 60000,
        connectTimeoutMS: 10000,
        serverSelectionTimeoutMS: 30000,
        retryWrites: true,
        retryReads: true,
      });
      global._mongoClientPromiseOrganizer = global._mongoClientOrganizer.connect();

      global._mongoClientOrganizer.on('error', (err) => {
        console.error('[MongoDB] Client error:', err.message);
      });
      global._mongoClientOrganizer.on('serverOpening', () => {
        console.log('[MongoDB] Connected for Better Auth storage (organizer)');
      });
    }
    return global._mongoClientPromiseOrganizer;
  }

  const client = new MongoClient(MONGODB_URI as string, {
    maxPoolSize: 50,
    minPoolSize: 10,
    connectTimeoutMS: 10000,
    retryWrites: true,
    retryReads: true,
  });
  return client.connect();
}

const mongoClientPromise = getMongoClientPromise();

// =============================================================================
// REDIS CLIENT (Singleton) - Production-Grade Implementation
// =============================================================================

declare global {
  var _redisClientOrganizer: Redis | undefined;
}

function getRedisClient(): Redis {
  if (process.env.NODE_ENV === 'development' && global._redisClientOrganizer) {
    return global._redisClientOrganizer;
  }

  const client = new Redis({
    host: REDIS_HOST,
    port: REDIS_PORT,
    password: REDIS_PASSWORD,
    maxRetriesPerRequest: 3,
    retryStrategy: (times) => {
      if (times > 5) {
        console.error('[Redis] Max retries exceeded, giving up');
        return null;
      }
      const delay = Math.min(times * 200, 5000);
      console.log(`[Redis] Retry attempt ${times}, waiting ${delay}ms`);
      return delay;
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
    console.log('[Redis] Connected for session storage (organizer)');
  });

  client.on('ready', () => {
    console.log('[Redis] Ready for commands');
  });

  if (process.env.NODE_ENV === 'development') {
    global._redisClientOrganizer = client;
  }

  return client;
}

// =============================================================================
// SESSION MANAGER INSTANCE
// =============================================================================

let sessionManagerInstance: SessionManager | null = null;

/**
 * Get the SessionManager instance for high-level session operations
 */
export function getSessionManager(): SessionManager {
  if (!sessionManagerInstance) {
    sessionManagerInstance = new SessionManager(getRedisClient(), 'pml-organizer:');
  }
  return sessionManagerInstance;
}

// =============================================================================
// SESSION CONFIGURATION (Production-Grade OWASP Compliant)
// =============================================================================

/**
 * Production Session Configuration
 *
 * Architecture:
 * - MongoDB: Source of truth (persistent storage)
 * - Redis: Fast cache for session lookups (secondary storage)
 * - Cookie Cache: Fastest validation layer (signed cookie)
 *
 * Session Validation Flow (Optimized):
 * 1. Check cookie cache (fastest - CPU only, no network)
 * 2. If cache miss → Check Redis (fast - in-memory)
 * 3. If Redis miss → Check MongoDB (fallback - network I/O)
 * 4. Update cookie cache with fresh session data
 *
 * Double Invalidation:
 * - On logout: Delete from Redis + MongoDB + Clear cookie
 * - preserveSessionInDatabase: false enables true dual deletion
 *
 * @see https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
 */
const SESSION_CONFIG = {
  // Absolute session timeout: 8 hours (OWASP recommended max)
  expiresIn: 8 * 60 * 60, // 8 hours in seconds

  // Update session timestamp every 5 minutes (extends session on activity)
  updateAge: 5 * 60, // 5 minutes

  // CRITICAL: Always store sessions in MongoDB (source of truth)
  storeSessionInDatabase: true,

  // CRITICAL: Set to FALSE for true dual invalidation
  // When false, sessions are deleted from BOTH Redis AND MongoDB on revocation
  preserveSessionInDatabase: false,

  // Cookie cache DISABLED for security
  // When disabled:
  // - No session_data cookie (no sensitive data in browser)
  // - Every session check hits Redis/MongoDB (slight latency increase)
  // - Immediate session revocation (no cache delay)
  //
  // Trade-off: Security > Performance
  // Redis secondary storage provides fast lookups to compensate
  cookieCache: {
    enabled: false,
  },
};

// =============================================================================
// BETTER AUTH INSTANCE (Async initialization for MongoDB)
// =============================================================================

async function createAuth() {
  const mongoClient = await mongoClientPromise;
  const mongoDb = mongoClient.db(MONGODB_DATABASE);

  // Prepare secondary storage based on configuration
  const redis = ENABLE_REDIS_SECONDARY_STORAGE ? getRedisClient() : null;
  const secondaryStorage = redis
    ? createEnhancedRedisStorage(redis, 'pml-organizer:', {
        enableAuditLog: true,
        auditRetentionDays: 30,
      })
    : undefined;

  if (ENABLE_REDIS_SECONDARY_STORAGE) {
    console.log('[Auth] Redis secondary storage ENABLED - dual storage active');
  } else {
    console.log('[Auth] Using MongoDB + Cookie Cache only');
  }

  return betterAuth({
    // Application base URL
    baseURL: APP_URL,

    // Secret for signing tokens (use strong secret in production)
    secret: AUTH_SECRET,

    // ==========================================================================
    // CUSTOM DATABASE ADAPTER (Shared Users Collection)
    // ==========================================================================
    // This adapter:
    // - Reads from existing `users` collection (Identity Service)
    // - Writes sessions to `auth_sessions` collection
    // - Writes OAuth accounts to `auth_accounts` collection
    // - Does NOT create duplicate user data
    database: keycloakMongoAdapter(mongoDb, {
      allowUserCreation: false, // Users must exist in Identity Service
      syncUserUpdates: false,   // Identity Service is source of truth
    }),

    // ==========================================================================
    // KEYCLOAK OIDC PROVIDER
    // ==========================================================================
    plugins: [
      genericOAuth({
        config: [
          keycloak({
            clientId: KEYCLOAK_CLIENT_ID as string,
            clientSecret: KEYCLOAK_CLIENT_SECRET as string,
            issuer: KEYCLOAK_ISSUER as string,
            scopes: ['openid', 'profile', 'email'],
            pkce: true,
          }),
        ],
      }),
    ],

    // ==========================================================================
    // SECONDARY STORAGE (Redis - Optional Caching Layer)
    // ==========================================================================
    ...(secondaryStorage && { secondaryStorage }),

    // ==========================================================================
    // SESSION CONFIGURATION (Production-Grade)
    // ==========================================================================
    session: SESSION_CONFIG,

    // ==========================================================================
    // ADVANCED SECURITY CONFIGURATION (OWASP Compliant)
    // ==========================================================================
    advanced: {
      // Use Better Auth's default secure ID generation (no custom override)
      // Better Auth uses cryptographically secure random IDs by default

      // OWASP: Track IP addresses for session binding
      ipAddress: {
        ipAddressHeaders: ['x-forwarded-for', 'x-real-ip', 'cf-connecting-ip'],
        disableIpTracking: false,
      },

      // OWASP: Use secure cookies in production
      useSecureCookies: isProduction,

      // OWASP: CSRF protection enabled
      disableCSRFCheck: false,

      // OWASP: Origin validation enabled
      disableOriginCheck: false,

      // OWASP: Cookie attributes for security
      defaultCookieAttributes: {
        httpOnly: true,
        secure: isProduction,
        sameSite: 'lax' as const,
        path: '/',
      },

      // Cookie prefix for namespacing
      // Result: session cookie will be named "pml_org.session_token"
      cookiePrefix: 'pml_org',
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
    // DATABASE HOOKS (Session Synchronization & Audit)
    // ==========================================================================
    databaseHooks: {
      session: {
        create: {
          after: async (session) => {
            // Log session creation
            console.log('[Session] Created:', {
              sessionId: session.id,
              userId: session.userId,
              expiresAt: session.expiresAt,
              timestamp: new Date().toISOString(),
            });

            // Ensure Redis active sessions tracking is updated
            if (redis) {
              try {
                const activeKey = `pml-organizer:active_sessions:${session.userId}`;
                await redis.sadd(activeKey, session.token);
                // Set TTL on active sessions set to match session expiry
                const ttl = Math.floor(
                  (new Date(session.expiresAt).getTime() - Date.now()) / 1000
                );
                if (ttl > 0) {
                  await redis.expire(activeKey, ttl);
                }
              } catch (error) {
                console.error('[Session] Redis tracking update failed:', error);
                // Don't throw - MongoDB is source of truth
              }
            }
          },
        },
        delete: {
          before: async (session) => {
            console.log('[Session] Deleting:', {
              sessionId: session.id,
              userId: session.userId,
              timestamp: new Date().toISOString(),
            });

            // Ensure Redis is cleaned up before MongoDB deletion
            if (redis) {
              try {
                // Remove from active sessions tracking
                await redis.srem(
                  `pml-organizer:active_sessions:${session.userId}`,
                  session.token
                );
                // The session itself will be deleted by Better Auth's secondary storage
              } catch (error) {
                console.error('[Session] Redis cleanup failed:', error);
              }
            }

            return true; // Allow deletion
          },
          after: async (session) => {
            console.log('[Session] Deleted:', {
              sessionId: session.id,
              userId: session.userId,
              timestamp: new Date().toISOString(),
            });
          },
        },
      },
    },

    // ==========================================================================
    // CALLBACKS
    // ==========================================================================
    callbacks: {
      onOAuthAccountCreated: async ({ user, account }: { user: { id: string; email: string }; account: { providerId: string } }) => {
        console.log('[Auth] OAuth account created:', {
          userId: user.id,
          provider: account.providerId,
          email: user.email,
        });
      },
    },
  });
}

// =============================================================================
// EXPORT AUTH INSTANCE
// =============================================================================

const authPromise = createAuth();

// Export a proxy that awaits the auth instance
export const auth = new Proxy({} as Awaited<ReturnType<typeof createAuth>>, {
  get(_, prop) {
    return async (...args: unknown[]) => {
      const authInstance = await authPromise;
      const value = (authInstance as Record<string, unknown>)[prop as string];
      if (typeof value === 'function') {
        return (value as (...args: unknown[]) => unknown).apply(authInstance, args);
      }
      return value;
    };
  },
});

export { authPromise };

// Export utilities for other modules
export { getRedisClient, getMongoClientPromise };

// =============================================================================
// TYPE EXPORTS
// =============================================================================

export type Auth = Awaited<ReturnType<typeof createAuth>>;
export type Session = Awaited<ReturnType<Auth['api']['getSession']>>;
