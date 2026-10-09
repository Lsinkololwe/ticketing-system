'use client';

/**
 * Hooks for the Users & organizations surface (list, detail, buyer history and
 * every staff action). Mutations return promises and refetch the active admin
 * queries so tables stay current.
 */
import type { IdentityAdminCreateUserMutation, IdentityAdminCreateUserMutationVariables, IdentityAdminOrgDocumentsQuery, IdentityAdminOrgDocumentsQueryVariables, IdentityAdminOrgEventsQuery, IdentityAdminOrgEventsQueryVariables, IdentityAdminOrgMembersQuery, IdentityAdminOrgMembersQueryVariables, IdentityAdminOrganizationQuery, IdentityAdminOrganizationQueryVariables, IdentityAdminOrganizationsQuery, IdentityAdminOrganizationsQueryVariables, IdentityAdminUserQuery, IdentityAdminUserQueryVariables, IdentityAdminUserRefundsQuery, IdentityAdminUserRefundsQueryVariables, IdentityAdminUserTicketsQuery, IdentityAdminUserTicketsQueryVariables, IdentityAdminUsersQuery, IdentityAdminUsersQueryVariables } from '../../../../types/graphql';
import { useCallback } from 'react';
import { useLazyQuery, useMutation, useQuery } from '@apollo/client/react';
import type {
  AdminOrgRecord, AdminOrgStatus,
  AdminAccountStatus, AdminUserRecord, AdminUserRole, AdminKybStatus,
} from './identity-admin.types';
import {
  BOOKING_USER_REFUNDS, BOOKING_USER_TICKETS, CATALOG_ORG_EVENTS, IDENTITY_ORGANIZATION, IDENTITY_ORGANIZATIONS,
  IDENTITY_ORG_DOCUMENTS, IDENTITY_ORG_MEMBERS, IDENTITY_USER, IDENTITY_USERS, IDENTITY_USER_BY_EMAIL, IDENTITY_USER_BY_PHONE,
} from './identity-admin.queries';
import {
  ACTIVATE_USER, CREATE_USER, DEACTIVATE_USER, LOCK_USER, SET_USER_ROLES, SUSPEND_ORG, SUSPEND_USER, SYNC_ALL_USERS, SYNC_USER,
  UNLOCK_USER, UNSUSPEND_ORG, UNSUSPEND_USER, UPDATE_ORG_STATUS, UPDATE_USER, VERIFY_PAYOUT_ACCOUNT,
} from './identity-admin.mutations';

interface ListResult<T> {
  rows: T[];
  total: number;
  pageSize: number;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}
const asError = (e: unknown) => (e as Error | undefined) ?? undefined;

/* ----------------------------------------------------------------- users */

export interface UseIdentityUsersOptions {
  search?: string;
  role?: AdminUserRole | null;
  accountStatus?: AdminAccountStatus | null;
  /** 0-based */
  page?: number;
  size?: number;
}

export function useIdentityUsers(o: UseIdentityUsersOptions = {}): ListResult<AdminUserRecord> {
  const size = o.size ?? 20;
  const { data, loading, error, refetch } = useQuery<IdentityAdminUsersQuery, IdentityAdminUsersQueryVariables>(IDENTITY_USERS, {
    variables: {
      search: o.search || null,
      role: o.role ?? null,
      accountStatus: o.accountStatus ?? null,
      pagination: { page: o.page ?? 0, size, sortBy: 'createdAt', sortDirection: 'DESC' },
    } as IdentityAdminUsersQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    rows: data?.users?.content ?? [],
    total: data?.users?.pageInfo?.totalCount ?? 0,
    pageSize: data?.users?.pageInfo?.pageSize ?? size,
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

export function useIdentityUser(id: string | null) {
  const { data, loading, error, refetch } = useQuery<IdentityAdminUserQuery, IdentityAdminUserQueryVariables>(IDENTITY_USER, {
    variables: { id: id ?? '' } as IdentityAdminUserQueryVariables,
    skip: !id,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { user: data?.user ?? null, loading, error: asError(error), refetch: () => void refetch() };
}

/** Buyer account lookup by email or phone number. */
export function useBuyerLookup() {
  const [byEmail, e] = useLazyQuery<{ userByEmail: AdminUserRecord | null }>(IDENTITY_USER_BY_EMAIL, { fetchPolicy: 'network-only' });
  const [byPhone, p] = useLazyQuery<{ userByPhone: AdminUserRecord | null }>(IDENTITY_USER_BY_PHONE, { fetchPolicy: 'network-only' });
  const lookup = useCallback(
    async (kind: 'email' | 'phone', value: string): Promise<AdminUserRecord | null> => {
      if (kind === 'email') {
        const r = await byEmail({ variables: { email: value.trim() } });
        return r.data?.userByEmail ?? null;
      }
      const r = await byPhone({ variables: { phoneNumber: value.trim() } });
      return r.data?.userByPhone ?? null;
    },
    [byEmail, byPhone]
  );
  return { lookup, loading: e.loading || p.loading };
}

export function useBuyerTickets(buyerId: string | null) {
  const { data, loading, error, refetch } = useQuery<IdentityAdminUserTicketsQuery, IdentityAdminUserTicketsQueryVariables>(BOOKING_USER_TICKETS, {
    variables: { buyerId: buyerId ?? '', pagination: { page: 0, size: 20, sortBy: 'purchaseDate', sortDirection: 'DESC' } } as IdentityAdminUserTicketsQueryVariables,
    skip: !buyerId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { tickets: data?.ticketsByBuyerOffsetPagination?.data ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

export function useBuyerRefunds(buyerId: string | null) {
  const { data, loading, error, refetch } = useQuery<IdentityAdminUserRefundsQuery, IdentityAdminUserRefundsQueryVariables>(BOOKING_USER_REFUNDS, {
    variables: { buyerId: buyerId ?? '', pagination: { page: 0, size: 20, sortBy: 'createdAt', sortDirection: 'DESC' } } as IdentityAdminUserRefundsQueryVariables,
    skip: !buyerId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { refunds: data?.refundRequestsByBuyer?.data ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

/** Every user mutation, refetching the active user queries afterwards. */
export function useUserAdminActions() {
  const opts = { errorPolicy: 'none' as const, refetchQueries: ['IdentityAdminUsers', 'IdentityAdminUser'], awaitRefetchQueries: false };
  const [create] = useMutation<IdentityAdminCreateUserMutation, IdentityAdminCreateUserMutationVariables>(CREATE_USER, opts);
  const [update] = useMutation(UPDATE_USER, opts);
  const [suspend] = useMutation(SUSPEND_USER, opts);
  const [unsuspend] = useMutation(UNSUSPEND_USER, opts);
  const [lock] = useMutation(LOCK_USER, opts);
  const [unlock] = useMutation(UNLOCK_USER, opts);
  const [activate] = useMutation(ACTIVATE_USER, opts);
  const [deactivate] = useMutation(DEACTIVATE_USER, opts);
  const [setRoles] = useMutation(SET_USER_ROLES, opts);
  const [sync] = useMutation(SYNC_USER, opts);
  const [syncAll] = useMutation(SYNC_ALL_USERS, opts);
  return {
    createUser: async (input: { email: string; firstName: string; lastName: string; password?: string; phoneNumber?: string; role?: AdminUserRole }) =>
      (await create({ variables: { input } as IdentityAdminCreateUserMutationVariables })).data?.createUser ?? null,
    updateUser: (id: string, input: { firstName?: string; lastName?: string; gender?: string }) =>
      update({ variables: { id, input } }),
    suspendUser: (id: string, reason: string) => suspend({ variables: { id, reason } }),
    unsuspendUser: (id: string) => unsuspend({ variables: { id } }),
    lockUser: (id: string, reason: string) => lock({ variables: { id, reason } }),
    unlockUser: (id: string) => unlock({ variables: { id } }),
    activateUser: (id: string) => activate({ variables: { id } }),
    deactivateUser: (id: string) => deactivate({ variables: { id } }),
    setUserRoles: (userId: string, roles: AdminUserRole[]) => setRoles({ variables: { userId, roles } }),
    syncUser: (userId: string) => sync({ variables: { userId } }),
    syncAllUsers: () => syncAll(),
  };
}

/* ---------------------------------------------------------- organizations */

export interface UseIdentityOrganizationsOptions {
  search?: string;
  status?: AdminOrgStatus | null;
  verified?: boolean | null;
  kybStatus?: AdminKybStatus | null;
  page?: number;
  size?: number;
}

export function useIdentityOrganizations(o: UseIdentityOrganizationsOptions = {}): ListResult<AdminOrgRecord> {
  const size = o.size ?? 20;
  const { data, loading, error, refetch } = useQuery<IdentityAdminOrganizationsQuery, IdentityAdminOrganizationsQueryVariables>(IDENTITY_ORGANIZATIONS, {
    variables: {
      search: o.search || null,
      status: o.status ?? null,
      verified: o.verified ?? null,
      kybStatus: o.kybStatus ?? null,
      pagination: { page: o.page ?? 0, size, sortBy: 'createdAt', sortDirection: 'DESC' },
    } as IdentityAdminOrganizationsQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    rows: data?.organizations?.content ?? [],
    total: data?.organizations?.pageInfo?.totalCount ?? 0,
    pageSize: data?.organizations?.pageInfo?.pageSize ?? size,
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

export function useIdentityOrganization(id: string | null) {
  const { data, loading, error, refetch } = useQuery<IdentityAdminOrganizationQuery, IdentityAdminOrganizationQueryVariables>(IDENTITY_ORGANIZATION, {
    variables: { id: id ?? '' } as IdentityAdminOrganizationQueryVariables,
    skip: !id,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { organization: data?.organization ?? null, loading, error: asError(error), refetch: () => void refetch() };
}

export function useOrgAdminMembers(organizationId: string | null) {
  const { data, loading, error, refetch } = useQuery<IdentityAdminOrgMembersQuery, IdentityAdminOrgMembersQueryVariables>(IDENTITY_ORG_MEMBERS, {
    variables: { organizationId: organizationId ?? '', pagination: { page: 0, size: 50 } } as IdentityAdminOrgMembersQueryVariables,
    skip: !organizationId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { members: data?.organizationMembers?.content ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

export function useOrgAdminDocuments(organizationId: string | null) {
  const { data, loading, error, refetch } = useQuery<IdentityAdminOrgDocumentsQuery, IdentityAdminOrgDocumentsQueryVariables>(
    IDENTITY_ORG_DOCUMENTS,
    { variables: { organizationId: organizationId ?? '' } as IdentityAdminOrgDocumentsQueryVariables, skip: !organizationId, fetchPolicy: 'cache-and-network', errorPolicy: 'all' }
  );
  return { documents: data?.verificationDocuments ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

/** Events created by an organization (catalog filters on the owner's organizerId). */
export function useOrgAdminEvents(organizerId: string | null) {
  const { data, loading, error, refetch } = useQuery<IdentityAdminOrgEventsQuery, IdentityAdminOrgEventsQueryVariables>(CATALOG_ORG_EVENTS, {
    variables: {
      filter: { organizerId, status: null, statuses: null, searchQuery: null },
      pagination: { page: 0, size: 50, sortBy: 'eventDateTime', sortDirection: 'DESC' },
    } as IdentityAdminOrgEventsQueryVariables,
    skip: !organizerId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { events: data?.events?.content ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

export function useOrgAdminActions() {
  const opts = { errorPolicy: 'none' as const, refetchQueries: ['IdentityAdminOrganizations', 'IdentityAdminOrganization'] };
  const [suspend] = useMutation(SUSPEND_ORG, opts);
  const [unsuspend] = useMutation(UNSUSPEND_ORG, opts);
  const [setStatus] = useMutation(UPDATE_ORG_STATUS, opts);
  const [verify] = useMutation(VERIFY_PAYOUT_ACCOUNT, opts);
  return {
    suspendOrganization: (id: string, reason: string) => suspend({ variables: { id, reason } }),
    unsuspendOrganization: (id: string) => unsuspend({ variables: { id } }),
    updateOrganizationStatus: (id: string, status: AdminOrgStatus) => setStatus({ variables: { id, status } }),
    /** verified=false is how a payout account is rejected: the backend has no separate reject operation. */
    verifyPayoutAccount: (organizationId: string, verified: boolean) => verify({ variables: { organizationId, verified } }),
  };
}

export type { AdminKybStatus };
