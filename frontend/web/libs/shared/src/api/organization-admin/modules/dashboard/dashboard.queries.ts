/**
 * Organizer Dashboard GraphQL Queries (Organization Admin App)
 *
 * Read-only overview data for the organizer dashboard: headline stats, upcoming
 * events, and the recent-activity feed. All resolved by booking-service.
 *
 * @see backend/booking-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

/**
 * Headline metrics for the dashboard cards (revenue, tickets, events, attendees, balances).
 */
export const MY_DASHBOARD_STATS = gql`
  query MyDashboardStats {
    myDashboardStats {
      totalRevenue
      revenueChange
      revenueCurrency
      totalTicketsSold
      ticketsSoldChange
      activeEvents
      eventsChange
      eventsEndingThisWeek
      totalAttendees
      attendeesChange
      pendingPayouts
      availableBalance
    }
  }
`;

/**
 * Upcoming events summary for the dashboard list.
 */
export const MY_UPCOMING_EVENTS = gql`
  query MyUpcomingEvents($limit: Int) {
    myUpcomingEvents(limit: $limit) {
      id
      title
      eventDateTime
      ticketsSold
      totalCapacity
      status
      revenue
      currency
    }
  }
`;

/**
 * Revenue per COMPLETE calendar month, oldest first.
 *
 * The partial current month is excluded server-side — a part-month column
 * next to full months reads as a revenue collapse.
 *
 * @see frontend/web/docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md §B1
 */
export const MY_REVENUE_SERIES = gql`
  query MyRevenueSeries($months: Int) {
    myRevenueSeries(months: $months) {
      periodStart
      revenue
      ticketsSold
      currency
    }
  }
`;

/**
 * Sold-ticket breakdown by tier.
 *
 * `totalSold` is the denominator every row's share is computed against, and is
 * printed on the tile — a rate without its denominator is not checkable.
 *
 * @see frontend/web/docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md §B3
 */
export const MY_TICKET_MIX = gql`
  query MyTicketMix {
    myTicketMix {
      totalSold
      totalRevenue
      currency
      rows {
        name
        count
        revenue
      }
    }
  }
`;

/**
 * Gate attendance for the most recent event that has run. Null when none has.
 *
 * @see frontend/web/docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md §B4
 */
export const MY_CHECK_IN_RATE = gql`
  query MyCheckInRate {
    myCheckInRate {
      eventId
      eventTitle
      eventDateTime
      issued
      scanned
      ratePercent
    }
  }
`;

/**
 * Withdrawable balance plus the escrow hold on the next tranche.
 *
 * @see frontend/web/docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md §B2
 */
export const MY_PAYOUT_WINDOW = gql`
  query MyPayoutWindow {
    myPayoutWindow {
      availableNow
      pendingRelease
      currency
      windowOpenedAt
      nextReleaseAt
      windowDaysTotal
      daysElapsed
      daysRemaining
    }
  }
`;

/**
 * Escrow accounts the organizer can draw a payout from right now.
 *
 * `createPayoutRequest` requires an `escrowAccountId`, and every other escrow
 * query is admin-scoped — this is the organizer's only route to that id.
 */
export const MY_PAYOUT_SOURCES = gql`
  query MyPayoutSources {
    myPayoutSources {
      escrowAccountId
      eventId
      eventTitle
      availableAmount
      currency
      eligibleSince
    }
  }
`;

/**
 * Whether a payout can be requested for one event, and if not, why.
 *
 * Complements MY_PAYOUT_SOURCES rather than duplicating it: that query lists
 * what is already drawable and so omits, by construction, every event an
 * organizer actually asks about. "Why can't I withdraw yet" is not answerable
 * from a list the event is missing from.
 *
 * The same evaluation runs again server-side when the request is made — this
 * one shapes the button, that one guards the money.
 *
 * @see specs/finance/003-payouts-and-settlement/spec.md
 */
export const PAYOUT_ELIGIBILITY = gql`
  query PayoutEligibility($eventId: ID!) {
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

/**
 * Recent activity feed for the dashboard.
 */
export const MY_RECENT_ACTIVITY = gql`
  query MyRecentActivity($limit: Int) {
    myRecentActivity(limit: $limit) {
      id
      type
      message
      timestamp
      eventId
      eventTitle
      amount
      currency
    }
  }
`;
