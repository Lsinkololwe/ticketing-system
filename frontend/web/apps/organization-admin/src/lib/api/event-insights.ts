'use client';

/** Per-event insights and attendee messaging (booking-service, organizer role). */
import type {
  HolderSegment,
  OrganizerMessageHoldersMutation,
  OrganizerMessageHoldersMutationVariables,
  OrganizerHolderAudienceQuery,
  OrganizerHolderAudienceQueryVariables,
  OrganizerHolderMessagesQuery,
  OrganizerHolderMessagesQueryVariables,
  OrganizerHeatmapQuery,
  OrganizerHeatmapQueryVariables,
  OrganizerSalesSeriesQuery,
  OrganizerSalesSeriesQueryVariables,
  SalesBucket,
} from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';

export const SALES_SERIES = gql`
  query OrganizerSalesSeries($eventId: ID!, $from: DateTime, $to: DateTime, $bucket: SalesBucket) {
    salesOverTime(eventId: $eventId, from: $from, to: $to, bucket: $bucket) {
      bucketStart bucketEnd tickets orders grossRevenue netRevenue refundedAmount
    }
  }
`;

export const PURCHASE_HEATMAP = gql`
  query OrganizerHeatmap($eventId: ID, $from: DateTime, $to: DateTime) {
    purchasesByDayAndHour(eventId: $eventId, from: $from, to: $to) { dayOfWeek hour purchases tickets revenue }
  }
`;

export const HOLDER_AUDIENCE = gql`
  query OrganizerHolderAudience($eventId: ID!, $segment: HolderSegment, $ticketTierId: String) {
    ticketHolderAudience(eventId: $eventId, segment: $segment, ticketTierId: $ticketTierId)
  }
`;

export const HOLDER_MESSAGES = gql`
  query OrganizerHolderMessages($eventId: ID!, $pagination: OffsetPaginationInput) {
    ticketHolderMessages(eventId: $eventId, pagination: $pagination) {
      data { id subject body segment ticketTierId recipientCount deliveredCount status sentBy createdAt }
      pagination { totalElements }
    }
  }
`;

const MESSAGE_HOLDERS = gql`
  mutation OrganizerMessageHolders($eventId: ID!, $input: MessageTicketHoldersInput!) {
    messageTicketHolders(eventId: $eventId, input: $input) { id status recipientCount deliveredCount }
  }
`;

export type SalesPointRow = OrganizerSalesSeriesQuery['salesOverTime'][number];
export type HeatCell = OrganizerHeatmapQuery['purchasesByDayAndHour'][number];
export type HolderMessageRow = OrganizerHolderMessagesQuery['ticketHolderMessages']['data'][number];

export function useSalesSeries(eventId: string, bucket: SalesBucket, range: { from: string | null; to: string | null }) {
  const { data: raw, dataState, loading, error } = useQuery<OrganizerSalesSeriesQuery, OrganizerSalesSeriesQueryVariables>(SALES_SERIES, {
    variables: { eventId, bucket, from: range.from, to: range.to },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { points: data?.salesOverTime ?? [], loading, error };
}

export function usePurchaseHeatmap(eventId: string) {
  const { data: raw, dataState, loading, error } = useQuery<OrganizerHeatmapQuery, OrganizerHeatmapQueryVariables>(PURCHASE_HEATMAP, {
    variables: { eventId, from: null, to: null },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { cells: data?.purchasesByDayAndHour ?? [], loading, error };
}

export function useHolderAudience(eventId: string, segment: HolderSegment, ticketTierId: string | null) {
  const { data: raw, dataState, loading, error } = useQuery<OrganizerHolderAudienceQuery, OrganizerHolderAudienceQueryVariables>(HOLDER_AUDIENCE, {
    variables: { eventId, segment, ticketTierId },
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { count: data?.ticketHolderAudience ?? null, loading, error };
}

export function useHolderMessages(eventId: string) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerHolderMessagesQuery, OrganizerHolderMessagesQueryVariables>(HOLDER_MESSAGES, {
    variables: { eventId, pagination: { page: 0, size: 20, sortBy: 'createdAt', sortDirection: 'DESC' } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { messages: data?.ticketHolderMessages.data ?? [], loading, error, refetch };
}

export function useMessageHolders(eventId: string) {
  const [mutate] = useMutation<OrganizerMessageHoldersMutation, OrganizerMessageHoldersMutationVariables>(MESSAGE_HOLDERS, { refetchQueries: [HOLDER_MESSAGES] });
  return {
    send: (input: { subject: string; body: string; segment: HolderSegment; ticketTierId: string | null }) => mutate({ variables: { eventId, input } }),
  };
}
