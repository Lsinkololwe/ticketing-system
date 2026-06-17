'use client';

/**
 * Business Info Page - Organization Application
 *
 * Form page using React Hook Form + Zod for validation.
 * Uses Controller for Radix UI controlled components.
 *
 * @see https://react-hook-form.com/docs/usecontroller
 */

import { useCallback, useEffect, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Flex, Text, Heading, Button, TextField, Select, TextArea, Card, Spinner, Grid } from '@radix-ui/themes';
import { Building, ArrowRight, ArrowLeft, InfoCircle, Globe, Phone } from 'iconoir-react';
import {
  StepIndicator,
  BusinessInfoSkeleton,
  APPLICATION_STEPS,
  ORGANIZATION_TYPE_OPTIONS,
  PROVINCE_OPTIONS,
} from '@/components/application';
import { useToast } from '@/components/ui';
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
  tagline: '',
  description: '',
  businessEmail: '',
  businessPhone: '',
  website: '',
  city: '',
  province: 'LUSAKA',
  country: 'Zambia',
  facebook: '',
  instagram: '',
  twitter: '',
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
  const userPhone = (session?.user as { phone?: string })?.phone || '';

  // GraphQL hooks
  const {
    organization,
    hasOrganization,
    loading: orgLoading,
  } = useMyOrganization({ fetchPolicy: 'cache-first' });

  const { apply } = useApplyToBeOrganizer();
  const { update } = useUpdateOrganizationApplication();

  // React Hook Form with Zod
  const { control, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(businessInfoFormSchema),
    defaultValues: DEFAULT_VALUES,
    mode: 'onBlur', // Validate on blur for better UX
  });

  // Navigation handlers
  const goBack = useCallback(() => router.push('/welcome'), [router]);

  // Build GraphQL input from form data
  const buildInput = useCallback(
    (data: BusinessInfoFormData): OrganizationApplicationInput => ({
      name: data.name,
      description: data.description || null,
      tagline: data.tagline || null,
      type: data.type,
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
          startTransition(() => router.push('/apply/review'));
        } else {
          toast.error('Update failed', 'No result returned from server.');
        }
      } else {
        const result = await apply(input);
        if (result) {
          toast.success('Application started', 'Your organization application has been created.');
          startTransition(() => router.push('/apply/review'));
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
        type: (organization.type as 'INDIVIDUAL' | 'BUSINESS') || 'INDIVIDUAL',
        tagline: organization.tagline || '',
        description: organization.description || '',
        businessEmail: organization.businessEmail || userEmail,
        businessPhone: organization.businessPhone || userPhone,
        website: organization.website || '',
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

  // Loading states
  const isFormSubmitting = isSubmitting || isPending;

  if (orgLoading && !formInitialized) {
    return <BusinessInfoSkeleton />;
  }

  return (
    <Box>
      {/* Step Indicator */}
      <StepIndicator steps={APPLICATION_STEPS} currentStep={0} allowNavigation={false} />

      {/* Page Header */}
      <Box mb="6">
        <Heading size="6" mb="2" highContrast>
          Organization Information
        </Heading>
        <Text as="p" size="3" color="gray">
          Tell us about your organization. This information will be visible on your event pages.
        </Text>
      </Box>

      {/* Form */}
      <form onSubmit={handleSubmit(onSubmit)}>
        {/* Basic Information Card */}
        <Card mb="6" variant="surface">
          <Box p="5">
            <Flex align="center" gap="2" mb="4">
              <Building width={20} height={20} className="icon-accent" aria-hidden="true" />
              <Text size="3" weight="medium" highContrast>
                Basic Information
              </Text>
            </Flex>

            <Grid columns={{ initial: '1', sm: '2' }} gap="4">
              <Controller
                name="name"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Organization Name <Text as="span" color="red">*</Text>
                    </Text>
                    <TextField.Root
                      size="3"
                      placeholder="Enter your organization or company name"
                      {...field}
                    />
                    {errors.name && (
                      <Text size="1" color="red" mt="1">{errors.name.message}</Text>
                    )}
                  </Box>
                )}
              />

              <Controller
                name="type"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Organization Type <Text as="span" color="red">*</Text>
                    </Text>
                    <Select.Root
                      size="3"
                      value={field.value}
                      onValueChange={field.onChange}
                    >
                      <Select.Trigger placeholder="Select type" />
                      <Select.Content>
                        {ORGANIZATION_TYPE_OPTIONS.map((type) => (
                          <Select.Item key={type.value} value={type.value}>
                            {type.label}
                          </Select.Item>
                        ))}
                      </Select.Content>
                    </Select.Root>
                    {errors.type && (
                      <Text size="1" color="red" mt="1">{errors.type.message}</Text>
                    )}
                  </Box>
                )}
              />

              <Box gridColumn={{ initial: '1', sm: '1 / -1' }}>
                <Controller
                  name="tagline"
                  control={control}
                  render={({ field }) => (
                    <Box>
                      <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                        Tagline
                      </Text>
                      <TextField.Root
                        size="3"
                        placeholder="e.g., Bringing Lusaka's best events to you"
                        {...field}
                        value={field.value || ''}
                      />
                      {!errors.tagline && (
                        <Text size="1" color="gray" mt="1">A short phrase that describes your organization (optional)</Text>
                      )}
                      {errors.tagline && (
                        <Text size="1" color="red" mt="1">{errors.tagline.message}</Text>
                      )}
                    </Box>
                  )}
                />
              </Box>

              <Box gridColumn={{ initial: '1', sm: '1 / -1' }}>
                <Controller
                  name="description"
                  control={control}
                  render={({ field }) => (
                    <Box>
                      <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                        About Your Organization
                      </Text>
                      <TextArea
                        size="3"
                        rows={4}
                        placeholder="Tell us about your organization and the events you plan to organize..."
                        {...field}
                        value={field.value || ''}
                      />
                      {!errors.description && (
                        <Text size="1" color="gray" mt="1">Brief description of what your organization does and the types of events you plan to host</Text>
                      )}
                      {errors.description && (
                        <Text size="1" color="red" mt="1">{errors.description.message}</Text>
                      )}
                    </Box>
                  )}
                />
              </Box>
            </Grid>
          </Box>
        </Card>

        {/* Contact Information Card */}
        <Card mb="6" variant="surface">
          <Box p="5">
            <Flex align="center" gap="2" mb="4">
              <Phone width={20} height={20} className="icon-accent" aria-hidden="true" />
              <Text size="3" weight="medium" highContrast>
                Contact Information
              </Text>
            </Flex>

            <Grid columns={{ initial: '1', sm: '2' }} gap="4">
              <Controller
                name="businessEmail"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Business Email <Text as="span" color="red">*</Text>
                    </Text>
                    <TextField.Root
                      size="3"
                      type="email"
                      placeholder="contact@yourorganization.com"
                      {...field}
                    />
                    {errors.businessEmail && (
                      <Text size="1" color="red" mt="1">{errors.businessEmail.message}</Text>
                    )}
                  </Box>
                )}
              />

              <Controller
                name="businessPhone"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Phone Number <Text as="span" color="red">*</Text>
                    </Text>
                    <TextField.Root
                      size="3"
                      type="tel"
                      placeholder="+260 97X XXX XXX"
                      {...field}
                    />
                    {errors.businessPhone && (
                      <Text size="1" color="red" mt="1">{errors.businessPhone.message}</Text>
                    )}
                  </Box>
                )}
              />

              <Controller
                name="website"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Website
                    </Text>
                    <TextField.Root
                      size="3"
                      type="url"
                      placeholder="https://yourorganization.com"
                      {...field}
                      value={field.value || ''}
                    />
                    {!errors.website && (
                      <Text size="1" color="gray" mt="1">Optional</Text>
                    )}
                    {errors.website && (
                      <Text size="1" color="red" mt="1">{errors.website.message}</Text>
                    )}
                  </Box>
                )}
              />
            </Grid>
          </Box>
        </Card>

        {/* Location Card */}
        <Card mb="6" variant="surface">
          <Box p="5">
            <Flex align="center" gap="2" mb="4">
              <Globe width={20} height={20} className="icon-accent" aria-hidden="true" />
              <Text size="3" weight="medium" highContrast>
                Location
              </Text>
            </Flex>

            <Grid columns={{ initial: '1', sm: '3' }} gap="4">
              <Controller
                name="city"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      City <Text as="span" color="red">*</Text>
                    </Text>
                    <TextField.Root
                      size="3"
                      placeholder="e.g., Lusaka"
                      {...field}
                    />
                    {errors.city && (
                      <Text size="1" color="red" mt="1">{errors.city.message}</Text>
                    )}
                  </Box>
                )}
              />

              <Controller
                name="province"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Province <Text as="span" color="red">*</Text>
                    </Text>
                    <Select.Root
                      size="3"
                      value={field.value}
                      onValueChange={field.onChange}
                    >
                      <Select.Trigger placeholder="Select province" />
                      <Select.Content>
                        {PROVINCE_OPTIONS.map((prov) => (
                          <Select.Item key={prov.value} value={prov.value}>
                            {prov.label}
                          </Select.Item>
                        ))}
                      </Select.Content>
                    </Select.Root>
                    {errors.province && (
                      <Text size="1" color="red" mt="1">{errors.province.message}</Text>
                    )}
                  </Box>
                )}
              />

              <Controller
                name="country"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Country
                    </Text>
                    <TextField.Root
                      size="3"
                      disabled
                      {...field}
                    />
                  </Box>
                )}
              />
            </Grid>
          </Box>
        </Card>

        {/* Social Links Card */}
        <Card mb="6" variant="surface">
          <Box p="5">
            <Flex align="center" gap="2" mb="4">
              <InfoCircle width={20} height={20} className="icon-accent" aria-hidden="true" />
              <Text size="3" weight="medium" highContrast>
                Social Media (Optional)
              </Text>
            </Flex>

            <Grid columns={{ initial: '1', sm: '3' }} gap="4">
              <Controller
                name="facebook"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Facebook
                    </Text>
                    <TextField.Root
                      size="3"
                      placeholder="https://facebook.com/yourpage"
                      {...field}
                      value={field.value || ''}
                    />
                    {!errors.facebook && (
                      <Text size="1" color="gray" mt="1">Your Facebook page URL</Text>
                    )}
                    {errors.facebook && (
                      <Text size="1" color="red" mt="1">{errors.facebook.message}</Text>
                    )}
                  </Box>
                )}
              />

              <Controller
                name="instagram"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Instagram
                    </Text>
                    <TextField.Root
                      size="3"
                      placeholder="https://instagram.com/yourprofile"
                      {...field}
                      value={field.value || ''}
                    />
                    {!errors.instagram && (
                      <Text size="1" color="gray" mt="1">Your Instagram profile URL</Text>
                    )}
                    {errors.instagram && (
                      <Text size="1" color="red" mt="1">{errors.instagram.message}</Text>
                    )}
                  </Box>
                )}
              />

              <Controller
                name="twitter"
                control={control}
                render={({ field }) => (
                  <Box>
                    <Text as="label" size="2" weight="medium" color="gray" mb="1" style={{ display: 'block' }}>
                      Twitter / X
                    </Text>
                    <TextField.Root
                      size="3"
                      placeholder="https://twitter.com/yourprofile"
                      {...field}
                      value={field.value || ''}
                    />
                    {!errors.twitter && (
                      <Text size="1" color="gray" mt="1">Your Twitter/X profile URL</Text>
                    )}
                    {errors.twitter && (
                      <Text size="1" color="red" mt="1">{errors.twitter.message}</Text>
                    )}
                  </Box>
                )}
              />
            </Grid>
          </Box>
        </Card>

        {/* Error Display */}
        {submitError && (
          <Card role="alert" aria-live="assertive" mb="4" variant="surface" className="error-card">
            <Box p="3">
              <Text size="2" color="red">
                {submitError}
              </Text>
            </Box>
          </Card>
        )}

        {/* Navigation */}
        <Flex justify="between" align="center" gap="3" mt="6">
          <Button
            type="button"
            variant="soft"
            size="3"
            onClick={goBack}
            disabled={isFormSubmitting}
          >
            <ArrowLeft width={16} height={16} aria-hidden="true" />
            Back
          </Button>

          <Button
            type="submit"
            size="3"
            color="teal"
            disabled={isFormSubmitting}
          >
            {isFormSubmitting ? (
              <>
                <Spinner size="1" />
                Saving...
              </>
            ) : (
              <>
                Continue to Review
                <ArrowRight width={18} height={18} aria-hidden="true" />
              </>
            )}
          </Button>
        </Flex>
      </form>
    </Box>
  );
}
