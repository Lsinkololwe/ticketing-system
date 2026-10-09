'use client';

import { useEffect } from 'react';
import { useDocumentUpload, useMyOrganization } from '@pml.tickets/shared/api/organization-admin/modules/organization';
import { StatusView } from '@/components/onboarding/StatusView';
import { requiredDocuments } from '@/lib/onboarding/documents';
import { useOnboardingCatalogue } from '@/lib/onboarding/catalogue';

export default function ApplicationStatusPage() {
  const { organization: o, loading, error, refetch } = useMyOrganization();
  const { documents, refresh } = useDocumentUpload(o?.id ?? '');
  useEffect(() => {
    if (o?.id) void refresh();
  }, [o?.id, refresh]);

  const catalogue = useOnboardingCatalogue();
  const required = requiredDocuments(catalogue.businessTypes, catalogue.documents, o?.businessType ?? null);
  const docs = required.map((d) => ({
    type: d.type,
    name: d.name,
    status: documents.find((x) => x.documentType === d.type)?.status ?? 'PENDING',
  }));

  return (
    <StatusView
      loading={loading}
      error={error ?? null}
      onRetry={() => void refetch()}
      organization={
        o
          ? {
              status: o.status,
              name: o.name,
              type: o.type ?? null,
              kybStatus: o.kybStatus ?? null,
              taxId: o.taxId ?? null,
              registrationNumber: o.businessRegistrationNumber ?? null,
              submittedAt: o.submittedAt ?? null,
              reviewerNote: o.rejectionReason ?? null,
            }
          : null
      }
      docs={docs}
    />
  );
}
