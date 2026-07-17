'use client';

/**
 * Protected Route Component (Better Auth)
 *
 * Guards routes that require authentication.
 * Redirects unauthenticated users to login.
 *
 * Uses Better Auth session instead of Keycloak.
 */

import { useEffect, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useSession } from '@/lib/auth/client';

export interface ProtectedRouteProps {
  children: ReactNode;
  /** Required roles (user must have at least one) - for future use */
  roles?: string[];
  /** Component to show while loading */
  loadingComponent?: ReactNode;
  /** Fallback redirect URL */
  redirectUrl?: string;
}

/**
 * Default loading component
 */
function DefaultLoadingComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-500 border-t-transparent" />
    </div>
  );
}

/**
 * ProtectedRoute guards routes requiring authentication.
 *
 * Uses Better Auth session to determine if user is authenticated.
 * Redirects to login if not authenticated.
 */
export function ProtectedRoute({
  children,
  roles,
  loadingComponent,
  redirectUrl = '/login',
}: ProtectedRouteProps) {
  const router = useRouter();
  const { data: session, isPending } = useSession();

  // Read the user's roles from the Better Auth session (seeded from the Keycloak
  // realm_access.roles claim). Normalised to upper-case for comparison.
  const userRoles = ((session?.user as { roles?: string[] } | undefined)?.roles ?? [])
    .map((r) => String(r).toUpperCase());
  const hasRequiredRole =
    !roles || roles.length === 0 || roles.some((r) => userRoles.includes(r.toUpperCase()));

  // Redirect to login if not authenticated, or /unauthorized if missing the role
  useEffect(() => {
    if (isPending) return;

    if (!session?.user) {
      // Store current URL for redirect after login
      const currentPath = window.location.pathname;
      const loginUrl = currentPath !== '/'
        ? `${redirectUrl}?callbackUrl=${encodeURIComponent(currentPath)}`
        : redirectUrl;

      router.push(loginUrl);
      return;
    }

    if (!hasRequiredRole) {
      router.replace('/unauthorized');
    }
  }, [isPending, session, router, redirectUrl, hasRequiredRole]);

  // Show loading state
  if (isPending) {
    return <>{loadingComponent || <DefaultLoadingComponent />}</>;
  }

  // Show loading while redirecting (unauthenticated or missing the required role)
  if (!session?.user || !hasRequiredRole) {
    return <>{loadingComponent || <DefaultLoadingComponent />}</>;
  }

  // User is authenticated and authorized
  return <>{children}</>;
}

export default ProtectedRoute;
