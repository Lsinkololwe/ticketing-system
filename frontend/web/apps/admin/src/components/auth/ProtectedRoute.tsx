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
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-violet-500 border-t-transparent" />
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

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!isPending && !session?.user) {
      // Store current URL for redirect after login
      const currentPath = window.location.pathname;
      const loginUrl = currentPath !== '/'
        ? `${redirectUrl}?callbackUrl=${encodeURIComponent(currentPath)}`
        : redirectUrl;

      router.push(loginUrl);
    }
  }, [isPending, session, router, redirectUrl]);

  // Show loading state
  if (isPending) {
    return <>{loadingComponent || <DefaultLoadingComponent />}</>;
  }

  // Show loading while redirecting
  if (!session?.user) {
    return <>{loadingComponent || <DefaultLoadingComponent />}</>;
  }

  // TODO: Implement role checking when roles are available in session
  // For now, just check if authenticated
  if (roles && roles.length > 0) {
    // Future: Check if user has required roles
    // const userRoles = session.user.roles || [];
    // const hasAccess = roles.some(role => userRoles.includes(role));
    // if (!hasAccess) { ... show access denied ... }
    console.log('[ProtectedRoute] Role checking not yet implemented, allowing authenticated user');
  }

  // User is authenticated
  return <>{children}</>;
}

export default ProtectedRoute;
