'use client';

/**
 * Event list, detail and lifecycle operations for the organizer console
 * (catalog-service). Defined in the app because the shared module only covers
 * list/publish/unpublish/create. Types are written against the schema.
 */
import { OPEN_PAYOUT_STATUSES } from '@/lib/format/enumLabels';
import type { OrganizerEventFilterInput, EventStatus as GqlEventStatus, OrgEventCountsQuery, OrgEventCountsQueryVariables, OrgEventsConnectionQuery, OrgEventsConnectionQueryVariables, OrgDuplicateEventMutation, OrgDuplicateEventMutationVariables, OrgEventDetailQuery, OrgEventDetailQueryVariables, OrgEventPayoutsQuery, OrgEventPayoutsQueryVariables, OrgEventStatisticsQuery, OrgEventStatisticsQueryVariables,  } from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';

export type EventStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'CHANGES_REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'PUBLISHED'
  | 'CANCELLED'
  | 'COMPLETED';

export interface OrgEventRow {
  id: string;
  title: string;
  status: EventStatus;
  eventDateTime: string;
  endDateTime: string | null;
  locationName: string | null;
  cityName: string | null;
  bannerImageUrl: string | null;
  totalCapacity: number;
  soldTickets: number;
  revenue: string | null;
  currency: string | null;
  category: { id: string; name: string } | null;
  ticketTiers: Array<{ id: string; isActive: boolean }> | null;
}

/** Cursor-paginated list of the organizer's events; filters run on the server. */
export const ORG_EVENTS_CONNECTION = gql`
  query OrgEventsConnection($filter: OrganizerEventFilterInput, $pagination: CursorPaginationInput) {
    myEventsConnection(filter: $filter, pagination: $pagination) {
      edges {
        cursor
        node {
          id title status eventDateTime endDateTime locationName cityName bannerImageUrl totalCapacity soldTickets revenue currency
          category { id name }
          ticketTiers { id isActive }
        }
      }
      pageInfo { hasNextPage endCursor }
    }
  }
`;
/** One count per lifecycle status for the quick-filter chips. */
export const ORG_EVENT_COUNTS = gql`
  query OrgEventCounts {
    total: myEventCount
    draft: myEventCountByStatus(status: DRAFT)
    pending: myEventCountByStatus(status: PENDING_APPROVAL)
    changes: myEventCountByStatus(status: CHANGES_REQUESTED)
    approved: myEventCountByStatus(status: APPROVED)
    rejected: myEventCountByStatus(status: REJECTED)
    published: myEventCountByStatus(status: PUBLISHED)
    cancelled: myEventCountByStatus(status: CANCELLED)
    completed: myEventCountByStatus(status: COMPLETED)
  }
`;

const PAGE = 20;

export function useOrgEvents(filter?: OrganizerEventFilterInput) {
  const { data: raw, dataState, loading, error, refetch, fetchMore } = useQuery<OrgEventsConnectionQuery, OrgEventsConnectionQueryVariables>(ORG_EVENTS_CONNECTION, {
    variables: { filter: filter ?? null, pagination: { first: PAGE, after: null, last: null, before: null } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  const conn = data?.myEventsConnection;
  return {
    events: conn?.edges.map((e) => e.node) ?? [],
    hasMore: conn?.pageInfo.hasNextPage ?? false,
    loading,
    error,
    refetch,
    loadMore: async () => {
      if (!conn?.pageInfo.endCursor) return;
      await fetchMore({
        variables: { pagination: { first: PAGE, after: conn.pageInfo.endCursor, last: null, before: null } },
        updateQuery: (prev, { fetchMoreResult }) =>
          fetchMoreResult
            ? { ...prev, myEventsConnection: { ...fetchMoreResult.myEventsConnection, edges: [...prev.myEventsConnection.edges, ...fetchMoreResult.myEventsConnection.edges] } }
            : prev,
      });
    },
  };
}

export function useOrgEventCounts() {
  const { data: raw, dataState } = useQuery<OrgEventCountsQuery, OrgEventCountsQueryVariables>(ORG_EVENT_COUNTS, { fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  const d = dataState === 'complete' ? raw : undefined;
  if (!d) return null;
  const byStatus: Record<string, number> = {
    DRAFT: d.draft, PENDING_APPROVAL: d.pending, CHANGES_REQUESTED: d.changes, APPROVED: d.approved,
    REJECTED: d.rejected, PUBLISHED: d.published, CANCELLED: d.cancelled, COMPLETED: d.completed,
  };
  return { all: d.total, byStatus };
}

export type { GqlEventStatus };

export interface OrgTier {
  id: string;
  eventId: string;
  code: string;
  name: string;
  description: string | null;
  price: string;
  currency: string;
  quantity: number;
  soldQuantity: number;
  availableQuantity: number;
  minPerOrder: number | null;
  maxPerOrder: number | null;
  benefits: string[] | null;
  salesStartAt: string | null;
  salesEndAt: string | null;
  earlyBirdPrice: string | null;
  earlyBirdEndsAt: string | null;
  sortOrder: number;
  isActive: boolean;
  isHidden: boolean;
  accessCode: string | null;
}

export interface OrgEventDetail {
  id: string;
  title: string;
  description: string;
  status: EventStatus;
  eventDateTime: string;
  endDateTime: string;
  locationName: string | null;
  locationAddress: string | null;
  cityName: string | null;
  bannerImageUrl: string | null;
  totalCapacity: number;
  soldTickets: number;
  availableTickets: number;
  revenue: string | null;
  currency: string | null;
  refundPolicy: string | null;
  rejectionReason: string | null;
  publishedAt: string | null;
  submittedForApprovalAt: string | null;
  approvedAt: string | null;
  rejectedAt: string | null;
  createdAt: string | null;
  category: { id: string; name: string } | null;
  ticketTiers: OrgTier[] | null;
}


export const ORG_EVENT_DETAIL = gql`
  query OrgEventDetail($id: ID!) {
    event(id: $id) {
      id title description status eventDateTime endDateTime locationName locationAddress cityName
      bannerImageUrl totalCapacity soldTickets availableTickets revenue currency refundPolicy
      rejectionReason publishedAt submittedForApprovalAt approvedAt rejectedAt createdAt
      category { id name }
      ticketTiers { id eventId code name description price currency quantity soldQuantity availableQuantity minPerOrder maxPerOrder benefits salesStartAt salesEndAt earlyBirdPrice earlyBirdEndsAt sortOrder isActive isHidden accessCode }
    }
  }
`;

export function useOrgEventDetail(id: string) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrgEventDetailQuery, OrgEventDetailQueryVariables>(ORG_EVENT_DETAIL, {
    variables: { id },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { event: data?.event ?? null, loading, error, refetch };
}

export interface EventStatistics {
  totalTicketsAvailable: number;
  totalTicketsSold: number;
  totalTicketsRefunded: number;
  totalGrossRevenue: string | null;
  totalCommissionEarned: string | null;
  overallSalesPercentage: number;
  bestSellingTier: string | null;
}

export const ORG_EVENT_STATISTICS = gql`
  query OrgEventStatistics($id: ID!) {
    eventStatistics(eventId: $id) {
      totalTicketsAvailable totalTicketsSold totalTicketsRefunded totalGrossRevenue
      totalCommissionEarned overallSalesPercentage bestSellingTier
    }
  }
`;

export function useEventStatistics(id: string) {
  const { data: raw, dataState, loading, error } = useQuery<OrgEventStatisticsQuery, OrgEventStatisticsQueryVariables>(ORG_EVENT_STATISTICS, {
    variables: { id },
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { stats: data?.eventStatistics ?? null, loading, error };
}

/* ------------------------------------------------------------- lifecycle */

const SUBMIT = gql`mutation OrgSubmitEvent($eventId: ID!) { submitEventForApproval(eventId: $eventId) { id status } }`;
const PUBLISH = gql`mutation OrgPublishEvent($id: ID!) { publishEvent(id: $id) { id status } }`;
const UNPUBLISH = gql`mutation OrgUnpublishEvent($id: ID!) { unpublishEvent(id: $id) { id status } }`;
const RESCHEDULE = gql`
  mutation OrgRescheduleEvent($input: RescheduleEventInput!) {
    rescheduleEvent(input: $input) { id status eventDateTime endDateTime }
  }
`;
const CANCEL = gql`
  mutation OrgCancelEvent($id: ID!, $input: EventCancellationInput!) {
    cancelEvent(id: $id, input: $input) { ticketsAffected refundSagaInitiated event { id status } }
  }
`;
const DUPLICATE = gql`mutation OrgDuplicateEvent($eventId: ID!, $newTitle: String!) { duplicateEvent(eventId: $eventId, newTitle: $newTitle) { id title status } }`;
const DELETE = gql`mutation OrgDeleteEvent($id: ID!) { deleteEvent(id: $id) }`;

export function useEventLifecycle() {
  const opts = { refetchQueries: [ORG_EVENTS_CONNECTION, ORG_EVENT_COUNTS], awaitRefetchQueries: false };
  const [submit] = useMutation(SUBMIT, opts);
  const [publish] = useMutation(PUBLISH, opts);
  const [unpublish] = useMutation(UNPUBLISH, opts);
  const [reschedule] = useMutation(RESCHEDULE, opts);
  const [cancel] = useMutation(CANCEL, opts);
  const [duplicate] = useMutation<OrgDuplicateEventMutation, OrgDuplicateEventMutationVariables>(DUPLICATE, opts);
  const [remove] = useMutation(DELETE, opts);
  return {
    submit: (eventId: string) => submit({ variables: { eventId } }),
    publish: (id: string) => publish({ variables: { id } }),
    unpublish: (id: string) => unpublish({ variables: { id } }),
    reschedule: (eventId: string, newStartsAt: string, reason: string) =>
      reschedule({ variables: { input: { eventId, newStartsAt, reason } } }),
    cancel: (id: string, reason: string) =>
      cancel({ variables: { id, input: { reason, notifyAttendees: true, triggerRefunds: true } } }),
    duplicate: (eventId: string, newTitle: string) => duplicate({ variables: { eventId, newTitle } }),
    remove: (id: string) => remove({ variables: { id } }),
  };
}

/* ----------------------------------------------------------------- tiers */

export interface TierInput {
  code?: string;
  name: string;
  description?: string | null;
  price: string;
  currency?: string;
  quantity: number;
  minPerOrder?: number | null;
  maxPerOrder?: number | null;
  benefits?: string[];
  salesStartAt?: string | null;
  salesEndAt?: string | null;
  earlyBirdPrice?: string | null;
  earlyBirdEndsAt?: string | null;
  isHidden?: boolean;
  accessCode?: string | null;
}

const CREATE_TIER = gql`mutation OrgCreateTier($eventId: ID!, $input: CreateTicketTierInput!) { createTicketTier(eventId: $eventId, input: $input) { id } }`;
const UPDATE_TIER = gql`mutation OrgUpdateTier($tierId: ID!, $input: UpdateTicketTierInput!) { updateTicketTier(tierId: $tierId, input: $input) { id } }`;
const DELETE_TIER = gql`mutation OrgDeleteTier($tierId: ID!) { deleteTicketTier(tierId: $tierId) }`;
const ACTIVATE_TIER = gql`mutation OrgActivateTier($tierId: ID!) { activateTicketTier(tierId: $tierId) { id isActive } }`;
const DEACTIVATE_TIER = gql`mutation OrgDeactivateTier($tierId: ID!) { deactivateTicketTier(tierId: $tierId) { id isActive } }`;
const REORDER_TIERS = gql`mutation OrgReorderTiers($eventId: ID!, $tierIds: [ID!]!) { reorderTicketTiers(eventId: $eventId, tierIds: $tierIds) { id sortOrder } }`;

export function useTierActions(eventId: string) {
  const opts = { refetchQueries: [ORG_EVENT_DETAIL] };
  const [create] = useMutation(CREATE_TIER, opts);
  const [update] = useMutation(UPDATE_TIER, opts);
  const [del] = useMutation(DELETE_TIER, opts);
  const [activate] = useMutation(ACTIVATE_TIER, opts);
  const [deactivate] = useMutation(DEACTIVATE_TIER, opts);
  const [reorder] = useMutation(REORDER_TIERS, opts);
  return {
    create: (input: TierInput) =>
      create({ variables: { eventId, input: { currency: 'ZMW', code: input.code ?? input.name.replace(/[^A-Za-z]/g, '').slice(0, 6).toUpperCase(), ...input } } }),
    update: (tierId: string, input: Partial<TierInput>) => {
      const { code: _c, currency: _cur, ...rest } = input;
      void _c; void _cur;
      return update({ variables: { tierId, input: rest } });
    },
    remove: (tierId: string) => del({ variables: { tierId } }),
    setActive: (tierId: string, active: boolean) => (active ? activate : deactivate)({ variables: { tierId } }),
    reorder: (tierIds: string[]) => reorder({ variables: { eventId, tierIds } }),
  };
}

/* ------------------------------------------------- open payout for event */

export { OPEN_PAYOUT_STATUSES } from '@/lib/format/enumLabels';
export interface EventPayout {
  requestId: string;
  status: string;
  requestedAmount: string;
  currency: string;
}
export const EVENT_PAYOUTS = gql`
  query OrgEventPayouts($eventId: String!) {
    payoutRequestsByEvent(eventId: $eventId, pagination: { page: 0, size: 20 }) {
      data { requestId status requestedAmount currency }
    }
  }
`;

/** Lazy lookup used by the cancel flow: cancelling is refused while a payout is open. */
export function useEventPayoutsLazy() {
  const { refetch } = useQuery<OrgEventPayoutsQuery, OrgEventPayoutsQueryVariables>(EVENT_PAYOUTS, { skip: true, variables: { eventId: '' } });
  return async (eventId: string): Promise<EventPayout[]> => {
    const res = await refetch({ eventId });
    return (res.data?.payoutRequestsByEvent?.data ?? []).filter((p) => (OPEN_PAYOUT_STATUSES as readonly string[]).includes(p.status));
  };
}
