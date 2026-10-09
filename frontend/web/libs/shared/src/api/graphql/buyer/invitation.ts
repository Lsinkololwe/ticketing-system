'use client';

import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';
import type { InvitationByTokenQuery, InvitationByTokenQueryVariables } from '../../../types/graphql';

export interface InvitationPreviewRow {
  organizationName: string;
  organizationLogoUrl: string | null;
  proposedRole: string;
  inviterDisplayName: string;
  expiresAt: string;
}

const PREVIEW = gql`
  query InvitationByToken($token: String!) {
    invitationByToken(token: $token) {
      organizationName
      organizationLogoUrl
      proposedRole
      inviterDisplayName
      expiresAt
    }
  }
`;
const ACCEPT = gql`
  mutation AcceptInvitation($token: String!) {
    acceptInvitation(token: $token) {
      id
    }
  }
`;
const DECLINE = gql`
  mutation DeclineInvitation($token: String!) {
    declineInvitation(token: $token)
  }
`;

export function useInvitation(token: string) {
  const { data, loading, error, refetch } = useQuery<InvitationByTokenQuery, InvitationByTokenQueryVariables>(PREVIEW, { variables: { token }, skip: !token });
  const [accept, { loading: accepting }] = useMutation(ACCEPT);
  const [decline, { loading: declining }] = useMutation(DECLINE);
  return {
    invitation: data?.invitationByToken ?? null,
    loading,
    error,
    refetch,
    busy: accepting || declining,
    accept: () => accept({ variables: { token } }),
    decline: () => decline({ variables: { token } }),
  };
}
