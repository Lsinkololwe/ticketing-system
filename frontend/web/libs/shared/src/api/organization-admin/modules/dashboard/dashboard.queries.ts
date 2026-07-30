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
