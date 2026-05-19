'use client';

/**
 * Protected Route Component
 *
 * Guards routes that require authentication or specific roles.
 * Redirects unauthenticated users to login or shows access denied.
 */

import { useEffect, type ReactNode } from 'react';
import { useKeycloak } from './keycloak-provider';
import type { KeycloakRole } from './keycloak-config';

export interface ProtectedRouteProps {
  children: ReactNode;
  /** Required roles (user must have at least one) */
  roles?: (KeycloakRole | string)[];
  /** Require all roles instead of any */
  requireAllRoles?: boolean;
  /** Component to show while loading */
  loadingComponent?: ReactNode;
  /** Component to show when access is denied (no roles) */
  accessDeniedComponent?: ReactNode;
  /** Component to show when not authenticated */
  unauthenticatedComponent?: ReactNode;
  /** Auto-redirect to login when not authenticated (default: true) */
  autoLogin?: boolean;
  /** Callback when access is denied */
  onAccessDenied?: () => void;
  /** Fallback redirect URL after login */
  redirectUri?: string;
}

/**
 * Default loading component
 */
function DefaultLoadingComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
    </div>
  );
}

/**
 * Default access denied component
 */
function DefaultAccessDeniedComponent() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4">
      <h1 className="text-2xl font-bold text-red-600">Access Denied</h1>
      <p className="text-gray-600">
        You do not have permission to access this page.
      </p>
    </div>
  );
}

/**
 * Default unauthenticated component
 */
function DefaultUnauthenticatedComponent() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4">
      <h1 className="text-2xl font-bold">Authentication Required</h1>
      <p className="text-gray-600">Please log in to access this page.</p>
    </div>
  );
}

export function ProtectedRoute({
  children,
  roles,
  requireAllRoles = false,
  loadingComponent,
  accessDeniedComponent,
  unauthenticatedComponent,
  autoLogin = true,
  onAccessDenied,
  redirectUri,
}: ProtectedRouteProps) {
  const { authenticated, loading, initialized, login, hasRole, hasAnyRole } =
    useKeycloak();

  // Check if user has required roles
  const hasAccess = (): boolean => {
    if (!roles || roles.length === 0) {
      // No roles required, just authentication
      return true;
    }

    if (requireAllRoles) {
      return roles.every((role) => hasRole(role));
    }

    return hasAnyRole(roles);
  };

  // Auto-login effect
  useEffect(() => {
    if (initialized && !loading && !authenticated && autoLogin) {
      login({
        redirectUri:
          redirectUri ||
          (typeof window !== 'undefined' ? window.location.href : undefined),
      });
    }
  }, [initialized, loading, authenticated, autoLogin, login, redirectUri]);

  // Access denied effect
  useEffect(() => {
    if (initialized && authenticated && !hasAccess()) {
      onAccessDenied?.();
    }
  }, [initialized, authenticated, onAccessDenied]);

  // Loading state
  if (loading || !initialized) {
    return <>{loadingComponent || <DefaultLoadingComponent />}</>;
  }

  // Not authenticated
  if (!authenticated) {
    if (autoLogin) {
      // Show loading while redirecting to login
      return <>{loadingComponent || <DefaultLoadingComponent />}</>;
    }
    return <>{unauthenticatedComponent || <DefaultUnauthenticatedComponent />}</>;
  }

  // Check roles
  if (!hasAccess()) {
    return <>{accessDeniedComponent || <DefaultAccessDeniedComponent />}</>;
  }

  // Render children
  return <>{children}</>;
}

/**
 * HOC to protect a component with authentication
 */
export function withAuth<P extends object>(
  Component: React.ComponentType<P>,
  options?: Omit<ProtectedRouteProps, 'children'>
) {
  return function AuthenticatedComponent(props: P) {
    return (
      <ProtectedRoute {...options}>
        <Component {...props} />
      </ProtectedRoute>
    );
  };
}

/**
 * Hook to check if user can access based on roles
 *
 * @param roles - Required roles (user must have at least one)
 * @param requireAll - Require all roles instead of any
 * @returns Object with canAccess boolean and isLoading state
 */
export function useCanAccessByRole(
  roles: (KeycloakRole | string)[],
  requireAll = false
): { canAccess: boolean; isLoading: boolean } {
  const { authenticated, loading, initialized, hasRole, hasAnyRole } =
    useKeycloak();

  if (loading || !initialized) {
    return { canAccess: false, isLoading: true };
  }

  if (!authenticated) {
    return { canAccess: false, isLoading: false };
  }

  if (!roles || roles.length === 0) {
    return { canAccess: true, isLoading: false };
  }

  const canAccess = requireAll
    ? roles.every((role) => hasRole(role))
    : hasAnyRole(roles);

  return { canAccess, isLoading: false };
}

