/**
 * Server-Side Auth Utilities
 *
 * Provides authentication utilities for Server Components:
 * - getServerSession: Get session in Server Components
 * - requireAuth: Get session or redirect to login
 * - getAccessToken: Get access token for API calls
 *
 * @see https://better-auth.com/docs/integrations/next
 */

import { headers } from 'next/headers';
import { redirect } from 'next/navigation';
import { auth, type Session } from './index';

// =============================================================================
// SERVER UTILITIES
// =============================================================================

/**
 * Get the current session in a Server Component
 *
 * Usage:
 * ```tsx
 * export default async function Page() {
 *   const session = await getServerSession();
 *   if (!session) return <LoginPrompt />;
 *   return <Dashboard user={session.user} />;
 * }
 * ```
 */
export async function getServerSession(): Promise<Session | null> {
  try {
    const session = await auth.api.getSession({
      headers: await headers(),
    });
    return session;
  } catch (error) {
    console.error('[Auth] Failed to get session:', error);
    return null;
  }
}

/**
 * Get session or redirect to login
 *
 * Usage in protected pages:
 * ```tsx
 * export default async function ProtectedPage() {
 *   const session = await requireAuth();
 *   // session is guaranteed to exist here
 *   return <div>Welcome {session.user.name}</div>;
 * }
 * ```
 */
export async function requireAuth(callbackUrl?: string): Promise<NonNullable<Session>> {
  const session = await getServerSession();

  if (!session) {
    const loginUrl = callbackUrl
      ? `/login?callbackUrl=${encodeURIComponent(callbackUrl)}`
      : '/login';
    redirect(loginUrl);
  }

  return session;
}

/**
 * Get access token for API calls in Server Components
 *
 * Usage:
 * ```tsx
 * export default async function Page() {
 *   const token = await getAccessToken();
 *   const data = await fetch(API_URL, {
 *     headers: { Authorization: `Bearer ${token}` }
 *   });
 * }
 * ```
 */
export async function getAccessToken(): Promise<string | null> {
  const session = await getServerSession();

  if (!session?.session) {
    return null;
  }

  // The session contains the access token from Keycloak
  // Better Auth stores it in the session object
  return session.session.token || null;
}

/**
 * Check if user has a specific role
 *
 * Usage:
 * ```tsx
 * export default async function AdminPage() {
 *   const hasAccess = await hasRole('ADMIN');
 *   if (!hasAccess) redirect('/unauthorized');
 * }
 * ```
 */
export async function hasRole(role: string): Promise<boolean> {
  const session = await getServerSession();

  if (!session?.user) {
    return false;
  }

  // Check roles in user data (populated from Keycloak token)
  const userRoles = (session.user as { roles?: string[] }).roles || [];
  return userRoles.includes(role);
}

/**
 * Check if user has any of the specified roles
 */
export async function hasAnyRole(roles: string[]): Promise<boolean> {
  const session = await getServerSession();

  if (!session?.user) {
    return false;
  }

  const userRoles = (session.user as { roles?: string[] }).roles || [];
  return roles.some((role) => userRoles.includes(role));
}
