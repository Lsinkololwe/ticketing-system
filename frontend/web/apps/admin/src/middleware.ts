/**
 * Next.js Middleware for Route Protection
 *
 * OWASP Security Requirements:
 * - Validates session on every protected route request
 * - Redirects unauthenticated users to login
 * - Prevents authenticated users from accessing auth pages
 * - Session validation with database check (Node.js runtime required)
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

// Routes that don't require authentication
const PUBLIC_ROUTES = [
  '/login',
  '/api/auth', // Better Auth API routes
  '/api/health',
  '/_next',
  '/favicon.ico',
];

// Routes that authenticated users should be redirected away from
const AUTH_ROUTES = ['/login'];

// =============================================================================
// MIDDLEWARE
// =============================================================================

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Skip middleware for public routes
  if (PUBLIC_ROUTES.some((route) => pathname.startsWith(route))) {
    return NextResponse.next();
  }

  // Get session from Better Auth (validates with Redis)
  const session = await auth.api.getSession({
    headers: await headers(),
  });

  const isAuthenticated = !!session?.user;
  const isAuthRoute = AUTH_ROUTES.some((route) => pathname.startsWith(route));

  // Redirect authenticated users away from auth pages
  if (isAuthRoute && isAuthenticated) {
    return NextResponse.redirect(new URL('/dashboard', request.url));
  }

  // Redirect unauthenticated users to login
  if (!isAuthenticated && !isAuthRoute) {
    const loginUrl = new URL('/login', request.url);
    // Save the original URL for redirect after login
    loginUrl.searchParams.set('callbackUrl', pathname);
    return NextResponse.redirect(loginUrl);
  }

  // Add security headers (OWASP recommendation)
  const response = NextResponse.next();

  // OWASP: Prevent clickjacking
  response.headers.set('X-Frame-Options', 'DENY');

  // OWASP: Prevent MIME type sniffing
  response.headers.set('X-Content-Type-Options', 'nosniff');

  // OWASP: Enable XSS filter
  response.headers.set('X-XSS-Protection', '1; mode=block');

  // OWASP: Referrer policy for privacy
  response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');

  return response;
}

// =============================================================================
// MIDDLEWARE CONFIG
// =============================================================================

export const config = {
  // Node.js runtime required for auth.api.getSession()
  runtime: 'nodejs',

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
