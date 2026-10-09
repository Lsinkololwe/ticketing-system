/**
 * Catalog admin operations the admin console needs beyond the shared event
 * module: a richer events list (featured flag, category/city ids), the event
 * detail page, the approval timeline, provinces/cities and the per-event
 * finance look-ups used to block cancellation.
 *
 * @see backend/catalog-service/src/main/resources/graphql/schema.graphqls
 * @see backend/booking-service/src/main/resources/graphql/schema.graphqls
 */
import { gql } from '@apollo/client';

export const ADMIN_EVENTS_TABLE = gql`
  query AdminEventsTable($filter: EventFilterInput, $pagination: OffsetPaginationInput) {
    events(filter: $filter, pagination: $pagination) {
      content {
        id
        title
        status
        published
        featured
        eventDateTime
        endDateTime
        organizerId
        organizerName
        organizationId
        locationName
        cityName
        categoryId
        totalCapacity
        soldTickets
        currency
        minTicketPrice
        submittedForApprovalAt
        approvalDeadline
        isOverdue
        category {
          id
          name
        }
      }
      pageNumber
      pageSize
      totalElements
      totalPages
      hasNext
      hasPrevious
    }
  }
`;

export const ADMIN_EVENT_DETAIL = gql`
  query AdminEventDetail($id: ID!) {
    event(id: $id) {
      id
      title
      description
      status
      published
      publishedAt
      featured
      eventDateTime
      endDateTime
      organizerId
      organizerName
      organizationId
      organizerEmail
      organizerPhone
      locationName
      locationAddress
      cityName
      categoryId
      category {
        id
        name
      }
      location {
        id
        city
        province
        country
      }
      totalCapacity
      soldTickets
      availableTickets
      currency
      minTicketPrice
      maxTicketPrice
      refundPolicy
      cancellationPolicy
      bannerImageUrl
      thumbnailImageUrl
      galleryImages
      submittedForApprovalAt
      approvalDeadline
      approvedAt
      approvedBy
      rejectedAt
      rejectedBy
      rejectionReason
      isOverdue
      approvalBlockers
      createdAt
      updatedAt
      ticketTiers {
        id
        name
        code
        price
        currency
        quantity
        soldQuantity
        isActive
        isHidden
        earlyBirdPrice
        earlyBirdEndsAt
      }
    }
  }
`;

export const EVENT_APPROVAL_TIMELINE = gql`
  query EventApprovalTimeline($eventId: String!) {
    approvalTimeline(eventId: $eventId) {
      eventId
      currentStatus
      assignedReviewerName
      submittedAt
      slaDeadline
      isOverdue
      hoursUntilDeadline
      submissionCount
      hasActiveEscalation
      escalation {
        status
      }
      timelineEvents {
        id
        timestamp
        action
        actorName
        actorRole
        description
        comments
        isEscalationRelated
      }
    }
  }
`;

export const PROVINCES_ADMIN = gql`
  query ProvincesAdmin {
    provinces {
      id
      name
      code
      country
      cityCount
      isActive
    }
  }
`;

export const CITIES_ADMIN = gql`
  query CitiesAdmin($provinceId: String) {
    cities(provinceId: $provinceId) {
      id
      name
      code
      provinceId
      province
      country
      eventCount
      isActive
    }
  }
`;

/** Escrow account of one event (booking-service). Null when none opened yet. */
export const ESCROW_BY_EVENT = gql`
  query EscrowByEvent($eventId: String!) {
    escrowAccountByEvent(eventId: $eventId) {
      id
      accountNumber
      eventId
      currentBalance
      totalDeposits
      totalWithdrawals
      totalRefunds
      totalCommissions
      pendingWithdrawals
      currency
      status
    }
  }
`;

export const PAYOUTS_BY_EVENT = gql`
  query PayoutsByEvent($eventId: String!) {
    payoutRequests(filter: { eventId: $eventId }, pagination: { page: 0, size: 50 }) {
      data {
        id
        requestId
        requestedAmount
        currency
        status
        payoutMethod
        requestedAt
      }
    }
  }
`;

export const REFUNDS_BY_EVENT = gql`
  query RefundsByEvent($eventId: String!) {
    refundRequests(filter: { eventId: $eventId }, pagination: { page: 0, size: 6 }) {
      data {
        id
        requestId
        refundAmount
        currency
        status
        requestedAt
      }
      pagination {
        totalCount
      }
    }
  }
`;
