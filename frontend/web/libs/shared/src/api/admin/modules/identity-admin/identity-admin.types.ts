/**
 * Local types for the Users & organizations admin surface.
 *
 * Generated GraphQL types do not yet cover these operations, so the shapes are
 * declared here, field-for-field against identity-service / booking-service
 * schema.graphqls.
 */

import type { AccountStatus, KybStatus, OrganizationStatus, UserType } from '../../../../types/graphql';

/**
 * The enums below ARE the schema's: the aliases name the generated types, and each list is typed from them,
 * so a value the schema does not have is a compile error. (A TS union has no runtime members, so a member
 * added to the schema still needs adding to the list; the label maps in apps/admin/src/lib/enumLabels.ts
 * are the exhaustive form of this and are what new code should use.)
 */
export type AdminUserRole = UserType;
export const ADMIN_USER_ROLES: readonly UserType[] = ['CUSTOMER', 'ORGANIZER', 'ADMIN', 'SUPER_ADMIN', 'FINANCE', 'FINANCE_LEAD'];
export const ADMIN_STAFF_ROLES: readonly UserType[] = ['ADMIN', 'SUPER_ADMIN', 'FINANCE', 'FINANCE_LEAD'];

export type AdminAccountStatus = AccountStatus;
export const ADMIN_ACCOUNT_STATUSES: readonly AccountStatus[] = ['ACTIVE', 'INACTIVE', 'LOCKED', 'SUSPENDED', 'PENDING_VERIFICATION', 'PENDING_DELETION'];

export type AdminOrgStatus = OrganizationStatus;
export const ADMIN_ORG_STATUSES: readonly OrganizationStatus[] = [
  'DRAFT', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED', 'ACTIVE', 'SUSPENDED', 'INACTIVE', 'PENDING_DELETION',
];

export type AdminKybStatus = KybStatus;
export const ADMIN_KYB_STATUSES: readonly KybStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'VERIFIED', 'REJECTED'];

export interface AdminUserRecord {
  id: string;
  username?: string | null;
  email?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  fullName: string;
  phoneNumber?: string | null;
  gender?: string | null;
  roles: AdminUserRole[];
  accountStatus: AdminAccountStatus;
  emailVerified: boolean;
  phoneVerified: boolean;
  active: boolean;
  locked: boolean;
  lockReason?: string | null;
  suspendReason?: string | null;
  twoFactorEnabled: boolean;
  memberSince?: string | null;
  lastLoginAt?: string | null;
  lastActiveAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
  contacts?: Array<{ id: string; type: 'WHATSAPP' | 'EMAIL'; valueMasked: string; verifiedAt?: string | null; primary: boolean }>;
  organizationMemberships?: Array<{ role: string; status: string; organization?: { id: string; name: string } | null }> | null;
}

export interface PageInfoLite {
  currentPage?: number | null;
  pageSize?: number | null;
  totalCount?: number | null;
}

export interface AdminOrgRecord {
  id: string;
  name: string;
  slug: string;
  type: string;
  status: AdminOrgStatus;
  kybStatus: AdminKybStatus;
  ownerId: string;
  owner?: { id: string; fullName: string; email?: string | null } | null;
  businessEmail?: string | null;
  businessPhone?: string | null;
  businessAddress?: { city?: string | null; province?: string | null; addressLine1?: string | null } | null;
  taxId?: string | null;
  businessRegistrationNumber?: string | null;
  verified: boolean;
  documentsVerified: boolean;
  payoutAccountVerified: boolean;
  rejectionReason?: string | null;
  suspensionReason?: string | null;
  commissionRate?: number | null;
  memberCount: number;
  totalEvents?: number | null;
  createdAt: string;
  payoutConfig?: {
    commissionRate?: number | null;
    preferredMethod?: string | null;
    verified: boolean;
    isConfigured: boolean;
    bankAccount?: {
      bankName?: string | null;
      maskedAccountNumber?: string | null;
      accountHolderName?: string | null;
      accountType?: string | null;
      verified: boolean;
    } | null;
    mobileMoneyAccount?: {
      provider?: string | null;
      maskedPhoneNumber?: string | null;
      accountHolderName?: string | null;
      verified: boolean;
    } | null;
  } | null;
}

export interface AdminOrgMember {
  id: string;
  userId: string;
  role: string;
  status: string;
  user?: { id: string; fullName: string } | null;
}

export interface AdminOrgDocument {
  id: string;
  documentType: string;
  fileName?: string | null;
  status: string;
  uploadedAt: string;
  rejectionReason?: string | null;
}

export interface AdminOrgEvent {
  id: string;
  title: string;
  status: string;
  eventDateTime?: string | null;
  soldTickets?: number | null;
}

export interface AdminBuyerTicket {
  id: string;
  ticketNumber: string;
  eventTitle: string;
  ticketCategoryName?: string | null;
  price: string | number;
  currency: string;
  status: string;
  purchaseDate?: string | null;
  paymentReference?: string | null;
}

export interface AdminBuyerRefund {
  id: string;
  requestId: string;
  ticketNumber: string;
  refundAmount: string | number;
  currency: string;
  status: string;
  requestedAt?: string | null;
}
