'use client';

/**
 * Review Page - Organization Application
 *
 * Final review before submission:
 * - Display all entered organization information
 * - Terms acceptance
 * - Submit application for review
 *
 * MIGRATION NOTE: This page now uses the Organization model and GraphQL operations.
 */

import { useCallback, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Button, Card, Checkbox, Spinner } from '@radix-ui/themes';
import {
  Building,
  Check,
  ArrowRight,
  ArrowLeft,
  Edit,
  SendDiagonal,
  Shield,
  WarningTriangle,
  Phone,
  Globe,
  Link as LinkIcon,
} from 'iconoir-react';
import { StepIndicator, Step } from '@/components/application/StepIndicator';
import {
  useMyOrganization,
  useSubmitOrganizationForReview,
  getRouteForStatus,
  canSubmitForReview,
  applicationReviewSchema,
  type ApplicationReviewFormData as ApplicationReviewSchema,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import { useZodForm } from '@pml.tickets/shared';

// =============================================================================
// CONSTANTS
// =============================================================================

const steps: Step[] = [
  { id: 'business-info', title: 'Organization Info' },
  { id: 'review', title: 'Review & Submit' },
];

const organizationTypeLabels: Record<string, string> = {
  INDIVIDUAL: 'Individual / Personal',
  BUSINESS: 'Business / Company',
};

const provinceLabels: Record<string, string> = {
  CENTRAL: 'Central Province',
  COPPERBELT: 'Copperbelt Province',
  EASTERN: 'Eastern Province',
  LUAPULA: 'Luapula Province',
  LUSAKA: 'Lusaka Province',
  MUCHINGA: 'Muchinga Province',
  NORTHERN: 'Northern Province',
  NORTH_WESTERN: 'North-Western Province',
  SOUTHERN: 'Southern Province',
  WESTERN: 'Western Province',
};

// =============================================================================
// REVIEW SECTION COMPONENT
// =============================================================================

interface ReviewSectionProps {
  title: string;
  icon: React.ReactNode;
  editLink: string;
  children: React.ReactNode;
}

function ReviewSection({ title, icon, editLink, children }: ReviewSectionProps) {
  const router = useRouter();

  return (
    <Card
      mb="4"
      style={{
        padding: '24px',
        background: 'rgba(30, 41, 59, 0.5)',
        border: '1px solid rgba(148, 163, 184, 0.1)',
        borderRadius: '12px',
      }}
    >
      <Flex justify="between" align="center" mb="4">
        <Flex align="center" gap="2">
          {icon}
          <Text size="3" weight="medium" style={{ color: '#F8FAFC' }}>
            {title}
          </Text>
        </Flex>
        <Button
          variant="ghost"
          size="1"
          onClick={() => router.push(editLink)}
          style={{ color: '#10B981' }}
        >
          <Edit style={{ width: 14, height: 14, marginRight: 4 }} />
          Edit
        </Button>
      </Flex>
      {children}
    </Card>
  );
}

// =============================================================================
// REVIEW FIELD COMPONENT
// =============================================================================

interface ReviewFieldProps {
  label: string;
  value: string | undefined | null;
}

function ReviewField({ label, value }: ReviewFieldProps) {
  return (
    <Box mb="3">
      <Text size="1" style={{ color: '#94A3B8', display: 'block', marginBottom: '4px' }}>
        {label}
      </Text>
      <Text size="2" style={{ color: '#F8FAFC' }}>
        {value || '-'}
      </Text>
    </Box>
  );
}

// =============================================================================
// VALIDATION CHECK COMPONENT
// =============================================================================

interface ValidationItemProps {
  label: string;
  isValid: boolean;
}

function ValidationItem({ label, isValid }: ValidationItemProps) {
  return (
    <Flex align="center" gap="2" py="1">
      {isValid ? (
        <Check style={{ width: 16, height: 16, color: '#10B981' }} />
      ) : (
        <WarningTriangle style={{ width: 16, height: 16, color: '#F59E0B' }} />
      )}
      <Text size="2" style={{ color: isValid ? '#94A3B8' : '#F59E0B' }}>
        {label}
      </Text>
    </Flex>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function ReviewPage() {
  const router = useRouter();
  const {
    organization,
    hasOrganization,
    status,
    loading: organizationLoading,
    error: organizationError,
    refetch,
  } = useMyOrganization();
  const { submit, error: submitError } = useSubmitOrganizationForReview();

  // Initial form data for terms agreement
  const initialFormData: ApplicationReviewSchema = {
    agreedToTerms: false,
    agreedToPrivacy: false,
  };

  // Zod-based form management
  const form = useZodForm(
    applicationReviewSchema,
    initialFormData,
    async (validatedData) => {
      // This handler receives validated data with correct types
      if (!organization) return;

      try {
        const result = await submit(organization.id);
        if (result) {
          router.push('/apply/status');
        }
      } catch (error) {
        console.error('Failed to submit application:', error);
      }
    }
  );

  // Redirect if user doesn't have an organization or can't submit
  useEffect(() => {
    if (!organizationLoading && !hasOrganization) {
      router.replace('/welcome');
      return;
    }

    if (!organizationLoading && hasOrganization && status && !canSubmitForReview(status)) {
      const route = getRouteForStatus(status, organization?.id);
      router.replace(route);
    }
  }, [organizationLoading, hasOrganization, status, router]);

  // Validate required organization fields
  const validation = {
    name: !!organization?.name?.trim(),
    businessEmail: !!organization?.businessEmail?.trim(),
    businessPhone: !!organization?.businessPhone?.trim(),
    city: !!organization?.businessAddress?.city?.trim(),
    province: !!organization?.businessAddress?.province,
  };

  const allFieldsValid = Object.values(validation).every(Boolean);

  // Can submit if all organization fields are valid and form is valid (terms agreed)
  const canSubmit = allFieldsValid && form.data.agreedToTerms && form.data.agreedToPrivacy && !form.isSubmitting;

  // Handle step navigation
  const handleStepClick = useCallback(
    (step: number) => {
      if (step === 0) {
        router.push('/apply/business-info');
      }
    },
    [router]
  );

  // Handle back
  const handleBack = useCallback(() => {
    router.push('/apply/business-info');
  }, [router]);

  // Show loading state
  if (organizationLoading) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spinner size="3" />
        <Text size="2" style={{ color: '#94A3B8', display: 'block', marginTop: 16 }}>
          Loading your application...
        </Text>
      </Box>
    );
  }

  // Show error state
  if (organizationError) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Box
          style={{
            width: 64,
            height: 64,
            borderRadius: '16px',
            background: 'rgba(239, 68, 68, 0.1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px',
          }}
        >
          <WarningTriangle style={{ width: 32, height: 32, color: '#EF4444' }} />
        </Box>
        <Text size="3" weight="medium" style={{ color: '#F8FAFC', display: 'block', marginBottom: 8 }}>
          Failed to load application
        </Text>
        <Text size="2" style={{ color: '#94A3B8', display: 'block', marginBottom: 16 }}>
          {organizationError.message || 'An error occurred while loading your application.'}
        </Text>
        <Button variant="outline" onClick={() => refetch()}>
          Try Again
        </Button>
      </Box>
    );
  }

  if (!organization) {
    return null;
  }

  return (
    <Box>
      {/* Step Indicator */}
      <StepIndicator steps={steps} currentStep={1} onStepClick={handleStepClick} />

      {/* Page Header */}
      <Box mb="6">
        <Heading size="5" mb="2" style={{ color: '#F8FAFC' }}>
          Review Your Application
        </Heading>
        <Text size="2" style={{ color: '#94A3B8' }}>
          Please review all the information below before submitting your application.
        </Text>
      </Box>

      {/* Validation Summary */}
      {!allFieldsValid && (
        <Card
          mb="4"
          style={{
            padding: '20px',
            background: 'rgba(245, 158, 11, 0.1)',
            border: '1px solid rgba(245, 158, 11, 0.3)',
            borderRadius: '12px',
          }}
        >
          <Flex align="center" gap="3" mb="3">
            <WarningTriangle style={{ width: 20, height: 20, color: '#F59E0B' }} />
            <Text size="2" weight="medium" style={{ color: '#FCD34D' }}>
              Please complete all required fields
            </Text>
          </Flex>
          <Box pl="7">
            <ValidationItem label="Organization Name" isValid={validation.name} />
            <ValidationItem label="Business Email" isValid={validation.businessEmail} />
            <ValidationItem label="Phone Number" isValid={validation.businessPhone} />
            <ValidationItem label="City" isValid={validation.city} />
            <ValidationItem label="Province" isValid={validation.province} />
          </Box>
        </Card>
      )}

      {/* Basic Information Section */}
      <ReviewSection
        title="Basic Information"
        icon={<Building style={{ width: 20, height: 20, color: '#10B981' }} />}
        editLink="/apply/business-info"
      >
        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '16px',
          }}
        >
          <ReviewField label="Organization Name" value={organization.name} />
          <ReviewField
            label="Organization Type"
            value={organization.type ? organizationTypeLabels[organization.type] || organization.type : undefined}
          />
          <ReviewField label="Tagline" value={organization.tagline} />
        </Box>

        {/* Description */}
        {organization.description && (
          <Box mt="4" pt="4" style={{ borderTop: '1px solid rgba(148, 163, 184, 0.1)' }}>
            <Text size="2" weight="medium" mb="2" style={{ color: '#CBD5E1', display: 'block' }}>
              About Your Organization
            </Text>
            <Text size="2" style={{ color: '#94A3B8', lineHeight: 1.6 }}>
              {organization.description}
            </Text>
          </Box>
        )}
      </ReviewSection>

      {/* Contact Information Section */}
      <ReviewSection
        title="Contact Information"
        icon={<Phone style={{ width: 20, height: 20, color: '#10B981' }} />}
        editLink="/apply/business-info"
      >
        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '16px',
          }}
        >
          <ReviewField label="Email" value={organization.businessEmail} />
          <ReviewField label="Phone" value={organization.businessPhone} />
          <ReviewField label="Website" value={organization.website} />
        </Box>
      </ReviewSection>

      {/* Location Section */}
      <ReviewSection
        title="Location"
        icon={<Globe style={{ width: 20, height: 20, color: '#10B981' }} />}
        editLink="/apply/business-info"
      >
        <Text size="2" style={{ color: '#F8FAFC', lineHeight: 1.6 }}>
          {organization.businessAddress?.city || '-'}, {organization.businessAddress?.province ? provinceLabels[organization.businessAddress.province] || organization.businessAddress.province : '-'}
          <br />
          {organization.businessAddress?.country || 'Zambia'}
        </Text>
      </ReviewSection>

      {/* Social Links Section */}
      {(organization.socialLinks?.facebook || organization.socialLinks?.instagram || organization.socialLinks?.twitter) && (
        <ReviewSection
          title="Social Media"
          icon={<LinkIcon style={{ width: 20, height: 20, color: '#10B981' }} />}
          editLink="/apply/business-info"
        >
          <Box
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: '16px',
            }}
          >
            {organization.socialLinks?.facebook && (
              <ReviewField label="Facebook" value={organization.socialLinks.facebook} />
            )}
            {organization.socialLinks?.instagram && (
              <ReviewField label="Instagram" value={organization.socialLinks.instagram} />
            )}
            {organization.socialLinks?.twitter && (
              <ReviewField label="Twitter / X" value={organization.socialLinks.twitter} />
            )}
          </Box>
        </ReviewSection>
      )}

      {/* Terms and Conditions */}
      <Card
        mb="6"
        style={{
          padding: '24px',
          background: 'rgba(30, 41, 59, 0.5)',
          border: '1px solid rgba(148, 163, 184, 0.1)',
          borderRadius: '12px',
        }}
      >
        <Flex align="center" gap="2" mb="4">
          <Shield style={{ width: 20, height: 20, color: '#10B981' }} />
          <Text size="3" weight="medium" style={{ color: '#F8FAFC' }}>
            Terms and Conditions
          </Text>
        </Flex>

        <Flex direction="column" gap="4">
          <Box>
            <Flex align="start" gap="3">
              <Checkbox
                checked={form.data.agreedToTerms}
                onCheckedChange={(checked) => form.updateField('agreedToTerms', checked === true)}
              />
              <Text size="2" style={{ color: '#CBD5E1' }}>
                I agree to the{' '}
                <a href="/terms" style={{ color: '#10B981', textDecoration: 'none' }}>
                  Terms of Service
                </a>{' '}
                and confirm that all information provided is accurate and complete.
              </Text>
            </Flex>
            {form.errors.agreedToTerms && (
              <Text size="1" style={{ color: '#F87171', display: 'block', marginTop: '4px', marginLeft: '28px' }}>
                {form.errors.agreedToTerms}
              </Text>
            )}
          </Box>

          <Box>
            <Flex align="start" gap="3">
              <Checkbox
                checked={form.data.agreedToPrivacy}
                onCheckedChange={(checked) => form.updateField('agreedToPrivacy', checked === true)}
              />
              <Text size="2" style={{ color: '#CBD5E1' }}>
                I acknowledge and agree to the{' '}
                <a href="/privacy" style={{ color: '#10B981', textDecoration: 'none' }}>
                  Privacy Policy
                </a>{' '}
                and consent to the processing of my data for verification purposes.
              </Text>
            </Flex>
            {form.errors.agreedToPrivacy && (
              <Text size="1" style={{ color: '#F87171', display: 'block', marginTop: '4px', marginLeft: '28px' }}>
                {form.errors.agreedToPrivacy}
              </Text>
            )}
          </Box>
        </Flex>
      </Card>

      {/* Submit Info */}
      <Card
        mb="6"
        style={{
          padding: '20px',
          background: 'rgba(59, 130, 246, 0.1)',
          border: '1px solid rgba(59, 130, 246, 0.2)',
          borderRadius: '12px',
        }}
      >
        <Flex align="center" gap="3">
          <Box
            style={{
              width: 40,
              height: 40,
              borderRadius: '8px',
              background: 'rgba(59, 130, 246, 0.2)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <SendDiagonal style={{ width: 20, height: 20, color: '#60A5FA' }} />
          </Box>
          <Box>
            <Text size="2" weight="medium" style={{ color: '#F8FAFC', display: 'block' }}>
              What happens next?
            </Text>
            <Text size="1" style={{ color: '#94A3B8' }}>
              After submission, our team will review your application within 2-3 business days.
              You&apos;ll receive an email notification when your application is approved.
              You can create draft events while waiting for approval.
            </Text>
          </Box>
        </Flex>
      </Card>

      {/* Submit Error */}
      {submitError && (
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
              {submitError.message || 'Failed to submit your application. Please try again.'}
            </Text>
          </Flex>
        </Card>
      )}

      {/* Navigation Buttons */}
      <Flex justify="between">
        <Button
          variant="outline"
          size="3"
          onClick={handleBack}
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
          disabled={!canSubmit}
          style={{
            background: canSubmit
              ? 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)'
              : 'rgba(148, 163, 184, 0.2)',
            cursor: canSubmit ? 'pointer' : 'not-allowed',
            minWidth: '160px',
            opacity: form.isSubmitting ? 0.7 : 1,
          }}
        >
          {form.isSubmitting ? (
            <>
              <Spinner size="1" />
              <span style={{ marginLeft: 8 }}>Submitting...</span>
            </>
          ) : (
            <>
              Submit Application
              <ArrowRight style={{ width: 18, height: 18, marginLeft: 8 }} />
            </>
          )}
        </Button>
      </Flex>
    </Box>
  );
}
