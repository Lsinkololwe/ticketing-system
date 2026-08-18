'use client';

/**
 * Application step 1 — business details.
 *
 * Single centred column on the shared {@link WizardShell}, per the design
 * authority. The previous two-column layout carried a sticky "On this page"
 * scroll-spy rail: navigation *within* one step, given the same visual weight as
 * navigation *between* steps — and the between-steps progress was the one not
 * being shown at all. Its footer still read "Step 1 of 2" and offered "Continue
 * to Review", describing the two-step wizard that existed before the documents
 * step, and skipping straight past it.
 *
 * Form state via React Hook Form + Zod.
 *
 * @see components/application/WizardShell.tsx
 * @see Org Admin - Onboarding Wizard.dc.html
 * @see https://react-hook-form.com/docs/usecontroller
 */

import { useCallback, useEffect, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Badge, Box, Flex, Grid, Separator, Text, TextArea, TextField } from '@radix-ui/themes';
import { Building, Page, Phone, Globe } from 'iconoir-react';
import { BusinessTypeCombobox } from '@/components/application/BusinessTypeCombobox';
import { WizardShell, WizardSection } from '@/components/application/WizardShell';
import { requiredDocuments } from '@/lib/onboarding/documents';
import { ROUTES } from '@/lib/onboarding/state';
import {
  BusinessInfoSkeleton,
  ORGANIZATION_TYPE_OPTIONS,
  PROVINCE_OPTIONS,
} from '@/components/application';
import { useToast } from '@/components/ui';
import { PhoneNumberInput, SearchableSelect } from '@pml.tickets/shared';
import { useSession } from '@/lib/auth/client';
import {
  useMyOrganization,
  useApplyToBeOrganizer,
  useUpdateOrganizationApplication,
  type OrganizationApplicationInput,
  businessInfoFormSchema,
  type BusinessInfoFormData,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';

// =============================================================================
// CONSTANTS
// =============================================================================

const DEFAULT_VALUES: BusinessInfoFormData = {
  name: '',
  type: 'INDIVIDUAL',
  // No default: business type decides which documents step 2 demands, so a
  // silent default would quietly pick the applicant's document burden for them.
  businessType: undefined as unknown as BusinessInfoFormData['businessType'],
  tagline: '',
  description: '',
  businessEmail: '',
  businessPhone: '',
  website: '',
  businessRegistrationNumber: '',
  taxId: '',
  city: '',
  province: 'LUSAKA',
  country: 'Zambia',
  facebook: '',
  instagram: '',
  twitter: '',
};



/* Micro uppercase form label (spec §8) — 10px / 600 / 0.08em tracking, the one
   deliberate ALL-CAPS exception in the type system. Mirrors `.ds-label`. */
const LABEL_STYLE: React.CSSProperties = {
  display: 'block',
  fontSize: 'var(--label-size)',
  fontWeight: 'var(--label-weight)',
  letterSpacing: 'var(--label-tracking)',
  textTransform: 'uppercase',
  color: 'var(--gray-11)',
};


// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function BusinessInfoPage() {
  const router = useRouter();
  const { toast } = useToast();
  const [isPending, startTransition] = useTransition();
  const [formInitialized, setFormInitialized] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Get user session for prepopulating email and phone
  const { data: session } = useSession();
  const userEmail = session?.user?.email || '';
  const userPhone = (session?.user as { phoneNumber?: string })?.phoneNumber || '';

  // GraphQL hooks
  const {
    organization,
    hasOrganization,
    loading: orgLoading,
  } = useMyOrganization({ fetchPolicy: 'cache-first' });

  const { apply } = useApplyToBeOrganizer();
  const { update } = useUpdateOrganizationApplication();

  // React Hook Form with Zod
  const { control, handleSubmit, reset, watch, formState: { isSubmitting } } = useForm({
    resolver: zodResolver(businessInfoFormSchema),
    defaultValues: DEFAULT_VALUES,
    mode: 'onBlur', // Validate on blur for better UX
  });

  // Tell the applicant the document cost of their choice before they commit to
  // it, rather than surprising them on the next step.
  const selectedBusinessType = watch('businessType');
  const requiredCount = selectedBusinessType
    ? requiredDocuments(selectedBusinessType).length
    : null;

  // Navigation handlers
  const goBack = useCallback(() => router.push('/welcome'), [router]);

  // Build GraphQL input from form data
  const buildInput = useCallback(
    (data: BusinessInfoFormData): OrganizationApplicationInput => ({
      name: data.name,
      description: data.description || null,
      tagline: data.tagline || null,
      type: data.type,
      businessType: data.businessType,
      businessRegistrationNumber: data.businessRegistrationNumber || null,
      taxId: data.taxId || null,
      businessEmail: data.businessEmail,
      businessPhone: data.businessPhone,
      website: data.website || null,
      city: data.city,
      province: data.province,
      country: data.country,
      socialLinks: (data.facebook || data.instagram || data.twitter)
        ? {
            facebook: data.facebook || null,
            instagram: data.instagram || null,
            twitter: data.twitter || null,
            linkedin: null,
            tiktok: null,
            youtube: null,
          }
        : null,
      logoUrl: null,
      bannerUrl: null,
    }),
    []
  );

  // Form submission handler
  const onSubmit = useCallback(async (data: BusinessInfoFormData) => {
    setSubmitError(null);
    const input = buildInput(data);

    try {
      if (hasOrganization && organization) {
        const result = await update(organization.id, input);
        if (result) {
          toast.success('Changes saved', 'Your organization information has been updated.');
          // Step 1 → step 2. The documents step needs the businessType we just
          // saved, so it must run after the mutation resolves, not before.
          startTransition(() => router.push(ROUTES.documents));
        } else {
          toast.error('Update failed', 'No result returned from server.');
        }
      } else {
        const result = await apply(input);
        if (result) {
          toast.success('Application started', 'Your organization application has been created.');
          // Step 1 → step 2. The documents step needs the businessType we just
          // saved, so it must run after the mutation resolves, not before.
          startTransition(() => router.push(ROUTES.documents));
        } else {
          toast.error('Creation failed', 'No result returned from server.');
        }
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to save. Please try again.';
      setSubmitError(errorMessage);
      toast.error('Save failed', errorMessage);
    }
  }, [buildInput, hasOrganization, organization, update, apply, toast, router]);

  // Pre-populate form when organization data loads
  useEffect(() => {
    if (formInitialized) return;

    if (organization) {
      reset({
        name: organization.name || '',
        type: (organization.type as BusinessInfoFormData['type']) || 'INDIVIDUAL',
        businessType: (organization.businessType as BusinessInfoFormData['businessType']) ?? undefined,
        tagline: organization.tagline || '',
        description: organization.description || '',
        businessEmail: organization.businessEmail || userEmail,
        businessPhone: organization.businessPhone || userPhone,
        website: organization.website || '',
        businessRegistrationNumber: organization.businessRegistrationNumber || '',
        taxId: organization.taxId || '',
        city: organization.businessAddress?.city || '',
        province: (organization.businessAddress?.province as BusinessInfoFormData['province']) || 'LUSAKA',
        country: organization.businessAddress?.country || 'Zambia',
        facebook: organization.socialLinks?.facebook || '',
        instagram: organization.socialLinks?.instagram || '',
        twitter: organization.socialLinks?.twitter || '',
      });
      setFormInitialized(true);
    } else if (!orgLoading && (userEmail || userPhone)) {
      reset({
        ...DEFAULT_VALUES,
        businessEmail: userEmail,
        businessPhone: userPhone,
      });
      setFormInitialized(true);
    }
  }, [organization, formInitialized, reset, userEmail, userPhone, orgLoading]);

  const isLoadingSkeleton = orgLoading && !formInitialized;
  const isFormSubmitting = isSubmitting || isPending;

  if (isLoadingSkeleton) {
    return <BusinessInfoSkeleton />;
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate data-testid="business-info-form">
      <WizardShell
        currentStep={0}
        title="Tell us about your business"
        subtitle="This determines which documents we'll need from you next."
        onBack={goBack}
        nextType="submit"
        nextLabel="Continue"
        nextLoading={isFormSubmitting}
      >
            {/* ── 1. Basic Information ──────────────────────────────── */}
            <WizardSection
              id="basic"
              icon={<Building width={20} height={20} />}
              title="Basic Information"
              subtitle="Your organization's name, type, and what you do."
            >
              <Grid columns={{ initial: '1', sm: '2' }} gap="4" gapY="4">
                <Controller
                  name="name"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="name" mb="1" style={LABEL_STYLE}>
                        Organization Name <Text as="span" color="red">*</Text>
                      </Text>
                      <TextField.Root id="name" size="2" autoComplete="organization" placeholder="Enter your organization or company name" aria-invalid={!!fieldState.error} {...field} />
                      {fieldState.error && <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>}
                    </Box>
                  )}
                />

                <Controller
                  name="type"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="type" mb="1" style={LABEL_STYLE}>
                        Organization Type <Text as="span" color="red">*</Text>
                      </Text>
                      <SearchableSelect
                        id="type"
                        size="2"
                        value={field.value}
                        onValueChange={field.onChange}
                        options={ORGANIZATION_TYPE_OPTIONS}
                        placeholder="Select type"
                        searchPlaceholder="Search type…"
                        aria-invalid={!!fieldState.error}
                        triggerStyle={{ width: '100%' }}
                      />
                      {fieldState.error && <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>}
                    </Box>
                  )}
                />

                <Box gridColumn={{ initial: '1', sm: '1 / -1' }}>
                  <Controller
                    name="tagline"
                    control={control}
                    render={({ field, fieldState }) => (
                      <Box>
                        <Text as="label" htmlFor="tagline" mb="1" style={LABEL_STYLE}>Tagline</Text>
                        <TextField.Root id="tagline" size="2" placeholder="e.g., Bringing Lusaka's best events to you" {...field} value={field.value || ''} />
                        {fieldState.error ? (
                          <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>
                        ) : (
                          <Text as="p" size="1" color="gray" mt="1">A short phrase that describes your organization (optional)</Text>
                        )}
                      </Box>
                    )}
                  />
                </Box>

                <Box gridColumn={{ initial: '1', sm: '1 / -1' }}>
                  <Controller
                    name="description"
                    control={control}
                    render={({ field, fieldState }) => (
                      <Box>
                        <Text as="label" htmlFor="description" mb="1" style={LABEL_STYLE}>About your organization</Text>
                        <TextArea id="description" size="2" rows={4} placeholder="Tell us about your organization and the events you plan to organize..." {...field} value={field.value || ''} />
                        {fieldState.error ? (
                          <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>
                        ) : (
                          <Text as="p" size="1" color="gray" mt="1">Brief description of what your organization does and the types of events you plan to host</Text>
                        )}
                      </Box>
                    )}
                  />
                </Box>
              </Grid>
            </WizardSection>

            {/* ── 2. Business Registration (KYB) ─────────────────────
                Business type lives here rather than beside "Organization Type"
                on purpose: the two read as near-duplicates side by side, and
                only this one carries consequences — it decides which documents
                the next step will ask for. */}
            <WizardSection
              id="kyb"
              icon={<Page width={20} height={20} />}
              title="Business Registration"
              subtitle="Determines which verification documents we ask for next."
            >
              <Grid columns={{ initial: '1', sm: '2' }} gap="4" gapY="4">
                <Box gridColumn={{ initial: '1', sm: '1 / -1' }}>
                  <Controller
                    name="businessType"
                    control={control}
                    render={({ field, fieldState }) => (
                      <Box>
                        <Text as="label" htmlFor="businessType" mb="1" style={LABEL_STYLE}>
                          Business Type <Text as="span" color="red">*</Text>
                        </Text>
                        <BusinessTypeCombobox
                          id="businessType"
                          value={field.value ?? null}
                          onChange={field.onChange}
                          error={fieldState.error?.message}
                        />
                        {!fieldState.error && (
                          <Text as="p" size="1" color="gray" mt="1">
                            {requiredCount === null
                              ? 'We only ask for documents your business type actually needs.'
                              : `We'll ask for ${requiredCount} document${requiredCount === 1 ? '' : 's'} on the next step.`}
                          </Text>
                        )}
                      </Box>
                    )}
                  />
                </Box>

                <Controller
                  name="businessRegistrationNumber"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="businessRegistrationNumber" mb="1" style={LABEL_STYLE}>
                        Business Registration No.
                      </Text>
                      <TextField.Root
                        id="businessRegistrationNumber"
                        size="2"
                        placeholder="PACRA number"
                        {...field}
                        value={field.value || ''}
                      />
                      {fieldState.error ? (
                        <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>
                      ) : (
                        <Text as="p" size="1" color="gray" mt="1">Optional — a reviewer may ask for it later</Text>
                      )}
                    </Box>
                  )}
                />

                <Controller
                  name="taxId"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="taxId" mb="1" style={LABEL_STYLE}>
                        Tax ID (TPIN)
                      </Text>
                      <TextField.Root
                        id="taxId"
                        size="2"
                        inputMode="numeric"
                        placeholder="10-digit ZRA TPIN"
                        {...field}
                        value={field.value || ''}
                      />
                      {fieldState.error ? (
                        <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>
                      ) : (
                        <Text as="p" size="1" color="gray" mt="1">Optional — a reviewer may ask for it later</Text>
                      )}
                    </Box>
                  )}
                />
              </Grid>
            </WizardSection>

            {/* ── 3. Contact Information ────────────────────────────── */}
            <WizardSection
              id="contact"
              icon={<Phone width={20} height={20} />}
              title="Contact Information"
              subtitle="How attendees and the team can reach your organization."
            >
              <Grid columns={{ initial: '1', sm: '2' }} gap="4" gapY="4">
                <Controller
                  name="businessEmail"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="businessEmail" mb="1" style={LABEL_STYLE}>
                        Business Email <Text as="span" color="red">*</Text>
                      </Text>
                      <TextField.Root id="businessEmail" size="2" type="email" autoComplete="email" placeholder="contact@yourorganization.com" aria-invalid={!!fieldState.error} {...field} />
                      {fieldState.error && <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>}
                    </Box>
                  )}
                />

                <Controller
                  name="businessPhone"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="businessPhone" mb="1" style={LABEL_STYLE}>
                        Phone Number <Text as="span" color="red">*</Text>
                      </Text>
                      <PhoneNumberInput
                        id="businessPhone"
                        name={field.name}
                        value={field.value}
                        onChange={(v) => field.onChange(v ?? '')}
                        onBlur={field.onBlur}
                        aria-invalid={!!fieldState.error}
                        placeholder="97X XXX XXX"
                        className="pml-phone-sm"
                      />
                      {fieldState.error && <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>}
                    </Box>
                  )}
                />

                <Box gridColumn={{ initial: '1', sm: '1 / -1' }}>
                  <Controller
                    name="website"
                    control={control}
                    render={({ field, fieldState }) => (
                      <Box>
                        <Text as="label" htmlFor="website" mb="1" style={LABEL_STYLE}>Website</Text>
                        <TextField.Root id="website" size="2" type="url" autoComplete="url" placeholder="https://yourorganization.com" {...field} value={field.value || ''} />
                        {fieldState.error ? (
                          <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>
                        ) : (
                          <Text as="p" size="1" color="gray" mt="1">Optional</Text>
                        )}
                      </Box>
                    )}
                  />
                </Box>
              </Grid>
            </WizardSection>

            {/* ── 3. Location & Social ──────────────────────────────── */}
            <WizardSection
              id="location"
              icon={<Globe width={20} height={20} />}
              title="Location & Social"
              subtitle="Where you are based, and where people can find you."
            >
              <Grid columns={{ initial: '1', sm: '3' }} gap="4" gapY="4">
                <Controller
                  name="city"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="city" mb="1" style={LABEL_STYLE}>
                        City <Text as="span" color="red">*</Text>
                      </Text>
                      <TextField.Root id="city" size="2" autoComplete="address-level2" placeholder="e.g., Lusaka" aria-invalid={!!fieldState.error} {...field} />
                      {fieldState.error && <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>}
                    </Box>
                  )}
                />

                <Controller
                  name="province"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="province" mb="1" style={LABEL_STYLE}>
                        Province <Text as="span" color="red">*</Text>
                      </Text>
                      <SearchableSelect
                        id="province"
                        size="2"
                        value={field.value}
                        onValueChange={field.onChange}
                        options={PROVINCE_OPTIONS}
                        placeholder="Select province"
                        searchPlaceholder="Search province…"
                        aria-invalid={!!fieldState.error}
                        triggerStyle={{ width: '100%' }}
                      />
                      {fieldState.error && <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>}
                    </Box>
                  )}
                />

                <Controller
                  name="country"
                  control={control}
                  render={({ field }) => (
                    <Box>
                      <Text as="label" htmlFor="country" mb="1" style={LABEL_STYLE}>Country</Text>
                      <TextField.Root id="country" size="2" autoComplete="country-name" disabled {...field} />
                    </Box>
                  )}
                />
              </Grid>

              <Separator size="4" my="4" />

              <Flex align="center" gap="2" mb="3">
                <Text as="span" size="1" weight="bold" style={{ color: 'var(--gray-11)', letterSpacing: '0.06em' }}>
                  SOCIAL MEDIA
                </Text>
                <Badge color="teal" variant="soft" radius="full">OPTIONAL</Badge>
              </Flex>
              <Grid columns={{ initial: '1', sm: '3' }} gap="4" gapY="4">
                <Controller
                  name="facebook"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="facebook" mb="1" style={LABEL_STYLE}>Facebook</Text>
                      <TextField.Root id="facebook" size="2" placeholder="https://facebook.com/yourpage" {...field} value={field.value || ''} />
                      {fieldState.error ? (
                        <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>
                      ) : (
                        <Text as="p" size="1" color="gray" mt="1">Your Facebook page URL</Text>
                      )}
                    </Box>
                  )}
                />

                <Controller
                  name="instagram"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="instagram" mb="1" style={LABEL_STYLE}>Instagram</Text>
                      <TextField.Root id="instagram" size="2" placeholder="https://instagram.com/yourprofile" {...field} value={field.value || ''} />
                      {fieldState.error ? (
                        <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>
                      ) : (
                        <Text as="p" size="1" color="gray" mt="1">Your Instagram profile URL</Text>
                      )}
                    </Box>
                  )}
                />

                <Controller
                  name="twitter"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="twitter" mb="1" style={LABEL_STYLE}>Twitter / X</Text>
                      <TextField.Root id="twitter" size="2" placeholder="https://twitter.com/yourprofile" {...field} value={field.value || ''} />
                      {fieldState.error ? (
                        <Text as="p" size="1" color="red" mt="1">{fieldState.error.message}</Text>
                      ) : (
                        <Text as="p" size="1" color="gray" mt="1">Your Twitter/X profile URL</Text>
                      )}
                    </Box>
                  )}
                />
              </Grid>
            </WizardSection>

            {/* Error Display */}
            {submitError && (
              <Box
                p="3"
                role="alert"
                aria-live="assertive"
                style={{ borderRadius: 'var(--radius-3)', border: '1px solid var(--red-a6)', background: 'var(--red-a2)' }}
              >
                <Text size="2" color="red">{submitError}</Text>
              </Box>
            )}
      </WizardShell>
    </form>
  );
}
