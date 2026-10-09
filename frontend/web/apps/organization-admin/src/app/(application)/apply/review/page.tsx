'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useGraphQLMutationForm } from '@pml.tickets/shared';
import {
  SUBMIT_ORGANIZATION_FOR_REVIEW,
  useDocumentUpload,
  useMyOrganization,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import { ReviewView } from '@/components/onboarding/ReviewView';
import { missingDocuments, requiredDocuments } from '@/lib/onboarding/documents';
import { useOnboardingCatalogue } from '@/lib/onboarding/catalogue';
import { ROUTES } from '@/lib/onboarding/state';
import { statusLabel } from '@/components/console/Status';

export default function ReviewPage() {
  const router = useRouter();
  const { organization, loading } = useMyOrganization({ fetchPolicy: 'cache-first' });
  const { documents, refresh } = useDocumentUpload(organization?.id ?? '');
  const submit = useGraphQLMutationForm({
    mutation: SUBMIT_ORGANIZATION_FOR_REVIEW,
    toVariables: () => ({ id: organization?.id ?? '' }),
    refetchQueries: ['MyOrganization'],
    awaitRefetchQueries: true,
    onSuccess: () => router.push(ROUTES.status),
  });

  useEffect(() => {
    if (organization?.id) void refresh();
  }, [organization?.id, refresh]);

  const o = organization;
  const businessType = o?.businessType ?? null;
  const catalogue = useOnboardingCatalogue();
  const missing = missingDocuments(catalogue.businessTypes, catalogue.documents, businessType, documents);
  const missingTypes = new Set(missing.map((d) => d.type));
  const missingFields = o
    ? [
        !o.name?.trim() && 'Organization name',
        !o.businessEmail?.trim() && 'Email',
        !o.businessPhone?.trim() && 'Phone',
        !o.businessAddress?.city?.trim() && 'City',
        !o.businessAddress?.province && 'Province',
      ].filter((x): x is string => Boolean(x))
    : [];

  return (
    <ReviewView
      loading={loading || catalogue.loading}
      summary={
        o
          ? {
              type: statusLabel(o.type),
              name: o.name ?? '',
              tpin: o.taxId ?? '',
              registrationNumber: o.businessRegistrationNumber ?? '',
              phone: o.businessPhone ?? '',
              email: o.businessEmail ?? '',
              address: [o.businessAddress?.city, o.businessAddress?.province && statusLabel(o.businessAddress.province)].filter(Boolean).join(', '),
            }
          : null
      }
      docs={requiredDocuments(catalogue.businessTypes, catalogue.documents, businessType).map((d) => ({
        type: d.type,
        name: d.name,
        fileName: documents.find((x) => x.documentType === d.type)?.fileName,
        uploaded: !missingTypes.has(d.type),
      }))}
      missingFields={missingFields}
      resubmit={o?.status === 'CHANGES_REQUESTED'}
      onBack={() => router.push(ROUTES.documents)}
      onEdit={() => router.push(ROUTES.businessInfo)}
      onSubmit={async () => {
        await submit.submit(undefined as never);
      }}
    />
  );
}
