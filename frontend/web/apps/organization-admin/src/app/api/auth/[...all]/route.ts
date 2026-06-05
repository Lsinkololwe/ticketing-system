/**
 * Better Auth API Route Handler
 *
 * Handles all authentication endpoints:
 * - /api/auth/signin/keycloak - Initiate Keycloak OAuth flow
 * - /api/auth/callback/keycloak - OAuth callback
 * - /api/auth/signout - Sign out
 * - /api/auth/get-session - Get session
 *
 * @see https://better-auth.com/docs/integrations/next
 */

import { authPromise } from '@/lib/auth';
import { toNextJsHandler } from 'better-auth/next-js';

// Create handlers that await the auth instance
const getHandler = async (request: Request) => {
  const url = new URL(request.url);
  const cookieHeader = request.headers.get('cookie');

  // Debug logging for session requests
  if (url.pathname.includes('session')) {
    console.log('[Auth API] GET request:', {
      path: url.pathname,
      hasCookies: !!cookieHeader,
      cookies: cookieHeader?.split(';').map(c => c.trim().split('=')[0]),
    });
  }

  const auth = await authPromise;
  const { GET } = toNextJsHandler(auth);
  const response = await GET(request);

  // Log session response
  if (url.pathname.includes('session')) {
    const clonedResponse = response.clone();
    try {
      const body = await clonedResponse.json();
      console.log('[Auth API] Session response:', {
        status: response.status,
        hasSession: !!body?.session,
        hasUser: !!body?.user,
        userId: body?.user?.id,
      });
    } catch {
      console.log('[Auth API] Session response (non-JSON):', response.status);
    }
  }

  return response;
};

const postHandler = async (request: Request) => {
  const auth = await authPromise;
  const { POST } = toNextJsHandler(auth);
  return POST(request);
};

export { getHandler as GET, postHandler as POST };
