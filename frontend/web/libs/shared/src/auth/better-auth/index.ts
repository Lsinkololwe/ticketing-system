/**
 * Better Auth Module Exports
 *
 * Provides shared Better Auth configuration for this project.
 * For official Better Auth utilities, import directly from official packages.
 *
 * ## What This Module Provides
 *
 * - `getBetterAuth` / `createBetterAuth` - Shared server configuration with MongoDB + Redis
 * - `JtiBlacklistService` - JTI blacklist for backchannel logout support
 * - `createBackchannelLogoutHandler` - Handler for Keycloak backchannel logout
 * - Type definitions specific to this project (`AuthUser`, `SessionResponse`, etc.)
 *
 * ## Server Configuration
 * ```typescript
 * import { getBetterAuth } from '@pml.tickets/shared/auth/better-auth';
 *
 * // Get complete result with auth, jtiBlacklist, and backchannelLogout handler
 * export const authResultPromise = getBetterAuth({
 *   appId: 'admin',
 *   cookiePrefix: 'pml_admin',
 *   redisKeyPrefix: 'pml-admin:',
 * });
 *
 * // Get just the auth instance
 * export const authPromise = authResultPromise.then(r => r.auth);
 * ```
 *
 * ## Backchannel Logout Route Handler
 * ```typescript
 * import { authResultPromise } from "@/lib/auth";
 *
 * export async function POST(request: Request) {
 *   const { handleBackchannelLogout } = await authResultPromise;
 *   if (!handleBackchannelLogout) {
 *     return new Response('Backchannel logout not configured', { status: 501 });
 *   }
 *
 *   const formData = await request.formData();
 *   const logoutToken = formData.get('logout_token') as string;
 *   const result = await handleBackchannelLogout(logoutToken);
 *
 *   return new Response(null, { status: result.success ? 200 : 400 });
 * }
 * ```
 *
 * ## Server Components (Official Pattern)
 * ```typescript
 * import { auth } from "@/lib/auth";
 * import { headers } from "next/headers";  // Direct from Next.js
 * import { redirect } from "next/navigation";  // Direct from Next.js
 *
 * const session = await auth.api.getSession({
 *   headers: await headers()
 * });
 *
 * if (!session) redirect("/login");
 * ```
 *
 * ## Client Components (Official Pattern)
 * ```typescript
 * import { createAuthClient } from 'better-auth/react';  // Direct from Better Auth
 * import { genericOAuthClient } from 'better-auth/client/plugins';  // Direct from Better Auth
 *
 * const authClient = createAuthClient({
 *   baseURL: APP_URL,
 *   plugins: [genericOAuthClient()],
 * });
 *
 * await authClient.signIn.oauth2({ providerId: 'keycloak', callbackURL: '/dashboard' });
 * const { data: session } = authClient.useSession();
 * ```
 *
 * ## Middleware (Official Pattern)
 * ```typescript
 * import { getSessionCookie } from 'better-auth/cookies';  // Direct from Better Auth
 *
 * export async function middleware(request: NextRequest) {
 *   const sessionCookie = getSessionCookie(request);
 *   if (!sessionCookie) return NextResponse.redirect(new URL("/login", request.url));
 *   return NextResponse.next();
 * }
 * ```
 *
 * ## Route Handler (Official Pattern)
 * ```typescript
 * import { toNextJsHandler } from 'better-auth/next-js';  // Direct from Better Auth
 * import { authPromise } from "@/lib/auth";
 *
 * const handler = authPromise.then((auth) => toNextJsHandler(auth));
 * export const GET = async (req: Request) => (await handler).GET(req);
 * export const POST = async (req: Request) => (await handler).POST(req);
 * ```
 *
 * @see https://better-auth.com/docs/integrations/next
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 */

// =============================================================================
// TYPES (Project-specific)
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

// =============================================================================
// SERVER CONFIGURATION (Project-specific)
// =============================================================================

export {
  createBetterAuth,
  getBetterAuth,
  getBetterAuthInstance,
  validateEnv,
  type BetterAuthInstance,
  type BetterAuthResult,
} from './config';

// =============================================================================
// JTI BLACKLIST (Backchannel Logout Support)
// =============================================================================

export {
  createJtiBlacklist,
  type JtiBlacklistService,
  type JtiBlacklistConfig,
  type BlacklistEntry,
} from './jti-blacklist';

// =============================================================================
// BACKCHANNEL LOGOUT HANDLER
// =============================================================================

export {
  createBackchannelLogoutHandler,
  decodeLogoutToken,
  extractJtiFromIdToken,
  type BackchannelLogoutConfig,
  type BackchannelLogoutResult,
  type LogoutTokenClaims,
} from './backchannel-logout';

