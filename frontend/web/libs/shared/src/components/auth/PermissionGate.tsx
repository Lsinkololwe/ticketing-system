'use client';

/**
 * PermissionGate - Access Control Component
 *
 * A declarative component for conditionally rendering UI elements
 * based on user permissions. Integrates with TanStack Query for
 * efficient permission caching.
 *
 * Permissions use dot notation (e.g., "event.create", "user.view")
 * matching the backend MongoDB permission names.
 *
 * @example
 * ```tsx
 * // Single permission (use Permission constants or string literals)
 * <PermissionGate permission="event.create">
 *   <CreateEventButton />
 * </PermissionGate>
 *
 * // Multiple permissions (any)
 * <PermissionGate permissions={["event.create", "event.update"]}>
 *   <EventEditor />
 * </PermissionGate>
 *
 * // Multiple permissions (all required)
 * <PermissionGate permissions={["payout.view", "payout.approve"]} requireAll>
 *   <PayoutApprovalPanel />
 * </PermissionGate>
 *
 * // With fallback
 * <PermissionGate permission="system.config.view" fallback={<AccessDenied />}>
 *   <AdminDashboard />
 * </PermissionGate>
 *
 * // Role-based access
 * <PermissionGate role="SUPER_ADMIN">
 *   <SuperAdminPanel />
 * </PermissionGate>
 * ```
 */

import { memo, type ReactNode } from 'react';
import { useMyPermissions } from '../../api/graphql/shared/permissions/hooks';

// ============================================================================
// Types
// ============================================================================

export interface PermissionGateProps {
  /** Single permission to check (string matching backend permission name) */
  permission?: string;

  /** Multiple permissions to check (strings matching backend permission names) */
  permissions?: string[];

  /** If true, all permissions are required. If false (default), any permission grants access */
  requireAll?: boolean;

  /** Role to check (alternative to permission-based access) */
  role?: string;

  /** Any of these roles grants access */
  roles?: string[];

  /** Content to render if user has access */
  children: ReactNode;

  /** Content to render if user does NOT have access (optional) */
  fallback?: ReactNode;

  /** Content to render while loading permissions (optional) */
  loading?: ReactNode;

  /** If true, shows children while loading instead of loading state */
  showWhileLoading?: boolean;

  /** Called when access is denied (useful for analytics/logging) */
  onAccessDenied?: () => void;
}

// ============================================================================
// Main Component
// ============================================================================

/**
 * PermissionGate conditionally renders children based on user permissions.
 *
 * Features:
 * - Single or multiple permission checks
 * - Any/All permission modes
 * - Role-based access control
 * - Loading states
 * - Fallback content
 * - Memoized for performance
 */
export const PermissionGate = memo(function PermissionGate({
  permission,
  permissions,
  requireAll = false,
  role,
  roles,
  children,
  fallback = null,
  loading = null,
  showWhileLoading = false,
  onAccessDenied,
}: PermissionGateProps) {
  const {
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    isLoading,
    roles: userRoles,
  } = useMyPermissions();

  // Handle loading state
  if (isLoading) {
    if (showWhileLoading) {
      return <>{children}</>;
    }
    return <>{loading}</>;
  }

  // Check role-based access
  if (role || roles) {
    const hasRoleAccess = role
      ? userRoles.includes(role)
      : roles?.some((r) => userRoles.includes(r)) ?? false;

    if (!hasRoleAccess) {
      onAccessDenied?.();
      return <>{fallback}</>;
    }

    // If only role check was requested, grant access
    if (!permission && !permissions) {
      return <>{children}</>;
    }
  }

  // Check permission-based access
  let hasAccess = false;

  if (permission) {
    // Single permission check
    hasAccess = hasPermission(permission);
  } else if (permissions && permissions.length > 0) {
    // Multiple permissions check
    hasAccess = requireAll
      ? hasAllPermissions(permissions)
      : hasAnyPermission(permissions);
  } else if (!role && !roles) {
    // No permissions or roles specified - allow access
    hasAccess = true;
  }

  // Combined check: if both role and permissions are specified,
  // user needs both role access AND permission access
  if ((role || roles) && !hasAccess) {
    onAccessDenied?.();
    return <>{fallback}</>;
  }

  if (!hasAccess) {
    onAccessDenied?.();
    return <>{fallback}</>;
  }

  return <>{children}</>;
});

// ============================================================================
// Specialized Gate Components
// ============================================================================

/**
 * Gate for Super Admin only access
 */
export const SuperAdminGate = memo(function SuperAdminGate({
  children,
  fallback = null,
}: {
  children: ReactNode;
  fallback?: ReactNode;
}) {
  return (
    <PermissionGate role="SUPER_ADMIN" fallback={fallback}>
      {children}
    </PermissionGate>
  );
});

/**
 * Gate for any Admin access (ADMIN or SUPER_ADMIN)
 */
export const AdminGate = memo(function AdminGate({
  children,
  fallback = null,
}: {
  children: ReactNode;
  fallback?: ReactNode;
}) {
  return (
    <PermissionGate roles={['ADMIN', 'SUPER_ADMIN']} fallback={fallback}>
      {children}
    </PermissionGate>
  );
});

/**
 * Gate for Finance team access
 */
export const FinanceGate = memo(function FinanceGate({
  children,
  fallback = null,
}: {
  children: ReactNode;
  fallback?: ReactNode;
}) {
  return (
    <PermissionGate roles={['FINANCE', 'SUPER_ADMIN']} fallback={fallback}>
      {children}
    </PermissionGate>
  );
});

// ============================================================================
// Hook for Programmatic Access Control
// ============================================================================

/**
 * Hook to check access programmatically (for non-JSX use cases)
 *
 * @example
 * ```tsx
 * const { canAccess, checkPermission } = useAccessControl();
 *
 * // Using dot notation strings
 * const canCreateEvent = checkPermission("event.create");
 *
 * // Or using Permission constants
 * const canViewFinancials = checkPermission(Permission.FINANCIAL_VIEW);
 *
 * const handleClick = () => {
 *   if (!canAccess(["event.update"], true)) {
 *     showToast('You do not have permission to update events');
 *     return;
 *   }
 *   // proceed with update
 * };
 * ```
 */
export function useAccessControl() {
  const {
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    isLoading,
    roles,
  } = useMyPermissions();

  return {
    isLoading,

    /**
     * Check if user has a specific permission
     * @param permission Permission name string (e.g., "EVENT_CREATE")
     */
    checkPermission: hasPermission,

    /**
     * Check if user has access based on permissions
     * @param permissions Permission name strings to check
     * @param requireAll If true, all permissions required
     */
    canAccess: (permissions: string[], requireAll = false): boolean => {
      if (isLoading) return false;
      return requireAll
        ? hasAllPermissions(permissions)
        : hasAnyPermission(permissions);
    },

    /**
     * Check if user has a specific role
     */
    hasRole: (role: string): boolean => {
      if (isLoading) return false;
      return roles.includes(role);
    },

    /**
     * Check if user has any of the specified roles
     */
    hasAnyRole: (rolesToCheck: string[]): boolean => {
      if (isLoading) return false;
      return rolesToCheck.some((r) => roles.includes(r));
    },

    /**
     * Check if user is a super admin
     */
    isSuperAdmin: (): boolean => {
      if (isLoading) return false;
      return roles.includes('SUPER_ADMIN');
    },

    /**
     * Check if user is any type of admin
     */
    isAdmin: (): boolean => {
      if (isLoading) return false;
      return roles.some((r: string) => ['ADMIN', 'SUPER_ADMIN'].includes(r));
    },
  };
}

// ============================================================================
// Link Component with Permission Check
// ============================================================================

interface PermissionLinkProps extends PermissionGateProps {
  /** Link href */
  href: string;
  /** Link className */
  className?: string;
  /** onClick handler */
  onClick?: () => void;
}

/**
 * A link that only renders if user has required permissions.
 * Useful for navigation menus and sidebars.
 *
 * @example
 * ```tsx
 * // Using Permission constant (recommended)
 * <PermissionLink
 *   href="/admin/payouts"
 *   permission={Permission.PAYOUT_VIEW}
 * >
 *   Manage Payouts
 * </PermissionLink>
 *
 * // Or using string literal
 * <PermissionLink
 *   href="/admin/events"
 *   permission="event.view"
 * >
 *   View Events
 * </PermissionLink>
 * ```
 */
export const PermissionLink = memo(function PermissionLink({
  href,
  className,
  onClick,
  children,
  ...gateProps
}: PermissionLinkProps) {
  // We use a simple anchor tag here. In a real app, you might want to use
  // Next.js Link or your router's Link component
  return (
    <PermissionGate {...gateProps}>
      <a href={href} className={className} onClick={onClick}>
        {children}
      </a>
    </PermissionGate>
  );
});

export default PermissionGate;
