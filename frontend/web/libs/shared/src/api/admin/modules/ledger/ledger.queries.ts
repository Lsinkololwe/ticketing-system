/**
 * Ledger documents (booking-service): chart of accounts, journal entries,
 * trial balance, platform accounts and reconciliation. Admin-tagged operations
 * only. Typed with local interfaces in `ledger.hooks.ts`.
 */
import { gql } from '@apollo/client';

export const CHART_OF_ACCOUNTS = gql`
  query LedgerChartOfAccounts {
    chartOfAccounts {
      id
      accountCode
      accountName
      accountType
      subType
      parentAccountCode
      currency
      isActive
      description
      normalBalance
    }
  }
`;

export const CREATE_CHART_OF_ACCOUNTS_ENTRY = gql`
  mutation LedgerCreateAccount($input: CreateChartOfAccountsInput!) {
    createChartOfAccountsEntry(input: $input) {
      id
      accountCode
    }
  }
`;

export const UPDATE_CHART_OF_ACCOUNTS_ENTRY = gql`
  mutation LedgerUpdateAccount($id: ID!, $input: CreateChartOfAccountsInput!) {
    updateChartOfAccountsEntry(id: $id, input: $input) {
      id
      accountCode
    }
  }
`;

export const DEACTIVATE_CHART_OF_ACCOUNTS_ENTRY = gql`
  mutation LedgerDeactivateAccount($id: ID!) {
    deactivateChartOfAccountsEntry(id: $id) {
      id
      isActive
    }
  }
`;

export const SEED_CHART_OF_ACCOUNTS = gql`
  mutation LedgerSeedChart {
    seedChartOfAccounts
  }
`;

export const JOURNAL_ENTRY_FIELDS = gql`
  fragment LedgerJournalEntryFields on JournalEntry {
    id
    entryNumber
    correlationId
    entryDate
    description
    type
    status
    createdBy
    postedBy
    postedAt
    reversedBy
    reversedAt
    reversalEntryId
    reversedByEntryId
    totalDebits
    totalCredits
    isBalanced
    lines {
      accountCode
      accountName
      debit
      credit
      description
    }
  }
`;

export const JOURNAL_ENTRIES = gql`
  ${JOURNAL_ENTRY_FIELDS}
  query LedgerJournalEntries($filter: JournalEntryFilterInput, $pagination: OffsetPaginationInput) {
    journalEntries(filter: $filter, pagination: $pagination) {
      data {
        ...LedgerJournalEntryFields
      }
      pagination {
        totalCount
        pageSize
        currentPage
        totalPages
        hasNextPage
        hasPreviousPage
      }
    }
  }
`;

export const CREATE_JOURNAL_ENTRY = gql`
  ${JOURNAL_ENTRY_FIELDS}
  mutation LedgerCreateJournalEntry($input: CreateJournalEntryInput!) {
    createJournalEntry(input: $input) {
      ...LedgerJournalEntryFields
    }
  }
`;

export const POST_JOURNAL_ENTRY = gql`
  ${JOURNAL_ENTRY_FIELDS}
  mutation LedgerPostJournalEntry($id: ID!) {
    postJournalEntry(id: $id) {
      ...LedgerJournalEntryFields
    }
  }
`;

export const REVERSE_JOURNAL_ENTRY = gql`
  ${JOURNAL_ENTRY_FIELDS}
  mutation LedgerReverseJournalEntry($id: ID!, $reason: String!) {
    reverseJournalEntry(id: $id, reason: $reason) {
      ...LedgerJournalEntryFields
    }
  }
`;

export const TRIAL_BALANCE = gql`
  query LedgerTrialBalance($asOf: DateTime) {
    trialBalance(asOf: $asOf) {
      accountCode
      accountName
      accountType
      debitBalance
      creditBalance
      netBalance
    }
  }
`;

export const PLATFORM_ACCOUNTS = gql`
  query LedgerPlatformAccounts {
    platformAccounts {
      id
      accountType
      name
      balance
      currency
      lastUpdatedAt
    }
  }
`;

export const RECONCILIATION_RUNS = gql`
  query LedgerReconciliationRuns($filter: ReconciliationFilterInput, $pagination: OffsetPaginationInput) {
    reconciliationRuns(filter: $filter, pagination: $pagination) {
      data {
        id
        reconciliationDate
        type
        status
        dataSource
        expectedTotal
        actualTotal
        variance
        matchedCount
        unmatchedCount
        runBy
        startedAt
        completedAt
        notes
        items {
          externalId
          internalId
          externalAmount
          internalAmount
          status
          resolution
          resolvedBy
          resolvedAt
        }
      }
      pagination {
        totalCount
        pageSize
        currentPage
        totalPages
        hasNextPage
        hasPreviousPage
      }
    }
  }
`;

export const START_RECONCILIATION = gql`
  mutation LedgerStartReconciliation($input: StartReconciliationInput!) {
    startReconciliation(input: $input) {
      id
      status
      notes
      unmatchedCount
    }
  }
`;

export const RESOLVE_RECONCILIATION_ITEM = gql`
  mutation LedgerResolveReconciliationItem($runId: ID!, $input: ResolveReconciliationItemInput!) {
    resolveReconciliationItem(runId: $runId, input: $input) {
      id
      status
      notes
      unmatchedCount
    }
  }
`;

export const COMPLETE_RECONCILIATION = gql`
  mutation LedgerCompleteReconciliation($runId: ID!, $notes: String) {
    completeReconciliation(runId: $runId, notes: $notes) {
      id
      status
      notes
      unmatchedCount
    }
  }
`;

export const FAIL_RECONCILIATION = gql`
  mutation LedgerFailReconciliation($runId: ID!, $reason: String!) {
    failReconciliation(runId: $runId, reason: $reason) {
      id
      status
      notes
      unmatchedCount
    }
  }
`;

export const RECORD_GATEWAY_SETTLEMENT = gql`
  mutation LedgerRecordGatewaySettlement($input: RecordGatewaySettlementInput!) {
    recordGatewaySettlement(input: $input) {
      id
      entryNumber
      status
    }
  }
`;
