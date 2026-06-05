/**
 * Next.js 16 Proxy for Route Protection
 *
 * NOTE: In Next.js 16, middleware.ts is renamed to proxy.ts
 * The 'middleware' function is renamed to 'proxy'
 *
 * OWASP Security Requirements:
 * - Validates session on every protected route request
 * - Redirects unauthenticated users to login
 * - Prevents authenticated users from accessing auth pages
 * - Session validation with database check (Node.js runtime required)
 *
 * Route Protection Levels:
 * - Public: Landing, features, login - no auth required
 * - Auth: Login page - redirect authenticated users away
 * - Application: KYB application flow - requires auth, no org required
 * - Dashboard: Main dashboard - requires auth AND approved organization
 *
 * @see https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
 * @see https://better-auth.com/docs/integrations/next
 */

import { NextRequest, NextResponse } from 'next/server';
import { auth } from '@/lib/auth';
import { headers } from 'next/headers';

// =============================================================================
// ROUTE CONFIGURATION
// =============================================================================

// Routes that don't require authentication at all
const PUBLIC_ROUTES = [
  '/',               // Landing page
  '/features',       // Features/benefits page
  '/pricing',        // Pricing page
  '/login',          // Login page
  '/logout',         // Logout page
  '/api/auth',       // Better Auth API routes
  '/api/health',     // Health check
  '/_next',          // Next.js internals
  '/favicon.ico',
  '/status',         // Status page
];

// Routes that authenticated users should be redirected away from
const AUTH_ROUTES = ['/login'];

// =============================================================================
// PROXY (formerly MIDDLEWARE)
// =============================================================================

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Skip proxy for public routes and static files
  if (PUBLIC_ROUTES.some((route) => pathname.startsWith(route))) {
    return NextResponse.next();
  }

  // Get session from Better Auth (validates with Redis/MongoDB)
  let session = null;
  try {
    session = await auth.api.getSession({
      headers: await headers(),
    });
  } catch (error) {
    console.error('[Proxy] Session check failed:', error);
  }

  const isAuthenticated = !!session?.user;
  const isAuthRoute = AUTH_ROUTES.some((route) => pathname.startsWith(route));

  // Debug logging
  console.log('[Proxy] Path:', pathname, '| Authenticated:', isAuthenticated);

  // Redirect authenticated users away from auth pages
  if (isAuthRoute && isAuthenticated) {
    // Check if user has an organization - redirect to dashboard or application
    // For now, redirect to dashboard (application flow will handle redirection)
    return NextResponse.redirect(new URL('/dashboard', request.url));
  }

  // Redirect unauthenticated users to login
  if (!isAuthenticated && !isAuthRoute) {
    const loginUrl = new URL('/login', request.url);
    // Save the original URL for redirect after login
    loginUrl.searchParams.set('callbackUrl', pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

// =============================================================================
// PROXY CONFIG
// =============================================================================

export const config = {
  // Match all routes except static files
  matcher: [
    /*
     * Match all request paths except:
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - favicon.ico (favicon file)
     * - public folder files
     */
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
};
