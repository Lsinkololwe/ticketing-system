'use client';

import { useEffect, useMemo } from 'react';
import { TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import { Banner, Card, CardHeader, FormCell, FormGrid, TextField } from '@pml.tickets/shared/components/m3';
import type { SettingsOrganization } from '@/lib/api/settings';
import { orgProfileSchema, type OrgProfileValues } from './schemas';
import { MediaPickerField } from '@/components/common/MediaPickerField';
import { RichTextField } from '@/components/common/RichTextField';
import { SettingsForm } from './SettingsForm';

export type OrgProfileDraft = OrgProfileValues;

export interface OrgProfileSectionProps {
  organization: SettingsOrganization;
  /** Only owner/admins may edit. */
  canEdit: boolean;
  /** Result of the slug check: null = unknown/unchecked. */
  slugAvailable: boolean | null;
  onCheckSlug: (slug: string) => void;
  /** Throw to have the server error mapped onto the form (SLUG_TAKEN lands on the slug field). */
  onSave: (draft: OrgProfileDraft) => Promise<void>;
}

const fromOrg = (o: SettingsOrganization): OrgProfileValues => ({
  name: o.name,
  slug: o.slug,
  description: o.description ?? '',
  logoUrl: o.logoUrl ?? '',
  bannerUrl: o.bannerUrl ?? '',
  tagline: o.tagline ?? '',
  website: o.website ?? '',
  facebook: o.socialLinks?.facebook ?? '',
  instagram: o.socialLinks?.instagram ?? '',
  twitter: o.socialLinks?.twitter ?? '',
  linkedin: o.socialLinks?.linkedin ?? '',
  youtube: o.socialLinks?.youtube ?? '',
  tiktok: o.socialLinks?.tiktok ?? '',
  taxId: o.taxId ?? '',
  businessRegistrationNumber: o.businessRegistrationNumber ?? '',
  businessPhone: o.businessPhone ?? '',
  businessEmail: o.businessEmail ?? '',
  addressLine1: o.businessAddress?.addressLine1 ?? '',
  city: o.businessAddress?.city ?? '',
  province: o.businessAddress?.province ?? '',
  country: o.businessAddress?.country ?? '',
});

/** The server accepts the identity fields only while the application can still be edited. */
export const IDENTITY_EDITABLE = new Set(['DRAFT', 'CHANGES_REQUESTED']);

export function OrgProfileSection({ organization: o, canEdit, slugAvailable, onCheckSlug, onSave }: OrgProfileSectionProps) {
  const base = useMemo(() => fromOrg(o), [o]);
  const form = useZodForm(orgProfileSchema, { defaultValues: base });
  useEffect(() => form.reset(base), [base, form]);
  const identityEditable = canEdit && IDENTITY_EDITABLE.has(o.status);
  const slug = form.watch('slug');
  const slugHelp = slug === o.slug ? 'Your current address' : slugAvailable === null ? 'Check availability when you leave the field' : slugAvailable ? 'Available' : 'Already taken';

  return (
    <div className="m3-stack" data-testid="settings-profile">
      {!canEdit ? <Banner tone="info">Only the owner and admins can edit the organization profile.</Banner> : null}
      <SettingsForm
        form={form}
        label="Organization profile"
        testId="settings-profile-form"
        readOnly={!canEdit}
        onSubmit={async (values) => {
          if (values.slug !== o.slug && slugAvailable === false) {
            // Same code the server answers with, so the message lands on the slug field.
            throw { errors: [{ message: 'Slug taken', extensions: { errorCode: 'SLUG_TAKEN', classification: 'FAILED_PRECONDITION' } }] };
          }
          await onSave(values);
        }}
      >
        <Card>
          <CardHeader title="Organization profile" subtitle="Shown on your event pages." />
          <FormGrid>
            <FormCell span={6}>
              <TextFieldRHF name="name" label="Organization name" required />
            </FormCell>
            <FormCell span={6}>
              <div onBlur={() => slug !== o.slug && slug.length >= 3 && onCheckSlug(slug)}>
                <TextFieldRHF name="slug" label="Web address (slug)" prefix="mytickets.zm/o/" helperText={slugHelp} />
              </div>
            </FormCell>
            <FormCell span={12}>
              <RichTextField name="description" label="Description" rows={4} />
            </FormCell>
            <FormCell span={6}>
              <MediaPickerField name="logoUrl" label="Logo" noun="logo" />
            </FormCell>
            <FormCell span={6}>
              <MediaPickerField name="bannerUrl" label="Banner" noun="banner" />
            </FormCell>
            <FormCell span={12}>
              <TextFieldRHF name="tagline" label="Tagline" helperText="One line under your name" />
            </FormCell>
            <FormCell span={6}><TextFieldRHF name="website" label="Website" placeholder="https://" /></FormCell>
            <FormCell span={6}><TextFieldRHF name="facebook" label="Facebook" placeholder="https://" /></FormCell>
            <FormCell span={6}><TextFieldRHF name="instagram" label="Instagram" placeholder="https://" /></FormCell>
            <FormCell span={6}><TextFieldRHF name="twitter" label="X (Twitter)" placeholder="https://" /></FormCell>
            <FormCell span={6}><TextFieldRHF name="linkedin" label="LinkedIn" placeholder="https://" /></FormCell>
            <FormCell span={6}><TextFieldRHF name="youtube" label="YouTube" placeholder="https://" /></FormCell>
            <FormCell span={6}><TextFieldRHF name="tiktok" label="TikTok" placeholder="https://" /></FormCell>
          </FormGrid>
        </Card>
        <Card>
          <CardHeader
            title="Business information"
            subtitle={identityEditable ? 'Editable until your application is approved.' : 'Verified during onboarding. Contact support to change these after approval.'}
          />
          <FormGrid>
            <FormCell span={6}><TextField label="Business type" value={o.businessType ?? ''} readOnly /></FormCell>
            <FormCell span={3}><TextFieldRHF name="taxId" label="TPIN" readOnly={!identityEditable} /></FormCell>
            <FormCell span={3}><TextFieldRHF name="businessRegistrationNumber" label="Registration number" readOnly={!identityEditable} /></FormCell>
            <FormCell span={6}><TextFieldRHF name="businessPhone" label="Business phone" readOnly={!identityEditable} /></FormCell>
            <FormCell span={6}><TextFieldRHF name="businessEmail" label="Business email" readOnly={!identityEditable} /></FormCell>
            <FormCell span={12}><TextFieldRHF name="addressLine1" label="Street address" readOnly={!identityEditable} /></FormCell>
            <FormCell span={3}><TextFieldRHF name="city" label="City" readOnly={!identityEditable} /></FormCell>
            <FormCell span={3}><TextFieldRHF name="province" label="Province" readOnly={!identityEditable} /></FormCell>
            <FormCell span={3}><TextFieldRHF name="country" label="Country" readOnly={!identityEditable} /></FormCell>
            <FormCell span={3}>
              <TextField
                label="Commission rate"
                value={o.commissionRate != null ? `${o.commissionRate}%` : 'Not configured yet'}
                readOnly
                helperText="Set by the platform"
              />
            </FormCell>
          </FormGrid>
        </Card>
      </SettingsForm>
    </div>
  );
}
