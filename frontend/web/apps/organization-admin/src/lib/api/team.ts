'use client';

/**
 * Team operations beyond the shared module: full roster with permission
 * overrides, invitations (list/resend/revoke/with event grants), suspend,
 * reactivate, leave, ownership transfer and event access grants
 * (identity-service). Types are declared against the schema.
 */
import type { OrganizerOrgAccessGrantsQuery, OrganizerOrgAccessGrantsQueryVariables, OrganizerIncomingTransfersQuery, OrganizerIncomingTransfersQueryVariables, OrganizerEventAccessGrantsQuery, OrganizerEventAccessGrantsQueryVariables, OrganizerInvitationsQuery, OrganizerInvitationsQueryVariables, OrganizerPendingTransferQuery, OrganizerPendingTransferQueryVariables, OrganizerRosterQuery, OrganizerRosterQueryVariables } from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';

export type OrgRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'MARKETER' | 'CONTRIBUTOR';
export type EventRole = 'EVENT_OWNER' | 'EVENT_ADMIN' | 'EDITOR' | 'CHECK_IN' | 'VIEWER';

export interface RosterMember {
  id: string;
  userId: string;
  role: OrgRole;
  status: string;
  joinedAt: string | null;
  lastActiveAt: string | null;
  customPermissions: string[] | null;
  deniedPermissions: string[] | null;
  user: { id: string; fullName: string | null; username: string | null } | null;
}

export interface InvitationRow {
  id: string;
  email: string | null;
  phoneNumber: string | null;
  inviteeName: string | null;
  proposedRole: OrgRole;
  eventAccessGrants: Array<{ eventId: string; role: EventRole; expiresAt: string | null }> | null;
  message: string | null;
  expiresAt: string;
  status: string;
  createdAt: string;
}

export interface AccessGrantRow {
  id: string;
  userId: string;
  user: { id: string; fullName: string | null } | null;
  eventId: string;
  eventRole: EventRole;
  reason: string | null;
  status: string;
  expiresAt: string | null;
}

export interface OwnershipTransferRow {
  id: string;
  newOwnerId: string;
  newOwner: { id: string; fullName: string | null } | null;
  status: string;
  expiresAt: string;
  initiatedAt: string;
}

export const ROSTER = gql`
  query OrganizerRoster {
    myOrganization {
      id
      ownerId
      members {
        id
        userId
        role
        status
        joinedAt
        lastActiveAt
        customPermissions
        deniedPermissions
        user {
          id
          fullName
          username
        }
      }
    }
  }
`;

export const PENDING_INVITATIONS = gql`
  query OrganizerInvitations($organizationId: ID!) {
    pendingInvitations(organizationId: $organizationId, pagination: { page: 0, size: 100 }) {
      content {
        id
        email
        phoneNumber
        inviteeName
        proposedRole
        eventAccessGrants {
          eventId
          role
          expiresAt
        }
        message
        expiresAt
        status
        createdAt
      }
    }
  }
`;

export const EVENT_ACCESS_GRANTS = gql`
  query OrganizerEventAccessGrants($eventId: ID!) {
    eventAccessGrants(eventId: $eventId, pagination: { page: 0, size: 100 }) {
      content {
        id
        userId
        user {
          id
          fullName
        }
        eventId
        eventRole
        reason
        status
        expiresAt
      }
    }
  }
`;

/** Every event-access grant across the organization (the "All events" view). */
export const ORG_ACCESS_GRANTS = gql`
  query OrganizerOrgAccessGrants($organizationId: ID!) {
    organizationEventAccessGrants(organizationId: $organizationId, pagination: { page: 0, size: 100 }) {
      content {
        id userId user { id fullName } eventId eventRole reason status expiresAt
      }
    }
  }
`;

/** Ownership offered to the signed-in person (they are the nominee). */
export const INCOMING_TRANSFERS = gql`
  query OrganizerIncomingTransfers {
    myPendingOwnershipTransfers {
      id status expiresAt reason
      organization { id name }
      currentOwner { id fullName }
    }
  }
`;

export const PENDING_OWNERSHIP_TRANSFER = gql`
  query OrganizerPendingTransfer($organizationId: ID!) {
    pendingOwnershipTransfer(organizationId: $organizationId) {
      id
      newOwnerId
      newOwner {
        id
        fullName
      }
      status
      expiresAt
      initiatedAt
    }
  }
`;

const M = {
  updateRole: gql`
    mutation OrganizerUpdateMemberRole($memberId: ID!, $input: UpdateMemberRoleInput!) {
      updateMemberRole(memberId: $memberId, input: $input) {
        id
        role
      }
    }
  `,
  suspend: gql`
    mutation OrganizerSuspendMember($memberId: ID!, $reason: String) {
      suspendMember(memberId: $memberId, reason: $reason) {
        id
        status
      }
    }
  `,
  reactivate: gql`
    mutation OrganizerReactivateMember($memberId: ID!) {
      reactivateMember(memberId: $memberId) {
        id
        status
      }
    }
  `,
  remove: gql`
    mutation OrganizerRemoveMember($memberId: ID!, $reason: String) {
      removeMember(memberId: $memberId, reason: $reason)
    }
  `,
  leave: gql`
    mutation OrganizerLeave($organizationId: ID!) {
      leaveOrganization(organizationId: $organizationId)
    }
  `,
  invite: gql`
    mutation OrganizerInvite($organizationId: ID!, $input: InviteMemberInput!) {
      inviteTeamMember(organizationId: $organizationId, input: $input) {
        id
        email
      }
    }
  `,
  resend: gql`
    mutation OrganizerResendInvite($invitationId: ID!) {
      resendInvitation(invitationId: $invitationId) {
        id
        status
      }
    }
  `,
  revoke: gql`
    mutation OrganizerRevokeInvite($invitationId: ID!) {
      revokeInvitation(invitationId: $invitationId) {
        id
        status
      }
    }
  `,
  initiateTransfer: gql`
    mutation OrganizerInitiateTransfer($organizationId: ID!, $newOwnerId: ID!, $reason: String) {
      initiateOwnershipTransfer(organizationId: $organizationId, newOwnerId: $newOwnerId, reason: $reason) {
        id
        status
      }
    }
  `,
  cancelTransfer: gql`
    mutation OrganizerCancelTransfer($organizationId: ID!) {
      cancelOwnershipTransfer(organizationId: $organizationId) {
        id
        status
      }
    }
  `,
  requestCode: gql`
    mutation OrganizerRequestTransferCode($token: String!) {
      requestOwnershipTransferCode(token: $token)
    }
  `,
  acceptTransfer: gql`
    mutation OrganizerAcceptTransfer($token: String!, $confirmationCode: String!) {
      acceptOwnershipTransfer(token: $token, confirmationCode: $confirmationCode) { id status }
    }
  `,
  declineTransfer: gql`
    mutation OrganizerDeclineTransfer($token: String!) {
      declineOwnershipTransfer(token: $token) { id status }
    }
  `,
  grant: gql`
    mutation OrganizerGrantAccess($eventId: ID!, $organizationId: ID!, $userId: ID!, $role: EventRole!, $reason: String, $expiresAt: DateTime) {
      grantEventAccess(eventId: $eventId, organizationId: $organizationId, userId: $userId, role: $role, reason: $reason, expiresAt: $expiresAt) {
        id
        status
      }
    }
  `,
  updateGrant: gql`
    mutation OrganizerUpdateGrant($accessId: ID!, $newRole: EventRole, $expiresAt: DateTime) {
      updateEventAccess(accessId: $accessId, newRole: $newRole, expiresAt: $expiresAt) {
        id
        eventRole
      }
    }
  `,
  revokeGrant: gql`
    mutation OrganizerRevokeGrant($accessId: ID!, $reason: String) {
      revokeEventAccess(accessId: $accessId, reason: $reason) {
        id
        status
      }
    }
  `,
};

export function useRoster() {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerRosterQuery, OrganizerRosterQueryVariables>(ROSTER, { fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  const data = dataState === 'complete' ? raw : undefined;
  const org = data?.myOrganization ?? null;
  return { organizationId: org?.id ?? null, ownerId: org?.ownerId ?? null, members: org?.members ?? [], loading, error, refetch };
}

export function useInvitations(organizationId: string | null) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerInvitationsQuery, OrganizerInvitationsQueryVariables>(PENDING_INVITATIONS, {
    variables: { organizationId: organizationId ?? '' },
    skip: !organizationId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { invitations: data?.pendingInvitations?.content ?? [], loading, error, refetch };
}

export function useEventAccessGrants(eventId: string | null) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerEventAccessGrantsQuery, OrganizerEventAccessGrantsQueryVariables>(EVENT_ACCESS_GRANTS, {
    variables: { eventId: eventId ?? '' },
    skip: !eventId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { grants: data?.eventAccessGrants?.content ?? [], loading, error, refetch };
}

export function useOrgAccessGrants(organizationId: string | null) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerOrgAccessGrantsQuery, OrganizerOrgAccessGrantsQueryVariables>(ORG_ACCESS_GRANTS, {
    variables: { organizationId: organizationId ?? '' },
    skip: !organizationId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { grants: data?.organizationEventAccessGrants?.content ?? [], loading, error, refetch };
}

export function useIncomingTransfers() {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerIncomingTransfersQuery, OrganizerIncomingTransfersQueryVariables>(INCOMING_TRANSFERS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { transfers: (data?.myPendingOwnershipTransfers ?? []).filter((t) => t.status === 'PENDING'), loading, error, refetch };
}

export function useOwnershipTransfer(organizationId: string | null) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerPendingTransferQuery, OrganizerPendingTransferQueryVariables>(PENDING_OWNERSHIP_TRANSFER, {
    variables: { organizationId: organizationId ?? '' },
    skip: !organizationId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { transfer: data?.pendingOwnershipTransfer ?? null, loading, error, refetch };
}

/** All team mutations in one hook; each returns the Apollo promise so callers can toast and refetch. */
export function useTeamActions() {
  const [updateRole] = useMutation(M.updateRole);
  const [suspend] = useMutation(M.suspend);
  const [reactivate] = useMutation(M.reactivate);
  const [remove] = useMutation(M.remove);
  const [leave] = useMutation(M.leave);
  const [invite] = useMutation(M.invite);
  const [resend] = useMutation(M.resend);
  const [revoke] = useMutation(M.revoke);
  const [initiateTransfer] = useMutation(M.initiateTransfer);
  const [cancelTransfer] = useMutation(M.cancelTransfer);
  const [requestCode] = useMutation(M.requestCode);
  const [acceptTransfer] = useMutation(M.acceptTransfer);
  const [declineTransfer] = useMutation(M.declineTransfer);
  const [grant] = useMutation(M.grant);
  const [updateGrant] = useMutation(M.updateGrant);
  const [revokeGrant] = useMutation(M.revokeGrant);
  return {
    changeRole: (organizationId: string, memberId: string, newRole: OrgRole, customPermissions: string[] | null = null, deniedPermissions: string[] | null = null) =>
      updateRole({ variables: { memberId, input: { organizationId, memberId, newRole, customPermissions, deniedPermissions } } }),
    suspendMember: (memberId: string, reason?: string) => suspend({ variables: { memberId, reason: reason ?? null } }),
    reactivateMember: (memberId: string) => reactivate({ variables: { memberId } }),
    removeMember: (memberId: string, reason?: string) => remove({ variables: { memberId, reason: reason ?? null } }),
    leaveOrganization: (organizationId: string) => leave({ variables: { organizationId } }),
    invitePerson: (
      organizationId: string,
      input: { email?: string | null; inviteeName?: string | null; phoneNumber?: string | null; role: OrgRole; message?: string | null; eventAccessGrants?: Array<{ eventId: string; role: EventRole }> | null }
    ) =>
      invite({
        variables: {
          organizationId,
          input: { email: input.email || null, inviteeName: input.inviteeName ?? null, phoneNumber: input.phoneNumber ?? null, role: input.role, message: input.message ?? null, eventAccessGrants: input.eventAccessGrants?.length ? input.eventAccessGrants : null },
        },
      }),
    resendInvitation: (invitationId: string) => resend({ variables: { invitationId } }),
    revokeInvitation: (invitationId: string) => revoke({ variables: { invitationId } }),
    initiateTransfer: (organizationId: string, newOwnerId: string, reason?: string) => initiateTransfer({ variables: { organizationId, newOwnerId, reason: reason ?? null } }),
    cancelTransfer: (organizationId: string) => cancelTransfer({ variables: { organizationId } }),
    requestTransferCode: (token: string) => requestCode({ variables: { token } }),
    acceptTransfer: (token: string, confirmationCode: string) => acceptTransfer({ variables: { token, confirmationCode } }),
    declineTransfer: (token: string) => declineTransfer({ variables: { token } }),
    grantAccess: (v: { eventId: string; organizationId: string; userId: string; role: EventRole; reason?: string; expiresAt?: string | null }) =>
      grant({ variables: { ...v, reason: v.reason ?? null, expiresAt: v.expiresAt ?? null } }),
    updateGrant: (accessId: string, newRole: EventRole, expiresAt: string | null) => updateGrant({ variables: { accessId, newRole, expiresAt } }),
    revokeGrant: (accessId: string, reason?: string) => revokeGrant({ variables: { accessId, reason: reason ?? null } }),
  };
}
