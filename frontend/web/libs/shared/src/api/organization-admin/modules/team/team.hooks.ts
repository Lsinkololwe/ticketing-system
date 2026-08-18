'use client';

/**
 * React hooks for the organizer's team roster.
 *
 * Thin, typed wrappers over the team operations. Types come from codegen —
 * never hand-defined — and safe defaults are returned so an organization with
 * no members renders an empty state rather than throwing on undefined access.
 */

import { useQuery, useMutation } from '@apollo/client/react';
import type { FetchPolicy } from '@apollo/client';
import {
  MY_TEAM_MEMBERS,
  UPDATE_MEMBER_ROLE,
  REMOVE_MEMBER,
  SUSPEND_MEMBER,
  REACTIVATE_MEMBER,
  INVITE_TEAM_MEMBER,
} from './team.queries';
import type { OrganizationMember, OrganizationRole } from '../../../../types/graphql';

interface QueryOptions {
  fetchPolicy?: FetchPolicy;
  skip?: boolean;
}

/**
 * Members of the signed-in organizer's own organization.
 *
 * `members` defaults to an empty array. An organization always has at least its
 * owner, so an empty result means the query has not resolved or the caller has
 * no organization — both of which the screen renders as an empty state, never
 * as "0 members".
 */
export function useMyTeamMembers(options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<{
    myOwnedOrganization: { id: string; members: OrganizationMember[] | null } | null;
  }>(MY_TEAM_MEMBERS, {
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
    skip: options?.skip ?? false,
  });

  return {
    members: data?.myOwnedOrganization?.members ?? [],
    organizationId: data?.myOwnedOrganization?.id ?? null,
    loading,
    error,
    refetch,
  };
}

/**
 * Change a member's role.
 *
 * Deliberately does NOT write an optimistic response. Role changes are
 * authorisation decisions — showing a member as an Admin before the server has
 * agreed would misrepresent what they can actually do.
 */
export function useUpdateMemberRole() {
  const [mutate, { loading }] = useMutation<{ updateMemberRole: OrganizationMember | null }>(
    UPDATE_MEMBER_ROLE
  );

  const updateRole = async (memberId: string, role: OrganizationRole) => {
    const result = await mutate({ variables: { memberId, input: { role } } });
    return result.data?.updateMemberRole ?? null;
  };

  return { updateRole, loading };
}

/** Remove a member from the organization. */
export function useRemoveMember() {
  const [mutate, { loading }] = useMutation<{ removeMember: boolean }>(REMOVE_MEMBER);

  const removeMember = async (memberId: string, reason?: string) => {
    const result = await mutate({ variables: { memberId, reason: reason ?? null } });
    return result.data?.removeMember ?? false;
  };

  return { removeMember, loading };
}

/** Suspend a member without removing them. */
export function useSuspendMember() {
  const [mutate, { loading }] = useMutation<{ suspendMember: OrganizationMember | null }>(
    SUSPEND_MEMBER
  );

  const suspendMember = async (memberId: string, reason?: string) => {
    const result = await mutate({ variables: { memberId, reason: reason ?? null } });
    return result.data?.suspendMember ?? null;
  };

  return { suspendMember, loading };
}

/** Reverse a suspension. */
export function useReactivateMember() {
  const [mutate, { loading }] = useMutation<{ reactivateMember: OrganizationMember | null }>(
    REACTIVATE_MEMBER
  );

  const reactivateMember = async (memberId: string) => {
    const result = await mutate({ variables: { memberId } });
    return result.data?.reactivateMember ?? null;
  };

  return { reactivateMember, loading };
}

export interface InviteResult {
  email: string;
  success: boolean;
  error: string | null;
}

/**
 * Invite team members.
 *
 * Sends one mutation per invitee and reports each outcome separately. A batch
 * that reported a single pass/fail would tell the user "some invites failed"
 * without saying which — and they would have no way to retry only those.
 *
 * Invites are sent SEQUENTIALLY rather than in parallel: identity-service rate
 * limits invitation sends, and firing ten at once turns a partial success into
 * a confusing cascade of throttling errors.
 */
export function useInviteTeamMembers() {
  const [mutate, { loading }] = useMutation<{
    inviteTeamMember: { id: string; email: string } | null;
  }>(INVITE_TEAM_MEMBER, { refetchQueries: [MY_TEAM_MEMBERS] });

  const inviteMembers = async (
    organizationId: string,
    invites: Array<{ email: string; role: OrganizationRole; message?: string | null }>
  ): Promise<InviteResult[]> => {
    const results: InviteResult[] = [];

    for (const invite of invites) {
      try {
        await mutate({
          variables: {
            organizationId,
            input: {
              email: invite.email,
              role: invite.role,
              message: invite.message ?? null,
            },
          },
        });
        results.push({ email: invite.email, success: true, error: null });
      } catch (error) {
        results.push({
          email: invite.email,
          success: false,
          error: error instanceof Error ? error.message : String(error),
        });
      }
    }

    return results;
  };

  return { inviteMembers, loading };
}
