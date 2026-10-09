'use client';

/**
 * Admin ledger hooks. Shapes are declared locally (the generated types do not
 * carry these admin operations yet) and mirror booking-service's schema.
 */
import type { OffsetPageInfo } from '../../../../types/pageInfo';
import type { LedgerChartOfAccountsQuery, LedgerChartOfAccountsQueryVariables, LedgerJournalEntriesQuery, LedgerJournalEntriesQueryVariables, LedgerPlatformAccountsQuery, LedgerPlatformAccountsQueryVariables, LedgerReconciliationRunsQuery, LedgerReconciliationRunsQueryVariables, LedgerSeedChartMutation, LedgerSeedChartMutationVariables, LedgerTrialBalanceQuery, LedgerTrialBalanceQueryVariables } from '../../../../types/graphql';
import { useCallback } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import {
  CHART_OF_ACCOUNTS,
  COMPLETE_RECONCILIATION,
  CREATE_CHART_OF_ACCOUNTS_ENTRY,
  CREATE_JOURNAL_ENTRY,
  DEACTIVATE_CHART_OF_ACCOUNTS_ENTRY,
  FAIL_RECONCILIATION,
  JOURNAL_ENTRIES,
  PLATFORM_ACCOUNTS,
  POST_JOURNAL_ENTRY,
  RECONCILIATION_RUNS,
  RECORD_GATEWAY_SETTLEMENT,
  RESOLVE_RECONCILIATION_ITEM,
  REVERSE_JOURNAL_ENTRY,
  SEED_CHART_OF_ACCOUNTS,
  START_RECONCILIATION,
  TRIAL_BALANCE,
  UPDATE_CHART_OF_ACCOUNTS_ENTRY,
} from './ledger.queries';

export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';
export type BalanceDirection = 'DEBIT' | 'CREDIT';
export type JournalEntryType = 'STANDARD' | 'ADJUSTMENT' | 'REVERSAL';
export type JournalEntryStatus = 'DRAFT' | 'POSTED' | 'REVERSED';
export type ReconciliationType = 'GATEWAY' | 'BANK' | 'ESCROW' | 'ESCROW_JOURNAL';
export type ReconciliationStatus = 'RUNNING' | 'COMPLETED' | 'REQUIRES_REVIEW' | 'FAILED';
export type ReconciliationItemStatus = 'MATCHED' | 'UNMATCHED_EXTERNAL' | 'UNMATCHED_INTERNAL' | 'AMOUNT_MISMATCH';

export type LedgerPageInfo = OffsetPageInfo;

const EMPTY_PAGE = (size: number): LedgerPageInfo => ({
  totalCount: 0,
  pageSize: size,
  currentPage: 0,
  totalPages: 0,
  hasNextPage: false,
  hasPreviousPage: false,
});

function pageInfo(p: { totalCount?: number | null; pageSize?: number | null; currentPage?: number | null; totalPages?: number | null; hasNextPage?: boolean | null; hasPreviousPage?: boolean | null } | null | undefined, size: number): LedgerPageInfo {
  return {
    totalCount: p?.totalCount ?? 0,
    pageSize: p?.pageSize ?? size,
    currentPage: p?.currentPage ?? 0,
    totalPages: p?.totalPages ?? 0,
    hasNextPage: p?.hasNextPage ?? false,
    hasPreviousPage: p?.hasPreviousPage ?? false,
  };
}

/** Money arrives as BigDecimal; Apollo returns a number or a numeric string. */
export const toNumber = (v: unknown): number => {
  const n = Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
};

interface Result<T> {
  items: T[];
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

/* ------------------------------------------------------- chart of accounts */

export interface LedgerAccount {
  id: string;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  subType: string | null;
  parentAccountCode: string | null;
  currency: string;
  isActive: boolean;
  description: string | null;
  normalBalance: BalanceDirection;
}

export interface LedgerAccountInput {
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  subType?: string | null;
  parentAccountCode?: string | null;
  currency?: string | null;
  description?: string | null;
}

export function useChartOfAccounts(): Result<LedgerAccount> {
  const { data, loading, error, refetch } = useQuery<LedgerChartOfAccountsQuery, LedgerChartOfAccountsQueryVariables>(CHART_OF_ACCOUNTS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    items: data?.chartOfAccounts ?? [],
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

export function useChartOfAccountsActions() {
  const opts = { refetchQueries: ['LedgerChartOfAccounts'], awaitRefetchQueries: true };
  const [create, c] = useMutation(CREATE_CHART_OF_ACCOUNTS_ENTRY, opts);
  const [update, u] = useMutation(UPDATE_CHART_OF_ACCOUNTS_ENTRY, opts);
  const [deactivate, d] = useMutation(DEACTIVATE_CHART_OF_ACCOUNTS_ENTRY, opts);
  const [seed, s] = useMutation<LedgerSeedChartMutation, LedgerSeedChartMutationVariables>(SEED_CHART_OF_ACCOUNTS, opts);
  return {
    createAccount: useCallback(async (input: LedgerAccountInput) => void (await create({ variables: { input } })), [create]),
    updateAccount: useCallback(
      async (id: string, input: LedgerAccountInput) => void (await update({ variables: { id, input } })),
      [update]
    ),
    deactivateAccount: useCallback(async (id: string) => void (await deactivate({ variables: { id } })), [deactivate]),
    seedChart: useCallback(async () => (await seed()).data?.seedChartOfAccounts ?? false, [seed]),
    busy: c.loading || u.loading || d.loading || s.loading,
  };
}

/* --------------------------------------------------------- journal entries */

export interface LedgerJournalLine {
  accountCode: string;
  accountName: string;
  debit: number | string | null;
  credit: number | string | null;
  description: string | null;
}

export interface LedgerJournalEntry {
  id: string;
  entryNumber: string;
  correlationId: string | null;
  entryDate: string;
  description: string;
  type: JournalEntryType;
  status: JournalEntryStatus;
  createdBy: string | null;
  postedBy: string | null;
  postedAt: string | null;
  reversedBy: string | null;
  reversedAt: string | null;
  reversalEntryId: string | null;
  reversedByEntryId: string | null;
  totalDebits: number | string;
  totalCredits: number | string;
  isBalanced: boolean;
  lines: LedgerJournalLine[];
}

export interface JournalFilter {
  status?: JournalEntryStatus;
  type?: JournalEntryType;
}

export interface JournalLineDraft {
  accountCode: string;
  accountName: string;
  debit?: number | null;
  credit?: number | null;
}

export interface JournalEntryInput {
  correlationId: string;
  entryDate: string;
  description: string;
  type: 'STANDARD' | 'ADJUSTMENT';
  lines: JournalLineDraft[];
}

export interface PagedResult<T> extends Result<T> {
  pageInfo: LedgerPageInfo;
}

export function useJournalEntries(filter: JournalFilter = {}, page = 0, size = 20): PagedResult<LedgerJournalEntry> {
  const { data, loading, error, refetch } = useQuery<LedgerJournalEntriesQuery, LedgerJournalEntriesQueryVariables>(JOURNAL_ENTRIES, {
    variables: {
      filter: { status: filter.status ?? null, type: filter.type ?? null },
      pagination: { page, size, sortBy: 'createdAt', sortDirection: 'DESC' },
    } as LedgerJournalEntriesQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    items: data?.journalEntries.data ?? [],
    pageInfo: data ? pageInfo(data.journalEntries.pagination, size) : EMPTY_PAGE(size),
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

export function useJournalActions() {
  const opts = { refetchQueries: ['LedgerJournalEntries', 'LedgerTrialBalance'], awaitRefetchQueries: true };
  const [create, c] = useMutation(CREATE_JOURNAL_ENTRY, opts);
  const [post, p] = useMutation(POST_JOURNAL_ENTRY, opts);
  const [reverse, r] = useMutation(REVERSE_JOURNAL_ENTRY, opts);
  return {
    createEntry: useCallback(
      async (input: JournalEntryInput) => {
        const res = await create({ variables: { input } });
        return (res.data as { createJournalEntry?: LedgerJournalEntry } | null | undefined)?.createJournalEntry ?? null;
      },
      [create]
    ),
    postEntry: useCallback(async (id: string) => void (await post({ variables: { id } })), [post]),
    reverseEntry: useCallback(async (id: string, reason: string) => void (await reverse({ variables: { id, reason } })), [reverse]),
    busy: c.loading || p.loading || r.loading,
  };
}

/* ------------------------------------------------------------ trial balance */

export interface LedgerAccountBalance {
  accountCode: string;
  accountName: string;
  accountType: string;
  debitBalance: number | string;
  creditBalance: number | string;
  netBalance: number | string;
}

/** `asOf` is an ISO date-time or null for "now". */
export function useTrialBalance(asOf: string | null): Result<LedgerAccountBalance> {
  const { data, loading, error, refetch } = useQuery<LedgerTrialBalanceQuery, LedgerTrialBalanceQueryVariables>(TRIAL_BALANCE, {
    variables: { asOf } as LedgerTrialBalanceQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    items: data?.trialBalance ?? [],
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

/* -------------------------------------------------------- platform accounts */

export interface LedgerPlatformAccount {
  id: string;
  accountType: 'OPERATING' | 'RESERVE' | 'TAX_HOLDING';
  name: string;
  balance: number | string;
  currency: string;
  lastUpdatedAt: string | null;
}

export function usePlatformAccounts(): Result<LedgerPlatformAccount> {
  const { data, loading, error, refetch } = useQuery<LedgerPlatformAccountsQuery, LedgerPlatformAccountsQueryVariables>(PLATFORM_ACCOUNTS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    items: data?.platformAccounts ?? [],
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

/* ----------------------------------------------------------- reconciliation */

export interface ReconciliationItemRow {
  externalId: string | null;
  internalId: string | null;
  externalAmount: number | string | null;
  internalAmount: number | string | null;
  status: ReconciliationItemStatus;
  resolution: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
}

export interface ReconciliationRunRow {
  id: string;
  reconciliationDate: string;
  type: ReconciliationType;
  status: ReconciliationStatus;
  dataSource: string | null;
  expectedTotal: number | string | null;
  actualTotal: number | string | null;
  variance: number | string | null;
  matchedCount: number;
  unmatchedCount: number;
  runBy: string | null;
  startedAt: string;
  completedAt: string | null;
  notes: string | null;
  items: ReconciliationItemRow[];
}

export interface ReconciliationFilter {
  type?: ReconciliationType;
  status?: ReconciliationStatus;
}

export function useReconciliationRuns(filter: ReconciliationFilter = {}, page = 0, size = 20): PagedResult<ReconciliationRunRow> {
  const { data, loading, error, refetch } = useQuery<LedgerReconciliationRunsQuery, LedgerReconciliationRunsQueryVariables>(RECONCILIATION_RUNS, {
    variables: {
      filter: { type: filter.type ?? null, status: filter.status ?? null },
      pagination: { page, size, sortBy: 'startedAt', sortDirection: 'DESC' },
    } as LedgerReconciliationRunsQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    items: data?.reconciliationRuns.data ?? [],
    pageInfo: data ? pageInfo(data.reconciliationRuns.pagination, size) : EMPTY_PAGE(size),
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

export interface GatewaySettlementInput {
  settlementId: string;
  grossAmount: number;
  feeAmount: number;
  netAmount: number;
  settlementDate: string;
  bankReference: string;
  currency: string;
}

export function useReconciliationActions() {
  const opts = { refetchQueries: ['LedgerReconciliationRuns'], awaitRefetchQueries: true };
  const [start, s] = useMutation(START_RECONCILIATION, opts);
  const [resolve, r] = useMutation(RESOLVE_RECONCILIATION_ITEM, opts);
  const [complete, c] = useMutation(COMPLETE_RECONCILIATION, opts);
  const [fail, f] = useMutation(FAIL_RECONCILIATION, opts);
  const [settle, g] = useMutation(RECORD_GATEWAY_SETTLEMENT, { refetchQueries: ['LedgerJournalEntries'] });
  return {
    startRun: useCallback(
      async (input: { reconciliationDate: string; type: ReconciliationType; dataSource?: string | null }) => {
        const res = await start({ variables: { input } });
        return (res.data as { startReconciliation?: { id: string; status: ReconciliationStatus; unmatchedCount: number } } | null | undefined)
          ?.startReconciliation ?? null;
      },
      [start]
    ),
    resolveItem: useCallback(
      async (runId: string, externalId: string, resolution: string) =>
        void (await resolve({ variables: { runId, input: { externalId, resolution } } })),
      [resolve]
    ),
    completeRun: useCallback(async (runId: string, notes?: string) => void (await complete({ variables: { runId, notes: notes ?? null } })), [complete]),
    failRun: useCallback(async (runId: string, reason: string) => void (await fail({ variables: { runId, reason } })), [fail]),
    recordSettlement: useCallback(
      async (input: GatewaySettlementInput) => {
        const res = await settle({ variables: { input } });
        return (res.data as { recordGatewaySettlement?: { id: string; entryNumber: string } } | null | undefined)?.recordGatewaySettlement ?? null;
      },
      [settle]
    ),
    busy: s.loading || r.loading || c.loading || f.loading || g.loading,
  };
}
