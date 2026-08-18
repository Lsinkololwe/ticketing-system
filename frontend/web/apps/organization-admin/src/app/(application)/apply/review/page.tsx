'use client';

/**
 * Review Page - Organization Application
 *
 * Compact review before submission. Shows all info in a scannable format.
 * Design: Data-dense layout, clear hierarchy, single CTA focus.
 */

import { useCallback, useEffect, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Button, Card, Checkbox, Separator } from '@radix-ui/themes';
import { Building, Page, Phone, Globe, Link as LinkIcon, SendDiagonal, Shield, WarningTriangle, EditPencil } from 'iconoir-react';
import {
  ReviewSkeleton,
  ORGANIZATION_TYPE_LABELS,
  PROVINCE_LABELS,
} from '@/components/application';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useToast } from '@/components/ui';
import {
  useMyOrganization,
  useSubmitOrganizationForReview,
  useDocumentUpload,
  applicationReviewSchema,
  type ApplicationReviewFormData,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import {
  missingDocuments,
  requiredDocuments,
  type BusinessType,
} from '@/lib/onboarding/documents';
import { ROUTES } from '@/lib/onboarding/state';
import { WizardShell } from '@/components/application/WizardShell';

// =============================================================================
// CONSTANTS
// =============================================================================

const INITIAL_FORM_DATA: ApplicationReviewFormData = {
  agreedToTerms: false,
  agreedToPrivacy: false,
};

// Light, professionally-themed card surface (subtle accent tint + border).
// Same footprint as the business-info cards (Card size="3") — just tinted.
const CARD_SURFACE: React.CSSProperties = {
  backgroundColor: 'var(--accent-a2)',
  borderColor: 'var(--accent-a5)',
};

// Rounded-square teal-tint icon chip used in each section header.
const SECTION_ICON_CHIP: React.CSSProperties = {
  width: 30,
  height: 30,
  borderRadius: 'var(--radius-2)',
  background: 'var(--accent-a3)',
  color: 'var(--accent-11)',
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  flexShrink: 0,
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
    <Box>
      <Flex justify="between" align="center" mb="3">
        <Flex align="center" gap="3">
          <span style={SECTION_ICON_CHIP} aria-hidden="true">
            <Icon width={16} height={16} />
          </span>
          <Text size="3" weight="bold" highContrast>
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
            <EditPencil width={14} height={14} aria-hidden="true" />
            Edit
          </Button>
        )}
      </Flex>
      <Box pl={{ initial: '0', sm: '6' }}>{children}</Box>
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
  const { documents, refresh: refreshDocuments } = useDocumentUpload(organization?.id ?? '');

  // The document set is the one part of the application not held in the
  // organization document, so the review summary has to fetch it separately.
  useEffect(() => {
    if (organization?.id) void refreshDocuments();
  }, [organization?.id, refreshDocuments]);

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

  // Documents. The backend refuses submitForReview with DOCUMENT_REQUIRED when
  // the set for this business type is incomplete — mirroring the check here
  // means the applicant is told before they press the button rather than after.
  const businessType = (organization?.businessType ?? null) as BusinessType | null;
  const requiredDocs = requiredDocuments(businessType);
  const missingDocs = missingDocuments(businessType, documents);
  const documentsComplete = missingDocs.length === 0;

  const isFormSubmitting = isSubmitting || isPending;
  const canSubmit =
    allFieldsValid && documentsComplete && agreedToTerms && agreedToPrivacy && !isFormSubmitting;

  const goToEdit = useCallback(() => router.push(ROUTES.businessInfo), [router]);
  const goToDocuments = useCallback(() => router.push(ROUTES.documents), [router]);

  // Loading state
  if (orgLoading) {
    return <ReviewSkeleton />;
  }

  // Organization not readable yet.
  //
  // Rendered inside the shell rather than instead of it: the server guard only
  // routes here when an application exists, so this is a slow or failed client
  // read, not an absent application. Replacing the whole page drops the step
  // context and reads as "your application is gone" — the exact wrong message
  // for somebody mid-way through one.
  if (!organization) {
    return (
      <WizardShell
        currentStep={2}
        title="Review & submit"
        subtitle="Fetching the details you entered."
        onBack={goToDocuments}
      >
        <Flex justify="center" py="8" aria-live="polite" data-testid="review-loading">
          <Text size="2" color="gray">
            Loading your application…
          </Text>
        </Flex>
      </WizardShell>
    );
  }

  // What is stopping submission, in the applicant's words rather than ours.
  const blockedReason = !documentsComplete
    ? `Still needed: ${missingDocs.map((d) => d.name).join(', ')}`
    : !allFieldsValid
      ? `Incomplete: ${invalidFields.join(', ')}`
      : !agreedToTerms || !agreedToPrivacy
        ? 'Accept the Terms of Service and Privacy Policy to continue'
        : undefined;

  return (
    <Box>
      <WizardShell
        currentStep={2}
        title="Review & submit"
        subtitle="A reviewer typically responds within 48 hours. You can start drafting an event while you wait."
        onBack={goToDocuments}
        onNext={handleSubmit(onSubmit)}
        nextLabel="Submit for review"
        nextDisabled={!canSubmit}
        nextLoading={isFormSubmitting}
        blockedReason={blockedReason}
      >
      <Flex justify="end" mb="2">
        <Button variant="soft" size="2" onClick={goToEdit} data-testid="review-edit">
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
      <Card size="3" variant="surface" mb="4" style={CARD_SURFACE}>
          <Section title="Organization" icon={Building} onEdit={goToEdit}>
            <Field label="Name" value={organization.name} />
            <Field label="Type" value={organization.type ? ORGANIZATION_TYPE_LABELS[organization.type] || organization.type : undefined} />
            <Field label="Tagline" value={organization.tagline} />
            <Box py="2">
              <Text as="label" size="2" color="gray" mb="1" style={{ display: 'block' }}>
                Description
              </Text>
              <Text as="p" size="2" color={organization.description ? undefined : 'gray'} style={{ fontStyle: organization.description ? 'normal' : 'italic' }}>
                {organization.description || '-'}
              </Text>
            </Box>
          </Section>

          <Separator size="4" my="4" />

          <Section title="Contact" icon={Phone} onEdit={goToEdit}>
            <Field label="Email" value={organization.businessEmail} />
            <Field label="Phone" value={organization.businessPhone} />
            <Field label="Website" value={organization.website} />
          </Section>

          <Separator size="4" my="4" />

          <Section title="Location" icon={Globe} onEdit={goToEdit}>
            <Field label="City" value={organization.businessAddress?.city} />
            <Field label="Province" value={organization.businessAddress?.province ? PROVINCE_LABELS[organization.businessAddress.province] || organization.businessAddress.province : undefined} />
            <Field label="Country" value={organization.businessAddress?.country || 'Zambia'} />
          </Section>

          <Separator size="4" my="4" />

          <Section title="Social media" icon={LinkIcon} onEdit={goToEdit}>
            <Field label="Facebook" value={organization.socialLinks?.facebook} />
            <Field label="Instagram" value={organization.socialLinks?.instagram} />
            <Field label="Twitter / X" value={organization.socialLinks?.twitter} />
          </Section>

          <Separator size="4" my="4" />

          {/* Documents. Edit routes to step 2, not step 1 — sending someone
              back to the business-details form to fix a missing upload is the
              kind of misdirection that ends an application. */}
          <Section title="Documents" icon={Page} onEdit={goToDocuments}>
            <Field
              label="Uploaded"
              value={`${requiredDocs.length - missingDocs.length} of ${requiredDocs.length}`}
            />
            {missingDocs.length > 0 && (
              <Box gridColumn="1 / -1" data-testid="review-missing-documents">
                <Text as="p" size="1" style={{ color: 'var(--amber-11)' }}>
                  Still needed: {missingDocs.map((d) => d.name).join(', ')}
                </Text>
              </Box>
            )}
          </Section>
      </Card>

      {/* Terms Card */}
      <Card size="3" variant="surface" mb="4" style={CARD_SURFACE}>
          <Flex align="center" gap="3" mb="3">
            <span style={SECTION_ICON_CHIP} aria-hidden="true">
              <Shield width={16} height={16} />
            </span>
            <Text size="3" weight="bold" highContrast>
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

      </WizardShell>

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
