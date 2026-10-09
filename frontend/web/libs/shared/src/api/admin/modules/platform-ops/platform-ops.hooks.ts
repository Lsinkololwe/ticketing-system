'use client';

/**
 * Platform operations hooks (identity-service). Types come from codegen; mutation hooks return
 * promise-returning functions that throw on failure so callers can map the server error.
 */
import type {
  OpsAcknowledgeAlertMutation,
  OpsAcknowledgeAlertMutationVariables,
  OpsBroadcastNotificationMutation,
  OpsBroadcastNotificationMutationVariables,
  OpsCancelAnnouncementMutation,
  OpsCancelAnnouncementMutationVariables,
  OpsCreateStaffMutation,
  OpsCreateStaffMutationVariables,
  OpsDeleteUserMutation,
  OpsDeleteUserMutationVariables,
  OpsReinstateBankAccountMutation,
  OpsReinstateBankAccountMutationVariables,
  OpsRejectBankAccountMutation,
  OpsRejectBankAccountMutationVariables,
  OpsRejectPayoutAccountMutation,
  OpsRejectPayoutAccountMutationVariables,
  OpsRevokeSessionMutation,
  OpsRevokeSessionMutationVariables,
  OpsSetOrganizationCommissionRateMutation,
  OpsSetOrganizationCommissionRateMutationVariables,
  OpsSuspendBankAccountMutation,
  OpsSuspendBankAccountMutationVariables,
  OpsUpdateMyProfileMutation,
  OpsUpdateMyProfileMutationVariables,
  AlertSeverity,
  AlertStatus,
  BroadcastInput,
  GrowthBucket,
  OpsAuditLogsQuery,
  OpsAuditLogsQueryVariables,
  OpsMeSecurityQuery,
  OpsMySessionsQuery,
  OpsPayoutAccountsQuery,
  OpsPayoutAccountsQueryVariables,
  OpsServiceHealthQuery,
  OpsStaffAccountsQuery,
  OpsStaffAccountsQueryVariables,
  OpsSystemAlertsQuery,
  OpsSystemAlertsQueryVariables,
  OpsSystemAnnouncementsQuery,
  OpsUserGrowthSeriesQuery,
  OpsUserGrowthSeriesQueryVariables,
  PayoutAccountStatus,
  UserType,
} from '../../../../types/graphql';
import { useCallback, useEffect, useState } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import type { OffsetPageInfo } from '../../../../types/pageInfo';
import {
  OPS_ACKNOWLEDGE_ALERT,
  OPS_AUDIT_LOGS,
  OPS_BROADCAST_NOTIFICATION,
  OPS_CANCEL_ANNOUNCEMENT,
  OPS_CREATE_STAFF,
  OPS_DELETE_USER,
  OPS_ME_SECURITY,
  OPS_MY_SESSIONS,
  OPS_PAYOUT_ACCOUNTS,
  OPS_REINSTATE_BANK_ACCOUNT,
  OPS_REJECT_BANK_ACCOUNT,
  OPS_REJECT_PAYOUT_ACCOUNT,
  OPS_REVOKE_SESSION,
  OPS_SERVICE_HEALTH,
  OPS_SET_ORG_COMMISSION,
  OPS_STAFF_ACCOUNTS,
  OPS_SUSPEND_BANK_ACCOUNT,
  OPS_SYSTEM_ALERTS,
  OPS_SYSTEM_ANNOUNCEMENTS,
  OPS_UPDATE_MY_PROFILE,
  OPS_USER_GROWTH_SERIES,
} from './platform-ops.queries';

const EMPTY_PAGE: OffsetPageInfo = { totalCount: 0, pageSize: 20, currentPage: 0, totalPages: 0, hasNextPage: false, hasPreviousPage: false };

type LoosePage = { totalCount?: number | null; pageSize?: number | null; currentPage?: number | null; totalPages?: number | null; hasNextPage?: boolean | null; hasPreviousPage?: boolean | null } | null | undefined;

export function normalizePage(p: LoosePage, size = 20): OffsetPageInfo {
  return {
    totalCount: p?.totalCount ?? 0,
    pageSize: p?.pageSize ?? size,
    currentPage: p?.currentPage ?? 0,
    totalPages: p?.totalPages ?? 0,
    hasNextPage: p?.hasNextPage ?? false,
    hasPreviousPage: p?.hasPreviousPage ?? false,
  };
}

const asError = (e: unknown): Error | undefined => (e ? (e as Error) : undefined);

/** Refresh interval for dashboards (D-12): polls only while the tab is visible. */
function useVisiblePollInterval(ms: number): number {
  const [visible, setVisible] = useState(true);
  useEffect(() => {
    const on = () => setVisible(document.visibilityState !== 'hidden');
    on();
    document.addEventListener('visibilitychange', on);
    return () => document.removeEventListener('visibilitychange', on);
  }, []);
  return visible ? ms : 0;
}

/* -------------------------------------------------------------- health */

export type ServiceHealthRow = OpsServiceHealthQuery['serviceHealth'][number];

export function useServiceHealth(pollMs = 30_000) {
  const pollInterval = useVisiblePollInterval(pollMs);
  const { data, loading, error, refetch } = useQuery<OpsServiceHealthQuery>(OPS_SERVICE_HEALTH, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    pollInterval,
  });
  return { services: data?.serviceHealth ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

export type SystemAlertRow = OpsSystemAlertsQuery['systemAlerts'][number];

export function useSystemAlerts(filter: { status?: AlertStatus | null; severity?: AlertSeverity | null } = {}, pollMs = 30_000) {
  const pollInterval = useVisiblePollInterval(pollMs);
  const { data, loading, error, refetch } = useQuery<OpsSystemAlertsQuery, OpsSystemAlertsQueryVariables>(OPS_SYSTEM_ALERTS, {
    variables: { status: filter.status ?? null, severity: filter.severity ?? null },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    pollInterval,
  });
  return { alerts: data?.systemAlerts ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

export function useAcknowledgeAlert() {
  const [mutate, { loading }] = useMutation<OpsAcknowledgeAlertMutation, OpsAcknowledgeAlertMutationVariables>(OPS_ACKNOWLEDGE_ALERT, { refetchQueries: ['OpsSystemAlerts'], errorPolicy: 'none' });
  const acknowledge = useCallback(async (id: string) => (await mutate({ variables: { id } })).data?.acknowledgeAlert ?? null, [mutate]);
  return { acknowledge, loading };
}

/* -------------------------------------------------------- announcements */

export type AnnouncementRow = OpsSystemAnnouncementsQuery['systemAnnouncements'][number];

export function useSystemAnnouncements() {
  const { data, loading, error, refetch } = useQuery<OpsSystemAnnouncementsQuery>(OPS_SYSTEM_ANNOUNCEMENTS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { announcements: data?.systemAnnouncements ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

export function useAnnouncementActions() {
  const opts = { refetchQueries: ['OpsSystemAnnouncements'], errorPolicy: 'none' as const };
  const [send, s] = useMutation<OpsBroadcastNotificationMutation, OpsBroadcastNotificationMutationVariables>(OPS_BROADCAST_NOTIFICATION, opts);
  const [cancel, c] = useMutation<OpsCancelAnnouncementMutation, OpsCancelAnnouncementMutationVariables>(OPS_CANCEL_ANNOUNCEMENT, opts);
  return {
    broadcast: useCallback(async (input: BroadcastInput) => (await send({ variables: { input } })).data?.broadcastNotification ?? null, [send]),
    cancelAnnouncement: useCallback(async (id: string) => (await cancel({ variables: { id } })).data?.cancelAnnouncement ?? null, [cancel]),
    busy: s.loading || c.loading,
  };
}

/* ------------------------------------------------------------- audit log */

export type AuditLogRow = OpsAuditLogsQuery['auditLogs']['content'][number];

export interface AuditLogOptions {
  from?: string | null;
  to?: string | null;
  action?: string | null;
  actorId?: string | null;
  resourceType?: string | null;
  resourceId?: string | null;
  status?: string | null;
  includeAccountEvents?: boolean;
  page?: number;
  size?: number;
  /** Skip the request (e.g. a quick view that is closed). */
  skip?: boolean;
}

export function useAuditLogs(o: AuditLogOptions = {}) {
  const size = o.size ?? 20;
  const { data, loading, error, refetch } = useQuery<OpsAuditLogsQuery, OpsAuditLogsQueryVariables>(OPS_AUDIT_LOGS, {
    variables: {
      filter: {
        from: o.from || null,
        to: o.to || null,
        action: o.action || null,
        actorId: o.actorId || null,
        resourceType: o.resourceType || null,
        resourceId: o.resourceId || null,
        status: o.status || null,
        includeAccountEvents: o.includeAccountEvents ?? null,
      },
      pagination: { page: o.page ?? 0, size, sortBy: 'at', sortDirection: 'DESC' },
    } as OpsAuditLogsQueryVariables,
    skip: o.skip,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    entries: data?.auditLogs.content ?? [],
    pageInfo: data ? normalizePage(data.auditLogs.pageInfo, size) : { ...EMPTY_PAGE, pageSize: size },
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

/* ----------------------------------------------------------------- staff */

export type StaffAccountRow = OpsStaffAccountsQuery['staffAccounts']['content'][number];

export function useStaffAccounts(o: { search?: string; role?: UserType | null; page?: number; size?: number; skip?: boolean } = {}) {
  const size = o.size ?? 20;
  const { data, loading, error, refetch } = useQuery<OpsStaffAccountsQuery, OpsStaffAccountsQueryVariables>(OPS_STAFF_ACCOUNTS, {
    variables: {
      search: o.search || null,
      role: o.role ?? null,
      pagination: { page: o.page ?? 0, size, sortBy: 'createdAt', sortDirection: 'DESC' },
    } as OpsStaffAccountsQueryVariables,
    skip: o.skip,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    staff: data?.staffAccounts.content ?? [],
    pageInfo: data ? normalizePage(data.staffAccounts.pageInfo, size) : { ...EMPTY_PAGE, pageSize: size },
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

export function useStaffActions() {
  const opts = { refetchQueries: ['OpsStaffAccounts', 'IdentityAdminUsers', 'IdentityAdminUser'], errorPolicy: 'none' as const };
  const [create, c] = useMutation<OpsCreateStaffMutation, OpsCreateStaffMutationVariables>(OPS_CREATE_STAFF, opts);
  const [del, d] = useMutation<OpsDeleteUserMutation, OpsDeleteUserMutationVariables>(OPS_DELETE_USER, opts);
  return {
    createStaff: useCallback(
      async (input: { email: string; firstName: string; lastName: string; phoneNumber: string; role: UserType }) =>
        (await create({ variables: { input } })).data?.createUser ?? null,
      [create]
    ),
    /** Soft delete: the account moves to pending deletion and can be restored by support. */
    deleteUser: useCallback(async (id: string) => (await del({ variables: { id } })).data?.deleteUser ?? null, [del]),
    busy: c.loading || d.loading,
  };
}

/* ------------------------------------------------ own account and sessions */

export type AccountSessionRow = OpsMySessionsQuery['mySessions'][number];

export function useMySessions() {
  const { data, loading, error, refetch } = useQuery<OpsMySessionsQuery>(OPS_MY_SESSIONS, { fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  return { sessions: data?.mySessions ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

export function useRevokeSession() {
  const [mutate, { loading }] = useMutation<OpsRevokeSessionMutation, OpsRevokeSessionMutationVariables>(OPS_REVOKE_SESSION, { refetchQueries: ['OpsMySessions'], errorPolicy: 'none' });
  const revoke = useCallback(async (sessionId: string) => (await mutate({ variables: { sessionId } })).data?.revokeSession ?? false, [mutate]);
  return { revoke, loading };
}

export function useMySecurity() {
  const { data, loading, error, refetch } = useQuery<OpsMeSecurityQuery>(OPS_ME_SECURITY, { fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  return { me: data?.me ?? null, loading, error: asError(error), refetch: () => void refetch() };
}

export function useUpdateMyProfile() {
  const [mutate, { loading }] = useMutation<OpsUpdateMyProfileMutation, OpsUpdateMyProfileMutationVariables>(OPS_UPDATE_MY_PROFILE, { errorPolicy: 'none' });
  const update = useCallback(
    async (input: { firstName?: string; lastName?: string; displayName?: string }) => (await mutate({ variables: { input } })).data?.updateMyProfile ?? null,
    [mutate]
  );
  return { update, loading };
}

/* ---------------------------------------------------------------- growth */

export type GrowthPointRow = OpsUserGrowthSeriesQuery['userGrowthSeries'][number];

export function useUserGrowthSeries(v: { from: string; to: string; bucket?: GrowthBucket; role?: UserType | null }) {
  const { data, loading, error, refetch } = useQuery<OpsUserGrowthSeriesQuery, OpsUserGrowthSeriesQueryVariables>(OPS_USER_GROWTH_SERIES, {
    variables: { from: v.from, to: v.to, bucket: v.bucket ?? 'MONTH', role: v.role ?? null },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { points: data?.userGrowthSeries ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

/* ------------------------------------------------------ payout accounts */

export type PayoutAccountRow = OpsPayoutAccountsQuery['bankAccounts']['content'][number];

export function usePayoutAccounts(o: { status?: PayoutAccountStatus | null; search?: string; page?: number; size?: number } = {}) {
  const size = o.size ?? 20;
  const { data, loading, error, refetch } = useQuery<OpsPayoutAccountsQuery, OpsPayoutAccountsQueryVariables>(OPS_PAYOUT_ACCOUNTS, {
    variables: {
      filter: { status: o.status ?? null, search: o.search || null },
      pagination: { page: o.page ?? 0, size },
    } as OpsPayoutAccountsQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    accounts: data?.bankAccounts.content ?? [],
    pageInfo: data ? normalizePage(data.bankAccounts.pageInfo, size) : { ...EMPTY_PAGE, pageSize: size },
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

export function usePayoutAccountActions() {
  const opts = { refetchQueries: ['OpsPayoutAccounts', 'IdentityAdminOrganization'], errorPolicy: 'none' as const };
  const [reject, a] = useMutation<OpsRejectBankAccountMutation, OpsRejectBankAccountMutationVariables>(OPS_REJECT_BANK_ACCOUNT, opts);
  const [suspend, b] = useMutation<OpsSuspendBankAccountMutation, OpsSuspendBankAccountMutationVariables>(OPS_SUSPEND_BANK_ACCOUNT, opts);
  const [reinstate, c] = useMutation<OpsReinstateBankAccountMutation, OpsReinstateBankAccountMutationVariables>(OPS_REINSTATE_BANK_ACCOUNT, opts);
  const [rejectPayout, d] = useMutation<OpsRejectPayoutAccountMutation, OpsRejectPayoutAccountMutationVariables>(OPS_REJECT_PAYOUT_ACCOUNT, opts);
  return {
    rejectBankAccount: useCallback(async (organizationId: string, reason: string) => void (await reject({ variables: { organizationId, reason } })), [reject]),
    suspendBankAccount: useCallback(async (organizationId: string, reason: string) => void (await suspend({ variables: { organizationId, reason } })), [suspend]),
    reinstateBankAccount: useCallback(async (organizationId: string) => void (await reinstate({ variables: { organizationId } })), [reinstate]),
    rejectPayoutAccount: useCallback(async (organizationId: string, reason: string) => void (await rejectPayout({ variables: { organizationId, reason } })), [rejectPayout]),
    busy: a.loading || b.loading || c.loading || d.loading,
  };
}

export function useSetOrganizationCommission() {
  const [mutate, { loading }] = useMutation<OpsSetOrganizationCommissionRateMutation, OpsSetOrganizationCommissionRateMutationVariables>(OPS_SET_ORG_COMMISSION, { refetchQueries: ['IdentityAdminOrganization', 'IdentityAdminOrganizations'], errorPolicy: 'none' });
  const setRate = useCallback(
    async (organizationId: string, rate: number, reason?: string) => (await mutate({ variables: { organizationId, rate, reason: reason || null } })).data?.setOrganizationCommissionRate ?? null,
    [mutate]
  );
  return { setRate, loading };
}
