import { describe, expect, it } from 'vitest';
import { businessTypeSpec, missingDocuments, requiredDocuments, toBusinessTypeSpecs, toDocumentSpecs } from './documents';

// Rows as the platform lists them (BUSINESS_TYPE and KYB_DOCUMENT_TYPE); production code carries neither list.
const row = (value: string, label: string, description: string | null = null, metadata: unknown = {}) => ({ value, label, description, metadata });
const types = toBusinessTypeSpecs([
  row('SOLE_PROPRIETORSHIP', 'Sole proprietorship', null, { requiredDocuments: ['NATIONAL_ID', 'TAX_CERTIFICATE'] }),
  row('LIMITED_COMPANY', 'Limited company', null, { requiredDocuments: ['NATIONAL_ID', 'TAX_CERTIFICATE', 'CERTIFICATE_OF_INCORPORATION', 'RETIRED_DOC'] }),
  row('BROKEN', 'No documents field', null, null),
]);
const documents = toDocumentSpecs([
  row('NATIONAL_ID', 'National ID', 'Government-issued photo ID'),
  row('TAX_CERTIFICATE', 'Tax certificate', 'ZRA tax clearance certificate'),
  row('CERTIFICATE_OF_INCORPORATION', 'Certificate of incorporation', 'PACRA registration certificate'),
]);

describe('required documents from reference data', () => {
  it('reads names and hints from the document rows and the requirement from the legal type row', () => {
    expect(documents[1]).toEqual({ type: 'TAX_CERTIFICATE', name: 'Tax certificate', hint: 'ZRA tax clearance certificate' });
    expect(types[0]).toEqual({ type: 'SOLE_PROPRIETORSHIP', label: 'Sole proprietorship', required: ['NATIONAL_ID', 'TAX_CERTIFICATE'] });
    expect(types[2]!.required).toEqual([]);
  });

  it('asks a sole proprietor for no incorporation certificate, and skips a document the platform no longer lists', () => {
    expect(requiredDocuments(types, documents, 'SOLE_PROPRIETORSHIP').map((d) => d.type)).toEqual(['NATIONAL_ID', 'TAX_CERTIFICATE']);
    expect(requiredDocuments(types, documents, 'LIMITED_COMPANY').map((d) => d.type)).toEqual(['NATIONAL_ID', 'TAX_CERTIFICATE', 'CERTIFICATE_OF_INCORPORATION']);
  });

  it('asks for nothing when the type is unknown, unset or the lists are not loaded', () => {
    expect(requiredDocuments(types, documents, 'MARS')).toEqual([]);
    expect(requiredDocuments(types, documents, null)).toEqual([]);
    expect(requiredDocuments([], [], 'LIMITED_COMPANY')).toEqual([]);
    expect(businessTypeSpec(types, undefined)).toBeNull();
  });

  it('counts pending and approved uploads, not rejected ones', () => {
    const uploaded = [
      { documentType: 'NATIONAL_ID', status: 'APPROVED' },
      { documentType: 'TAX_CERTIFICATE', status: 'REJECTED' },
    ];
    expect(missingDocuments(types, documents, 'SOLE_PROPRIETORSHIP', uploaded).map((d) => d.type)).toEqual(['TAX_CERTIFICATE']);
    expect(missingDocuments(types, documents, 'SOLE_PROPRIETORSHIP', [...uploaded, { documentType: 'TAX_CERTIFICATE', status: 'PENDING' }])).toEqual([]);
  });
});
