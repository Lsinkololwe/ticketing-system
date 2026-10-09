/**
 * Admin user queries.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

export const USER_LIST_FIELDS = gql`
  fragment UserListFields on User {
    id
    fullName
    email
    phoneNumber
    roles
    accountStatus
    emailVerified
    memberSince
    createdAt
    lastLoginAt
  }
`;

/** The admin users table — one page, filtered by role and status. */
export const ADMIN_USERS = gql`
  ${USER_LIST_FIELDS}
  query AdminUsers(
    $search: String
    $role: UserType
    $accountStatus: AccountStatus
    $pagination: OffsetPaginationInput
  ) {
    users(
      search: $search
      role: $role
      accountStatus: $accountStatus
      pagination: $pagination
    ) {
      content {
        ...UserListFields
      }
      pageInfo {
        currentPage
        pageSize
        totalCount
        hasNext
        hasPrevious
      }
    }
  }
`;
