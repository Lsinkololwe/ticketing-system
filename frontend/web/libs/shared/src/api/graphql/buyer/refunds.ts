'use client';

import { gql } from '@apollo/client';
import { useLazyQuery, useMutation, useQuery } from '@apollo/client/react';
import type { MyRefundRequestsQuery, MyRefundRequestsQueryVariables } from '../../../types/graphql';

export interface RefundQuote {
  ticketId: string;
  ticketNumber: string;
  eventId: string;
  eventDate: string;
  originalAmount: string | number;
  daysBeforeEvent: number;
  refundPercentage: number;
  refundAmount: string | number;
  platformRetains: string | number;
  policyApplied: string;
  isEligible: boolean;
  ineligibleReason: string | null;
}

export type RefundRequestRow = NonNullable<MyRefundRequestsQuery['myRefundRequests']>['data'][number];

const QUOTE = gql`
  query CalculateRefundAmount($ticketId: String!) {
    calculateRefundAmount(ticketId: $ticketId) {
      ticketId
      ticketNumber
      eventId
      eventDate
      originalAmount
      daysBeforeEvent
      refundPercentage
      refundAmount
      platformRetains
      policyApplied
      isEligible
      ineligibleReason
    }
  }
`;
const CREATE = gql`
  mutation CreateUserRefundRequest($input: CreateRefundRequestInput!) {
    createUserRefundRequest(input: $input) {
      id
      status
    }
  }
`;
const LIST = gql`
  query MyRefundRequests($pagination: OffsetPaginationInput) {
    myRefundRequests(pagination: $pagination) {
      data {
        id
        requestId
        ticketId
        ticketNumber
        eventId
        refundAmount
        refundPercentage
        currency
        status
        reason
        rejectionReason
        requestedAt
      }
      pagination {
        totalElements
        hasNext
      }
    }
  }
`;

export function useRefundQuote() {
  const [run, { data, loading, error }] = useLazyQuery<{ calculateRefundAmount: RefundQuote }>(QUOTE, { fetchPolicy: 'network-only' });
  return { quote: data?.calculateRefundAmount ?? null, loading, error, load: (ticketId: string) => run({ variables: { ticketId } }) };
}

export function useCreateRefund(requestedById: string) {
  const [mutate, { loading }] = useMutation(CREATE, { refetchQueries: [LIST] });
  return {
    loading,
    create: (ticketId: string, reason: string, idempotencyKey: string) =>
      mutate({ variables: { input: { ticketId, reason, requestedById, idempotencyKey } } }),
  };
}

export function useMyRefunds(size = 50) {
  const { data, loading, error, refetch } = useQuery<MyRefundRequestsQuery, MyRefundRequestsQueryVariables>(LIST, {
    variables: { pagination: { page: 0, size, sortBy: null, sortDirection: null } },
    fetchPolicy: 'cache-and-network',
  });
  return { refunds: (data?.myRefundRequests?.data ?? []) as RefundRequestRow[], loading, error, refetch };
}

// Refund reasons are the platform's `REFUND_REASON` reference list: read them with useReferenceOptions('REFUND_REASON').
