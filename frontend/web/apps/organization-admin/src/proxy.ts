/**
 * Next.js 16 Proxy for Organization Admin App
 *
 * SECURITY MODEL (Industry Standard 3-Layer):
 *
 * Layer 1: Proxy (THIS FILE) - UX Optimization Only
 * ─────────────────────────────────────────────────
 * - Optimistic redirects based on session cookie PRESENCE
 * - NOT SECURE - cookies can be forged
 * - Purpose: Better UX with fast redirects
 *
 * Layer 2: Server Components/Layouts - Defense in Depth
 * ──────────────────────────────────────────────────────
 * - Validates session via auth.api.getSession()
 * - Checks organization status from database
 * - Redirects unauthorized users
 *
 * Layer 3: Backend API - Source of Truth (SECURE)
 * ────────────────────────────────────────────────
 * - @PreAuthorize annotations on GraphQL resolvers
 * - JWT validation + role-based access control
 * - Returns only authorized data
 *
 * @see https://nextjs.org/docs/app/api-reference/file-conventions/proxy
 * @see https://better-auth.com/docs/integrations/next
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSessionCookie } from 'better-auth/cookies';

// =============================================================================
// COOKIE CONFIGURATION
// =============================================================================

/**
 * Cookie prefix used by this app's Better Auth configuration
 * MUST match the cookiePrefix in lib/auth/index.ts
 */
const COOKIE_PREFIX = 'pml_org';

// =============================================================================
// ROUTE CONFIGURATION
// =============================================================================

/**
 * Routes that require authentication
 * Actual authorization is enforced in layouts and backend
 */
const PROTECTED_ROUTES = [
  '/dashboard',
  '/events',
  '/tickets',
  '/analytics',
  '/team',
  '/settings',
  '/payouts',
  '/finance',
  '/welcome',
  '/apply',
];

/**
 * Public routes that don't require authentication
 * Includes landing page and public marketing pages
 */
const PUBLIC_ROUTES = ['/login', '/register', '/api/auth', '/features'];

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Check if the path starts with any of the given prefixes
 */
function pathStartsWith(path: string, prefixes: string[]): boolean {
  return prefixes.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));
}

// =============================================================================
// PROXY FUNCTION
// =============================================================================

/**
 * Optimistic authentication proxy
 *
 * IMPORTANT: This only checks if a session cookie EXISTS.
 * It does NOT validate the session - that happens in:
 * 1. Server Components via auth.api.getSession()
 * 2. Backend via @PreAuthorize annotations
 *
 * This is the recommended pattern per Better Auth docs:
 * "This is NOT secure! This is the recommended approach to optimistically
 * redirect users. We recommend handling auth checks in each page/route."
 */
export default async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Skip proxy for static files
  if (
    pathname.startsWith('/_next') ||
    pathname.startsWith('/favicon') ||
    pathname.includes('.')
  ) {
    return NextResponse.next();
  }

  // Get session cookie (optimistic check - NOT SECURE)
  // This just checks if the cookie exists, not if it's valid
  // IMPORTANT: Must pass cookiePrefix to match the Better Auth configuration
  const sessionCookie = getSessionCookie(request, {
    cookiePrefix: COOKIE_PREFIX,
  });
  const hasSessionCookie = !!sessionCookie;

  // -----------------------------------------------------------------------------
  // Public routes - redirect authenticated users to welcome (org check happens there)
  // -----------------------------------------------------------------------------
  if (pathStartsWith(pathname, PUBLIC_ROUTES)) {
    if (hasSessionCookie && pathname === '/login') {
      // Has session cookie - redirect to welcome for server-side org check
      return NextResponse.redirect(new URL('/welcome', request.url));
    }
    return NextResponse.next();
  }

  // -----------------------------------------------------------------------------
  // Protected routes - redirect to logout if no session cookie
  // -----------------------------------------------------------------------------
  // WHY /logout instead of /login:
  // - If no Better Auth cookie but Keycloak SSO is active, /login auto-logs in
  // - /logout ensures Keycloak SSO is also cleared before showing login
  // - Clean slate: both Better Auth AND Keycloak sessions are terminated
  if (pathStartsWith(pathname, PROTECTED_ROUTES)) {
    if (!hasSessionCookie) {
      return NextResponse.redirect(new URL('/logout', request.url));
    }
    // Has session cookie - let through, server component will validate
    return NextResponse.next();
  }

  // -----------------------------------------------------------------------------
  // Root path - Show landing page (public), redirect authenticated users to welcome
  // -----------------------------------------------------------------------------
  if (pathname === '/') {
    if (hasSessionCookie) {
      // Has session - redirect to welcome for server-side org check
      return NextResponse.redirect(new URL('/welcome', request.url));
    }
    // No session - show public landing page
    return NextResponse.next();
  }

  return NextResponse.next();
}

// =============================================================================
// PROXY CONFIGURATION
// =============================================================================

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
};
