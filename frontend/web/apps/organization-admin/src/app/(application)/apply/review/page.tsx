'use client';

/**
 * Review Page - Organization Application
 *
 * Compact review before submission. Shows all info in a scannable format.
 * Design: Data-dense layout, clear hierarchy, single CTA focus.
 */

import { useCallback, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Button, Card, Checkbox, Spinner } from '@radix-ui/themes';
import { ArrowLeft, SendDiagonal, Building, Phone, Globe, Link as LinkIcon, Shield, WarningTriangle, EditPencil } from 'iconoir-react';
import {
  StepIndicator,
  ReviewSkeleton,
  APPLICATION_STEPS,
  ORGANIZATION_TYPE_LABELS,
  PROVINCE_LABELS,
} from '@/components/application';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useToast } from '@/components/ui';
import {
  useMyOrganization,
  useSubmitOrganizationForReview,
  applicationReviewSchema,
  type ApplicationReviewFormData,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';

// =============================================================================
// CONSTANTS
// =============================================================================

const INITIAL_FORM_DATA: ApplicationReviewFormData = {
  agreedToTerms: false,
  agreedToPrivacy: false,
};

// =============================================================================
// INLINE COMPONENTS (Compact Design)
// =============================================================================

/** Compact field display: label and value on same line when possible */
function Field({ label, value }: { label: string; value?: string | null }) {
  const hasValue = value && value.trim();
  return (
    <Flex gap="2" py="1" className="field-row">
      <Text size="2" color="gray" style={{ minWidth: 100, flexShrink: 0 }}>
        {label}
      </Text>
      <Text size="2" color={hasValue ? undefined : 'gray'} style={{ fontStyle: hasValue ? 'normal' : 'italic' }}>
        {hasValue ? value : '-'}
      </Text>
    </Flex>
  );
}

/** Compact section with inline edit button */
function Section({
  title,
  icon: Icon,
  children,
  onEdit
}: {
  title: string;
  icon: React.ElementType;
  children: React.ReactNode;
  onEdit?: () => void;
}) {
  return (
    <Box mb="4">
      <Flex justify="between" align="center" mb="2">
        <Flex align="center" gap="2">
          <Icon width={16} height={16} className="icon-accent" aria-hidden="true" />
          <Text size="2" weight="medium" highContrast>
            {title}
          </Text>
        </Flex>
        {onEdit && (
          <Button
            variant="ghost"
            size="1"
            color="teal"
            onClick={onEdit}
            aria-label={`Edit ${title}`}
          >
            <EditPencil width={12} height={12} aria-hidden="true" />
          </Button>
        )}
      </Flex>
      <Box pl="5">{children}</Box>
    </Box>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function ReviewPage() {
  const router = useRouter();
  const { toast } = useToast();
  const [isPending, startTransition] = useTransition();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { organization, loading: orgLoading } = useMyOrganization({ fetchPolicy: 'cache-first' });
  const { submit } = useSubmitOrganizationForReview();

  const { watch, setValue, handleSubmit, formState: { errors, isSubmitting } } = useForm<ApplicationReviewFormData>({
    resolver: zodResolver(applicationReviewSchema),
    defaultValues: INITIAL_FORM_DATA,
  });

  // Watch form values
  const agreedToTerms = watch('agreedToTerms');
  const agreedToPrivacy = watch('agreedToPrivacy');

  // Form submission handler
  const onSubmit = useCallback(async () => {
    if (!organization) return;
    setSubmitError(null);

    try {
      const result = await submit(organization.id);
      if (result) {
        toast.success('Application submitted', 'Your application is now under review. We\'ll notify you of the outcome.');
        startTransition(() => router.push('/apply/status'));
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Submission failed. Please try again.';
      setSubmitError(errorMessage);
      toast.error('Submission failed', errorMessage);
    }
  }, [organization, submit, toast, router]);

  // Validation
  const validation = {
    name: !!organization?.name?.trim(),
    businessEmail: !!organization?.businessEmail?.trim(),
    businessPhone: !!organization?.businessPhone?.trim(),
    city: !!organization?.businessAddress?.city?.trim(),
    province: !!organization?.businessAddress?.province,
  };
  const invalidFields = Object.entries(validation).filter(([, v]) => !v).map(([k]) => k);
  const allFieldsValid = invalidFields.length === 0;

  const isFormSubmitting = isSubmitting || isPending;
  const canSubmit = allFieldsValid && agreedToTerms && agreedToPrivacy && !isFormSubmitting;

  const goToEdit = useCallback(() => router.push('/apply/business-info'), [router]);
  const handleStepClick = useCallback((step: number) => {
    if (step === 0) goToEdit();
  }, [goToEdit]);

  // Loading state
  if (orgLoading) {
    return <ReviewSkeleton />;
  }

  // No organization
  if (!organization) {
    return (
      <Flex justify="center" py="9">
        <Text size="2" color="gray">
          No application found. Redirecting...
        </Text>
      </Flex>
    );
  }

  return (
    <Box>
      <StepIndicator steps={APPLICATION_STEPS} currentStep={1} onStepClick={handleStepClick} />

      {/* Header */}
      <Flex justify="between" align="start" mb="5">
        <Box>
          <Heading size="5" mb="1" highContrast>
            Review Application
          </Heading>
          <Text size="2" color="gray">
            Verify your information before submitting.
          </Text>
        </Box>
        <Button variant="soft" size="2" onClick={goToEdit}>
          <EditPencil width={14} height={14} aria-hidden="true" />
          Edit
        </Button>
      </Flex>

      {/* Validation Warning */}
      {!allFieldsValid && (
        <Card role="alert" aria-live="polite" mb="4" className="warning-card" variant="surface">
          <Flex align="center" gap="2" p="3">
            <WarningTriangle width={16} height={16} aria-hidden="true" style={{ flexShrink: 0, color: 'var(--amber-9)' }} />
            <Text size="2" color="amber">
              Missing: {invalidFields.map(f => f === 'businessEmail' ? 'Email' : f === 'businessPhone' ? 'Phone' : f.charAt(0).toUpperCase() + f.slice(1)).join(', ')}
            </Text>
          </Flex>
        </Card>
      )}

      {/* Review Card */}
      <Card variant="surface" mb="4">
        <Box p="5">
          <Section title="Organization" icon={Building} onEdit={goToEdit}>
            <Field label="Name" value={organization.name} />
            <Field label="Type" value={organization.type ? ORGANIZATION_TYPE_LABELS[organization.type] || organization.type : undefined} />
            <Field label="Tagline" value={organization.tagline} />
            {organization.description && (
              <Box py="2">
                <Text as="label" size="1" color="gray" mb="1" style={{ display: 'block' }}>
                  Description
                </Text>
                <Text as="p" size="2" color="gray">
                  {organization.description}
                </Text>
              </Box>
            )}
          </Section>

          <Section title="Contact" icon={Phone} onEdit={goToEdit}>
            <Field label="Email" value={organization.businessEmail} />
            <Field label="Phone" value={organization.businessPhone} />
            <Field label="Website" value={organization.website} />
          </Section>

          <Section title="Location" icon={Globe} onEdit={goToEdit}>
            <Field label="City" value={organization.businessAddress?.city} />
            <Field label="Province" value={organization.businessAddress?.province ? PROVINCE_LABELS[organization.businessAddress.province] || organization.businessAddress.province : undefined} />
            <Field label="Country" value={organization.businessAddress?.country || 'Zambia'} />
          </Section>

          {(organization.socialLinks?.facebook || organization.socialLinks?.instagram || organization.socialLinks?.twitter) && (
            <Section title="Social" icon={LinkIcon} onEdit={goToEdit}>
              {organization.socialLinks?.facebook && <Field label="Facebook" value={organization.socialLinks.facebook} />}
              {organization.socialLinks?.instagram && <Field label="Instagram" value={organization.socialLinks.instagram} />}
              {organization.socialLinks?.twitter && <Field label="Twitter" value={organization.socialLinks.twitter} />}
            </Section>
          )}
        </Box>
      </Card>

      {/* Terms Card */}
      <Card variant="surface" mb="4">
        <Box p="5">
          <Flex align="center" gap="2" mb="3">
            <Shield width={16} height={16} className="icon-accent" aria-hidden="true" />
            <Text size="2" weight="medium" highContrast>
              Agreements
            </Text>
          </Flex>

          <Flex direction="column" gap="3">
            <Text as="label" size="2" color="gray">
              <Flex align="start" gap="3" style={{ cursor: 'pointer' }}>
                <Checkbox
                  checked={agreedToTerms}
                  onCheckedChange={(checked) => setValue('agreedToTerms', checked === true)}
                  mt="1"
                />
                <span>
                  I agree to the{' '}
                  <Text color="teal" style={{ textDecoration: 'underline' }} asChild>
                    <a href="/terms">Terms of Service</a>
                  </Text>
                  {' '}and confirm all information is accurate.
                </span>
              </Flex>
            </Text>
            {errors.agreedToTerms && (
              <Text size="1" color="red" ml="6">
                {errors.agreedToTerms.message}
              </Text>
            )}

            <Text as="label" size="2" color="gray">
              <Flex align="start" gap="3" style={{ cursor: 'pointer' }}>
                <Checkbox
                  checked={agreedToPrivacy}
                  onCheckedChange={(checked) => setValue('agreedToPrivacy', checked === true)}
                  mt="1"
                />
                <span>
                  I acknowledge the{' '}
                  <Text color="teal" style={{ textDecoration: 'underline' }} asChild>
                    <a href="/privacy">Privacy Policy</a>
                  </Text>
                  {' '}and consent to data processing.
                </span>
              </Flex>
            </Text>
            {errors.agreedToPrivacy && (
              <Text size="1" color="red" ml="6">
                {errors.agreedToPrivacy.message}
              </Text>
            )}
          </Flex>
        </Box>
      </Card>

      {/* Info Banner */}
      <Card variant="surface" mb="4" className="info-card">
        <Flex align="center" gap="3" p="3">
          <SendDiagonal width={18} height={18} aria-hidden="true" style={{ flexShrink: 0, color: 'var(--blue-9)' }} />
          <Box>
            <Text size="2" weight="medium" highContrast style={{ display: 'block' }}>
              What happens next?
            </Text>
            <Text size="1" color="gray">
              Review takes 2-3 business days. You can create draft events while waiting.
            </Text>
          </Box>
        </Flex>
      </Card>

      {/* Error */}
      {submitError && (
        <Card role="alert" aria-live="assertive" mb="4" variant="surface" className="error-card">
          <Flex align="center" gap="2" p="3">
            <WarningTriangle width={16} height={16} aria-hidden="true" style={{ color: 'var(--red-9)' }} />
            <Text size="2" color="red">{submitError}</Text>
          </Flex>
        </Card>
      )}

      {/* Actions */}
      <Flex justify="between" gap="3">
        <Button variant="soft" size="3" onClick={goToEdit} disabled={isFormSubmitting}>
          <ArrowLeft width={16} height={16} aria-hidden="true" />
          Back
        </Button>
        <Button
          size="3"
          color="teal"
          onClick={handleSubmit(onSubmit)}
          disabled={!canSubmit}
        >
          {isFormSubmitting ? (
            <>
              <Spinner size="1" />
              Submitting...
            </>
          ) : (
            <>
              Submit
              <SendDiagonal width={16} height={16} aria-hidden="true" />
            </>
          )}
        </Button>
      </Flex>

      {/* Styles for field rows */}
      <style jsx global>{`
        .field-row {
          border-bottom: 1px solid var(--gray-a5);
        }
        .field-row:last-child {
          border-bottom: none;
        }
      `}</style>
    </Box>
  );
}
