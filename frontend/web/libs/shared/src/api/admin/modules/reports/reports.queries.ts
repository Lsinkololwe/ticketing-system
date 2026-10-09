import { gql } from '@apollo/client';

export const USER_STATS = gql`
  query AdminUserStats {
    userStats {
      totalUsers
      organizers
      attendees
      adminUsers
      verifiedUsers
      activeUsers
      suspendedUsers
      lockedUsers
      pendingVerificationUsers
      newUsersThisMonth
      newUsersThisWeek
      growthRate
    }
  }
`;

export const FINANCIAL_REPORT = gql`
  query AdminFinancialReport($filter: FinancialReportFilterInput!) {
    financialReport(filter: $filter) {
      startDate
      endDate
      totalRevenue
      totalCommissions
      totalRefunds
      totalPayouts
      pendingPayouts
      escrowBalance
      netPlatformRevenue
      dataPoints {
        period
        revenue
        commissions
        refunds
        payouts
        ticketsSold
      }
    }
  }
`;

export const CHARGEBACK_STATS = gql`
  query AdminChargebackStats {
    chargebackStats {
      totalCount
      pendingCount
      disputedCount
      wonCount
      lostCount
      totalAmount
      recoveredAmount
      writtenOffAmount
      chargebackRate
      winRate
    }
  }
`;

export const TRANSACTION_STATS = gql`
  query AdminTransactionStats {
    transactionStats {
      totalTransactions
      completedTransactions
      failedTransactions
      pendingTransactions
      timedOutTransactions
      totalVolume
      totalCommissions
      averageTransactionValue
    }
  }
`;

export const TICKET_STATS = gql`
  query AdminTicketStats {
    ticketStats {
      totalTickets
      issuedTickets
      validatedTickets
      refundPendingTickets
      refundedTickets
      cancelledTickets
      expiredTickets
      ticketsByStatus {
        status
        count
        percentage
      }
    }
  }
`;

export const EXPORT_FINANCIAL_REPORT = gql`
  query AdminExportFinancialReport($filter: FinancialReportFilterInput!, $format: ExportFormat!) {
    exportFinancialReport(filter: $filter, format: $format) {
      downloadUrl
      expiresAt
      format
      generatedAt
      fileName
      errorMessage
    }
  }
`;
