/**
 * Users & organizations admin queries.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 * @see backend/booking-service/src/main/resources/graphql/schema.graphqls
 */
import { gql } from '@apollo/client';

export const ADMIN_USER_ROW_FIELDS = gql`
  fragment AdminUserRowFields on User {
    id
    username
    email
    firstName
    lastName
    fullName
    phoneNumber
    gender
    roles
    accountStatus
    emailVerified
    phoneVerified
    active
    locked
    lockReason
    suspendReason
    twoFactorEnabled
    memberSince
    lastLoginAt
    lastActiveAt
    createdAt
    updatedAt
  }
`;

export const IDENTITY_USERS = gql`
  ${ADMIN_USER_ROW_FIELDS}
  query IdentityAdminUsers($search: String, $role: UserType, $accountStatus: AccountStatus, $pagination: OffsetPaginationInput) {
    users(search: $search, role: $role, accountStatus: $accountStatus, pagination: $pagination) {
      content { ...AdminUserRowFields }
      pageInfo { currentPage pageSize totalCount }
    }
  }
`;

export const IDENTITY_USER = gql`
  ${ADMIN_USER_ROW_FIELDS}
  query IdentityAdminUser($id: ID!) {
    user(id: $id) {
      ...AdminUserRowFields
      contacts { id type valueMasked verifiedAt primary }
      organizationMemberships { role status organization { id name } }
    }
  }
`;

export const IDENTITY_USER_BY_EMAIL = gql`
  ${ADMIN_USER_ROW_FIELDS}
  query IdentityAdminUserByEmail($email: String!) {
    userByEmail(email: $email) { ...AdminUserRowFields }
  }
`;

export const IDENTITY_USER_BY_PHONE = gql`
  ${ADMIN_USER_ROW_FIELDS}
  query IdentityAdminUserByPhone($phoneNumber: String!) {
    userByPhone(phoneNumber: $phoneNumber) { ...AdminUserRowFields }
  }
`;

export const ADMIN_ORG_ROW_FIELDS = gql`
  fragment AdminOrgRowFields on Organization {
    id
    name
    slug
    type
    status
    kybStatus
    ownerId
    owner { id fullName email contacts { valueMasked primary } }
    businessEmail
    businessPhone
    businessAddress { city province addressLine1 }
    taxId
    businessRegistrationNumber
    verified
    documentsVerified
    payoutAccountVerified
    rejectionReason
    suspensionReason
    commissionRate
    memberCount
    totalEvents
    createdAt
    payoutConfig {
      commissionRate
      preferredMethod
      verified
      isConfigured
      bankAccount { bankName maskedAccountNumber accountHolderName accountType verified }
      mobileMoneyAccount { provider maskedPhoneNumber accountHolderName verified }
    }
  }
`;

export const IDENTITY_ORGANIZATIONS = gql`
  ${ADMIN_ORG_ROW_FIELDS}
  query IdentityAdminOrganizations($search: String, $status: OrganizationStatus, $verified: Boolean, $kybStatus: KybStatus, $pagination: OffsetPaginationInput) {
    organizations(search: $search, status: $status, verified: $verified, kybStatus: $kybStatus, pagination: $pagination) {
      content { ...AdminOrgRowFields }
      pageInfo { currentPage pageSize totalCount }
    }
  }
`;

export const IDENTITY_ORGANIZATION = gql`
  ${ADMIN_ORG_ROW_FIELDS}
  query IdentityAdminOrganization($id: ID!) {
    organization(id: $id) { ...AdminOrgRowFields }
  }
`;

export const IDENTITY_ORG_MEMBERS = gql`
  query IdentityAdminOrgMembers($organizationId: ID!, $pagination: OffsetPaginationInput) {
    organizationMembers(organizationId: $organizationId, pagination: $pagination) {
      content { id userId role status user { id fullName } }
      pageInfo { currentPage pageSize totalCount }
    }
  }
`;

export const IDENTITY_ORG_DOCUMENTS = gql`
  query IdentityAdminOrgDocuments($organizationId: ID!) {
    verificationDocuments(organizationId: $organizationId) {
      id documentType fileName status uploadedAt rejectionReason
    }
  }
`;

export const CATALOG_ORG_EVENTS = gql`
  query IdentityAdminOrgEvents($filter: EventFilterInput, $pagination: OffsetPaginationInput) {
    events(filter: $filter, pagination: $pagination) {
      content { id title status eventDateTime soldTickets }
      totalElements
    }
  }
`;

export const BOOKING_USER_TICKETS = gql`
  query IdentityAdminUserTickets($buyerId: String!, $pagination: OffsetPaginationInput) {
    ticketsByBuyerOffsetPagination(buyerId: $buyerId, pagination: $pagination) {
      data { id ticketNumber eventTitle ticketCategoryName price currency status purchaseDate paymentReference }
      pagination { totalElements totalCount }
    }
  }
`;

export const BOOKING_USER_REFUNDS = gql`
  query IdentityAdminUserRefunds($buyerId: String!, $pagination: OffsetPaginationInput) {
    refundRequestsByBuyer(buyerId: $buyerId, pagination: $pagination) {
      data { id requestId ticketNumber refundAmount currency status requestedAt }
      pagination { totalElements totalCount }
    }
  }
`;
