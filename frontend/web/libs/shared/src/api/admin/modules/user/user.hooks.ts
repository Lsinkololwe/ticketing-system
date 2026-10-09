'use client';

/**
 * Admin users list.
 *
 * <h2>Counts come from the page, not a second query</h2>
 * `pageInfo.totalCount` is the filtered total, so the stat tiles above the table
 * are computed by re-querying with a role filter rather than counting the
 * current page — a page of 20 cannot tell you how many customers exist.
 */

import { useQuery } from '@apollo/client/react';
import type { UserType, AccountStatus, AdminUsersQuery, AdminUsersQueryVariables } from '../../../../types/graphql';
import { ADMIN_USERS } from './user.queries';

/** One row of the admin users table — the `UserListFields` selection, not the full `User` entity. */
export type AdminUserRow = AdminUsersQuery['users']['content'][number];

export interface UseAdminUsersOptions {
  search?: string;
  role?: UserType | null;
  accountStatus?: AccountStatus | null;
  page?: number;
  size?: number;
}

export interface UseAdminUsersResult {
  users: AdminUserRow[];
  totalCount: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminUsers(options: UseAdminUsersOptions = {}): UseAdminUsersResult {
  const size = options.size ?? 20;
  const { data, loading, error, refetch } = useQuery<AdminUsersQuery, AdminUsersQueryVariables>(ADMIN_USERS, {
    variables: {
      search: options.search || null,
      role: options.role ?? null,
      accountStatus: options.accountStatus ?? null,
      pagination: {
        page: options.page ?? 0,
        size,
        sortBy: 'createdAt',
        sortDirection: 'DESC',
      },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.users;
  const total = page?.pageInfo?.totalCount ?? 0;
  const pageSize = page?.pageInfo?.pageSize ?? size;

  return {
    users: page?.content ?? [],
    totalCount: total,
    totalPages: pageSize > 0 ? Math.ceil(total / pageSize) : 0,
    currentPage: page?.pageInfo?.currentPage ?? 0,
    pageSize,
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}
