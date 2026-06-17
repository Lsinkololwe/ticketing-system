/**
 * Create Auth Factory (Backward Compatible Entry Point)
 *
 * SERVER-ONLY: Production entry point for Better Auth configuration.
 *
 * This factory function provides backward compatibility with the previous
 * functional API while using the new class-based AuthContainer internally.
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
 * // Type inference
 * type Session = typeof auth.$Infer.Session;
 * type User = typeof auth.$Infer.Session.user;
 * ```
 *
 * @module libs/shared/src/auth/better-auth/createAuth
 */

import 'server-only';

import { AuthContainer } from './container/AuthContainer';
import type { AppAuthConfig } from './types';
import type { Db } from 'mongodb';
import type { Redis } from 'ioredis';
import type { IJtiBlacklistService } from './interfaces/IJtiBlacklistService';

// =============================================================================
// AUTH SERVICES TYPE (Backward Compatible)
// =============================================================================

/**
 * Environment config exposed to consumers
 */
interface EnvConfig {
  KEYCLOAK_ISSUER: string;
  KEYCLOAK_CLIENT_ID: string;
  APP_URL: string;
}

/**
 * Auth services returned by createAuth
 *
 * Maintains backward compatibility with existing code.
 */
export interface AuthServices {
  /** Better Auth instance */
  auth: ReturnType<AuthContainer['getAuth']>;
  /** MongoDB database instance */
  db: Db;
  /** Redis client (null if not configured) */
  redis: Redis | null;
  /** JTI blacklist service (null if Redis not configured) */
  jtiBlacklist: IJtiBlacklistService | null;
  /** Backchannel logout handler (null if Redis not configured) */
  handleBackchannelLogout: ((token: string) => Promise<{ success: boolean; error?: string }>) | null;
  /** Environment configuration */
  env: EnvConfig;
}

// =============================================================================
// CREATE AUTH FACTORY
// =============================================================================

/**
 * Create Better Auth instance with all services
 *
 * Factory function that uses AuthContainer internally.
 * Returns the same structure as before for backward compatibility.
 *
 * @param config - Application auth configuration
 * @returns Auth services object
 *
 * @example
 * ```typescript
 * export const { auth, db, redis, jtiBlacklist, handleBackchannelLogout, env } = createAuth({
 *   appId: 'organization-admin',
 *   cookiePrefix: 'pml_org',
 * });
 * ```
 */
export function createAuth(config: AppAuthConfig): AuthServices {
  // Use AuthContainer with production providers
  const container = AuthContainer.createDefault(config);

  // Build backward-compatible services object
  return buildAuthServices(container);
}

/**
 * Build AuthServices object from container
 *
 * Extracts services from container into the legacy format.
 */
function buildAuthServices(container: AuthContainer): AuthServices {
  const backchannelHandler = container.getBackchannelHandler();

  return {
    auth: container.getAuth(),
    db: container.getDb(),
    redis: container.getRedis(),
    jtiBlacklist: container.getJtiBlacklist(),
    handleBackchannelLogout: backchannelHandler
      ? (token: string) => backchannelHandler.handle(token)
      : null,
    env: container.getEnvConfig(),
  };
}

// =============================================================================
// ADDITIONAL EXPORTS
// =============================================================================

/**
 * Get AuthContainer instance directly
 *
 * For advanced usage where full container access is needed.
 *
 * @param config - Application auth configuration
 * @returns AuthContainer instance
 */
export function getAuthContainer(config: AppAuthConfig): AuthContainer {
  return AuthContainer.createDefault(config);
}

/**
 * Reset all cached containers
 *
 * Use in tests to ensure clean state between test runs.
 */
export function resetAuthContainers(): void {
  AuthContainer.resetAll();
}

// Re-export container for advanced usage
export { AuthContainer };

// Re-export auth type
export type Auth = AuthServices['auth'];
