'use client';

import { useMemo } from 'react';
import { useReferenceOptions, type ReferenceOption } from '@pml.tickets/shared/api/graphql/shared/reference';

type RoleOption = ReferenceOption<{ invitable?: boolean }>;

export interface TeamRoles {
  /** Every organization role, highest first, as the platform lists them. */
  orgRoles: RoleOption[];
  /** The organization roles a person may be invited or changed to (everything but the owner). */
  invitableRoles: RoleOption[];
  /** Every event role. */
  eventRoles: RoleOption[];
  /** The event roles that may be granted. */
  grantableEventRoles: RoleOption[];
  /** The platform's description of a role, or an empty string when it no longer lists it. */
  describe: (role: string | null | undefined) => string;
  /** The platform's name for a role (falls back to the code). */
  nameOf: (role: string | null | undefined) => string;
  loading: boolean;
  /** Either list is empty or unreadable: show the unavailable state. */
  unavailable: boolean;
}

/** Organization and event roles from the platform's ORGANIZATION_ROLE / EVENT_ROLE lists. */
export function useTeamRoles(): TeamRoles {
  const org = useReferenceOptions<{ invitable?: boolean }>('ORGANIZATION_ROLE');
  const event = useReferenceOptions<{ invitable?: boolean }>('EVENT_ROLE');
  return useMemo(
    () => ({
      orgRoles: org.options,
      invitableRoles: org.options.filter((o) => o.metadata.invitable === true),
      eventRoles: event.options,
      grantableEventRoles: event.options.filter((o) => o.metadata.invitable === true),
      describe: (role) => (role ? org.byCode.get(role)?.description ?? event.byCode.get(role)?.description ?? '' : ''),
      nameOf: (role) => (role ? org.byCode.get(role)?.label ?? event.byCode.get(role)?.label ?? role : ''),
      loading: org.loading || event.loading,
      unavailable: (!org.loading && org.empty) || (!event.loading && event.empty),
    }),
    [org, event],
  );
}
