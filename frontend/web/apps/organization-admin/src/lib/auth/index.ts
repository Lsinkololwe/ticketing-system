/**
 * Organization Admin App - Better Auth Configuration
 *
 * Uses shared Better Auth configuration from @pml.tickets/shared.
 * Follows Better Auth's recommended synchronous export pattern.
 *
 * ## Usage
 *
 * ```typescript
 * // In Server Components
 * import { auth } from '@/lib/auth';
 * const session = await auth.api.getSession({ headers: await headers() });
 *
 * // For type inference
 * type Session = typeof auth.$Infer.Session;
 * type User = typeof auth.$Infer.Session.user;
 * ```
 *
 * @see https://better-auth.com/docs/integrations/next
 */

import {
  createAuth,
  type AuthServices,
} from '@pml.tickets/shared/auth/better-auth/server';

// =============================================================================
// AUTH INSTANCE (Synchronous - Industry Standard Pattern)
// =============================================================================

/**
 * Create auth services with lazy database connections
 *
 * This follows the Better Auth recommended pattern:
 * - Synchronous export (no Promise wrapper)
 * - Global singleton (survives hot reload)
 * - Lazy connections (established on first query)
 */
const services: AuthServices = createAuth({
  appId: 'organization-admin',
  cookiePrefix: 'pml_org',
  redisKeyPrefix: 'pml-organizer:',
  enableRedis: process.env.ENABLE_REDIS_SECONDARY_STORAGE === 'true',
});

// =============================================================================
// EXPORTS
// =============================================================================

/**
 * Better Auth instance
 *
 * @example
 * ```typescript
 * // Get session in Server Component
 * const session = await auth.api.getSession({ headers: await headers() });
 *
 * // Sign out
 * await auth.api.signOut({ headers: request.headers });
 * ```
 */
export const auth = services.auth;

/** MongoDB database instance */
export const db = services.db;

/** Redis client (null if not enabled) */
export const redis = services.redis;

/** JTI blacklist service for token revocation (null if Redis not enabled) */
export const jtiBlacklist = services.jtiBlacklist;

/** Backchannel logout handler for Keycloak (null if Redis not enabled) */
export const handleBackchannelLogout = services.handleBackchannelLogout;

/** Environment configuration (Keycloak URLs, etc.) */
export const env = services.env;

// =============================================================================
// SERVICE CONTAINER EXPORTS
// =============================================================================

/**
 * Dependency injection container exports
 *
 * These provide singleton service instances following industry-standard
 * dependency injection patterns.
 */
export {
  getSessionService,
  getOrganizationService,
  getTokenService,
  resetServices,
  setMockServices,
} from './container';

// =============================================================================
// SERVICE INTERFACE EXPORTS
// =============================================================================

/**
 * Service interfaces for type-safe dependency injection
 */
export type {
  ISessionService,
  IOrganizationService,
  ITokenService,
  IAuthConfig,
  OrganizationStatus,
} from './interfaces';

// =============================================================================
// TYPE EXPORTS
// =============================================================================

export type { AuthServices };

/** Session type - inferred from auth instance */
export type Session = typeof auth.$Infer.Session;

/** User type - inferred from auth instance */
export type User = typeof auth.$Infer.Session.user;
