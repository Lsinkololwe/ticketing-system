'use client';

/**
 * Who the signed-in person is inside the organization they work for. The console never
 * assumes ownership: the role comes from the membership (`myOrganization` +
 * `myOrganizationMembership`), and every capability below is derived from it.
 */
import type {
  OrganizerContextQuery,
  OrganizerContextQueryVariables,
  OrganizerMembershipQuery,
  OrganizerMembershipQueryVariables,
} from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { useMemo } from 'react';
import type { OrgRole } from '@/lib/api/team';

export const ORGANIZER_CONTEXT = gql`
  query OrganizerContext {
    myOrganization {
      id
      name
      slug
      status
      ownerId
      commissionRate
      deletionRequestedAt
      deletionScheduledFor
      settings {
        id
        managersCanViewFinancials
        adminsCanRequestPayouts
      }
    }
  }
`;

export const ORGANIZER_MEMBERSHIP = gql`
  query OrganizerMembership($organizationId: ID!) {
    myOrganizationMembership(organizationId: $organizationId) {
      id
      userId
      role
      status
      customPermissions
      deniedPermissions
    }
  }
`;

export interface OrgCapabilities {
  /** Owner only: bank accounts, deletion, ownership transfer. */
  isOwner: boolean;
  canEditOrganization: boolean;
  canManageTeam: boolean;
  canWriteEvents: boolean;
  canRefund: boolean;
  canViewFinance: boolean;
  canRequestPayout: boolean;
  canManageMedia: boolean;
  canNotify: boolean;
  /** Bookings, attendees and ticket scanning. */
  canViewBookings: boolean;
  canViewTeam: boolean;
}

const NONE: OrgCapabilities = {
  isOwner: false,
  canEditOrganization: false,
  canManageTeam: false,
  canWriteEvents: false,
  canRefund: false,
  canViewFinance: false,
  canRequestPayout: false,
  canManageMedia: false,
  canNotify: false,
  canViewBookings: false,
  canViewTeam: false,
};

/** Capabilities of one membership role (mirrors the catalogue on the Team page). */
export function capabilitiesFor(
  role: OrgRole | null,
  settings?: { managersCanViewFinancials?: boolean | null; adminsCanRequestPayouts?: boolean | null } | null
): OrgCapabilities {
  if (!role) return NONE;
  const owner = role === 'OWNER';
  const admin = owner || role === 'ADMIN';
  const manager = admin || role === 'MANAGER';
  return {
    isOwner: owner,
    canEditOrganization: admin,
    canManageTeam: admin,
    canWriteEvents: manager,
    canRefund: manager,
    canViewFinance: admin || (role === 'MANAGER' && Boolean(settings?.managersCanViewFinancials)),
    canRequestPayout: owner || (role === 'ADMIN' && Boolean(settings?.adminsCanRequestPayouts)),
    canManageMedia: manager || role === 'MARKETER',
    canNotify: manager,
    canViewBookings: manager || role === 'CONTRIBUTOR',
    canViewTeam: manager,
  };
}

export function useOrgContext() {
  const ctx = useQuery<OrganizerContextQuery, OrganizerContextQueryVariables>(ORGANIZER_CONTEXT, { fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  const ctxData = ctx.dataState === 'complete' ? ctx.data : undefined;
  const organization = ctxData?.myOrganization ?? null;
  const mem = useQuery<OrganizerMembershipQuery, OrganizerMembershipQueryVariables>(ORGANIZER_MEMBERSHIP, {
    variables: { organizationId: organization?.id ?? '' },
    skip: !organization?.id,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const memData = mem.dataState === 'complete' ? mem.data : undefined;
  const membership = memData?.myOrganizationMembership ?? null;
  const role = (membership?.role ?? null) as OrgRole | null;
  const capabilities = useMemo(() => capabilitiesFor(role, organization?.settings), [role, organization?.settings]);
  return {
    organization,
    organizationId: organization?.id ?? null,
    membership,
    role,
    capabilities,
    loading: ctx.loading || (Boolean(organization?.id) && mem.loading && !membership),
    error: ctx.error ?? mem.error ?? null,
    refetch: async () => {
      await ctx.refetch();
      await mem.refetch();
    },
  };
}
