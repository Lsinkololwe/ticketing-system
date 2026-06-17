/**
 * Better Auth API Route Handler
 *
 * Handles all Better Auth endpoints using the official Next.js integration.
 *
 * @see https://better-auth.com/docs/integrations/next
 */

import { toNextJsHandler } from 'better-auth/next-js';
import { auth } from '@/lib/auth';

// Create handlers from auth instance (synchronous - no Promise needed)
export const { GET, POST } = toNextJsHandler(auth);
