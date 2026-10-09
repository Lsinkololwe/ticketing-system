'use client';

/**
 * Read-only platform reports used by the admin dashboard and analytics page:
 * user, ticket, transaction and chargeback statistics plus the financial report.
 * Result types are local because the generated types predate these operations.
 */

import type { AdminUserStatsQuery, AdminChargebackStatsQuery, AdminTransactionStatsQuery, AdminTicketStatsQuery, AdminFinancialReportQuery } from '../../../../types/graphql';
import { useLazyQuery, useQuery } from '@apollo/client/react';
import {
  CHARGEBACK_STATS,
  EXPORT_FINANCIAL_REPORT,
  FINANCIAL_REPORT,
  TICKET_STATS,
  TRANSACTION_STATS,
  USER_STATS,
} from './reports.queries';

type Money = number | string;

export interface AdminUserStats {
  totalUsers: number;
  organizers: number;
  attendees: number;
  adminUsers: number;
  verifiedUsers: number;
  activeUsers: number;
  suspendedUsers: number;
  lockedUsers: number;
  pendingVerificationUsers: number;
  newUsersThisMonth: number;
  newUsersThisWeek: number;
  growthRate: number | null;
}

export interface FinancialDataPointRow {
  period: string;
  revenue: Money;
  commissions: Money;
  refunds: Money;
  payouts: Money;
  ticketsSold: number;
}

export interface AdminFinancialReport {
  startDate: string;
  endDate: string;
  totalRevenue: Money;
  totalCommissions: Money;
  totalRefunds: Money;
  totalPayouts: Money;
  pendingPayouts: Money;
  escrowBalance: Money;
  netPlatformRevenue: Money;
  dataPoints: FinancialDataPointRow[];
}

export type ReportGroupBy = 'HOUR' | 'DAY' | 'WEEK' | 'MONTH';

export interface AdminChargebackStats {
  totalCount: number;
  pendingCount: number;
  disputedCount: number;
  wonCount: number;
  lostCount: number;
  totalAmount: Money;
  recoveredAmount: Money;
  writtenOffAmount: Money;
  chargebackRate: number;
  winRate: number;
}

export interface AdminTransactionStats {
  totalTransactions: number;
  completedTransactions: number;
  failedTransactions: number;
  pendingTransactions: number;
  timedOutTransactions: number;
  totalVolume: Money;
  totalCommissions: Money;
  averageTransactionValue: Money | null;
}

export interface AdminTicketStats {
  totalTickets: number;
  issuedTickets: number;
  validatedTickets: number;
  refundPendingTickets: number;
  refundedTickets: number;
  cancelledTickets: number;
  expiredTickets: number;
  ticketsByStatus: Array<{ status: string; count: number; percentage: number }> | null;
}

export interface ReportResult<T> {
  data: T | null;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

function wrap<T>(r: { data?: T; loading: boolean; error?: unknown; refetch: () => Promise<unknown> }, pick: (d: T) => unknown) {
  return {
    data: (r.data ? (pick(r.data) ?? null) : null) as never,
    loading: r.loading,
    error: r.error as Error | undefined,
    refetch: () => {
      void r.refetch();
    },
  };
}

const OPTS = { fetchPolicy: 'cache-and-network', errorPolicy: 'all' } as const;

export function useUserStats(): ReportResult<AdminUserStats> {
  const r = useQuery<AdminUserStatsQuery>(USER_STATS, OPTS);
  return wrap(r, (d) => d.userStats);
}

export function useChargebackStats(): ReportResult<AdminChargebackStats> {
  const r = useQuery<AdminChargebackStatsQuery>(CHARGEBACK_STATS, OPTS);
  return wrap(r, (d) => d.chargebackStats);
}

export function useTransactionStats(): ReportResult<AdminTransactionStats> {
  const r = useQuery<AdminTransactionStatsQuery>(TRANSACTION_STATS, OPTS);
  return wrap(r, (d) => d.transactionStats);
}

export function useTicketStats(): ReportResult<AdminTicketStats> {
  const r = useQuery<AdminTicketStatsQuery>(TICKET_STATS, OPTS);
  return wrap(r, (d) => d.ticketStats);
}

export interface FinancialReportRange {
  /** ISO date-times. Pass stable strings (not `new Date()` each render) to avoid refetch loops. */
  startDate: string;
  endDate: string;
  groupBy?: ReportGroupBy;
  skip?: boolean;
}

export function useFinancialReport(range: FinancialReportRange): ReportResult<AdminFinancialReport> {
  const r = useQuery<AdminFinancialReportQuery>(FINANCIAL_REPORT, {
    ...OPTS,
    skip: range.skip,
    variables: { filter: { startDate: range.startDate, endDate: range.endDate, groupBy: range.groupBy ?? 'MONTH' } },
  });
  return wrap(r, (d) => d.financialReport);
}

export type AdminExportFormat = 'CSV' | 'PDF' | 'EXCEL' | 'JSON';

export interface AdminReportExport {
  downloadUrl: string | null;
  expiresAt: string | null;
  format: AdminExportFormat;
  generatedAt: string;
  fileName: string | null;
  errorMessage: string | null;
}

/** Server-side export of the financial report (`exportFinancialReport`); resolves to the download link. */
export function useExportFinancialReport() {
  const [run, { loading }] = useLazyQuery<{ exportFinancialReport: AdminReportExport }>(EXPORT_FINANCIAL_REPORT, {
    fetchPolicy: 'network-only',
  });
  const exportReport = async (range: { startDate: string; endDate: string }, format: AdminExportFormat) => {
    const res = await run({ variables: { filter: { ...range, groupBy: 'MONTH' }, format } });
    if (res.error || !res.data) throw res.error ?? new Error('Export failed');
    return res.data.exportFinancialReport;
  };
  return { exportReport, loading };
}
