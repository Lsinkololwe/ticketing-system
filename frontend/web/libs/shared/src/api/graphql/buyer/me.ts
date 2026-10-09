'use client';

import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';
import type { UpdateUserInput, MeQuery, BuyerRequestAccountDeletionMutation, BuyerRequestAccountDeletionMutationVariables, BuyerCancelAccountDeletionMutation, BuyerUpdateMyProfileMutation, BuyerUpdateMyProfileMutationVariables } from '../../../types/graphql';

export interface MeRow {
  id: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string | null;
  fullName: string;
  deletionRequestedAt?: string | null;
  deletionScheduledFor?: string | null;
}

const ME = gql`
  query Me {
    me {
      id
      firstName
      lastName
      displayName
      fullName
      deletionRequestedAt
      deletionScheduledFor
    }
  }
`;
const UPDATE = gql`
  mutation BuyerUpdateMyProfile($input: UpdateUserInput!) {
    updateMyProfile(input: $input) {
      id
      firstName
      lastName
      displayName
      fullName
    }
  }
`;

const REQUEST_DELETION = gql`
  mutation BuyerRequestAccountDeletion($reason: String) {
    requestAccountDeletion(reason: $reason) {
      id
      deletionRequestedAt
      deletionScheduledFor
    }
  }
`;
const CANCEL_DELETION = gql`
  mutation BuyerCancelAccountDeletion {
    cancelAccountDeletion {
      id
      deletionRequestedAt
      deletionScheduledFor
    }
  }
`;

export function useMe() {
  const { data, loading, error, refetch } = useQuery<MeQuery>(ME, { fetchPolicy: 'cache-and-network' });
  const [mutate, { loading: saving }] = useMutation<BuyerUpdateMyProfileMutation, BuyerUpdateMyProfileMutationVariables>(UPDATE, { refetchQueries: [ME] });
  const [requestDeletion, { loading: requestingDeletion }] = useMutation<BuyerRequestAccountDeletionMutation, BuyerRequestAccountDeletionMutationVariables>(REQUEST_DELETION);
  const [cancelDeletion, { loading: cancellingDeletion }] = useMutation<BuyerCancelAccountDeletionMutation>(CANCEL_DELETION);
  return {
    deleting: requestingDeletion || cancellingDeletion,
    requestDeletion: (reason?: string) => requestDeletion({ variables: { reason: reason ?? null } }),
    cancelDeletion: () => cancelDeletion(),
    me: data?.me ?? null,
    loading,
    error,
    refetch,
    saving,
    save: (input: { firstName: string; lastName: string }) => mutate({ variables: { input: input as UpdateUserInput } }),
  };
}
