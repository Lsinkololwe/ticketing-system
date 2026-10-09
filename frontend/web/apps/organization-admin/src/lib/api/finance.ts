'use client';

/**
 * Finance operations the shared organization-admin module does not expose yet:
 * escrow accounts + ledger, payout eligibility, payout cancel and bank-account
 * verification (booking-service). Types are declared against the schema.
 */
import type { OrganizerPayoutWalletQuery, OrganizerPayoutWalletQueryVariables, SetMobileMoneyAccountInput, OrganizerEscrowAccountsQuery, OrganizerEscrowAccountsQueryVariables, OrganizerEscrowTransactionsQuery, OrganizerEscrowTransactionsQueryVariables, OrganizerPayoutEligibilityQuery, OrganizerPayoutEligibilityQueryVariables } from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';

export interface EscrowAccountRow {
  id: string;
  accountNumber: string;
  eventId: string;
  eventTitle: string | null;
  currentBalance: string;
  totalDeposits: string;
  totalWithdrawals: string;
  totalRefunds: string;
  totalCommissions: string;
  pendingWithdrawals: string | null;
  currency: string;
  status: string;
  lockUntil: string | null;
  payoutEligibleAt: string | null;
}

export interface EscrowTransactionRow {
  id: string;
  type: string;
  category: string;
  amount: string;
  balanceAfter: string;
  currency: string;
  description: string | null;
  journalEntryId: string | null;
  timestamp: string;
}

export interface PayoutEligibilityRow {
  eligible: boolean;
  reasons: string[];
  availableAmount: string;
  currency: string;
  opensAt: string | null;
  minimumAmount: string;
}

export const MY_ESCROW_ACCOUNTS = gql`
  query OrganizerEscrowAccounts($pagination: OffsetPaginationInput) {
    myEscrowAccounts(pagination: $pagination) {
      data {
        id
        accountNumber
        eventId
        eventTitle
        currentBalance
        totalDeposits
        totalWithdrawals
        totalRefunds
        totalCommissions
        pendingWithdrawals
        currency
        status
        lockUntil
        payoutEligibleAt
      }
      pagination {
        totalElements
        totalPages
        currentPage
        hasNext
      }
    }
  }
`;

export const ESCROW_TRANSACTIONS = gql`
  query OrganizerEscrowTransactions($escrowAccountId: String!, $pagination: OffsetPaginationInput) {
    escrowTransactions(escrowAccountId: $escrowAccountId, pagination: $pagination) {
      data {
        id
        type
        category
        amount
        balanceAfter
        currency
        description
        journalEntryId
        timestamp
      }
      pagination {
        totalElements
        totalPages
        currentPage
        hasNext
      }
    }
  }
`;

export const PAYOUT_ELIGIBILITY = gql`
  query OrganizerPayoutEligibility($eventId: ID!) {
    payoutEligibility(eventId: $eventId) {
      eligible
      reasons
      availableAmount
      currency
      opensAt
      minimumAmount
    }
  }
`;

export const CANCEL_PAYOUT_REQUEST = gql`
  mutation OrganizerCancelPayoutRequest($payoutRequestId: ID!, $reason: String!) {
    cancelPayoutRequest(payoutRequestId: $payoutRequestId, reason: $reason) {
      id
      status
    }
  }
`;

export const START_BANK_VERIFICATION = gql`
  mutation OrganizerStartBankVerification($id: ID!) {
    startBankVerification(id: $id) {
      id
      status
    }
  }
`;

export const CONFIRM_BANK_VERIFICATION = gql`
  mutation OrganizerConfirmBankVerification($id: ID!, $amount: BigDecimal!) {
    confirmBankVerification(id: $id, amount: $amount) {
      id
      status
      isVerified
    }
  }
`;

export function useMyEscrowAccounts(size = 50) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerEscrowAccountsQuery, OrganizerEscrowAccountsQueryVariables>(MY_ESCROW_ACCOUNTS, {
    variables: { pagination: { page: 0, size, sortBy: null, sortDirection: null } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { accounts: data?.myEscrowAccounts?.data ?? [], loading, error, refetch };
}

export function useEscrowTransactions(escrowAccountId: string | null, page = 0, size = 12) {
  const { data: raw, dataState, loading, error } = useQuery<OrganizerEscrowTransactionsQuery, OrganizerEscrowTransactionsQueryVariables>(ESCROW_TRANSACTIONS, {
    variables: { escrowAccountId: escrowAccountId ?? '', pagination: { page, size, sortBy: null, sortDirection: null } },
    skip: !escrowAccountId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return {
    rows: data?.escrowTransactions?.data ?? [],
    total: data?.escrowTransactions?.pagination?.totalElements ?? 0,
    loading,
    error,
  };
}

/** Eligibility for one event, fetched when the payout dialog picks an escrow. */
export function usePayoutEligibility(eventId: string | null) {
  const { data: raw, dataState, loading, error } = useQuery<OrganizerPayoutEligibilityQuery, OrganizerPayoutEligibilityQueryVariables>(PAYOUT_ELIGIBILITY, {
    variables: { eventId: eventId ?? '' },
    skip: !eventId,
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { eligibility: data?.payoutEligibility ?? null, loading, error };
}

export function useCancelPayout() {
  const [mutate, { loading }] = useMutation(CANCEL_PAYOUT_REQUEST);
  return { cancelPayout: (payoutRequestId: string, reason: string) => mutate({ variables: { payoutRequestId, reason } }), loading };
}

export function useBankVerification() {
  const [start, s] = useMutation(START_BANK_VERIFICATION);
  const [confirm, c] = useMutation(CONFIRM_BANK_VERIFICATION);
  return {
    start: (id: string) => start({ variables: { id } }),
    confirm: (id: string, amount: string) => confirm({ variables: { id, amount } }),
    loading: s.loading || c.loading,
  };
}

/* --------------------------------------------------------- mobile wallet */

/** The organization's mobile-wallet payout destination (identity-service `payoutConfig`). */
export const PAYOUT_WALLET = gql`
  query OrganizerPayoutWallet {
    myOrganization {
      id
      payoutConfig {
        mobileMoneyAccount {
          provider
          maskedPhoneNumber
          accountHolderName
          verified
          status
          rejectionReason
          suspended
          suspendedReason
          testDepositSentAt
          verificationAttemptsLeft
        }
      }
    }
  }
`;

export const SET_MOBILE_MONEY_ACCOUNT = gql`
  mutation OrganizerSetMobileMoneyAccount($organizationId: ID!, $input: SetMobileMoneyAccountInput!) {
    setMobileMoneyAccount(organizationId: $organizationId, input: $input) {
      id
      payoutConfig {
        mobileMoneyAccount { provider maskedPhoneNumber accountHolderName verified status testDepositSentAt verificationAttemptsLeft }
      }
    }
  }
`;

export type WalletRow = NonNullable<NonNullable<NonNullable<OrganizerPayoutWalletQuery['myOrganization']>['payoutConfig']>['mobileMoneyAccount']>;

export function usePayoutWallet() {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerPayoutWalletQuery, OrganizerPayoutWalletQueryVariables>(PAYOUT_WALLET, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  const wallet = data?.myOrganization?.payoutConfig?.mobileMoneyAccount ?? null;
  return { organizationId: data?.myOrganization?.id ?? null, wallet: wallet && wallet.status !== 'NONE' ? wallet : null, loading, error, refetch };
}

export function useSetMobileMoneyAccount() {
  const [mutate, state] = useMutation(SET_MOBILE_MONEY_ACCOUNT, { refetchQueries: [PAYOUT_WALLET] });
  return {
    saveWallet: (organizationId: string, input: SetMobileMoneyAccountInput) => mutate({ variables: { organizationId, input } }),
    ...state,
  };
}
