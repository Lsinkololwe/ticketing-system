/**
 * Admin App - Next.js Middleware
 *
 * Provides route protection using Better Auth's getSessionCookie utility.
 *
 * ## Architecture Overview
 *
 * This middleware runs on EVERY request (matching the config pattern).
 * It uses cookie-based session checking which is:
 * - **CPU-only**: No database or Redis hits
 * - **Fast**: Sub-millisecond execution
 * - **Scalable**: No backend calls per request
 *
 * ## Flow
 *
 * ```
 * Request → Middleware
 *     ↓
 * Is public route? → YES → Allow request
 *     ↓ NO
 * Has session cookie? → NO → Redirect to /login
 *     ↓ YES
 * Is auth route (login)? → YES → Redirect to /dashboard
 *     ↓ NO
 * Add security headers → Allow request
 * ```
 *
 * ## Important Notes
 *
 * 1. **Cookie-based checking is NOT secure by itself**
 *    - Cookie presence only means "might be authenticated"
 *    - Actual auth validation happens in Server Components via auth.api.getSession()
 *    - This is an optimization, not a security boundary
 *
 * 2. **Cookie name must match server config**
 *    - Server uses `cookiePrefix: 'pml_admin'`
 *    - Cookie name is `pml_admin.session_token`
 *
 * @see https://better-auth.com/docs/integrations/next
 * @module apps/admin/src/middleware
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSessionCookie } from 'better-auth/cookies';

// =============================================================================
// ROUTE CONFIGURATION
// =============================================================================

/**
 * Routes that don't require authentication
 *
 * These routes are accessible without a session cookie.
 *
 * @used-by middleware - Checked first to skip auth
 */
const PUBLIC_ROUTES = [
  '/login',       // Login page
  '/api/auth',    // Better Auth API endpoints
  '/api/health',  // Health check endpoint
];

/**
 * Routes that authenticated users should be redirected away from
 *
 * If a user with a session cookie visits these, redirect to dashboard.
 *
 * @used-by middleware - Redirects logged-in users
 */
const AUTH_ROUTES = ['/login'];

/**
 * Where to redirect unauthenticated users
 *
 * @used-by middleware - Redirect target for protected routes
 */
const LOGIN_URL = '/login';

/**
 * Where to redirect authenticated users from auth pages
 *
 * @used-by middleware - Redirect target for /login when logged in
 */
const DASHBOARD_URL = '/dashboard';

/**
 * Session cookie name (must match server config)
 *
 * Server config: `cookiePrefix: 'pml_admin'`
 * Better Auth adds `.session_token` suffix
 *
 * @used-by getSessionCookie() - Cookie lookup
 */
const COOKIE_NAME = 'pml_admin.session_token';

// =============================================================================
// MIDDLEWARE (Official Better Auth Pattern)
// =============================================================================

/**
 * Next.js middleware function
 *
 * Runs on every request matching the config pattern.
 * Uses cookie-based checking for performance.
 *
 * @param request - Incoming Next.js request
 * @returns NextResponse - Continue, redirect, or modified response
 *
 * @used-by Next.js - Called automatically for matching routes
 */
export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // ==========================================================================
  // Step 1: Allow public routes without checking auth
  // ==========================================================================

  if (PUBLIC_ROUTES.some((route) => pathname.startsWith(route))) {
    return NextResponse.next();
  }

  // ==========================================================================
  // Step 2: Check for session cookie (CPU-only, no DB hit)
  // ==========================================================================

  /**
   * getSessionCookie extracts and validates the session cookie.
   * It does NOT verify with the database - that happens in Server Components.
   *
   * @see https://better-auth.com/docs/integrations/next
   */
  const sessionCookie = getSessionCookie(request, {
    cookieName: COOKIE_NAME,
  });

  const isAuthenticated = !!sessionCookie;
  const isAuthRoute = AUTH_ROUTES.some((route) => pathname.startsWith(route));

  // ==========================================================================
  // Step 3: Redirect authenticated users away from auth pages
  // ==========================================================================

  /**
   * If user has session cookie and is visiting /login,
   * redirect them to dashboard (they're already logged in).
   */
  if (isAuthRoute && isAuthenticated) {
    return NextResponse.redirect(new URL(DASHBOARD_URL, request.url));
  }

  // ==========================================================================
  // Step 4: Redirect unauthenticated users to login
  // ==========================================================================

  /**
   * If user has no session cookie and is visiting a protected route,
   * redirect them to login with callback URL.
   */
  if (!isAuthenticated && !isAuthRoute) {
    const url = new URL(LOGIN_URL, request.url);
    // Save original URL for redirect after login
    url.searchParams.set('callbackUrl', pathname);
    return NextResponse.redirect(url);
  }

  // ==========================================================================
  // Step 5: Add security headers (OWASP recommendations)
  // ==========================================================================

  const response = NextResponse.next();

  // Prevent clickjacking attacks
  response.headers.set('X-Frame-Options', 'DENY');

  // Prevent MIME type sniffing
  response.headers.set('X-Content-Type-Options', 'nosniff');

  // Enable browser XSS filter
  response.headers.set('X-XSS-Protection', '1; mode=block');

  // Control referrer information
  response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');

  return response;
}

// =============================================================================
// MIDDLEWARE CONFIG
// =============================================================================

/**
 * Next.js middleware configuration
 *
 * The matcher determines which routes the middleware runs on.
 * We exclude static files for performance.
 *
 * @used-by Next.js - Determines middleware scope
 */
export const config = {
  matcher: [
    /*
     * Match all request paths EXCEPT:
     * - _next/static (static files like JS/CSS)
     * - _next/image (optimized images)
     * - favicon.ico (browser favicon)
     * - Image files (svg, png, jpg, etc.)
     *
     * This regex is a negative lookahead that excludes these patterns.
     */
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
};
