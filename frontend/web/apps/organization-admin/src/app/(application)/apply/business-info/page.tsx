'use client';

/**
 * Business Info Page - Organization Application
 *
 * Two-column wizard layout (built entirely from native Radix UI Themes
 * components: Card, Box, Grid, Flex, Text, Heading, TextField, TextArea, Select,
 * Button, Separator, Badge).
 *
 *   Left rail  : step progress, an "On this page" section nav (scroll-spy), help.
 *   Right pane : page title + one Card per section (icon chip + title + divider).
 *
 * Form state via React Hook Form + Zod (unchanged).
 *
 * @see https://react-hook-form.com/docs/usecontroller
 */

import { useCallback, useEffect, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Box,
  Flex,
  Text,
  Heading,
  Button,
  TextField,
  TextArea,
  Spinner,
  Grid,
  Card,
  Separator,
  Badge,
} from '@radix-ui/themes';
import { ArrowRight, ArrowLeft, Building, Phone, Globe } from 'iconoir-react';
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

// In-page sections (drives the "On this page" nav + scroll-spy)
const SECTIONS = [
  { id: 'basic', title: 'Basic Information', subtitle: 'Name, type & description' },
  { id: 'contact', title: 'Contact Information', subtitle: 'Email, phone & website' },
  { id: 'location', title: 'Location & Social', subtitle: 'City, province & links' },
] as const;

const LABEL_STYLE: React.CSSProperties = { display: 'block', color: 'var(--gray-12)' };
const SECTION_SCROLL_STYLE: React.CSSProperties = { scrollMarginTop: '96px' };

// Rounded-square icon chip (teal tint) used in each section header
const ICON_CHIP_STYLE: React.CSSProperties = {
  width: 44,
  height: 44,
  borderRadius: 'var(--radius-3)',
  background: 'var(--accent-a3)',
  color: 'var(--accent-11)',
  flexShrink: 0,
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
  const [activeSection, setActiveSection] = useState<string>('basic');

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
  const { control, handleSubmit, reset, formState: { isSubmitting } } = useForm({
    resolver: zodResolver(businessInfoFormSchema),
    defaultValues: DEFAULT_VALUES,
    mode: 'onBlur', // Validate on blur for better UX
  });

  // Navigation handlers
  const goBack = useCallback(() => router.push('/welcome'), [router]);

  const scrollToSection = useCallback((id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, []);

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
        type: (organization.type as BusinessInfoFormData['type']) || 'INDIVIDUAL',
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

  const isLoadingSkeleton = orgLoading && !formInitialized;
  const isFormSubmitting = isSubmitting || isPending;

  // Scroll-spy: highlight the section currently in view (re-attaches once the
  // form has rendered, i.e. when the skeleton is replaced).
  useEffect(() => {
    if (isLoadingSkeleton) return;
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) setActiveSection(visible[0].target.id);
      },
      { rootMargin: '-100px 0px -55% 0px', threshold: 0 }
    );
    SECTIONS.forEach((s) => {
      const el = document.getElementById(s.id);
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, [isLoadingSkeleton]);

  if (isLoadingSkeleton) {
    return <BusinessInfoSkeleton />;
  }

  // ── Section header (native Radix composition) ────────────────────────────
  const sectionHeader = (icon: React.ReactNode, title: string, subtitle: string, optional = false) => (
    <>
      <Flex align="center" gap="3" mb="3">
        <Flex align="center" justify="center" style={ICON_CHIP_STYLE} aria-hidden="true">
          {icon}
        </Flex>
        <Box flexGrow="1">
          <Flex align="center" gap="2">
            <Heading as="h2" size="4" color="teal" highContrast style={{ letterSpacing: '-0.01em' }}>
              {title}
            </Heading>
            {optional && <Badge color="teal" variant="soft" radius="full">OPTIONAL</Badge>}
          </Flex>
          <Text as="p" size="2" color="gray">{subtitle}</Text>
        </Box>
      </Flex>
      <Separator size="4" mb="4" />
    </>
  );

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <Flex gap="6" align="start" direction={{ initial: 'column', md: 'row' }}>
        {/* ════════════════ LEFT RAIL ════════════════ */}
        <Box
          flexShrink="0"
          width={{ initial: '100%', md: '300px' }}
          display={{ initial: 'none', md: 'block' }}
          style={{ position: 'sticky', top: '88px', alignSelf: 'flex-start' }}
        >
          <Flex direction="column" gap="4">
            {/* On this page */}
            <Card size="2">
              <Text size="1" weight="bold" color="gray" style={{ letterSpacing: '0.08em', display: 'block' }} mb="2">
                ON THIS PAGE
              </Text>
              <Flex direction="column" gap="1">
                {SECTIONS.map((s, i) => {
                  const active = activeSection === s.id;
                  return (
                    <Box key={s.id} asChild>
                      <button
                        type="button"
                        onClick={() => scrollToSection(s.id)}
                        style={{
                          width: '100%',
                          textAlign: 'left',
                          cursor: 'pointer',
                          background: active ? 'var(--accent-a3)' : 'transparent',
                          border: 'none',
                          borderRadius: 'var(--radius-3)',
                          padding: '8px',
                        }}
                      >
                        <Flex align="center" gap="3">
                          <Flex
                            align="center"
                            justify="center"
                            style={{
                              width: 24,
                              height: 24,
                              borderRadius: '50%',
                              flexShrink: 0,
                              background: active ? 'var(--color-background)' : 'var(--gray-a3)',
                              border: active ? '2px solid var(--accent-9)' : '2px solid transparent',
                              color: active ? 'var(--accent-11)' : 'var(--gray-9)',
                            }}
                          >
                            <Text size="1" weight="bold">{i + 1}</Text>
                          </Flex>
                          <Box>
                            <Text size="2" weight={active ? 'bold' : 'medium'} style={{ display: 'block', color: active ? 'var(--accent-12)' : 'var(--gray-12)' }}>
                              {s.title}
                            </Text>
                            <Text size="1" color="gray">{s.subtitle}</Text>
                          </Box>
                        </Flex>
                      </button>
                    </Box>
                  );
                })}
              </Flex>
            </Card>

            {/* Need a hand? */}
            <Box
              p="4"
              style={{ background: 'var(--accent-a3)', borderRadius: 'var(--radius-4)' }}
            >
              <Text as="p" size="2" weight="bold" mb="1" style={{ color: 'var(--accent-12)' }}>
                Need a hand?
              </Text>
              <Text as="p" size="2" style={{ color: 'var(--accent-12)', opacity: 0.85 }}>
                Your organization details appear publicly on your event pages. You can edit them later from settings.
              </Text>
            </Box>
          </Flex>
        </Box>

        {/* ════════════════ RIGHT PANE ════════════════ */}
        <Box flexGrow="1" width="100%" style={{ minWidth: 0 }}>
          {/* Page Header */}
          <Box mb="5">
            <Heading size="7" mb="1" color="teal" highContrast style={{ letterSpacing: '-0.02em' }}>
              Organization Information
            </Heading>
            <Text as="p" size="3" color="gray">
              Tell us about your organization. This information will be visible on your event pages.
            </Text>
          </Box>

          <Flex direction="column" gap="5">
            {/* ── 1. Basic Information ──────────────────────────────── */}
            <Card id="basic" size="3" style={SECTION_SCROLL_STYLE}>
              {sectionHeader(<Building width={22} height={22} />, 'Basic Information', "Your organization's name, type, and what you do.")}
              <Grid columns={{ initial: '1', sm: '2' }} gap="4" gapY="4">
                <Controller
                  name="name"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="name" size="1" weight="bold" mb="1" style={LABEL_STYLE}>
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
                      <Text as="label" htmlFor="type" size="1" weight="bold" mb="1" style={LABEL_STYLE}>
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
                        <Text as="label" htmlFor="tagline" size="1" weight="bold" mb="1" style={LABEL_STYLE}>Tagline</Text>
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
                        <Text as="label" htmlFor="description" size="1" weight="bold" mb="1" style={LABEL_STYLE}>About Your Organization</Text>
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
            </Card>

            {/* ── 2. Contact Information ────────────────────────────── */}
            <Card id="contact" size="3" style={SECTION_SCROLL_STYLE}>
              {sectionHeader(<Phone width={22} height={22} />, 'Contact Information', 'How attendees and the team can reach your organization.')}
              <Grid columns={{ initial: '1', sm: '2' }} gap="4" gapY="4">
                <Controller
                  name="businessEmail"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="businessEmail" size="1" weight="bold" mb="1" style={LABEL_STYLE}>
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
                      <Text as="label" htmlFor="businessPhone" size="1" weight="bold" mb="1" style={LABEL_STYLE}>
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
                        <Text as="label" htmlFor="website" size="1" weight="bold" mb="1" style={LABEL_STYLE}>Website</Text>
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
            </Card>

            {/* ── 3. Location & Social ──────────────────────────────── */}
            <Card id="location" size="3" style={SECTION_SCROLL_STYLE}>
              {sectionHeader(<Globe width={22} height={22} />, 'Location & Social', "Where you're based and where to find you online.")}
              <Grid columns={{ initial: '1', sm: '3' }} gap="4" gapY="4">
                <Controller
                  name="city"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Box>
                      <Text as="label" htmlFor="city" size="1" weight="bold" mb="1" style={LABEL_STYLE}>
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
                      <Text as="label" htmlFor="province" size="1" weight="bold" mb="1" style={LABEL_STYLE}>
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
                      <Text as="label" htmlFor="country" size="1" weight="bold" mb="1" style={LABEL_STYLE}>Country</Text>
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
                      <Text as="label" htmlFor="facebook" size="1" weight="bold" mb="1" style={LABEL_STYLE}>Facebook</Text>
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
                      <Text as="label" htmlFor="instagram" size="1" weight="bold" mb="1" style={LABEL_STYLE}>Instagram</Text>
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
                      <Text as="label" htmlFor="twitter" size="1" weight="bold" mb="1" style={LABEL_STYLE}>Twitter / X</Text>
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
            </Card>

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
          </Flex>
        </Box>
      </Flex>

      {/* ════════════════ STICKY FOOTER ════════════════ */}
      <Box
        mt="6"
        py="3"
        style={{
          position: 'sticky',
          bottom: 0,
          background: 'var(--color-background)',
          borderTop: '1px solid var(--gray-a5)',
          zIndex: 10,
        }}
      >
        <Flex justify="between" align="center" gap="3">
          <Button type="button" variant="soft" color="gray" size="3" onClick={goBack} disabled={isFormSubmitting}>
            <ArrowLeft width={16} height={16} aria-hidden="true" />
            Back
          </Button>

          <Box display={{ initial: 'none', sm: 'block' }}>
            <Text size="2" color="gray" weight="medium">Step 1 of 2</Text>
          </Box>

          <Button type="submit" size="3" disabled={isFormSubmitting}>
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
      </Box>
    </form>
  );
}
