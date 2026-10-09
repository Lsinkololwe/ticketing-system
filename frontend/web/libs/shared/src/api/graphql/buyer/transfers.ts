'use client';

import { gql } from '@apollo/client';
import { useLazyQuery, useMutation, useQuery } from '@apollo/client/react';
import type {
  BuyerTransferRecipientQuery,
  BuyerTransferRecipientQueryVariables,
  BuyerMyTicketTransfersQuery,
  BuyerMyTicketTransfersQueryVariables,
  BuyerInitiateTicketTransferMutation,
  BuyerInitiateTicketTransferMutationVariables,
  BuyerCancelTicketTransferMutation,
  BuyerCancelTicketTransferMutationVariables,
  BuyerAcceptTicketTransferMutation,
  BuyerAcceptTicketTransferMutationVariables,
  BuyerDeclineTicketTransferMutation,
  BuyerDeclineTicketTransferMutationVariables,
  TransferChannel,
  TransferDirection,
  TicketTransferStatus,
} from '../../../types/graphql';

export type TicketTransferRow = BuyerMyTicketTransfersQuery['myTicketTransfers']['data'][number];

const RECIPIENT = gql`
  query BuyerTransferRecipient($channel: TransferChannel!, $value: String!) {
    transferRecipient(channel: $channel, value: $value) {
      displayName
      maskedContact
    }
  }
`;

const TRANSFERS = gql`
  query BuyerMyTicketTransfers($direction: TransferDirection, $status: TicketTransferStatus, $pagination: OffsetPaginationInput) {
    myTicketTransfers(direction: $direction, status: $status, pagination: $pagination) {
      data {
        id
        ticketId
        ticketNumber
        bookingNumber
        eventId
        eventTitle
        status
        direction
        fromDisplayName
        toDisplayName
        recipientMasked
        note
        createdAt
        expiresAt
        resolvedAt
      }
      pagination {
        totalElements
        hasNext
      }
    }
  }
`;

const INITIATE = gql`
  mutation BuyerInitiateTicketTransfer($input: InitiateTicketTransferInput!) {
    initiateTicketTransfer(input: $input) {
      id
      ticketId
      status
      recipientMasked
      toDisplayName
      expiresAt
    }
  }
`;

const CANCEL = gql`
  mutation BuyerCancelTicketTransfer($transferId: ID!) {
    cancelTicketTransfer(transferId: $transferId) {
      id
      status
    }
  }
`;

const ACCEPT = gql`
  mutation BuyerAcceptTicketTransfer($transferId: ID!) {
    acceptTicketTransfer(transferId: $transferId) {
      id
      ticketNumber
      status
    }
  }
`;

const DECLINE = gql`
  mutation BuyerDeclineTicketTransfer($transferId: ID!) {
    declineTicketTransfer(transferId: $transferId) {
      id
      status
    }
  }
`;

const REFRESH = ['BuyerMyTicketTransfers', 'BuyerMyBookings', 'GetMyTickets'];

/** Looks up who a ticket would go to. Answers null for an unknown contact, so existence is never disclosed. */
export function useTransferRecipient() {
  const [run, { loading }] = useLazyQuery<BuyerTransferRecipientQuery, BuyerTransferRecipientQueryVariables>(RECIPIENT, { fetchPolicy: 'network-only' });
  return {
    loading,
    lookup: async (channel: TransferChannel, value: string) => (await run({ variables: { channel, value } })).data?.transferRecipient ?? null,
  };
}

export interface TransfersOptions {
  direction?: TransferDirection;
  status?: TicketTransferStatus;
  size?: number;
  skip?: boolean;
}

/** The buyer's transfers, outgoing and incoming. */
export function useMyTicketTransfers({ direction, status, size = 50, skip }: TransfersOptions = {}) {
  const { data, loading, error, refetch } = useQuery<BuyerMyTicketTransfersQuery, BuyerMyTicketTransfersQueryVariables>(TRANSFERS, {
    variables: { direction: direction ?? null, status: status ?? null, pagination: { page: 0, size, sortBy: null, sortDirection: null } },
    skip,
    fetchPolicy: 'cache-and-network',
  });
  return { transfers: data?.myTicketTransfers.data ?? [], loading, error, refetch };
}

export function useTicketTransferActions() {
  const opts = { refetchQueries: REFRESH };
  const [initiate, i] = useMutation<BuyerInitiateTicketTransferMutation, BuyerInitiateTicketTransferMutationVariables>(INITIATE, opts);
  const [cancel, c] = useMutation<BuyerCancelTicketTransferMutation, BuyerCancelTicketTransferMutationVariables>(CANCEL, opts);
  const [accept, a] = useMutation<BuyerAcceptTicketTransferMutation, BuyerAcceptTicketTransferMutationVariables>(ACCEPT, opts);
  const [decline, d] = useMutation<BuyerDeclineTicketTransferMutation, BuyerDeclineTicketTransferMutationVariables>(DECLINE, opts);
  return {
    busy: i.loading || c.loading || a.loading || d.loading,
    initiate: (ticketId: string, channel: TransferChannel, recipient: string, note?: string) =>
      initiate({ variables: { input: { ticketId, channel, recipient, note: note || null } } }),
    cancel: (transferId: string) => cancel({ variables: { transferId } }),
    accept: (transferId: string) => accept({ variables: { transferId } }),
    decline: (transferId: string) => decline({ variables: { transferId } }),
  };
}
