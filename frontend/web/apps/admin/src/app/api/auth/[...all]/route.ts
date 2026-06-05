/**
 * Better Auth API Route Handler
 *
 * Handles all authentication endpoints:
 * - /api/auth/signin/keycloak - Initiate Keycloak OAuth flow
 * - /api/auth/callback/keycloak - OAuth callback
 * - /api/auth/signout - Sign out
 * - /api/auth/session - Get session
 *
 * @see https://better-auth.com/docs/integrations/next
 */

import { authPromise } from '@/lib/auth';
import { toNextJsHandler } from 'better-auth/next-js';

// Create handlers that await the auth instance
const getHandler = async (request: Request) => {
  const auth = await authPromise;
  const { GET } = toNextJsHandler(auth);
  return GET(request);
};

const postHandler = async (request: Request) => {
  const auth = await authPromise;
  const { POST } = toNextJsHandler(auth);
  return POST(request);
};

export { getHandler as GET, postHandler as POST };
