/**
 * Reference Data Type Definitions
 *
 * The polymorphic reference/catalog store lives in catalog-service. These types are hand-authored
 * to match the backend GraphQL schema. Once the supergraph is recomposed and `npm run codegen` has
 * run, `ReferenceType` / `ReferenceData` may be swapped for the generated versions in
 * `../../../../types/graphql` (mirroring how organization.types.ts re-exports generated types).
 *
 * @see backend/catalog-service/src/main/resources/graphql/schema.graphqls
 */

// ==========================================
// Core Types (match backend schema)
// ==========================================

export type ReferenceType =
  | 'COUNTRY'
  | 'CURRENCY'
  | 'LANGUAGE'
  | 'TIMEZONE'
  | 'PROVINCE'
  | 'MOBILE_MONEY_OPERATOR'
  | 'BANK'
  | 'EVENT_TYPE'
  | 'EVENT_CATEGORY'
  | 'MUSIC_GENRE'
  | 'AGE_RESTRICTION'
  | 'KYB_DOCUMENT_TYPE'
  | 'CANCELLATION_REASON'
  | 'REFUND_REASON'
  | 'REJECTION_REASON'
  | 'TAX_RATE'
  | 'CARD_SCHEME';

export interface ReferenceData {
  id: string;
  type: ReferenceType;
  code: string;
  name: string;
  description: string | null;
  parentType: ReferenceType | null;
  parentCode: string | null;
  displayOrder: number;
  isActive: boolean;
  isSystem: boolean;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string | null;
  updatedAt: string | null;
  createdBy: string | null;
  updatedBy: string | null;
}

/** Registry entry describing a type for the admin picker + dynamic metadata form. */
export interface ReferenceTypeInfo {
  type: ReferenceType;
  label: string;
  group: string;
  groupLabel: string;
  requiredMetadataKeys: string[];
}

export interface CreateReferenceDataInput {
  type: ReferenceType;
  code: string;
  name: string;
  description?: string | null;
  parentType?: ReferenceType | null;
  parentCode?: string | null;
  displayOrder?: number | null;
  isActive?: boolean | null;
  metadata?: Record<string, unknown> | null;
}

export interface UpdateReferenceDataInput {
  name?: string | null;
  description?: string | null;
  parentType?: ReferenceType | null;
  parentCode?: string | null;
  displayOrder?: number | null;
  isActive?: boolean | null;
  metadata?: Record<string, unknown> | null;
}

export interface ReferenceDataMutationResponse {
  success: boolean;
  message: string | null;
  data: ReferenceData | null;
  errors: string[];
}

export interface DeleteMutationResponse {
  success: boolean;
  message: string | null;
  errors: string[];
}

export interface ReferenceDataOffsetPage {
  content: ReferenceData[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

// ==========================================
// UI Helpers
// ==========================================

/** Radix color for a reference-data row's active state. */
export function getActiveColor(isActive: boolean): string {
  return isActive ? 'green' : 'gray';
}

/** Convert a list of reference rows to SearchableSelect options. */
export function toSelectOptions(
  items: ReferenceData[]
): { value: string; label: string }[] {
  return items.map((item) => ({ value: item.code, label: item.name }));
}
