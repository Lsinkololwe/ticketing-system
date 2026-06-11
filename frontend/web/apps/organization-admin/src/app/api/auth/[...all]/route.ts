/**
 * Organization Admin App - Better Auth API Route Handler
 *
 * This is the main authentication API route that handles all Better Auth endpoints.
 *
 * ## Architecture Overview
 *
 * Better Auth uses a catch-all route (`[...all]`) to handle all auth operations.
 * The `toNextJsHandler` function creates handlers for all auth endpoints:
 *
 * ## Handled Endpoints
 *
 * | Method | Path | Description |
 * |--------|------|-------------|
 * | GET/POST | `/api/auth/signin/keycloak` | Initiate Keycloak OAuth flow |
 * | GET | `/api/auth/callback/keycloak` | Handle OAuth callback |
 * | POST | `/api/auth/signout` | Sign out (clear session) |
 * | GET | `/api/auth/get-session` | Get current session |
 * | GET | `/api/auth/session` | Alternative session endpoint |
 *
 * ## How It Works
 *
 * ```
 * Client Request → Next.js Route Handler
 *     ↓
 * await authPromise (get auth instance)
 *     ↓
 * toNextJsHandler(auth) → { GET, POST }
 *     ↓
 * Better Auth handles the request
 *     ↓
 * Response (session, redirect, etc.)
 * ```
 *
 * ## Why async auth initialization?
 *
 * The auth instance requires async setup:
 * 1. Connect to MongoDB (for session storage)
 * 2. Connect to Redis (for session caching)
 * 3. Fetch Keycloak OIDC discovery document
 *
 * We use `authPromise` to handle this initialization once.
 *
 * @see https://better-auth.com/docs/integrations/next
 * @module apps/organization-admin/src/app/api/auth/[...all]/route
 */

import { toNextJsHandler } from 'better-auth/next-js';
import { authPromise } from '@/lib/auth';

// =============================================================================
// ROUTE HANDLERS
// =============================================================================

/**
 * Create handler promise (resolved once on first request)
 *
 * This awaits the auth instance and creates the Next.js handlers.
 * The promise is created at module load time but only resolved
 * when the first request arrives.
 *
 * @used-by GET, POST handlers
 */
const authHandler = authPromise.then((auth) => toNextJsHandler(auth));

/**
 * GET handler for Better Auth endpoints
 *
 * Handles:
 * - `/api/auth/callback/keycloak` - OAuth callback
 * - `/api/auth/get-session` - Get session
 * - `/api/auth/session` - Alternative session endpoint
 *
 * @param request - Incoming HTTP request
 * @returns Response from Better Auth
 *
 * @used-by
 * - OAuth callback after Keycloak login
 * - Client-side session fetching (authClient.getSession())
 * - useSession hook polling
 */
export const GET = async (request: Request) => {
  const { GET } = await authHandler;
  return GET(request);
};

/**
 * POST handler for Better Auth endpoints
 *
 * Handles:
 * - `/api/auth/signin/keycloak` - Initiate OAuth flow
 * - `/api/auth/signout` - Sign out
 *
 * @param request - Incoming HTTP request
 * @returns Response from Better Auth
 *
 * @used-by
 * - Login button (authClient.signIn.oauth2())
 * - Logout button (authClient.signOut())
 */
export const POST = async (request: Request) => {
  const { POST } = await authHandler;
  return POST(request);
};
