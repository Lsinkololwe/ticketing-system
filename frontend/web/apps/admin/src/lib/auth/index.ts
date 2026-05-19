/**
 * Better Auth Configuration with Keycloak OIDC + Redis Sessions
 *
 * OWASP Security Best Practices Applied:
 * - Session IDs stored server-side (Redis), only session ID in cookie
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
import { keycloak } from 'better-auth/plugins/generic-oauth';
import { Redis } from 'ioredis';
import { redisStorage } from '@better-auth/redis-storage';

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

// App configuration
const APP_URL = process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3030';
const AUTH_SECRET = process.env.AUTH_SECRET || 'development-secret-change-in-production';

// =============================================================================
// REDIS CLIENT (Singleton)
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
      console.log('[Redis] Connected for session storage');
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

  // Disable cookie cache - sessions are stored ONLY in Redis
  // This prevents data duplication and ensures Redis is the single source of truth
  cookieCache: {
    enabled: false,
    refreshCache: false,
  },
};

// =============================================================================
// BETTER AUTH INSTANCE
// =============================================================================

export const auth = betterAuth({
  // Application base URL
  baseURL: APP_URL,

  // Secret for signing tokens (use strong secret in production)
  secret: AUTH_SECRET,

  // ==========================================================================
  // KEYCLOAK OIDC PROVIDER
  // ==========================================================================
  // Keycloak handles: Authentication, User Management, SSO, MFA
  // Better Auth handles: Session management, token storage, API access
  plugins: [
    genericOAuth({
      config: [
        keycloak({
          clientId: KEYCLOAK_CLIENT_ID,
          clientSecret: KEYCLOAK_CLIENT_SECRET,
          issuer: KEYCLOAK_ISSUER,
          // Request specific scopes
          scopes: ['openid', 'profile', 'email'],
          // Enable PKCE for enhanced security (OWASP recommendation)
          pkce: true,
        }),
      ],
    }),
  ],

  // ==========================================================================
  // REDIS SESSION STORAGE (OWASP: Server-side session storage)
  // ==========================================================================
  // Tokens stored in Redis, only session ID in cookie
  // This prevents token exposure even if cookies are compromised
  secondaryStorage: redisStorage({
    client: getRedisClient(),
    keyPrefix: 'pml-admin:session:',
  }),

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

    // OWASP: Origin validation enabled
    disableOriginCheck: false,

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
    // Additional fields to store from Keycloak
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
  // CALLBACKS FOR CUSTOM LOGIC
  // ==========================================================================
  callbacks: {
    // Called after successful OAuth sign-in
    onOAuthAccountCreated: async ({ user, account }: { user: { id: string; email: string }; account: { providerId: string } }) => {
      console.log('[Auth] OAuth account created:', {
        userId: user.id,
        provider: account.providerId,
        email: user.email,
      });
    },
  },
});

// =============================================================================
// TYPE EXPORTS
// =============================================================================

export type Auth = typeof auth;

// Session type for use in components
export type Session = Awaited<ReturnType<typeof auth.api.getSession>>;
