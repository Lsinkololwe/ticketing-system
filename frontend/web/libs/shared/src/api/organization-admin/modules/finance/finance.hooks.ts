'use client';

/**
 * React Hooks for Organizer Finance (Organization Admin App)
 *
 * Overview + transactions read from the JWT identity; payouts and bank accounts
 * are keyed by organizerId (the org owner). Money movement is enforced
 * server-side.
 *
 * None of `createPayoutRequest`, `createBankAccount`, `updateBankAccount`,
 * `deleteBankAccount` or `setDefaultBankAccount` return a `success`/`message`/
 * `errors` envelope on this schema — each returns the entity (or, for delete,
 * a bare id) directly. The mutation hooks below build the envelope the UI
 * expects from whether the call resolved, not from a field the wire never
 * sends.
 */

import { useQuery, useMutation } from '@apollo/client/react';
import type { FetchPolicy } from '@apollo/client';
import {
  MY_FINANCE_OVERVIEW,
  MY_TRANSACTIONS,
  PAYOUTS_BY_ORGANIZER,
  BANK_ACCOUNTS_BY_ORGANIZER,
  CREATE_PAYOUT_REQUEST,
  CREATE_BANK_ACCOUNT,
  UPDATE_BANK_ACCOUNT,
  DELETE_BANK_ACCOUNT,
  SET_DEFAULT_BANK_ACCOUNT,
} from './finance.queries';
import type {
  PayoutMethod,
  OrganizerTransactionType,
  MyFinanceOverviewQuery,
  MyFinanceOverviewQueryVariables,
  MyTransactionsQuery,
  MyTransactionsQueryVariables,
  PayoutsByOrganizerQuery,
  PayoutsByOrganizerQueryVariables,
  BankAccountsByOrganizerQuery,
  BankAccountsByOrganizerQueryVariables,
  CreatePayoutRequestMutation,
  CreatePayoutRequestMutationVariables,
  CreateBankAccountMutation,
  CreateBankAccountMutationVariables,
  UpdateBankAccountMutation,
  UpdateBankAccountMutationVariables,
  DeleteBankAccountMutation,
  DeleteBankAccountMutationVariables,
  SetDefaultBankAccountMutation,
  SetDefaultBankAccountMutationVariables,
} from '../../../../types/graphql';

// ---------------------------------------------------------------------------
// View-model row types — indexed access into the generated query types, so a
// selection change in finance.queries.ts surfaces here as a compile error.
// ---------------------------------------------------------------------------

export type FinanceOverview = MyFinanceOverviewQuery['myFinanceOverview'];
export type TransactionRowVM = MyTransactionsQuery['myTransactions']['content'][number];
export type PayoutRowVM = PayoutsByOrganizerQuery['payoutRequestsByOrganizer']['data'][number];
export type BankAccountVM = BankAccountsByOrganizerQuery['bankAccountsByOrganizer'][number];

interface MutationEnvelope {
  success: boolean;
  message: string | null;
  errors: string[];
}

// ---------------------------------------------------------------------------
// Queries
// ---------------------------------------------------------------------------

export function useMyFinanceOverview(options?: { fetchPolicy?: FetchPolicy; skip?: boolean }) {
  const { data, loading, error, refetch } = useQuery<
    MyFinanceOverviewQuery,
    MyFinanceOverviewQueryVariables
  >(MY_FINANCE_OVERVIEW, {
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
    skip: options?.skip ?? false,
  });
  return { overview: data?.myFinanceOverview ?? null, loading, error, refetch };
}

export function useMyTransactions(options?: {
  filter?: { type?: string; eventId?: string };
  size?: number;
  fetchPolicy?: FetchPolicy;
  skip?: boolean;
}) {
  const { data, loading, error, refetch } = useQuery<MyTransactionsQuery, MyTransactionsQueryVariables>(
    MY_TRANSACTIONS,
    {
      variables: {
        filter: options?.filter
          ? {
              type: (options.filter.type as OrganizerTransactionType | undefined) ?? null,
              eventId: options.filter.eventId ?? null,
              endDate: null,
              maxAmount: null,
              minAmount: null,
              startDate: null,
            }
          : null,
        pagination: { page: 0, size: options?.size ?? 25, sortBy: null, sortDirection: null },
      },
      fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
      errorPolicy: 'all',
      skip: options?.skip ?? false,
    }
  );
  // `errorPolicy: 'all'` makes Apollo type `data` as deeply partial, since a
  // partial GraphQL response is possible alongside errors. Absent an error,
  // the response matches the query exactly, so the read site trusts that.
  const page = data?.myTransactions as MyTransactionsQuery['myTransactions'] | undefined;
  return {
    transactions: page?.content ?? [],
    totalElements: page?.totalElements ?? 0,
    loading,
    error,
    refetch,
  };
}

export function useMyPayouts(organizerId: string | null | undefined, options?: {
  size?: number;
  fetchPolicy?: FetchPolicy;
}) {
  const { data, loading, error, refetch } = useQuery<
    PayoutsByOrganizerQuery,
    PayoutsByOrganizerQueryVariables
  >(PAYOUTS_BY_ORGANIZER, {
    variables: {
      organizerId: organizerId ?? '',
      pagination: { page: 0, size: options?.size ?? 25, sortBy: null, sortDirection: null },
    },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: !organizerId,
  });
  const page = data?.payoutRequestsByOrganizer as
    | PayoutsByOrganizerQuery['payoutRequestsByOrganizer']
    | undefined;
  return {
    payouts: page?.data ?? [],
    loading,
    error,
    refetch,
  };
}

export function useMyBankAccounts(organizerId: string | null | undefined, options?: {
  fetchPolicy?: FetchPolicy;
}) {
  const { data, loading, error, refetch } = useQuery<
    BankAccountsByOrganizerQuery,
    BankAccountsByOrganizerQueryVariables
  >(BANK_ACCOUNTS_BY_ORGANIZER, {
    variables: { organizerId: organizerId ?? '' },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: !organizerId,
  });
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
  payoutMethod: PayoutMethod;
  notes?: string | null;
  /**
   * Client-supplied key that makes retrying this request safe.
   *
   * Sending the same key twice returns the ORIGINAL payout rather than
   * creating a second one for the same money; sending it with a different
   * body is refused rather than replayed. Required by the schema.
   *
   * The key must be stable across retries of the same user intent — generate
   * it once when the user opens the payout dialog, NOT per submit attempt, or
   * every retry carries a fresh key and the protection does nothing.
   */
  idempotencyKey: string;
}

export function useCreatePayoutRequest() {
  const [mutate, { loading }] = useMutation<
    CreatePayoutRequestMutation,
    CreatePayoutRequestMutationVariables
  >(CREATE_PAYOUT_REQUEST);
  const createPayout = async (input: CreatePayoutInput): Promise<MutationEnvelope> => {
    try {
      const res = await mutate({
        variables: {
          input: {
            organizerId: input.organizerId,
            eventId: input.eventId ?? null,
            escrowAccountId: input.escrowAccountId,
            bankAccountId: input.bankAccountId,
            requestedAmount: String(input.requestedAmount),
            currency: input.currency,
            payoutMethod: input.payoutMethod,
            notes: input.notes ?? null,
            idempotencyKey: input.idempotencyKey,
            metadata: null,
          },
        },
      });
      if (!res.data?.createPayoutRequest) {
        return { success: false, message: 'Payout request failed', errors: ['Payout request failed'] };
      }
      return { success: true, message: null, errors: [] };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Payout request failed';
      return { success: false, message, errors: [message] };
    }
  };
  return { createPayout, loading };
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
  const [mutate, { loading }] = useMutation<
    CreateBankAccountMutation,
    CreateBankAccountMutationVariables
  >(CREATE_BANK_ACCOUNT);
  const createBankAccount = async (input: BankAccountInput): Promise<MutationEnvelope> => {
    try {
      const res = await mutate({
        variables: {
          input: {
            organizerId: input.organizerId ?? '',
            accountHolderName: input.accountHolderName,
            bankName: input.bankName,
            bankCode: input.bankCode ?? null,
            branchName: input.branchName ?? null,
            branchCode: input.branchCode ?? null,
            accountNumber: input.accountNumber,
            accountType: input.accountType ?? null,
            currency: input.currency ?? 'ZMW',
            swiftCode: input.swiftCode ?? null,
            isDefault: input.isDefault ?? null,
          },
        },
      });
      if (!res.data?.createBankAccount) {
        return { success: false, message: 'Could not add the bank account', errors: ['Could not add the bank account'] };
      }
      return { success: true, message: null, errors: [] };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Could not add the bank account';
      return { success: false, message, errors: [message] };
    }
  };
  return { createBankAccount, loading };
}

export function useUpdateBankAccount() {
  const [mutate, { loading }] = useMutation<
    UpdateBankAccountMutation,
    UpdateBankAccountMutationVariables
  >(UPDATE_BANK_ACCOUNT);
  const updateBankAccount = async (
    id: string,
    input: Partial<BankAccountInput>
  ): Promise<MutationEnvelope> => {
    try {
      const res = await mutate({
        variables: {
          id,
          input: {
            accountHolderName: input.accountHolderName ?? null,
            accountNumber: input.accountNumber ?? null,
            accountType: input.accountType ?? null,
            bankCode: input.bankCode ?? null,
            bankName: input.bankName ?? null,
            branchCode: input.branchCode ?? null,
            branchName: input.branchName ?? null,
            isDefault: input.isDefault ?? null,
            swiftCode: input.swiftCode ?? null,
          },
        },
      });
      if (!res.data?.updateBankAccount) {
        return { success: false, message: 'Could not update the bank account', errors: ['Could not update the bank account'] };
      }
      return { success: true, message: null, errors: [] };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Could not update the bank account';
      return { success: false, message, errors: [message] };
    }
  };
  return { updateBankAccount, loading };
}

export function useDeleteBankAccount() {
  const [mutate, { loading }] = useMutation<
    DeleteBankAccountMutation,
    DeleteBankAccountMutationVariables
  >(DELETE_BANK_ACCOUNT);
  const deleteBankAccount = async (id: string): Promise<MutationEnvelope> => {
    try {
      const res = await mutate({ variables: { id } });
      if (res.data?.deleteBankAccount == null) {
        return { success: false, message: 'Could not remove the bank account', errors: ['Could not remove the bank account'] };
      }
      return { success: true, message: null, errors: [] };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Could not remove the bank account';
      return { success: false, message, errors: [message] };
    }
  };
  return { deleteBankAccount, loading };
}

export function useSetDefaultBankAccount() {
  const [mutate, { loading }] = useMutation<
    SetDefaultBankAccountMutation,
    SetDefaultBankAccountMutationVariables
  >(SET_DEFAULT_BANK_ACCOUNT);
  const setDefaultBankAccount = async (id: string): Promise<MutationEnvelope> => {
    try {
      const res = await mutate({ variables: { id } });
      if (!res.data?.setDefaultBankAccount) {
        return { success: false, message: 'Could not set the default bank account', errors: ['Could not set the default bank account'] };
      }
      return { success: true, message: null, errors: [] };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Could not set the default bank account';
      return { success: false, message, errors: [message] };
    }
  };
  return { setDefaultBankAccount, loading };
}
