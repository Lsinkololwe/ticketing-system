/**
 * Better Auth Configuration with Keycloak OIDC + Shared MongoDB
 *
 * Architecture (No Duplication):
 * ============================================================================
 *
 * DATA OWNERSHIP:
 * - Keycloak (PostgreSQL): User identity, credentials, roles, verification
 * - MongoDB `users` collection: Business profile data (managed by Identity Service)
 * - MongoDB `auth_sessions`: Better Auth session management
 * - MongoDB `auth_accounts`: OAuth token storage
 *
 * KEY PRINCIPLE:
 * - Better Auth does NOT create its own users collection
 * - Uses the existing `users` collection from Identity Service (read-only)
 * - Only manages sessions and OAuth accounts
 *
 * OWASP Security Best Practices Applied:
 * - Session IDs stored server-side (MongoDB + Redis), only session ID in cookie
 * - HttpOnly, Secure, SameSite cookies to prevent XSS and CSRF
 * - Session expiration and idle timeout
 * - Token encryption at rest
 * - Automatic session invalidation
 * - IP address tracking for session binding
 *
 * @see https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
 * @see https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
 */

import { betterAuth } from 'better-auth';
import { genericOAuth } from 'better-auth/plugins';
import { Redis } from 'ioredis';
import { MongoClient } from 'mongodb';
import { keycloakMongoAdapter } from './keycloak-mongo-adapter';

// =============================================================================
// ENVIRONMENT CONFIGURATION
// =============================================================================

const isProduction = process.env.NODE_ENV === 'production';

// Keycloak configuration
const KEYCLOAK_CLIENT_ID = process.env.AUTH_KEYCLOAK_ID || 'event-ticketing-admin';
const KEYCLOAK_CLIENT_SECRET = process.env.AUTH_KEYCLOAK_SECRET || '';
const KEYCLOAK_ISSUER = process.env.AUTH_KEYCLOAK_ISSUER || 'http://localhost:8084/realms/event-ticketing';

// Redis configuration
const REDIS_HOST = process.env.REDIS_HOST || 'localhost';
const REDIS_PORT = parseInt(process.env.REDIS_PORT || '6379', 10);
const REDIS_PASSWORD = process.env.REDIS_PASSWORD || undefined;

// MongoDB configuration
const MONGODB_URI = process.env.MONGODB_URI ||
  'mongodb://app_user:app_password@localhost:27017/dev_ticketing?authSource=admin&directConnection=true';
const MONGODB_DATABASE = process.env.MONGODB_DATABASE || 'dev_ticketing';

// App configuration
const APP_URL = process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3030';
const AUTH_SECRET = process.env.AUTH_SECRET || 'development-secret-change-in-production';

// =============================================================================
// MONGODB CLIENT (Singleton with Connection Pooling)
// =============================================================================

// Global singleton to persist across hot reloads in development
declare global {
  // eslint-disable-next-line no-var
  var _mongoClientAdmin: MongoClient | undefined;
  // eslint-disable-next-line no-var
  var _mongoClientPromiseAdmin: Promise<MongoClient> | undefined;
}

function getMongoClientPromise(): Promise<MongoClient> {
  if (process.env.NODE_ENV === 'development') {
    // Development: Reuse client across hot reloads
    if (!global._mongoClientPromiseAdmin) {
      global._mongoClientAdmin = new MongoClient(MONGODB_URI, {
        maxPoolSize: 10,
        minPoolSize: 2,
        maxIdleTimeMS: 60000,
        connectTimeoutMS: 10000,
        serverSelectionTimeoutMS: 30000,
        retryWrites: true,
        retryReads: true,
      });
      global._mongoClientPromiseAdmin = global._mongoClientAdmin.connect();

      global._mongoClientAdmin.on('error', (err) => {
        console.error('[MongoDB] Client error:', err.message);
      });
      global._mongoClientAdmin.on('serverOpening', () => {
        console.log('[MongoDB] Connected for Better Auth (shared users collection)');
      });
    }
    return global._mongoClientPromiseAdmin;
  }

  // Production: Create client (will be managed by serverless/container lifecycle)
  const client = new MongoClient(MONGODB_URI, {
    maxPoolSize: 50,
    minPoolSize: 10,
    connectTimeoutMS: 10000,
    retryWrites: true,
    retryReads: true,
  });
  return client.connect();
}

// Initialize client promise (lazy connection)
const mongoClientPromise = getMongoClientPromise();

// =============================================================================
// REDIS CLIENT (Singleton for Session Caching)
// =============================================================================

let redisClient: Redis | null = null;

function getRedisClient(): Redis {
  if (!redisClient) {
    redisClient = new Redis({
      host: REDIS_HOST,
      port: REDIS_PORT,
      password: REDIS_PASSWORD,
      maxRetriesPerRequest: 3,
      retryStrategy: (times) => {
        if (times > 3) return null;
        return Math.min(times * 100, 3000);
      },
      connectTimeout: 5000,
      commandTimeout: 2000,
      lazyConnect: true,
      enableOfflineQueue: true,
    });

    redisClient.on('error', (err) => {
      console.error('[Redis] Connection error:', err.message);
    });

    redisClient.on('connect', () => {
      console.log('[Redis] Connected for session caching');
    });
  }

  return redisClient;
}

// =============================================================================
// OWASP SESSION CONFIGURATION
// =============================================================================

/**
 * OWASP Session Management Requirements:
 *
 * 1. Session ID Length: Minimum 128 bits of entropy (Better Auth default: UUID)
 * 2. Session Timeout:
 *    - Idle Timeout: 15-30 minutes for sensitive apps
 *    - Absolute Timeout: 4-8 hours max
 * 3. Session Cookie Attributes:
 *    - HttpOnly: true (prevent XSS access)
 *    - Secure: true (HTTPS only in production)
 *    - SameSite: Lax or Strict (CSRF protection)
 * 4. Session Binding: IP address tracking
 * 5. Session Regeneration: After authentication
 */

const SESSION_CONFIG = {
  // Absolute session timeout: 8 hours (OWASP recommended max)
  expiresIn: 8 * 60 * 60, // 8 hours in seconds

  // Update session timestamp every 5 minutes (for idle timeout tracking)
  updateAge: 5 * 60, // 5 minutes

  // Store sessions in MongoDB (primary) with Redis caching (secondary)
  storeSessionInDatabase: true,

  // Disable cookie cache - sessions are stored in MongoDB + cached in Redis
  cookieCache: {
    enabled: false,
  },
};

// =============================================================================
// REDIS SESSION CACHE (Secondary Storage)
// =============================================================================

/**
 * Custom Redis storage for session caching
 * Sessions are stored in MongoDB (primary) and cached in Redis (fast reads)
 */
function createRedisSecondaryStorage() {
  const redis = getRedisClient();
  const keyPrefix = 'pml-admin:session:';
  const ttlSeconds = SESSION_CONFIG.expiresIn;

  return {
    async get(key: string): Promise<string | null> {
      try {
        return await redis.get(`${keyPrefix}${key}`);
      } catch (error) {
        console.error('[Redis] Get error:', error);
        return null;
      }
    },

    async set(key: string, value: string, ttl?: number): Promise<void> {
      try {
        await redis.setex(`${keyPrefix}${key}`, ttl || ttlSeconds, value);
      } catch (error) {
        console.error('[Redis] Set error:', error);
      }
    },

    async delete(key: string): Promise<void> {
      try {
        await redis.del(`${keyPrefix}${key}`);
      } catch (error) {
        console.error('[Redis] Delete error:', error);
      }
    },
  };
}

// =============================================================================
// BETTER AUTH INSTANCE (Async initialization for MongoDB)
// =============================================================================

async function createAuth() {
  const mongoClient = await mongoClientPromise;
  const mongoDb = mongoClient.db(MONGODB_DATABASE);

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
    // Keycloak handles: Authentication, User Management, SSO, MFA
    // Better Auth handles: Session management, token storage, API access
    socialProviders: {
      keycloak: {
        clientId: KEYCLOAK_CLIENT_ID,
        clientSecret: KEYCLOAK_CLIENT_SECRET,
        issuer: KEYCLOAK_ISSUER,
        // Enable PKCE for enhanced security (OWASP recommendation)
        pkce: true,
        // Request specific scopes
        scope: ['openid', 'profile', 'email'],
        // Map Keycloak user ID to our user ID
        mapProfileToUser: (profile) => {
          return {
            id: profile.sub, // Use Keycloak user ID as Better Auth user ID
            email: profile.email || '',
            name: profile.name || profile.preferred_username || '',
            emailVerified: profile.email_verified || false,
            image: profile.picture || null,
          };
        },
      },
    },

    // ==========================================================================
    // REDIS SESSION CACHE (Secondary Storage)
    // ==========================================================================
    // Sessions are stored in MongoDB (primary) and cached in Redis (fast reads)
    secondaryStorage: createRedisSecondaryStorage(),

    // ==========================================================================
    // SESSION CONFIGURATION (OWASP Compliant)
    // ==========================================================================
    session: SESSION_CONFIG,

    // ==========================================================================
    // ADVANCED SECURITY CONFIGURATION
    // ==========================================================================
    advanced: {
      // Generate cryptographically secure session IDs
      generateId: () => crypto.randomUUID(),

      // OWASP: Track IP addresses for session binding
      ipAddress: {
        ipAddressHeaders: ['x-forwarded-for', 'x-real-ip', 'cf-connecting-ip'],
        disableIpTracking: false,
      },

      // OWASP: Use secure cookies in production
      useSecureCookies: isProduction,

      // OWASP: CSRF protection enabled
      disableCSRFCheck: false,

      // OWASP: Cookie attributes for security
      defaultCookieAttributes: {
        httpOnly: true, // Prevent XSS access to cookies
        secure: isProduction, // HTTPS only in production
        sameSite: 'lax' as const, // CSRF protection
        path: '/',
      },

      // Custom cookie configuration
      cookies: {
        session_token: {
          name: 'pml_session',
          attributes: {
            httpOnly: true,
            secure: isProduction,
            sameSite: 'lax' as const,
            path: '/',
            // OWASP: Session cookie should not have expiry (session cookie)
            // maxAge is handled by session.expiresIn
          },
        },
      },

      // Cookie prefix for namespacing
      cookiePrefix: 'pml',
    },

    // ==========================================================================
    // ACCOUNT SETTINGS
    // ==========================================================================
    account: {
      // Enable account linking (same email from different providers)
      accountLinking: {
        enabled: true,
        trustedProviders: ['keycloak'],
      },
    },

    // ==========================================================================
    // USER CONFIGURATION
    // ==========================================================================
    user: {
      // Map the user ID from Keycloak
      modelName: 'user',
      // Additional fields from the shared users collection
      additionalFields: {
        roles: {
          type: 'string[]',
          required: false,
          input: false, // Not settable via Better Auth
        },
        phoneNumber: {
          type: 'string',
          required: false,
          input: false,
        },
        firstName: {
          type: 'string',
          required: false,
          input: false,
        },
        lastName: {
          type: 'string',
          required: false,
          input: false,
        },
      },
    },

    // ==========================================================================
    // CALLBACKS FOR CUSTOM LOGIC
    // ==========================================================================
    callbacks: {
      // Called after successful OAuth sign-in
      onOAuthSignIn: async ({ user, account }) => {
        console.log('[Auth] OAuth sign-in:', {
          userId: user.id,
          provider: account.providerId,
          email: user.email,
        });

        // The user should already exist in MongoDB (created by Identity Service)
        // If not, they need to register through Keycloak first
        return true;
      },
    },

    // ==========================================================================
    // TRUSTED ORIGINS (CSRF Protection)
    // ==========================================================================
    trustedOrigins: [
      APP_URL,
      ...(isProduction ? [] : ['http://localhost:3030', 'http://localhost:3031']),
    ],
  });
}

// =============================================================================
// EXPORT AUTH INSTANCE
// =============================================================================

// Create auth instance promise (initialized on first use)
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

// Export the promise for direct access when needed
export { authPromise };

// =============================================================================
// TYPE EXPORTS
// =============================================================================

export type Auth = Awaited<ReturnType<typeof createAuth>>;

// Session type for use in components
export type Session = Awaited<ReturnType<Auth['api']['getSession']>>;

// User type with additional fields from shared collection
export interface AuthUser {
  id: string;
  email: string;
  name: string;
  emailVerified: boolean;
  image: string | null;
  roles?: string[];
  phoneNumber?: string;
  firstName?: string;
  lastName?: string;
}
