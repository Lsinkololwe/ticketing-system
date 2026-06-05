'use client';

/**
 * Protected Route Component
 *
 * OWASP Security:
 * - Validates session on client-side before rendering protected content
 * - Redirects unauthenticated users to login
 * - Preserves original URL for post-login redirect
 * - Shows loading state while checking authentication
 *
 * Note: This is a client-side guard. Server-side protection
 * is handled by middleware.ts for defense-in-depth.
 */

import { useEffect, ReactNode, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Flex, Spinner, Text } from '@radix-ui/themes';
import { useSession, signInWithKeycloak } from '@/lib/auth/client';
import type { OrganizationRole } from '@/config/navigation';

interface ProtectedRouteProps {
  children: ReactNode;
  /** Required roles to access this route (if not specified, any authenticated user can access) */
  roles?: OrganizationRole[];
  /** Custom loading component */
  loadingComponent?: ReactNode;
  /** If true, redirect to Keycloak directly instead of showing login page */
  directKeycloakRedirect?: boolean;
}

/**
 * Default loading component
 */
function DefaultLoadingComponent() {
  return (
    <Flex
      align="center"
      justify="center"
      style={{ minHeight: '100vh' }}
    >
      <Flex direction="column" align="center" gap="3">
        <Spinner size="3" />
        <Text color="gray" size="2">Loading...</Text>
      </Flex>
    </Flex>
  );
}

/**
 * Protected Route HOC
 *
 * Wraps content that requires authentication.
 * Redirects to login if user is not authenticated.
 */
export function ProtectedRoute({
  children,
  roles,
  loadingComponent,
  directKeycloakRedirect = true,
}: ProtectedRouteProps) {
  const router = useRouter();
  const { data: session, isPending, error } = useSession();
  const hasRedirected = useRef(false);

  // Debug logging
  useEffect(() => {
    console.log('[ProtectedRoute] Session state:', {
      isPending,
      hasSession: !!session,
      hasUser: !!session?.user,
      userId: session?.user?.id,
      error: error?.message,
      sessionKeys: session ? Object.keys(session) : [],
    });
  }, [session, isPending, error]);

  useEffect(() => {
    // Wait for session check to complete
    if (isPending) return;

    // Prevent multiple redirects
    if (hasRedirected.current) return;

    // Redirect to Keycloak if not authenticated
    if (!session?.user) {
      hasRedirected.current = true;
      console.log('[ProtectedRoute] No user in session, redirecting to Keycloak');
      const currentPath = window.location.pathname;

      if (directKeycloakRedirect) {
        // Redirect directly to Keycloak for authentication
        signInWithKeycloak(currentPath).catch((err) => {
          console.error('[ProtectedRoute] Failed to redirect to Keycloak:', err);
          // Fallback to login page on error
          router.replace(`/login?callbackUrl=${encodeURIComponent(currentPath)}`);
        });
      } else {
        // Fallback to traditional login page
        router.replace(`/login?callbackUrl=${encodeURIComponent(currentPath)}`);
      }
      return;
    }

    // Role-based access control (if roles are specified)
    if (roles && roles.length > 0) {
      const userRoles = (session.user as { roles?: string[] }).roles || [];
      const hasRequiredRole = roles.some((role) => userRoles.includes(role));

      if (!hasRequiredRole) {
        // User doesn't have required role - redirect to dashboard or unauthorized page
        router.replace('/dashboard');
      }
    }
  }, [session, isPending, router, roles, directKeycloakRedirect]);

  // Show loading state while checking authentication
  if (isPending) {
    return loadingComponent || <DefaultLoadingComponent />;
  }

  // Don't render content if not authenticated (will redirect)
  if (!session?.user) {
    return loadingComponent || <DefaultLoadingComponent />;
  }

  // Check role access
  if (roles && roles.length > 0) {
    const userRoles = (session.user as { roles?: string[] }).roles || [];
    const hasRequiredRole = roles.some((role) => userRoles.includes(role));

    if (!hasRequiredRole) {
      return loadingComponent || <DefaultLoadingComponent />;
    }
  }

  // User is authenticated (and has required role if specified)
  return <>{children}</>;
}

/**
 * Hook to check if user has specific role
 */
export function useHasRole(role: OrganizationRole): boolean {
  const { data: session } = useSession();

  if (!session?.user) return false;

  const userRoles = (session.user as { roles?: string[] }).roles || [];
  return userRoles.includes(role);
}

/**
 * Hook to check if user has any of the specified roles
 */
export function useHasAnyRole(roles: OrganizationRole[]): boolean {
  const { data: session } = useSession();

  if (!session?.user) return false;

  const userRoles = (session.user as { roles?: string[] }).roles || [];
  return roles.some((role) => userRoles.includes(role));
}
