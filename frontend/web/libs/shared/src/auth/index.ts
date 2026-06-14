/**
 * Auth Module Exports
 *
 * Provides authentication utilities for the ticketing system.
 *
 * ## Better Auth (Recommended for Next.js Apps)
 *
 * Better Auth handles session management with Keycloak as OAuth provider.
 *
 * ### Client Components
 *
 * Use official Better Auth React client directly:
 *
 * ```typescript
 * import { createAuthClient } from 'better-auth/react';
 *
 * const authClient = createAuthClient({
 *   baseURL: process.env.NEXT_PUBLIC_APP_URL,
 * });
 *
 * // Hooks
 * const { data: session } = authClient.useSession();
 * await authClient.signOut();
 * await authClient.signIn.oauth2({ providerId: 'keycloak' });
 * ```
 *
 * ### Server Components
 *
 * Import server-only code from the server module:
 *
 * ```typescript
 * // In app's lib/auth/index.ts
 * import { getBetterAuth } from '@pml.tickets/shared/auth/better-auth/server';
 *
 * export const authResultPromise = getBetterAuth({
 *   appId: 'admin',
 *   cookiePrefix: 'pml_admin',
 *   redisKeyPrefix: 'pml-admin:',
 * });
 *
 * export const authPromise = authResultPromise.then(r => r.auth);
 * ```
 *
 * ### Middleware/Proxy
 *
 * Use official Better Auth cookie utilities:
 *
 * ```typescript
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
 * ### Route Handler
 *
 * ```typescript
 * import { toNextJsHandler } from 'better-auth/next-js';
 * import { authPromise } from '@/lib/auth';
 *
 * const handler = authPromise.then(auth => toNextJsHandler(auth));
 * export const GET = async (req: Request) => (await handler).GET(req);
 * export const POST = async (req: Request) => (await handler).POST(req);
 * ```
 *
 * ## Keycloak Direct (Legacy/Mobile Apps)
 *
 * Direct keycloak-js integration for client-side token management.
 *
 * @see https://better-auth.com/docs/integrations/next
 */

// =============================================================================
// BETTER AUTH (Types Only - Client Safe)
// =============================================================================

// Re-export only client-safe types from better-auth
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
} from './better-auth';

export { getKeycloakEndpoints } from './better-auth';

// =============================================================================
// KEYCLOAK DIRECT (Legacy)
// =============================================================================

// Configuration
export {
  getKeycloakConfig,
  requireKeycloakConfig,
  getKeycloakEndpoints as getKeycloakDirectEndpoints,
  KEYCLOAK_CLIENTS,
  KEYCLOAK_ROLES,
  DEFAULT_SCOPES,
  ADMIN_SCOPES,
  TOKEN_CONFIG,
  type KeycloakConfig,
  type KeycloakRole,
} from './keycloak-config';

// Provider and hooks
export {
  KeycloakProvider,
  useKeycloak,
  useAuth,
  type KeycloakUser,
  type KeycloakContextValue,
  type KeycloakProviderProps,
  type KeycloakInitOptions,
} from './keycloak-provider';

// Protected routes
export {
  ProtectedRoute,
  withAuth,
  useCanAccessByRole,
  type ProtectedRouteProps,
} from './protected-route';
