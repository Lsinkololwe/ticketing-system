'use client';

/**
 * Business Info Page - Organization Application
 *
 * Collects organization information for the organizer application:
 * - Organization name and type
 * - Contact information (email, phone, website)
 * - Location (city, province, country)
 * - Description
 *
 * MIGRATION NOTE: This page now uses the Organization model and GraphQL operations
 * instead of OrganizerProfile.
 *
 * Fields collected match OrganizationApplicationInput:
 * - name (required)
 * - type (INDIVIDUAL/BUSINESS)
 * - description
 * - tagline
 * - businessEmail
 * - businessPhone
 * - website
 * - city
 * - province
 * - country
 * - socialLinks
 *
 * OWASP Compliance:
 * - Uses authenticated GraphQL mutations
 * - Validates all input before submission
 * - No sensitive data stored in client state beyond what's needed
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Button, TextField, Select, TextArea, Card, Spinner } from '@radix-ui/themes';
import { Building, ArrowRight, ArrowLeft, InfoCircle, WarningTriangle, Wifi, Globe, Phone } from 'iconoir-react';
import { StepIndicator, Step } from '@/components/application/StepIndicator';
import {
  useMyOrganization,
  useApplyToBeOrganizer,
  useUpdateOrganizationApplication,
  type OrganizationApplicationInput,
  getRouteForStatus,
  canEditApplication,
  businessInfoFormSchema,
  type BusinessInfoFormData as BusinessInfoFormSchema,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import { useZodForm } from '@pml.tickets/shared';
import { isNetworkError, isServerUnavailable } from '@pml.tickets/shared';

// =============================================================================
// TYPES
// =============================================================================

// Form data type is now inferred from Zod schema
type FormData = BusinessInfoFormSchema;

// =============================================================================
// CONSTANTS
// =============================================================================

const steps: Step[] = [
  { id: 'business-info', title: 'Organization Info' },
  { id: 'review', title: 'Review & Submit' },
];

const organizationTypes = [
  { value: 'INDIVIDUAL', label: 'Individual / Personal' },
  { value: 'BUSINESS', label: 'Business / Company' },
];

const provinces = [
  { value: 'CENTRAL', label: 'Central Province' },
  { value: 'COPPERBELT', label: 'Copperbelt Province' },
  { value: 'EASTERN', label: 'Eastern Province' },
  { value: 'LUAPULA', label: 'Luapula Province' },
  { value: 'LUSAKA', label: 'Lusaka Province' },
  { value: 'MUCHINGA', label: 'Muchinga Province' },
  { value: 'NORTHERN', label: 'Northern Province' },
  { value: 'NORTH_WESTERN', label: 'North-Western Province' },
  { value: 'SOUTHERN', label: 'Southern Province' },
  { value: 'WESTERN', label: 'Western Province' },
];

// =============================================================================
// FORM FIELD COMPONENT
// =============================================================================

interface FormFieldProps {
  label: string;
  required?: boolean;
  error?: string;
  helper?: string;
  children: React.ReactNode;
}

function FormField({ label, required, error, helper, children }: FormFieldProps) {
  return (
    <Box mb="4">
      <Text
        as="label"
        size="2"
        weight="medium"
        style={{ color: '#CBD5E1', display: 'block', marginBottom: '8px' }}
      >
        {label}
        {required && <span style={{ color: '#F87171', marginLeft: 4 }}>*</span>}
      </Text>
      {children}
      {helper && !error && (
        <Text size="1" style={{ color: '#94A3B8', display: 'block', marginTop: '4px' }}>
          {helper}
        </Text>
      )}
      {error && (
        <Text size="1" style={{ color: '#F87171', display: 'block', marginTop: '4px' }}>
          {error}
        </Text>
      )}
    </Box>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function BusinessInfoPage() {
  const router = useRouter();
  const {
    organization,
    hasOrganization,
    status,
    loading: organizationLoading,
    error: organizationError,
    refetch,
  } = useMyOrganization();
  const { apply, error: applyError } = useApplyToBeOrganizer();
  const { update, error: updateError } = useUpdateOrganizationApplication();
  // Note: isInitializing is kept as a constant since initialization is handled by organizationLoading
  const isInitializing = false;

  // Track initialization attempts to prevent infinite loops
  const initAttemptedRef = useRef(false);
  const [formInitialized, setFormInitialized] = useState(false);

  // Initial form data (will be updated from organization data)
  const initialFormData: FormData = {
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

  // Build GraphQL input from validated Zod data
  const buildInputFromValidatedData = useCallback(
    (validatedData: FormData): OrganizationApplicationInput => {
      const socialLinks =
        validatedData.facebook || validatedData.instagram || validatedData.twitter
          ? {
              facebook: validatedData.facebook || null,
              instagram: validatedData.instagram || null,
              twitter: validatedData.twitter || null,
              linkedin: null,
              tiktok: null,
              youtube: null,
            }
          : null;

      return {
        name: validatedData.name,
        description: validatedData.description || null,
        tagline: validatedData.tagline || null,
        type: validatedData.type,
        businessEmail: validatedData.businessEmail,
        businessPhone: validatedData.businessPhone,
        website: validatedData.website || null,
        city: validatedData.city,
        province: validatedData.province,
        country: validatedData.country,
        socialLinks,
        logoUrl: null,
        bannerUrl: null,
      };
    },
    []
  );

  // Zod-based form management with type inference
  const form = useZodForm(
    businessInfoFormSchema,
    initialFormData,
    async (validatedData) => {
      // This handler receives validated data with correct types
      const input = buildInputFromValidatedData(validatedData);

      if (hasOrganization && organization) {
        const result = await update(organization.id, input);
        if (result) {
          router.push('/apply/review');
        }
      } else {
        const result = await apply(input);
        if (result) {
          router.push('/apply/review');
        }
      }
    }
  );

  // Redirect if user has profile and can't edit
  useEffect(() => {
    if (organizationLoading || isInitializing) return;

    // If there's a network error, don't redirect
    if (organizationError && isNetworkError(organizationError)) {
      return;
    }

    if (hasOrganization && status && !canEditApplication(status)) {
      const route = getRouteForStatus(status, organization?.id);
      router.replace(route);
    }
  }, [organizationLoading, hasOrganization, status, isInitializing, organizationError, router]);

  // Pre-populate form with existing organization data
  useEffect(() => {
    if (organization && !formInitialized) {
      form.setData({
        name: organization.name || '',
        type: (organization.type as 'INDIVIDUAL' | 'BUSINESS') || 'INDIVIDUAL',
        tagline: organization.tagline || '',
        description: organization.description || '',
        businessEmail: organization.businessEmail || '',
        businessPhone: organization.businessPhone || '',
        website: organization.website || '',
        city: organization.businessAddress?.city || '',
        province: (organization.businessAddress?.province as any) || 'LUSAKA',
        country: organization.businessAddress?.country || 'Zambia',
        facebook: organization.socialLinks?.facebook || '',
        instagram: organization.socialLinks?.instagram || '',
        twitter: organization.socialLinks?.twitter || '',
      });
      setFormInitialized(true);
    }
  }, [organization, formInitialized, form]);

  // Show loading state while fetching profile
  if (organizationLoading || isInitializing || form.isSubmitting) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spinner size="3" />
        <Text size="2" style={{ color: '#94A3B8', display: 'block', marginTop: 16 }}>
          {form.isSubmitting ? 'Saving your information...' : 'Loading...'}
        </Text>
      </Box>
    );
  }

  // Show error state
  const currentError = organizationError || applyError;
  const isBackendUnavailable = currentError && isServerUnavailable(currentError);

  if (currentError && !hasOrganization) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Box
          style={{
            width: 64,
            height: 64,
            borderRadius: '16px',
            background: isBackendUnavailable ? 'rgba(251, 191, 36, 0.1)' : 'rgba(239, 68, 68, 0.1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px',
          }}
        >
          {isBackendUnavailable ? (
            <Wifi style={{ width: 32, height: 32, color: '#FBBF24' }} />
          ) : (
            <WarningTriangle style={{ width: 32, height: 32, color: '#EF4444' }} />
          )}
        </Box>
        <Text size="3" weight="medium" style={{ color: '#F8FAFC', display: 'block', marginBottom: 8 }}>
          {isBackendUnavailable ? 'Unable to connect to server' : 'Something went wrong'}
        </Text>
        <Text size="2" style={{ color: '#94A3B8', display: 'block', marginBottom: 16, maxWidth: 400, margin: '0 auto 16px' }}>
          {isBackendUnavailable
            ? 'The server is currently unavailable. Please check your internet connection or try again later.'
            : currentError?.message || 'An error occurred.'}
        </Text>
        <Flex gap="3" justify="center">
          <Button variant="outline" onClick={() => router.push('/welcome')}>
            Go Back
          </Button>
          <Button
            onClick={() => {
              initAttemptedRef.current = false;
              refetch();
            }}
            style={{
              background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
            }}
          >
            Try Again
          </Button>
        </Flex>
      </Box>
    );
  }

  return (
    <Box>
      {/* Step Indicator */}
      <StepIndicator steps={steps} currentStep={0} allowNavigation={false} />

      {/* Page Header */}
      <Box mb="6">
        <Heading size="5" mb="2" style={{ color: '#F8FAFC' }}>
          Organization Information
        </Heading>
        <Text size="2" style={{ color: '#94A3B8' }}>
          Tell us about your organization. This information will be visible on your event pages.
        </Text>
      </Box>

      {/* Form */}
      <Box>
        {/* Basic Information Card */}
        <Card
          mb="6"
          className="application-card"
          style={{
            background: 'rgba(30, 41, 59, 0.5)',
            border: '1px solid rgba(148, 163, 184, 0.1)',
            borderRadius: '16px',
            padding: '32px',
          }}
        >
          <Flex align="center" gap="2" mb="4">
            <Building style={{ width: 20, height: 20, color: '#10B981' }} />
            <Text size="3" weight="medium" style={{ color: '#F8FAFC' }}>
              Basic Information
            </Text>
          </Flex>

          <Box
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: '16px',
            }}
          >
            <FormField label="Organization Name" required error={form.errors.name}>
              <TextField.Root
                size="3"
                placeholder="Enter your organization or company name"
                value={form.data.name}
                onChange={(e) => form.updateField('name', e.target.value)}
                className="application-input"
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: form.errors.name ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Organization Type" required>
              <Select.Root
                value={form.data.type}
                onValueChange={(value) => form.updateField('type', value as 'INDIVIDUAL' | 'BUSINESS')}
              >
                <Select.Trigger
                  placeholder="Select type"
                  style={{
                    width: '100%',
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: '1px solid rgba(148, 163, 184, 0.2)',
                  }}
                />
                <Select.Content>
                  {organizationTypes.map((type) => (
                    <Select.Item key={type.value} value={type.value}>
                      {type.label}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </FormField>

            <Box style={{ gridColumn: '1 / -1' }}>
              <FormField
                label="Tagline"
                helper="A short phrase that describes your organization (optional)"
                error={form.errors.tagline}
              >
                <TextField.Root
                  size="3"
                  placeholder="e.g., Bringing Lusaka's best events to you"
                  value={form.data.tagline || ''}
                  onChange={(e) => form.updateField('tagline', e.target.value)}
                  style={{
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: '1px solid rgba(148, 163, 184, 0.2)',
                  }}
                />
              </FormField>
            </Box>

            <Box style={{ gridColumn: '1 / -1' }}>
              <FormField
                label="About Your Organization"
                helper="Brief description of what your organization does and the types of events you plan to host"
                error={form.errors.description}
              >
                <TextArea
                  size="3"
                  rows={4}
                  placeholder="Tell us about your organization and the events you plan to organize..."
                  value={form.data.description || ''}
                  onChange={(e) => form.updateField('description', e.target.value)}
                  style={{
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: '1px solid rgba(148, 163, 184, 0.2)',
                    resize: 'vertical',
                  }}
                />
              </FormField>
            </Box>
          </Box>
        </Card>

        {/* Contact Information Card */}
        <Card
          mb="6"
          style={{
            background: 'rgba(30, 41, 59, 0.5)',
            border: '1px solid rgba(148, 163, 184, 0.1)',
            borderRadius: '16px',
            padding: '32px',
          }}
        >
          <Flex align="center" gap="2" mb="4">
            <Phone style={{ width: 20, height: 20, color: '#10B981' }} />
            <Text size="3" weight="medium" style={{ color: '#F8FAFC' }}>
              Contact Information
            </Text>
          </Flex>

          <Box
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: '16px',
            }}
          >
            <FormField label="Business Email" required error={form.errors.businessEmail}>
              <TextField.Root
                size="3"
                type="email"
                placeholder="contact@yourorganization.com"
                value={form.data.businessEmail}
                onChange={(e) => form.updateField('businessEmail', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: form.errors.businessEmail ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Phone Number" required error={form.errors.businessPhone}>
              <TextField.Root
                size="3"
                type="tel"
                placeholder="+260 97X XXX XXX"
                value={form.data.businessPhone}
                onChange={(e) => form.updateField('businessPhone', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: form.errors.businessPhone ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Website" helper="Optional" error={form.errors.website}>
              <TextField.Root
                size="3"
                type="url"
                placeholder="https://yourorganization.com"
                value={form.data.website || ''}
                onChange={(e) => form.updateField('website', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>
          </Box>
        </Card>

        {/* Location Card */}
        <Card
          mb="6"
          style={{
            background: 'rgba(30, 41, 59, 0.5)',
            border: '1px solid rgba(148, 163, 184, 0.1)',
            borderRadius: '16px',
            padding: '32px',
          }}
        >
          <Flex align="center" gap="2" mb="4">
            <Globe style={{ width: 20, height: 20, color: '#10B981' }} />
            <Text size="3" weight="medium" style={{ color: '#F8FAFC' }}>
              Location
            </Text>
          </Flex>

          <Box
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: '16px',
            }}
          >
            <FormField label="City" required error={form.errors.city}>
              <TextField.Root
                size="3"
                placeholder="e.g., Lusaka"
                value={form.data.city}
                onChange={(e) => form.updateField('city', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: form.errors.city ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Province" required error={form.errors.province}>
              <Select.Root
                value={form.data.province}
                onValueChange={(value) => form.updateField('province', value)}
              >
                <Select.Trigger
                  placeholder="Select province"
                  style={{
                    width: '100%',
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: form.errors.province ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                  }}
                />
                <Select.Content>
                  {provinces.map((prov) => (
                    <Select.Item key={prov.value} value={prov.value}>
                      {prov.label}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </FormField>

            <FormField label="Country">
              <TextField.Root
                size="3"
                value={form.data.country}
                disabled
                style={{
                  background: 'rgba(15, 23, 42, 0.4)',
                  border: '1px solid rgba(148, 163, 184, 0.1)',
                  color: '#94A3B8',
                }}
              />
            </FormField>
          </Box>
        </Card>

        {/* Social Links Card */}
        <Card
          mb="6"
          style={{
            background: 'rgba(30, 41, 59, 0.5)',
            border: '1px solid rgba(148, 163, 184, 0.1)',
            borderRadius: '16px',
            padding: '32px',
          }}
        >
          <Flex align="center" gap="2" mb="4">
            <InfoCircle style={{ width: 20, height: 20, color: '#10B981' }} />
            <Text size="3" weight="medium" style={{ color: '#F8FAFC' }}>
              Social Media (Optional)
            </Text>
          </Flex>

          <Box
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: '16px',
            }}
          >
            <FormField label="Facebook" helper="Your Facebook page URL" error={form.errors.facebook}>
              <TextField.Root
                size="3"
                placeholder="https://facebook.com/yourpage"
                value={form.data.facebook || ''}
                onChange={(e) => form.updateField('facebook', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Instagram" helper="Your Instagram profile URL" error={form.errors.instagram}>
              <TextField.Root
                size="3"
                placeholder="https://instagram.com/yourprofile"
                value={form.data.instagram || ''}
                onChange={(e) => form.updateField('instagram', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Twitter / X" helper="Your Twitter/X profile URL" error={form.errors.twitter}>
              <TextField.Root
                size="3"
                placeholder="https://twitter.com/yourprofile"
                value={form.data.twitter || ''}
                onChange={(e) => form.updateField('twitter', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>
          </Box>
        </Card>

        {/* Update Error Display */}
        {(updateError || applyError) && (
          <Card
            mb="4"
            style={{
              padding: '16px',
              background: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              borderRadius: '12px',
            }}
          >
            <Flex align="center" gap="3">
              <WarningTriangle style={{ width: 20, height: 20, color: '#EF4444' }} />
              <Text size="2" style={{ color: '#FCA5A5' }}>
                {updateError?.message || applyError?.message || 'Failed to save your information. Please try again.'}
              </Text>
            </Flex>
          </Card>
        )}

        {/* Navigation Buttons */}
        <Flex justify="between" mt="6">
          <Button
            variant="outline"
            size="3"
            onClick={() => router.push('/welcome')}
            disabled={form.isSubmitting}
            style={{
              borderColor: 'rgba(148, 163, 184, 0.3)',
              color: '#94A3B8',
            }}
          >
            <ArrowLeft style={{ width: 18, height: 18, marginRight: 8 }} />
            Back
          </Button>
          <Button
            size="3"
            onClick={() => form.handleSubmit()}
            disabled={form.isSubmitting}
            style={{
              background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
              cursor: form.isSubmitting ? 'not-allowed' : 'pointer',
              opacity: form.isSubmitting ? 0.7 : 1,
            }}
          >
            {form.isSubmitting ? (
              <>
                <Spinner size="1" />
                <span style={{ marginLeft: 8 }}>Saving...</span>
              </>
            ) : (
              <>
                Continue
                <ArrowRight style={{ width: 18, height: 18, marginLeft: 8 }} />
              </>
            )}
          </Button>
        </Flex>
      </Box>
    </Box>
  );
}
