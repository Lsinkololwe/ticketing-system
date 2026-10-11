/** Users & organizations admin mutations. Every operation exists in identity-service schema.graphqls. */
import { gql } from '@apollo/client';
import { ADMIN_USER_ROW_FIELDS, ADMIN_ORG_ROW_FIELDS } from './identity-admin.queries';

export const CREATE_USER = gql`
  ${ADMIN_USER_ROW_FIELDS}
  mutation IdentityAdminCreateUser($input: CreateUserInput!) { createUser(input: $input) { ...AdminUserRowFields } }
`;
export const UPDATE_USER = gql`
  ${ADMIN_USER_ROW_FIELDS}
  mutation IdentityAdminUpdateUser($id: ID!, $input: UpdateUserInput!) { updateUser(id: $id, input: $input) { ...AdminUserRowFields } }
`;
export const SUSPEND_USER = gql`
  mutation IdentityAdminSuspendUser($id: ID!, $reason: String!) { suspendUser(id: $id, reason: $reason) { id accountStatus } }
`;
export const UNSUSPEND_USER = gql`
  mutation IdentityAdminUnsuspendUser($id: ID!) { unsuspendUser(id: $id) { id accountStatus } }
`;
export const LOCK_USER = gql`
  mutation IdentityAdminLockUser($id: ID!, $reason: String!) { lockUser(id: $id, reason: $reason) }
`;
export const UNLOCK_USER = gql`
  mutation IdentityAdminUnlockUser($id: ID!) { unlockUser(id: $id) }
`;
export const ACTIVATE_USER = gql`
  mutation IdentityAdminActivateUser($id: ID!) { activateUser(id: $id) }
`;
export const DEACTIVATE_USER = gql`
  mutation IdentityAdminDeactivateUser($id: ID!) { deactivateUser(id: $id) }
`;
export const SET_USER_ROLES = gql`
  mutation IdentityAdminSetUserRoles($userId: ID!, $roles: [UserType!]!) { setUserRoles(userId: $userId, roles: $roles) { id roles } }
`;
export const SUSPEND_ORG = gql`
  ${ADMIN_ORG_ROW_FIELDS}
  mutation IdentityAdminSuspendOrg($id: ID!, $reason: String!) { suspendOrganization(id: $id, reason: $reason) { ...AdminOrgRowFields } }
`;
export const UNSUSPEND_ORG = gql`
  ${ADMIN_ORG_ROW_FIELDS}
  mutation IdentityAdminUnsuspendOrg($id: ID!) { unsuspendOrganization(id: $id) { ...AdminOrgRowFields } }
`;
export const UPDATE_ORG_STATUS = gql`
  ${ADMIN_ORG_ROW_FIELDS}
  mutation IdentityAdminUpdateOrgStatus($id: ID!, $status: OrganizationStatus!) { updateOrganizationStatus(id: $id, status: $status) { ...AdminOrgRowFields } }
`;
export const VERIFY_PAYOUT_ACCOUNT = gql`
  ${ADMIN_ORG_ROW_FIELDS}
  mutation IdentityAdminVerifyPayout($organizationId: ID!, $verified: Boolean!) { verifyPayoutAccount(organizationId: $organizationId, verified: $verified) { ...AdminOrgRowFields } }
`;
