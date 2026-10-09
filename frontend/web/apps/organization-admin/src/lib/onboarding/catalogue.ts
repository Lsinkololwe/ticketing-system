'use client';

import { useMemo } from 'react';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { toBusinessTypeSpecs, toDocumentSpecs, type BusinessTypeSpec, type DocumentSpec } from './documents';

/** The platform's legal business types and KYB documents, for the wizard's three steps. */
export function useOnboardingCatalogue(): {
  businessTypes: BusinessTypeSpec[];
  documents: DocumentSpec[];
  loading: boolean;
  /** Either list could not be read: show the unavailable state rather than an empty requirement. */
  unavailable: boolean;
} {
  const types = useReferenceOptions('BUSINESS_TYPE');
  const docs = useReferenceOptions('KYB_DOCUMENT_TYPE');
  const businessTypes = useMemo(() => toBusinessTypeSpecs(types.options), [types.options]);
  const documents = useMemo(() => toDocumentSpecs(docs.options), [docs.options]);
  return {
    businessTypes,
    documents,
    loading: types.loading || docs.loading,
    unavailable: !types.loading && !docs.loading && (types.empty || docs.empty),
  };
}
