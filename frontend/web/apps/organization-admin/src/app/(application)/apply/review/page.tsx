'use client';

/**
 * Review Page - Step 2 of KYB Application
 *
 * Final review before submission:
 * - Display all entered information
 * - Terms acceptance
 * - Submit application for review
 *
 * OWASP Compliance:
 * - Uses authenticated GraphQL mutations
 * - No sensitive data stored in client state
 * - Proper loading states to prevent race conditions
 */

import { useState, useCallback, useEffect } from 'react';
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
} from 'iconoir-react';
import { StepIndicator, Step } from '@/components/application/StepIndicator';
import {
  useMyOrganizerProfile,
  useSubmitOrganizerApplication,
  getRouteForStatus,
  canSubmitForReview,
} from '@pml.tickets/shared/api/graphql/organizer';

// =============================================================================
// CONSTANTS
// =============================================================================

const steps: Step[] = [
  { id: 'business-info', title: 'Business Info' },
  { id: 'review', title: 'Review' },
];

const businessTypeLabels: Record<string, string> = {
  SOLE_PROPRIETORSHIP: 'Sole Proprietorship',
  PARTNERSHIP: 'Partnership',
  LIMITED_COMPANY: 'Limited Company',
  NGO: 'Non-Profit / NGO',
  GOVERNMENT: 'Government Entity',
  OTHER: 'Other',
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
  const { profile, hasProfile, status, loading: profileLoading, error: profileError, refetch } = useMyOrganizerProfile();
  const { submitApplication, loading: submitting, error: submitError } = useSubmitOrganizerApplication();

  const [agreedToTerms, setAgreedToTerms] = useState(false);
  const [agreedToPrivacy, setAgreedToPrivacy] = useState(false);

  // Redirect if user doesn't have a profile or can't submit
  useEffect(() => {
    if (!profileLoading && !hasProfile) {
      router.replace('/apply');
      return;
    }

    if (!profileLoading && hasProfile && status && !canSubmitForReview(status)) {
      const route = getRouteForStatus(status, hasProfile);
      router.replace(route);
    }
  }, [profileLoading, hasProfile, status, router]);

  // Validate required fields
  const validation = {
    companyName: !!profile?.companyName?.trim(),
    businessType: !!profile?.businessType,
    registrationNumber: !!profile?.businessRegistrationNumber?.trim(),
    taxId: !!profile?.taxId?.trim(),
    email: !!profile?.businessEmail?.trim(),
    phone: !!profile?.businessPhone?.trim(),
    address: !!profile?.businessAddress?.trim(),
    city: !!profile?.city?.trim(),
    province: !!profile?.province,
  };

  const allFieldsValid = Object.values(validation).every(Boolean);
  const canSubmit = agreedToTerms && agreedToPrivacy && allFieldsValid && !submitting;

  // Handle step navigation
  const handleStepClick = useCallback((step: number) => {
    if (step === 0) {
      router.push('/apply/business-info');
    }
  }, [router]);

  // Handle back
  const handleBack = useCallback(() => {
    router.push('/apply/business-info');
  }, [router]);

  // Handle submit
  const handleSubmit = useCallback(async () => {
    if (!canSubmit) return;

    try {
      const result = await submitApplication();

      if (result) {
        // Redirect to status page
        router.push('/status');
      }
    } catch (error) {
      console.error('Failed to submit application:', error);
    }
  }, [canSubmit, submitApplication, router]);

  // Show loading state
  if (profileLoading) {
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
  if (profileError) {
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
          {profileError.message || 'An error occurred while loading your application.'}
        </Text>
        <Button variant="outline" onClick={() => refetch()}>
          Try Again
        </Button>
      </Box>
    );
  }

  if (!profile) {
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
            <ValidationItem label="Company Name" isValid={validation.companyName} />
            <ValidationItem label="Business Type" isValid={validation.businessType} />
            <ValidationItem label="Registration Number" isValid={validation.registrationNumber} />
            <ValidationItem label="Tax ID (TPIN)" isValid={validation.taxId} />
            <ValidationItem label="Business Email" isValid={validation.email} />
            <ValidationItem label="Phone Number" isValid={validation.phone} />
            <ValidationItem label="Business Address" isValid={validation.address} />
            <ValidationItem label="City" isValid={validation.city} />
            <ValidationItem label="Province" isValid={validation.province} />
          </Box>
        </Card>
      )}

      {/* Business Information Section */}
      <ReviewSection
        title="Business Information"
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
          <ReviewField label="Company Name" value={profile.companyName} />
          <ReviewField
            label="Business Type"
            value={profile.businessType ? businessTypeLabels[profile.businessType] || profile.businessType : undefined}
          />
          <ReviewField label="Registration Number" value={profile.businessRegistrationNumber} />
          <ReviewField label="Tax ID (TPIN)" value={profile.taxId} />
          <ReviewField label="Email" value={profile.businessEmail} />
          <ReviewField label="Phone" value={profile.businessPhone} />
          <ReviewField label="Website" value={profile.website} />
        </Box>

        {/* Address */}
        <Box mt="4" pt="4" style={{ borderTop: '1px solid rgba(148, 163, 184, 0.1)' }}>
          <Text size="2" weight="medium" mb="3" style={{ color: '#CBD5E1', display: 'block' }}>
            Business Address
          </Text>
          <Text size="2" style={{ color: '#F8FAFC', display: 'block', lineHeight: 1.6 }}>
            {profile.businessAddress || '-'}
            <br />
            {profile.city || '-'}, {profile.province ? provinceLabels[profile.province] || profile.province : '-'}
            {profile.postalCode && `, ${profile.postalCode}`}
            <br />
            {profile.country || 'Zambia'}
          </Text>
        </Box>

        {/* Description */}
        {profile.companyDescription && (
          <Box mt="4" pt="4" style={{ borderTop: '1px solid rgba(148, 163, 184, 0.1)' }}>
            <Text size="2" weight="medium" mb="2" style={{ color: '#CBD5E1', display: 'block' }}>
              About Your Organization
            </Text>
            <Text size="2" style={{ color: '#94A3B8' }}>
              {profile.companyDescription}
            </Text>
          </Box>
        )}
      </ReviewSection>

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
          <Flex align="start" gap="3">
            <Checkbox
              checked={agreedToTerms}
              onCheckedChange={(checked) => setAgreedToTerms(checked === true)}
            />
            <Text size="2" style={{ color: '#CBD5E1' }}>
              I agree to the{' '}
              <a href="/terms" style={{ color: '#10B981', textDecoration: 'none' }}>
                Terms of Service
              </a>{' '}
              and confirm that all information provided is accurate and complete.
            </Text>
          </Flex>

          <Flex align="start" gap="3">
            <Checkbox
              checked={agreedToPrivacy}
              onCheckedChange={(checked) => setAgreedToPrivacy(checked === true)}
            />
            <Text size="2" style={{ color: '#CBD5E1' }}>
              I acknowledge and agree to the{' '}
              <a href="/privacy" style={{ color: '#10B981', textDecoration: 'none' }}>
                Privacy Policy
              </a>{' '}
              and consent to the processing of my business data for verification purposes.
            </Text>
          </Flex>
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
              After submission, our team will review your application within 1-2 business days.
              You&apos;ll receive an email notification when your application is approved.
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
          disabled={submitting}
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
          onClick={handleSubmit}
          disabled={!canSubmit}
          style={{
            background: canSubmit
              ? 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)'
              : 'rgba(148, 163, 184, 0.2)',
            cursor: canSubmit ? 'pointer' : 'not-allowed',
            minWidth: '160px',
            opacity: submitting ? 0.7 : 1,
          }}
        >
          {submitting ? (
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
