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
  OrganizerPermissionsQuery,
  OrganizerPermissionsQueryVariables,
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

export const ORGANIZER_PERMISSIONS = gql`
  query OrganizerPermissions($organizationId: ID!) {
    myEffectivePermissions(organizationId: $organizationId) {
      permissions
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

/**
 * What the screens may offer, from the permission codes the platform reports for the caller in this
 * organization. The organization's own switches (managers see finance, admins request payouts) are
 * already applied by the platform when it builds that list, so nothing is re-derived from the role.
 */
export function capabilitiesFromPermissions(permissions: readonly string[] | null | undefined): OrgCapabilities {
  const held = new Set(permissions ?? []);
  const has = (...codes: string[]) => codes.some((code) => held.has(code));
  if (held.size === 0) return NONE;
  return {
    isOwner: has('organization:transfer'),
    canEditOrganization: has('organization:edit'),
    canManageTeam: has('team:invite', 'team:remove', 'team:role'),
    canWriteEvents: has('event:create', 'event:edit'),
    canRefund: has('ticket:refund'),
    canViewFinance: has('financial:view'),
    canRequestPayout: has('payout:request'),
    canManageMedia: has('media:manage'),
    canNotify: has('event:edit'),
    canViewBookings: has('attendee:view'),
    canViewTeam: has('team:view'),
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
  const perms = useQuery<OrganizerPermissionsQuery, OrganizerPermissionsQueryVariables>(ORGANIZER_PERMISSIONS, {
    variables: { organizationId: organization?.id ?? '' },
    skip: !organization?.id,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const permissions = perms.dataState === 'complete' ? perms.data?.myEffectivePermissions?.permissions : undefined;
  const capabilities = useMemo(() => capabilitiesFromPermissions(permissions), [permissions]);
  return {
    organization,
    organizationId: organization?.id ?? null,
    membership,
    role,
    capabilities,
    permissions: permissions ?? [],
    loading: ctx.loading || (Boolean(organization?.id) && ((mem.loading && !membership) || (perms.loading && !permissions))),
    error: ctx.error ?? mem.error ?? null,
    refetch: async () => {
      await ctx.refetch();
      await mem.refetch();
    },
  };
}
