/**
 * Team GraphQL Operations (Organization Admin App)
 *
 * Members of the signed-in organizer's own organization, plus the role and
 * membership mutations the team screen needs. All resolved by identity-service.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

/**
 * The organization's members.
 *
 * Scoped through `myOwnedOrganization` rather than taking an organizationId
 * argument: the server derives the organization from the JWT, so a caller
 * cannot read another organization's roster by passing a different id.
 *
 * `user.email` and `user.phoneNumber` are PII and `@tag(name: "admin")` in the
 * schema — they are deliberately NOT selected here. The team list shows a name
 * and a role; it does not need contact details.
 */
export const MY_TEAM_MEMBERS = gql`
  query MyTeamMembers {
    myOwnedOrganization {
      id
      members {
        id
        userId
        role
        status
        joinedAt
        lastActiveAt
        user {
          id
          fullName
          username
        }
      }
    }
  }
`;

/** Change a member's role. */
export const UPDATE_MEMBER_ROLE = gql`
  mutation UpdateMemberRole($memberId: ID!, $input: UpdateMemberRoleInput!) {
    updateMemberRole(memberId: $memberId, input: $input) {
      id
      role
      status
    }
  }
`;

/**
 * Remove a member from the organization.
 *
 * Returns Boolean, not the member — the row is gone, so there is nothing to
 * merge back into the cache. The caller refetches.
 */
export const REMOVE_MEMBER = gql`
  mutation RemoveMember($memberId: ID!, $reason: String) {
    removeMember(memberId: $memberId, reason: $reason)
  }
`;

/**
 * Invite one person to the organization.
 *
 * `bulkInviteTeamMembers` also exists, but it returns a flat list with no
 * mapping back to the inputs — so a partial failure cannot be attributed to a
 * specific address. The invite screen sends one mutation per row instead and
 * reports per-row outcomes.
 */
export const INVITE_TEAM_MEMBER = gql`
  mutation InviteTeamMember($organizationId: ID!, $input: InviteMemberInput!) {
    inviteTeamMember(organizationId: $organizationId, input: $input) {
      id
      email
      proposedRole
    }
  }
`;
