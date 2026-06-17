/**
 * Admin App - Better Auth API Route Handler
 *
 * Handles all Better Auth endpoints using the official Next.js integration.
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
 * @see https://better-auth.com/docs/integrations/next
 */

import { toNextJsHandler } from 'better-auth/next-js';
import { auth } from '@/lib/auth';

// Create handlers from auth instance (synchronous - no Promise needed)
export const { GET, POST } = toNextJsHandler(auth);
