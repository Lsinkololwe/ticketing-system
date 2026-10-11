/**
 * Organizer Finance GraphQL (Organization Admin App)
 *
 * Finance overview + transactions (organizerId from JWT), and payout requests /
 * bank accounts (keyed by organizerId = the org owner). Money movement is gated
 * server-side (Organization.canPerform → isApproved); these documents surface the
 * backend envelope so the UI can react.
 *
 * @see backend/booking-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

export const MY_FINANCE_OVERVIEW = gql`
  query MyFinanceOverview {
    myFinanceOverview {
      availableBalance
      pendingBalance
      totalEarned
      currency
      pendingPayoutRequests
      lastPayoutDate
      lastPayoutAmount
      totalTicketRevenue
      totalRefunds
      platformFees
      netEarnings
      earningsThisMonth
      earningsLastMonth
      monthlyGrowth
    }
  }
`;

export const MY_TRANSACTIONS = gql`
  query MyTransactions($filter: OrganizerTransactionFilterInput, $pagination: OffsetPaginationInput) {
    myTransactions(filter: $filter, pagination: $pagination) {
      content {
        id
        type
        description
        amount
        currency
        status
        timestamp
        eventId
        eventTitle
        reference
      }
      totalElements
      totalPages
      page
      size
      hasNext
      hasPrevious
    }
  }
`;

export const PAYOUTS_BY_ORGANIZER = gql`
  query PayoutsByOrganizer($organizerId: String!, $pagination: OffsetPaginationInput) {
    payoutRequestsByOrganizer(organizerId: $organizerId, pagination: $pagination) {
      data {
        id
        requestId
        organizerId
        eventId
        eventTitle
        event {
          id
          title
        }
        requestedAmount
        settledAmount
        currency
        status
        payoutMethod
        requestedAt
        approvedAt
        processedAt
        rejectionReason
        bankName
        accountNumber
        bankAccountName
        notes
      }
      pagination {
        totalElements
        totalPages
        currentPage
        hasNext
        hasPrevious
      }
    }
  }
`;

export const BANK_ACCOUNTS_BY_ORGANIZER = gql`
  query BankAccountsByOrganizer($organizerId: String!) {
    bankAccountsByOrganizer(organizerId: $organizerId) {
      id
      organizerId
      accountHolderName
      bankName
      bankCode
      branchName
      branchCode
      accountNumber
      accountType
      currency
      swiftCode
      isDefault
      isVerified
      status
      createdAt
    }
  }
`;

export const CREATE_PAYOUT_REQUEST = gql`
  mutation CreatePayoutRequest($input: CreatePayoutRequestInput!) {
    createPayoutRequest(input: $input) {
      id
      requestId
      status
      requestedAmount
    }
  }
`;

export const CREATE_BANK_ACCOUNT = gql`
  mutation CreateBankAccount($input: CreateBankAccountInput!) {
    createBankAccount(input: $input) {
      id
    }
  }
`;

export const UPDATE_BANK_ACCOUNT = gql`
  mutation UpdateBankAccount($id: ID!, $input: UpdateBankAccountInput!) {
    updateBankAccount(id: $id, input: $input) {
      id
    }
  }
`;

export const DELETE_BANK_ACCOUNT = gql`
  mutation DeleteBankAccount($id: ID!) {
    deleteBankAccount(id: $id)
  }
`;

export const SET_DEFAULT_BANK_ACCOUNT = gql`
  mutation SetDefaultBankAccount($id: ID!) {
    setDefaultBankAccount(id: $id) {
      id
      isDefault
    }
  }
`;
