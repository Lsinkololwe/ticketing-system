/**
 * Platform summary query — the admin dashboard's hero tiles.
 *
 * Every field is computed by a MongoDB aggregation in booking-service
 * (`PlatformSummaryRepositoryImpl`), so this is one round trip for the whole
 * strip rather than a count query per tile.
 *
 * @see backend/booking-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

export const PLATFORM_SUMMARY = gql`
  query PlatformSummary {
    platformSummary {
      # Money
      totalTicketRevenue
      totalEscrowBalance
      availableForPayout
      primaryCurrency

      # Volume
      totalTicketsSold
      totalTransactions
      pendingTransactions
      failedTransactions

      # Payouts — the "at risk" tile reads pending payout exposure
      totalPayoutRequests
      pendingPayoutRequests
      totalPayoutAmount
    }
  }
`;
