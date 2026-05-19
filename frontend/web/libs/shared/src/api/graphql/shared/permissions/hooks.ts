'use client';

/**
 * Permissions Hooks
 *
 * Hooks for checking user permissions.
 * Used by PermissionGate component.
 */

import { useQuery } from '@apollo/client/react';
import { gql } from '@apollo/client';

// Simple query to get current user's permissions
const GET_MY_PERMISSIONS = gql`
  query GetMyPermissions {
    myPermissions {
      permissions
      roles
    }
  }
`;

interface MyPermissionsResult {
  permissions: string[];
  roles: string[];
}

/**
 * Hook to fetch current user's permissions
 */
export function useMyPermissions() {
  const { data, loading, error, refetch } = useQuery<{
    myPermissions: MyPermissionsResult;
  }>(GET_MY_PERMISSIONS, {
    fetchPolicy: 'cache-first',
    errorPolicy: 'all',
  });

  const permissions = data?.myPermissions?.permissions || [];
  const roles = data?.myPermissions?.roles || [];

  const hasPermission = (permission: string): boolean => {
    return permissions.includes(permission);
  };

  const hasAnyPermission = (perms: string[]): boolean => {
    return perms.some((p) => permissions.includes(p));
  };

  const hasAllPermissions = (perms: string[]): boolean => {
    return perms.every((p) => permissions.includes(p));
  };

  return {
    permissions,
    roles,
    loading,
    isLoading: loading,
    error,
    refetch,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
  };
}
