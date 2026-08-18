/**
 * Required verification documents, as a function of business type.
 *
 * ISOMORPHIC: mirrors the backend `RequiredDocuments` class so the wizard can
 * gate its own Continue button without a round-trip. The backend remains the
 * authority — `submitForReview` re-derives this set server-side and refuses with
 * the missing types named. This copy exists to keep the UI honest, not to
 * replace the check.
 *
 * ## Why the set varies
 *
 * Spec ET-ORG-001: "A sole proprietor does not have a certificate of
 * incorporation and asking for one is how an application is abandoned." The
 * required set is a function of `businessType` and nothing else.
 *
 * @see specs/organization/001-organizer-onboarding/spec.md §4
 * @see Org Admin - Onboarding Wizard.dc.html (design authority)
 * @module lib/onboarding/documents
 */

// =============================================================================
// BUSINESS TYPES
// =============================================================================

/**
 * Business types accepted by the backend.
 *
 * These are the values of `com.pml.identity.domain.enums.BusinessType`. Note
 * that the backend spells it `SOLE_PROPRIETORSHIP` where spec §4 says
 * `SOLE_PROPRIETOR`, and carries an extra `INDIVIDUAL`. We send what the API
 * accepts and label it the way the design does.
 */
export type BusinessType =
  | 'SOLE_PROPRIETORSHIP'
  | 'PARTNERSHIP'
  | 'LIMITED_COMPANY'
  | 'NGO'
  | 'GOVERNMENT'
  | 'INDIVIDUAL';

/** Document type keys. Free-form strings on the wire; spec §4 vocabulary here. */
export type DocumentType =
  | 'NATIONAL_ID'
  | 'TAX_CERTIFICATE'
  | 'CERTIFICATE_OF_INCORPORATION'
  | 'PARTNERSHIP_AGREEMENT'
  | 'NGO_REGISTRATION'
  | 'AUTHORISATION_LETTER'
  | 'PROOF_OF_ADDRESS'
  | 'BANK_STATEMENT';

export interface DocumentSpec {
  readonly type: DocumentType;
  /** Human label, from the design authority. */
  readonly name: string;
  /** One-line hint naming the real-world Zambian document. */
  readonly hint: string;
  /** Iconoir icon name. */
  readonly icon: string;
}

export interface BusinessTypeSpec {
  readonly type: BusinessType;
  readonly label: string;
  readonly icon: string;
  readonly required: readonly DocumentType[];
}

// =============================================================================
// DOCUMENT CATALOGUE
// =============================================================================

const DOCUMENTS: Record<DocumentType, DocumentSpec> = {
  NATIONAL_ID: {
    type: 'NATIONAL_ID',
    name: 'National ID',
    hint: 'Government-issued photo ID',
    icon: 'id-card',
  },
  TAX_CERTIFICATE: {
    type: 'TAX_CERTIFICATE',
    name: 'Tax certificate',
    hint: 'ZRA tax clearance certificate',
    icon: 'page',
  },
  CERTIFICATE_OF_INCORPORATION: {
    type: 'CERTIFICATE_OF_INCORPORATION',
    name: 'Certificate of incorporation',
    hint: 'PACRA registration certificate',
    icon: 'page-star',
  },
  PARTNERSHIP_AGREEMENT: {
    type: 'PARTNERSHIP_AGREEMENT',
    name: 'Partnership agreement',
    hint: 'Signed partnership deed',
    icon: 'page-edit',
  },
  NGO_REGISTRATION: {
    type: 'NGO_REGISTRATION',
    name: 'NGO registration',
    hint: 'Registrar of Societies certificate',
    icon: 'page-star',
  },
  AUTHORISATION_LETTER: {
    type: 'AUTHORISATION_LETTER',
    name: 'Authorisation letter',
    hint: 'Letter on official letterhead',
    icon: 'page-edit',
  },
  // Optional everywhere — what a reviewer asks for via requestOrganizationChanges
  // when something does not add up (spec §4).
  PROOF_OF_ADDRESS: {
    type: 'PROOF_OF_ADDRESS',
    name: 'Proof of address',
    hint: 'Utility bill or lease agreement',
    icon: 'home',
  },
  BANK_STATEMENT: {
    type: 'BANK_STATEMENT',
    name: 'Bank statement',
    hint: 'Statement showing the account name',
    icon: 'bank',
  },
};

export function documentSpec(type: DocumentType): DocumentSpec {
  return DOCUMENTS[type];
}

// =============================================================================
// BUSINESS TYPE → REQUIRED DOCUMENTS (spec ET-ORG-001 §4)
// =============================================================================

export const BUSINESS_TYPES: readonly BusinessTypeSpec[] = [
  {
    type: 'SOLE_PROPRIETORSHIP',
    label: 'Sole proprietor',
    icon: 'user',
    required: ['NATIONAL_ID', 'TAX_CERTIFICATE'],
  },
  {
    type: 'INDIVIDUAL',
    label: 'Individual',
    icon: 'user',
    required: ['NATIONAL_ID', 'TAX_CERTIFICATE'],
  },
  {
    type: 'PARTNERSHIP',
    label: 'Partnership',
    icon: 'group',
    required: ['NATIONAL_ID', 'TAX_CERTIFICATE', 'PARTNERSHIP_AGREEMENT'],
  },
  {
    type: 'LIMITED_COMPANY',
    label: 'Limited company',
    icon: 'building',
    required: ['NATIONAL_ID', 'TAX_CERTIFICATE', 'CERTIFICATE_OF_INCORPORATION'],
  },
  {
    type: 'NGO',
    label: 'NGO',
    icon: 'heart',
    required: ['NATIONAL_ID', 'TAX_CERTIFICATE', 'NGO_REGISTRATION'],
  },
  {
    type: 'GOVERNMENT',
    label: 'Government',
    icon: 'bank',
    required: ['NATIONAL_ID', 'AUTHORISATION_LETTER'],
  },
] as const;

const BY_TYPE = new Map<BusinessType, BusinessTypeSpec>(
  BUSINESS_TYPES.map((b) => [b.type, b])
);

export function businessTypeSpec(type: BusinessType | null | undefined): BusinessTypeSpec | null {
  if (!type) return null;
  return BY_TYPE.get(type) ?? null;
}

/**
 * The documents this business type must supply.
 *
 * Returns an empty list for an unknown type rather than a default set — asking
 * for documents we cannot justify is the abandonment the spec warns about, and
 * the backend will refuse the submit anyway if the type is genuinely missing.
 */
export function requiredDocuments(type: BusinessType | null | undefined): readonly DocumentSpec[] {
  const spec = businessTypeSpec(type);
  if (!spec) return [];
  return spec.required.map(documentSpec);
}

/**
 * Which required documents are still outstanding?
 *
 * A document in `REJECTED` does not satisfy its requirement (spec R3), so only
 * `PENDING` and `APPROVED` uploads count.
 */
export function missingDocuments(
  type: BusinessType | null | undefined,
  uploaded: ReadonlyArray<{ documentType: string; status?: string | null }>
): readonly DocumentSpec[] {
  const satisfied = new Set(
    uploaded
      .filter((d) => d.status !== 'REJECTED')
      .map((d) => d.documentType)
  );
  return requiredDocuments(type).filter((d) => !satisfied.has(d.type));
}

// =============================================================================
// UPLOAD CONSTRAINTS (identity.onboarding.document.* — spec §4)
// =============================================================================

export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024; // 10 MB

export const ACCEPTED_MIME_TYPES: readonly string[] = [
  'image/jpeg',
  'image/png',
  'application/pdf',
];

export const ACCEPT_ATTRIBUTE = ACCEPTED_MIME_TYPES.join(',');

/**
 * Client-side pre-flight. The server enforces the same rules independently
 * (spec R4: "enforced server-side, not only in the browser") — this exists to
 * fail fast before a 10 MB upload starts, not to be trusted.
 */
export function validateFile(file: File): string | null {
  if (!ACCEPTED_MIME_TYPES.includes(file.type)) {
    return 'Upload a JPEG, PNG or PDF file.';
  }
  if (file.size > MAX_DOCUMENT_BYTES) {
    return 'File is larger than 10 MB. Try a smaller scan or photo.';
  }
  return null;
}
