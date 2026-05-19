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

import { auth } from '@/lib/auth';
import { toNextJsHandler } from 'better-auth/next-js';

export const { GET, POST } = toNextJsHandler(auth);
