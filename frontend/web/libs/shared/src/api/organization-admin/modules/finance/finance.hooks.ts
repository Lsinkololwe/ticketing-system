'use client';

/**
 * React Hooks for Organizer Finance (Organization Admin App)
 *
 * Overview + transactions read from the JWT identity; payouts and bank accounts
 * are keyed by organizerId (the org owner). Mutations return the backend
 * success/message/errors envelope. Money movement is enforced server-side.
 */

import { useQuery, useMutation } from '@apollo/client/react';
import type { FetchPolicy } from '@apollo/client';
import {
  MY_FINANCE_OVERVIEW,
  MY_TRANSACTIONS,
  PAYOUTS_BY_ORGANIZER,
  BANK_ACCOUNTS_BY_ORGANIZER,
  CREATE_PAYOUT_REQUEST,
  CANCEL_PAYOUT_REQUEST,
  CREATE_BANK_ACCOUNT,
  UPDATE_BANK_ACCOUNT,
  DELETE_BANK_ACCOUNT,
  SET_DEFAULT_BANK_ACCOUNT,
} from './finance.queries';

// ---------------------------------------------------------------------------
// View-model row types (subset of the backend types actually selected)
// ---------------------------------------------------------------------------

export interface FinanceOverview {
  availableBalance: string;
  pendingBalance: string;
  totalEarned: string;
  currency: string;
  pendingPayoutRequests: number;
  lastPayoutDate: string | null;
  lastPayoutAmount: string | null;
  totalTicketRevenue: string;
  totalRefunds: string;
  platformFees: string;
  netEarnings: string;
  earningsThisMonth: string;
  earningsLastMonth: string;
  monthlyGrowth: number | null;
}

export interface TransactionRowVM {
  id: string;
  type: string;
  description: string;
  amount: string;
  currency: string;
  status: string;
  timestamp: string;
  eventId: string | null;
  eventTitle: string | null;
  reference: string | null;
}

export interface PayoutRowVM {
  id: string;
  requestId: string;
  organizerId: string;
  eventId: string | null;
  eventTitle: string | null;
  requestedAmount: string;
  netPayoutAmount: string;
  currency: string;
  status: string;
  payoutMethod: string | null;
  requestedAt: string;
  approvedAt: string | null;
  processedAt: string | null;
  rejectionReason: string | null;
  bankName: string | null;
  accountNumber: string | null;
  bankAccountName: string | null;
  notes: string | null;
}

export interface BankAccountVM {
  id: string;
  organizerId: string;
  accountHolderName: string;
  bankName: string;
  bankCode: string | null;
  branchName: string | null;
  branchCode: string | null;
  accountNumber: string;
  accountType: string | null;
  currency: string;
  swiftCode: string | null;
  isDefault: boolean;
  isVerified: boolean;
  status: string | null;
  createdAt: string;
}

interface MutationEnvelope {
  success: boolean;
  message: string | null;
  errors: string[];
}

function envelope(res: { success?: boolean; message?: string | null; errors?: string[] } | null | undefined): MutationEnvelope {
  return {
    success: res?.success ?? false,
    message: res?.message ?? null,
    errors: res?.errors ?? ['Request failed'],
  };
}

// ---------------------------------------------------------------------------
// Queries
// ---------------------------------------------------------------------------

export function useMyFinanceOverview(options?: { fetchPolicy?: FetchPolicy; skip?: boolean }) {
  const { data, loading, error, refetch } = useQuery<{ myFinanceOverview: FinanceOverview }>(
    MY_FINANCE_OVERVIEW,
    {
      fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
      errorPolicy: 'all',
      notifyOnNetworkStatusChange: true,
      skip: options?.skip ?? false,
    }
  );
  return { overview: data?.myFinanceOverview ?? null, loading, error, refetch };
}

export function useMyTransactions(options?: {
  filter?: { type?: string; eventId?: string };
  size?: number;
  fetchPolicy?: FetchPolicy;
  skip?: boolean;
}) {
  const { data, loading, error, refetch } = useQuery<{
    myTransactionsOffsetPagination: { content: TransactionRowVM[]; totalElements: number };
  }>(MY_TRANSACTIONS, {
    variables: {
      filter: options?.filter ?? null,
      pagination: { page: 0, size: options?.size ?? 25 },
    },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip ?? false,
  });
  return {
    transactions: data?.myTransactionsOffsetPagination.content ?? [],
    totalElements: data?.myTransactionsOffsetPagination.totalElements ?? 0,
    loading,
    error,
    refetch,
  };
}

export function useMyPayouts(organizerId: string | null | undefined, options?: {
  size?: number;
  fetchPolicy?: FetchPolicy;
}) {
  const { data, loading, error, refetch } = useQuery<{
    payoutRequestsByOrganizerOffsetPagination: { data: PayoutRowVM[] };
  }>(PAYOUTS_BY_ORGANIZER, {
    variables: { organizerId, pagination: { page: 0, size: options?.size ?? 25 } },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: !organizerId,
  });
  return {
    payouts: data?.payoutRequestsByOrganizerOffsetPagination.data ?? [],
    loading,
    error,
    refetch,
  };
}

export function useMyBankAccounts(organizerId: string | null | undefined, options?: {
  fetchPolicy?: FetchPolicy;
}) {
  const { data, loading, error, refetch } = useQuery<{ bankAccountsByOrganizer: BankAccountVM[] }>(
    BANK_ACCOUNTS_BY_ORGANIZER,
    {
      variables: { organizerId },
      fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
      errorPolicy: 'all',
      skip: !organizerId,
    }
  );
  return { bankAccounts: data?.bankAccountsByOrganizer ?? [], loading, error, refetch };
}

// ---------------------------------------------------------------------------
// Mutations
// ---------------------------------------------------------------------------

export interface CreatePayoutInput {
  organizerId: string;
  eventId?: string | null;
  escrowAccountId: string;
  bankAccountId: string;
  requestedAmount: string | number;
  currency: string;
  payoutMethod: string;
  notes?: string | null;
}

export function useCreatePayoutRequest() {
  const [mutate, { loading }] = useMutation<{ createPayoutRequest: MutationEnvelope }>(CREATE_PAYOUT_REQUEST);
  const createPayout = async (input: CreatePayoutInput): Promise<MutationEnvelope> => {
    const res = await mutate({ variables: { input } });
    return envelope(res.data?.createPayoutRequest);
  };
  return { createPayout, loading };
}

export function useCancelPayoutRequest() {
  const [mutate, { loading }] = useMutation<{ cancelPayoutRequest: MutationEnvelope }>(CANCEL_PAYOUT_REQUEST);
  const cancelPayout = async (payoutRequestId: string, reason: string): Promise<MutationEnvelope> => {
    const res = await mutate({ variables: { payoutRequestId, reason } });
    return envelope(res.data?.cancelPayoutRequest);
  };
  return { cancelPayout, loading };
}

export interface BankAccountInput {
  organizerId?: string;
  accountHolderName: string;
  bankName: string;
  bankCode?: string | null;
  branchName?: string | null;
  branchCode?: string | null;
  accountNumber: string;
  accountType?: string | null;
  currency?: string;
  swiftCode?: string | null;
  isDefault?: boolean;
}

export function useCreateBankAccount() {
  const [mutate, { loading }] = useMutation<{ createBankAccount: MutationEnvelope }>(CREATE_BANK_ACCOUNT);
  const createBankAccount = async (input: BankAccountInput): Promise<MutationEnvelope> => {
    const res = await mutate({ variables: { input } });
    return envelope(res.data?.createBankAccount);
  };
  return { createBankAccount, loading };
}

export function useUpdateBankAccount() {
  const [mutate, { loading }] = useMutation<{ updateBankAccount: MutationEnvelope }>(UPDATE_BANK_ACCOUNT);
  const updateBankAccount = async (id: string, input: Partial<BankAccountInput>): Promise<MutationEnvelope> => {
    const res = await mutate({ variables: { id, input } });
    return envelope(res.data?.updateBankAccount);
  };
  return { updateBankAccount, loading };
}

export function useDeleteBankAccount() {
  const [mutate, { loading }] = useMutation<{ deleteBankAccount: MutationEnvelope }>(DELETE_BANK_ACCOUNT);
  const deleteBankAccount = async (id: string): Promise<MutationEnvelope> => {
    const res = await mutate({ variables: { id } });
    return envelope(res.data?.deleteBankAccount);
  };
  return { deleteBankAccount, loading };
}

export function useSetDefaultBankAccount() {
  const [mutate, { loading }] = useMutation<{ setDefaultBankAccount: MutationEnvelope }>(SET_DEFAULT_BANK_ACCOUNT);
  const setDefaultBankAccount = async (id: string): Promise<MutationEnvelope> => {
    const res = await mutate({ variables: { id } });
    return envelope(res.data?.setDefaultBankAccount);
  };
  return { setDefaultBankAccount, loading };
}
