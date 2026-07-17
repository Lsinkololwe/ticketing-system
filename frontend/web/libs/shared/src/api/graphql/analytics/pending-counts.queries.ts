/**
 * Pending Counts GraphQL Query (Admin App)
 *
 * Federation-composed query that fans out across the three subgraphs to fetch
 * the "action center" pending queue sizes shown as dynamic sidebar badges.
 *
 * Federation note:
 *   In Apollo Federation 2 a single root query field can only be OWNED by one
 *   subgraph. The six counts span three services, so instead of one shared
 *   `PendingCounts` object we expose ONE grouped root field per subgraph, each
 *   owning its own object type:
 *     - identityPendingCounts  -> identity-service  (organizerApplications, documentVerifications)
 *     - catalogPendingCounts   -> catalog-service   (eventReviews)
 *     - bookingPendingCounts   -> booking-service   (payoutRequests, refundRequests)
 *   The Apollo Router composes these three root fields into the SINGLE query
 *   document below and fans the request out to each subgraph in parallel. The
 *   hook then maps the result onto the nav-item ids (and derives the combined
 *   "All Approvals" total client-side from already-aggregated counts).
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 * @see backend/catalog-service/src/main/resources/graphql/schema.graphqls
 * @see backend/booking-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

export const PENDING_COUNTS = gql`
  query PendingCounts {
    identityPendingCounts {
      organizerApplications
      documentVerifications
    }
    catalogPendingCounts {
      eventReviews
    }
    bookingPendingCounts {
      payoutRequests
      refundRequests
    }
  }
`;
