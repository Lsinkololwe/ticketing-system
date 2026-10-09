'use client';

import { useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { useGraphQLMutationForm } from '@pml.tickets/shared';
import {
  APPLY_TO_BE_ORGANIZER,
  UPDATE_ORGANIZATION_APPLICATION,
  useMyOrganization,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import type { OrganizationApplicationInput } from '@pml.tickets/shared/types/graphql';
import { Skeleton } from '@pml.tickets/shared/components/m3';
import { BusinessInfoView } from '@/components/onboarding/BusinessInfoView';
import type { BusinessInfoInput, BusinessInfoOutput } from '@/components/onboarding/schemas';
import { ROUTES } from '@/lib/onboarding/state';

const blank = (v?: string | null) => v || null;

export function toApplicationInput(d: BusinessInfoOutput): OrganizationApplicationInput {
  const social = d.facebook || d.instagram || d.twitter;
  return {
    name: d.name,
    description: blank(d.description),
    tagline: blank(d.tagline),
    // Checked against the platform's lists by the form; the server validates against the same rows.
    type: d.type as OrganizationApplicationInput['type'],
    businessType: d.businessType as OrganizationApplicationInput['businessType'],
    businessRegistrationNumber: blank(d.businessRegistrationNumber),
    taxId: blank(d.taxId),
    businessEmail: d.businessEmail,
    businessPhone: d.businessPhone,
    website: blank(d.website),
    city: d.city,
    province: d.province,
    country: d.country || 'Zambia',
    socialLinks: social
      ? { facebook: blank(d.facebook), instagram: blank(d.instagram), twitter: blank(d.twitter), linkedin: null, tiktok: null, youtube: null }
      : null,
    logoUrl: null,
    bannerUrl: null,
  };
}

export default function BusinessInfoPage() {
  const router = useRouter();
  const { organization, loading } = useMyOrganization({ fetchPolicy: 'cache-first' });

  const goNext = () => router.push(ROUTES.documents);
  const apply = useGraphQLMutationForm({
    mutation: APPLY_TO_BE_ORGANIZER,
    toVariables: (v: BusinessInfoOutput) => ({ input: toApplicationInput(v) }),
    refetchQueries: ['MyOrganization'],
    awaitRefetchQueries: true,
    onSuccess: goNext,
  });
  const update = useGraphQLMutationForm({
    mutation: UPDATE_ORGANIZATION_APPLICATION,
    toVariables: (v: BusinessInfoOutput) => ({ id: organization?.id ?? '', input: toApplicationInput(v) }),
    refetchQueries: ['MyOrganization'],
    awaitRefetchQueries: true,
    onSuccess: goNext,
  });


  const defaults: BusinessInfoInput = useMemo(
    () => ({
      name: organization?.name ?? '',
      type: organization?.type ?? '',
      businessType: organization?.businessType ?? '',
      tagline: organization?.tagline ?? '',
      description: organization?.description ?? '',
      businessEmail: organization?.businessEmail ?? '',
      businessPhone: organization?.businessPhone ?? '',
      website: organization?.website ?? '',
      businessRegistrationNumber: organization?.businessRegistrationNumber ?? '',
      taxId: organization?.taxId ?? '',
      city: organization?.businessAddress?.city ?? '',
      province: organization?.businessAddress?.province ?? '',
      country: organization?.businessAddress?.country ?? 'Zambia',
      facebook: organization?.socialLinks?.facebook ?? '',
      instagram: organization?.socialLinks?.instagram ?? '',
      twitter: organization?.socialLinks?.twitter ?? '',
    }),
    [organization]
  );

  if (loading && !organization) {
    return (
      <div className="m3-stack" role="status" aria-label="Loading" data-testid="loading">
        <Skeleton />
        <Skeleton />
      </div>
    );
  }

  return (
    <BusinessInfoView
      key={organization?.id ?? 'new'}
      defaultValues={defaults}
      onSubmit={async (v) => {
        if (organization) await update.submit(v);
        else await apply.submit(v);
      }}
      onBack={() => router.push(ROUTES.welcome)}
      changesNote={organization?.status === 'CHANGES_REQUESTED' ? organization.rejectionReason ?? null : null}
    />
  );
}
