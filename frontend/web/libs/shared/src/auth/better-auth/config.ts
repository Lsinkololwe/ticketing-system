/**
 * Shared Better Auth Server Configuration
 *
 * SERVER-ONLY: This module uses MongoDB and Redis which require Node.js runtime.
 *
 * ## Industry-Standard Pattern
 *
 * Following Better Auth and Next.js recommended patterns:
 * - Synchronous auth export (no Promise wrapper)
 * - Global singleton for database connections (survives hot reload)
 * - Lazy connection (connects when first query is made)
 *
 * ## Usage
 *
 * ```typescript
 * import { createAuth } from '@pml.tickets/shared/auth/better-auth/server';
 *
 * export const { auth, db, redis, jtiBlacklist, handleBackchannelLogout, env } = createAuth({
 *   appId: 'organization-admin',
 *   cookiePrefix: 'pml_org',
 * });
 *
 * // Use Better Auth's type inference
 * type Session = typeof auth.$Infer.Session;
 * type User = typeof auth.$Infer.Session.user;
 * ```
 *
 * @see https://better-auth.com/docs/integrations/next
 */

import 'server-only';

import { betterAuth } from 'better-auth';
import { genericOAuth } from 'better-auth/plugins';
import { nextCookies } from 'better-auth/next-js';
import { mongodbAdapter } from '@better-auth/mongo-adapter';
import { redisStorage } from '@better-auth/redis-storage';
import { MongoClient, type Db } from 'mongodb';
import { Redis } from 'ioredis';

import type { AppAuthConfig } from './types';
import { createJtiBlacklist, type JtiBlacklistService } from './jti-blacklist';
import { createBackchannelLogoutHandler } from './backchannel-logout';

// =============================================================================
// GLOBAL SINGLETONS (Survive Hot Reload)
// =============================================================================

/** Global references for singleton pattern */
declare global {
  // eslint-disable-next-line no-var
  var _mongoClient: MongoClient | undefined;
  // eslint-disable-next-line no-var
  var _redisClient: Redis | undefined;
  // eslint-disable-next-line no-var
  var _authInstances: Map<string, AuthServices> | undefined;
}

// =============================================================================
// ENVIRONMENT
// =============================================================================

function getEnv() {
  const MONGODB_URI = process.env.MONGODB_URI;
  const MONGODB_DATABASE = process.env.MONGODB_DATABASE;
  const APP_URL = process.env.NEXT_PUBLIC_APP_URL;
  const AUTH_SECRET = process.env.AUTH_SECRET;
  const KEYCLOAK_CLIENT_ID = process.env.AUTH_KEYCLOAK_ID;
  const KEYCLOAK_CLIENT_SECRET = process.env.AUTH_KEYCLOAK_SECRET;
  const KEYCLOAK_ISSUER = process.env.AUTH_KEYCLOAK_ISSUER;

  const missing: string[] = [];
  if (!MONGODB_URI) missing.push('MONGODB_URI');
  if (!MONGODB_DATABASE) missing.push('MONGODB_DATABASE');
  if (!APP_URL) missing.push('NEXT_PUBLIC_APP_URL');
  if (!AUTH_SECRET) missing.push('AUTH_SECRET');
  if (!KEYCLOAK_CLIENT_ID) missing.push('AUTH_KEYCLOAK_ID');
  if (!KEYCLOAK_CLIENT_SECRET) missing.push('AUTH_KEYCLOAK_SECRET');
  if (!KEYCLOAK_ISSUER) missing.push('AUTH_KEYCLOAK_ISSUER');

  if (missing.length > 0) {
    throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
  }

  return {
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
  };
}

// =============================================================================
// DATABASE CONNECTIONS (Lazy Singletons)
// =============================================================================

function getMongoClient(uri: string): MongoClient {
  if (process.env.NODE_ENV === 'development') {
    if (!global._mongoClient) {
      global._mongoClient = new MongoClient(uri, {
        maxPoolSize: 10,
        minPoolSize: 2,
        maxIdleTimeMS: 60000,
      });
    }
    return global._mongoClient;
  }

  return new MongoClient(uri, {
    maxPoolSize: 50,
    minPoolSize: 10,
  });
}

function getRedisClient(host: string, port: number, password?: string): Redis {
  if (process.env.NODE_ENV === 'development' && global._redisClient) {
    return global._redisClient;
  }

  const client = new Redis({
    host,
    port,
    password,
    maxRetriesPerRequest: 3,
    retryStrategy: (times) => (times > 5 ? null : Math.min(times * 200, 5000)),
    connectTimeout: 10000,
    lazyConnect: true,
    enableOfflineQueue: true,
  });

  client.on('connect', () => {
    console.log('[Redis] Connected for Better Auth session storage');
  });

  client.on('error', (err) => {
    console.error('[Redis] Connection error:', err.message);
  });

  if (process.env.NODE_ENV === 'development') {
    global._redisClient = client;
  }

  return client;
}

// =============================================================================
// SESSION CONFIGURATION
// =============================================================================

const SESSION_CONFIG = {
  expiresIn: 8 * 60 * 60, // 8 hours
  updateAge: 5 * 60, // 5 minutes
  storeSessionInDatabase: true,
  cookieCache: { enabled: false },
} as const;

// =============================================================================
// AUTH SERVICES TYPE
// =============================================================================

/** Backchannel logout handler type */
type BackchannelLogoutHandler = (token: string) => Promise<{ success: boolean; error?: string }>;

/** Environment config exposed to consumers */
interface EnvConfig {
  KEYCLOAK_ISSUER: string;
  KEYCLOAK_CLIENT_ID: string;
  APP_URL: string;
}

/**
 * Auth services returned by createAuth
 * Type is inferred from function return
 */
export interface AuthServices {
  auth: ReturnType<typeof createBetterAuthInstance>;
  db: Db;
  redis: Redis | null;
  jtiBlacklist: JtiBlacklistService | null;
  handleBackchannelLogout: BackchannelLogoutHandler | null;
  env: EnvConfig;
}

// =============================================================================
// BETTER AUTH INSTANCE FACTORY
// =============================================================================

/**
 * Create the Better Auth instance with our configuration
 * Separated to allow type inference
 */
function createBetterAuthInstance(
  env: ReturnType<typeof getEnv>,
  db: Db,
  secondaryStorage: ReturnType<typeof redisStorage> | undefined,
  cookiePrefix: string,
  isProduction: boolean
) {
  return betterAuth({
    baseURL: env.APP_URL,
    secret: env.AUTH_SECRET,
    database: mongodbAdapter(db),
    plugins: [
      genericOAuth({
        config: [{
          providerId: 'keycloak',
          clientId: env.KEYCLOAK_CLIENT_ID,
          clientSecret: env.KEYCLOAK_CLIENT_SECRET,
          discoveryUrl: `${env.KEYCLOAK_ISSUER}/.well-known/openid-configuration`,
          pkce: true,
          scopes: ['openid', 'profile', 'email', 'phone'],
          mapProfileToUser: (profile) => ({
            id: profile.sub as string,
            email: (profile.email as string) || '',
            name: (profile.name as string) || (profile.preferred_username as string) || '',
            emailVerified: (profile.email_verified as boolean) || false,
            image: (profile.picture as string) || null,
            // Phone number from Keycloak (if available)
            phone: (profile.phone_number as string) || null,
            phoneVerified: (profile.phone_number_verified as boolean) || false,
          }),
        }],
      }),
      nextCookies(),
    ],
    ...(secondaryStorage && { secondaryStorage }),
    session: SESSION_CONFIG,
    advanced: {
      useSecureCookies: isProduction,
      cookiePrefix,
      defaultCookieAttributes: {
        httpOnly: true,
        secure: isProduction,
        sameSite: 'lax' as const,
        path: '/',
      },
    },
    account: {
      accountLinking: {
        enabled: true,
        trustedProviders: ['keycloak'],
      },
    },
    user: {
      additionalFields: {
        roles: { type: 'string[]', required: false },
        keycloakId: { type: 'string', required: false },
        phone: { type: 'string', required: false },
        phoneVerified: { type: 'boolean', required: false },
      },
    },
    trustedOrigins: [
      env.APP_URL,
      ...(isProduction ? [] : ['http://localhost:3030', 'http://localhost:3031', 'http://localhost:3000']),
    ],
  });
}

// =============================================================================
// MAIN FACTORY (Synchronous)
// =============================================================================

/**
 * Create Better Auth instance with all services
 *
 * Returns synchronously - connections are established lazily.
 * Uses singleton pattern to prevent duplicate instances.
 */
export function createAuth(config: AppAuthConfig): AuthServices {
  const { appId, cookiePrefix, redisKeyPrefix = `${appId}:`, enableRedis = true } = config;

  // Check singleton cache
  if (!global._authInstances) {
    global._authInstances = new Map();
  }

  if (global._authInstances.has(appId)) {
    return global._authInstances.get(appId)!;
  }

  // Get environment
  const env = getEnv();
  const isProduction = process.env.NODE_ENV === 'production';

  // Get database connections
  const mongoClient = getMongoClient(env.MONGODB_URI);
  const db = mongoClient.db(env.MONGODB_DATABASE);

  // Setup Redis (optional)
  let redis: Redis | null = null;
  let secondaryStorage: ReturnType<typeof redisStorage> | undefined;
  let jtiBlacklist: JtiBlacklistService | null = null;

  if (enableRedis && env.REDIS_HOST) {
    redis = getRedisClient(
      env.REDIS_HOST,
      parseInt(env.REDIS_PORT || '6379', 10),
      env.REDIS_PASSWORD
    );

    secondaryStorage = redisStorage({
      client: redis,
      keyPrefix: redisKeyPrefix,
    });

    jtiBlacklist = createJtiBlacklist({
      redis,
      keyPrefix: redisKeyPrefix,
      defaultTtlSeconds: 86400,
    });
  }

  // Create Better Auth instance
  const auth = createBetterAuthInstance(env, db, secondaryStorage, cookiePrefix, isProduction);

  // Create backchannel logout handler
  let handleBackchannelLogout: BackchannelLogoutHandler | null = null;

  if (jtiBlacklist && redis) {
    handleBackchannelLogout = createBackchannelLogoutHandler({
      keycloakJwksUrl: `${env.KEYCLOAK_ISSUER}/protocol/openid-connect/certs`,
      keycloakIssuer: env.KEYCLOAK_ISSUER,
      clientId: env.KEYCLOAK_CLIENT_ID,
      jtiBlacklist,
      revokeUserSessions: async (keycloakUserId: string) => {
        const sessionsCollection = db.collection('sessions');
        const usersCollection = db.collection('users');

        const user = await usersCollection.findOne({
          $or: [{ keycloakId: keycloakUserId }, { id: keycloakUserId }],
        });

        if (user) {
          const result = await sessionsCollection.deleteMany({ userId: user.id });
          console.log(`[Auth:${appId}] Backchannel logout: Revoked ${result.deletedCount} sessions`);
        }
      },
    });
  }

  // Build services object
  const services: AuthServices = {
    auth,
    db,
    redis,
    jtiBlacklist,
    handleBackchannelLogout,
    env: {
      KEYCLOAK_ISSUER: env.KEYCLOAK_ISSUER,
      KEYCLOAK_CLIENT_ID: env.KEYCLOAK_CLIENT_ID,
      APP_URL: env.APP_URL,
    },
  };

  // Cache in singleton
  global._authInstances.set(appId, services);

  return services;
}

// =============================================================================
// TYPE EXPORTS
// =============================================================================

/** Better Auth instance type */
export type Auth = AuthServices['auth'];
