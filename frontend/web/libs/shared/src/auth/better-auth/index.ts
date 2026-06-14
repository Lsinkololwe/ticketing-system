/**
 * Better Auth Module Exports (Client-Safe)
 *
 * This module exports ONLY client-safe types and utilities.
 * For server-side configuration, import from './server' instead.
 *
 * ## Client Usage (Types Only)
 *
 * ```typescript
 * import type { AuthUser, SessionResponse, AppId } from '@pml.tickets/shared/auth/better-auth';
 * ```
 *
 * ## Server Usage (Configuration)
 *
 * ```typescript
 * // Import server-only code from the server module
 * import { getBetterAuth } from '@pml.tickets/shared/auth/better-auth/server';
 *
 * export const authResultPromise = getBetterAuth({
 *   appId: 'admin',
 *   cookiePrefix: 'pml_admin',
 *   redisKeyPrefix: 'pml-admin:',
 * });
 * ```
 *
 * ## Client Components (Official Better Auth Pattern)
 *
 * ```typescript
 * // Import directly from better-auth/react
 * import { createAuthClient } from 'better-auth/react';
 *
 * const authClient = createAuthClient({
 *   baseURL: process.env.NEXT_PUBLIC_APP_URL,
 * });
 *
 * // Use hooks
 * const { data: session } = authClient.useSession();
 * await authClient.signOut();
 * ```
 *
 * ## Middleware/Proxy (Official Better Auth Pattern)
 *
 * ```typescript
 * // Import directly from better-auth/cookies
 * import { getSessionCookie, getCookieCache } from 'better-auth/cookies';
 *
 * export async function proxy(request: NextRequest) {
 *   const session = await getCookieCache(request);
 *   if (!session) {
 *     return NextResponse.redirect(new URL('/login', request.url));
 *   }
 *   return NextResponse.next();
 * }
 * ```
 *
 * @see https://better-auth.com/docs/integrations/next
 * @module libs/shared/src/auth/better-auth
 */

// =============================================================================
// TYPES (Client-Safe)
// =============================================================================

export type {
  AppId,
  AppAuthConfig,
  BetterAuthEnv,
  EnvValidationResult,
  AuthUser,
  AuthSession,
  SessionResponse,
  KeycloakEndpoints,
  BetterAuthOptions,
} from './types';

export { getKeycloakEndpoints } from './types';
