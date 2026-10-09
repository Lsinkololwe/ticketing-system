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
  INVITE_TEAM_MEMBER,
} from './team.queries';
import type {
  OrganizationRole,
  MyTeamMembersQuery,
  MyTeamMembersQueryVariables,
  UpdateMemberRoleMutation,
  UpdateMemberRoleMutationVariables,
  RemoveMemberMutation,
  RemoveMemberMutationVariables,
  InviteTeamMemberMutation,
  InviteTeamMemberMutationVariables,
} from '../../../../types/graphql';

/**
 * One row of the team roster — the `MyTeamMembers` selection, not the full
 * `OrganizationMember` entity. `user` here is `{id, fullName, username}` only:
 * `user.email` and `user.phoneNumber` are PII and deliberately not selected
 * (see `team.queries.ts`), so they must not appear on this type either.
 */
export type TeamMemberVM = NonNullable<
  NonNullable<MyTeamMembersQuery['myOwnedOrganization']>['members']
>[number];

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
  const { data, loading, error, refetch } = useQuery<MyTeamMembersQuery, MyTeamMembersQueryVariables>(
    MY_TEAM_MEMBERS,
    {
      fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
      errorPolicy: 'all',
      notifyOnNetworkStatusChange: true,
      skip: options?.skip ?? false,
    }
  );

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
 * `UpdateMemberRoleInput` carries `organizationId` and `memberId` on the
 * input itself (alongside `newRole`), not just the `$memberId` mutation
 * argument — the caller must supply the organization explicitly rather than
 * have the server infer it, so a member can only be re-roled inside the
 * organization the caller actually owns.
 *
 * Deliberately does NOT write an optimistic response. Role changes are
 * authorisation decisions — showing a member as an Admin before the server has
 * agreed would misrepresent what they can actually do.
 */
export function useUpdateMemberRole() {
  const [mutate, { loading }] = useMutation<UpdateMemberRoleMutation, UpdateMemberRoleMutationVariables>(
    UPDATE_MEMBER_ROLE
  );

  const updateRole = async (organizationId: string, memberId: string, newRole: OrganizationRole) => {
    const result = await mutate({
      variables: {
        memberId,
        input: {
          organizationId,
          memberId,
          newRole,
          customPermissions: null,
          deniedPermissions: null,
        },
      },
    });
    return result.data?.updateMemberRole ?? null;
  };

  return { updateRole, loading };
}

/** Remove a member from the organization. */
export function useRemoveMember() {
  const [mutate, { loading }] = useMutation<RemoveMemberMutation, RemoveMemberMutationVariables>(
    REMOVE_MEMBER
  );

  const removeMember = async (memberId: string, reason?: string) => {
    const result = await mutate({ variables: { memberId, reason: reason ?? null } });
    return result.data?.removeMember ?? false;
  };

  return { removeMember, loading };
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
  const [mutate, { loading }] = useMutation<
    InviteTeamMemberMutation,
    InviteTeamMemberMutationVariables
  >(INVITE_TEAM_MEMBER, { refetchQueries: [MY_TEAM_MEMBERS] });

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
              inviteeName: null,
              phoneNumber: null,
              eventAccessGrants: null,
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
