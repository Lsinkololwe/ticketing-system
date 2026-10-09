/**
 * Required verification documents, as a function of business type.
 *
 * Both lists come from the platform's reference data: `BUSINESS_TYPE` rows carry the codes of the
 * documents they require (`metadata.requiredDocuments`) and `KYB_DOCUMENT_TYPE` rows carry each
 * document's name and hint. Nothing here knows a single business type or document code, so a new
 * legal form or document is administration, not a release. The backend stays the authority:
 * `submitForReview` re-derives the set server-side. These functions only keep the wizard honest.
 *
 * @module lib/onboarding/documents
 */

/** A document the platform can ask for (a `KYB_DOCUMENT_TYPE` row). */
export interface DocumentSpec {
  readonly type: string;
  readonly name: string;
  /** One-line hint naming the real-world document. */
  readonly hint: string;
}

/** A legal business type (a `BUSINESS_TYPE` row) and the document codes it must supply. */
export interface BusinessTypeSpec {
  readonly type: string;
  readonly label: string;
  readonly required: readonly string[];
}

interface Row {
  value: string;
  label: string;
  description: string | null;
  metadata: unknown;
}

export function toDocumentSpecs(rows: readonly Row[]): DocumentSpec[] {
  return rows.map((r) => ({ type: r.value, name: r.label, hint: r.description ?? '' }));
}

export function toBusinessTypeSpecs(rows: readonly Row[]): BusinessTypeSpec[] {
  return rows.map((r) => {
    const docs = (r.metadata as { requiredDocuments?: unknown } | null)?.requiredDocuments;
    return { type: r.value, label: r.label, required: Array.isArray(docs) ? docs.filter((d): d is string => typeof d === 'string') : [] };
  });
}

export function businessTypeSpec(types: readonly BusinessTypeSpec[], type: string | null | undefined): BusinessTypeSpec | null {
  if (!type) return null;
  return types.find((b) => b.type === type) ?? null;
}

/**
 * The documents this business type must supply, in the platform's order.
 *
 * Empty for an unknown type or a document the platform no longer lists: asking for documents we cannot
 * justify is how applications get abandoned, and the backend refuses the submit anyway.
 */
export function requiredDocuments(
  types: readonly BusinessTypeSpec[],
  documents: readonly DocumentSpec[],
  type: string | null | undefined,
): readonly DocumentSpec[] {
  const spec = businessTypeSpec(types, type);
  if (!spec) return [];
  return spec.required.flatMap((code) => documents.filter((d) => d.type === code));
}

/**
 * Which required documents are still outstanding? A document in `REJECTED` does not satisfy its
 * requirement, so only `PENDING` and `APPROVED` uploads count.
 */
export function missingDocuments(
  types: readonly BusinessTypeSpec[],
  documents: readonly DocumentSpec[],
  type: string | null | undefined,
  uploaded: ReadonlyArray<{ documentType: string; status?: string | null }>,
): readonly DocumentSpec[] {
  const satisfied = new Set(uploaded.filter((d) => d.status !== 'REJECTED').map((d) => d.documentType));
  return requiredDocuments(types, documents, type).filter((d) => !satisfied.has(d.type));
}

// =============================================================================
// UPLOAD CONSTRAINTS (identity.onboarding.document.*)
// =============================================================================

export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024; // 10 MB

export const ACCEPTED_MIME_TYPES: readonly string[] = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'application/pdf',
];

export const ACCEPT_ATTRIBUTE = ACCEPTED_MIME_TYPES.join(',');

/**
 * Client-side pre-flight. The server enforces the same rules independently
 * (enforced server-side, not only in the browser) — this exists to
 * fail fast before a 10 MB upload starts, not to be trusted.
 */
export function validateFile(file: File): string | null {
  if (!ACCEPTED_MIME_TYPES.includes(file.type)) {
    return 'Use a PDF, JPEG, PNG or WEBP file.';
  }
  if (file.size > MAX_DOCUMENT_BYTES) {
    return 'File is larger than 10 MB. Try a smaller scan or photo.';
  }
  return null;
}
