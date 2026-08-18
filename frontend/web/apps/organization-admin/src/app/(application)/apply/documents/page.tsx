'use client';

/**
 * Application Step 2 — Verification documents
 *
 * The step that did not exist. The wizard previously ran business-info → review
 * → submit, so an applicant reached "Submit for review" having uploaded nothing,
 * and the backend's document requirements (spec ET-ORG-001 R3) had no surface
 * through which they could ever be satisfied.
 *
 * ## What is asked for, and why it varies
 *
 * The required set is a function of the business type chosen in step 1, and
 * nothing else. A sole proprietor is never shown a certificate-of-incorporation
 * slot, because asking for a document somebody cannot possibly have is how an
 * application gets abandoned rather than corrected.
 *
 * ## Upload path
 *
 * Bytes go browser → storage directly via a presigned PUT; only the metadata
 * comes back through our API (spec R4, D-11). Nothing passes through a GraphQL
 * resolver. For a photographed business licence over a Zambian mobile
 * connection, the progress bar this buys is the difference between waiting and
 * giving up.
 *
 * @see Org Admin - Onboarding Wizard.dc.html — step 2
 * @see lib/onboarding/documents.ts — the required-set table
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Button,
  Card,
  Flex,
  Heading,
  Progress,
  Text,
} from '@radix-ui/themes';
import {
  ArrowLeft,
  Bank,
  Check,
  Home,
  SecurityPass,
  Page,
  PageEdit,
  PageStar,
  Upload,
  WarningTriangle,
} from 'iconoir-react';
import {
  useMyOrganization,
  useDocumentUpload,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import {
  ACCEPT_ATTRIBUTE,
  requiredDocuments,
  businessTypeSpec,
  validateFile,
  type BusinessType,
  type DocumentSpec,
} from '@/lib/onboarding/documents';
import { ROUTES } from '@/lib/onboarding/state';
import { WizardShell } from '@/components/application/WizardShell';
import { useToast } from '@/components/ui/useToast';

// =============================================================================
// ICONS
// =============================================================================

const DOC_ICONS = {
  'id-card': SecurityPass,
  page: Page,
  'page-star': PageStar,
  'page-edit': PageEdit,
  home: Home,
  bank: Bank,
} as const;

function DocumentIcon({ name }: { name: string }) {
  const Icon = DOC_ICONS[name as keyof typeof DOC_ICONS] ?? Page;
  return <Icon width={17} height={17} strokeWidth={1.5} />;
}

// =============================================================================
// TYPES
// =============================================================================

/** Per-slot UI state. `status` mirrors the server's DocumentStatus. */
interface SlotState {
  status: 'EMPTY' | 'UPLOADING' | 'PENDING' | 'APPROVED' | 'REJECTED';
  fileName?: string;
  rejectionReason?: string;
  progress?: number;
  error?: string;
}

// =============================================================================
// PAGE
// =============================================================================

export default function DocumentsStepPage() {
  const router = useRouter();
  const { toast } = useToast();

  const { organization, loading: orgLoading } = useMyOrganization({
    fetchPolicy: 'network-only',
  });

  const organizationId = organization?.id ?? '';
  const businessType = (organization?.businessType ?? null) as BusinessType | null;

  const { upload, documents, refresh, isLoading } = useDocumentUpload(organizationId);

  const required = useMemo(() => requiredDocuments(businessType), [businessType]);
  const typeSpec = businessTypeSpec(businessType);

  const [slots, setSlots] = useState<Record<string, SlotState>>({});
  const inputRefs = useRef<Record<string, HTMLInputElement | null>>({});

  // Pull the server's view of what has already been uploaded, so returning to
  // this step after a reload (or after "changes requested") shows real state
  // rather than an empty set of slots.
  useEffect(() => {
    if (!organizationId) return;
    void refresh();
  }, [organizationId, refresh]);

  useEffect(() => {
    if (!documents.length) return;
    setSlots((prev) => {
      const next = { ...prev };
      for (const doc of documents) {
        // Never clobber an upload that is mid-flight with stale server state.
        if (next[doc.documentType]?.status === 'UPLOADING') continue;
        next[doc.documentType] = {
          status: doc.status,
          fileName: doc.fileName,
          rejectionReason: doc.rejectionReason,
        };
      }
      return next;
    });
  }, [documents]);

  const setSlot = useCallback((type: string, patch: Partial<SlotState>) => {
    setSlots((prev) => ({
      ...prev,
      [type]: { ...(prev[type] ?? { status: 'EMPTY' }), ...patch },
    }));
  }, []);

  const handleFile = useCallback(
    async (doc: DocumentSpec, file: File) => {
      const invalid = validateFile(file);
      if (invalid) {
        // Client-side pre-flight only; the server enforces the same rules.
        setSlot(doc.type, { status: 'EMPTY', error: invalid });
        return;
      }

      setSlot(doc.type, {
        status: 'UPLOADING',
        fileName: file.name,
        progress: 0,
        error: undefined,
      });

      try {
        await upload(doc.type, file);
        // Re-uploading a rejected document supersedes it and returns it to
        // PENDING (spec R4).
        setSlot(doc.type, {
          status: 'PENDING',
          fileName: file.name,
          progress: 100,
          rejectionReason: undefined,
          error: undefined,
        });
        toast.success('Uploaded', `${doc.name} received.`);
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Upload failed. Try again.';
        setSlot(doc.type, { status: 'EMPTY', progress: undefined, error: message });
        toast.error('Upload failed', message);
      }
    },
    [upload, setSlot, toast]
  );

  const satisfied = useCallback(
    (type: string) => {
      const s = slots[type]?.status;
      // A REJECTED document does not satisfy its requirement (spec R3).
      return s === 'PENDING' || s === 'APPROVED';
    },
    [slots]
  );

  const outstanding = required.filter((d) => !satisfied(d.type));
  const allSupplied = required.length > 0 && outstanding.length === 0;

  const goBack = useCallback(() => router.push(ROUTES.businessInfo), [router]);
  const goNext = useCallback(() => {
    if (allSupplied) router.push(ROUTES.review);
  }, [allSupplied, router]);

  // ---------------------------------------------------------------------------
  // Business type missing — step 1 was skipped or never saved.
  // ---------------------------------------------------------------------------
  if (!orgLoading && !businessType) {
    return (
      <Box data-testid="documents-step">
        <WizardShell
          currentStep={1}
          title="We need your business type first"
          subtitle="Which documents we ask for depends on the kind of business you run."
          onBack={goBack}
        >
        <Card size="3" data-testid="business-type-missing">
          <Flex direction="column" align="center" gap="3" p="4">
            <Box aria-hidden="true" style={{ color: 'var(--amber-11)' }}>
              <WarningTriangle width={24} height={24} />
            </Box>
            <Heading as="h1" size="4" align="center">
              We need your business type first
            </Heading>
            <Text as="p" size="2" color="gray" align="center">
              Which documents we ask for depends on the kind of business you run.
              Choose it on the previous step and we&apos;ll show you exactly what
              to upload.
            </Text>
            <Button
              size="3"
              variant="solid"
              color="teal"
              onClick={goBack}
              data-testid="back-to-business-info"
            >
              <ArrowLeft width={16} height={16} aria-hidden="true" />
              Back to business details
            </Button>
          </Flex>
        </Card>
        </WizardShell>
      </Box>
    );
  }

  return (
    <Box data-testid="documents-step">
      <WizardShell
        currentStep={1}
        title="Upload your documents"
        subtitle={`Required for a ${typeSpec?.label ?? 'business'}. Files upload directly and securely — nothing is sent through our servers.`}
        onBack={goBack}
        onNext={goNext}
        nextDisabled={!allSupplied || isLoading}
        blockedReason={
          outstanding.length > 0
            ? `Still needed: ${outstanding.map((d) => d.name).join(', ')}`
            : undefined
        }
      >

      {/* Progress is announced, not just drawn, so it is usable without sight. */}
      <Box aria-live="polite" role="status" mb="3">
        <Text size="1" color="gray" data-testid="documents-progress-text">
          {required.length - outstanding.length} of {required.length} uploaded
        </Text>
      </Box>

      <Flex direction="column" gap="3">
        {required.map((doc) => {
          const slot = slots[doc.type] ?? { status: 'EMPTY' as const };
          const done = satisfied(doc.type);
          const uploading = slot.status === 'UPLOADING';
          const rejected = slot.status === 'REJECTED';

          return (
            <Card key={doc.type} size="2" data-testid={`document-slot-${doc.type}`}>
              <Flex align="center" gap="4">
                <Flex
                  align="center"
                  justify="center"
                  flexShrink="0"
                  width="38px"
                  height="38px"
                  aria-hidden="true"
                  style={{
                    borderRadius: 'var(--radius-3)',
                    background: done ? 'var(--jade-a3)' : rejected ? 'var(--red-a3)' : 'var(--gray-a3)',
                    color: done ? 'var(--jade-11)' : rejected ? 'var(--red-11)' : 'var(--gray-11)',
                  }}
                >
                  <DocumentIcon name={doc.icon} />
                </Flex>

                <Box flexGrow="1" minWidth="0">
                  <Text as="p" size="2" weight="medium" highContrast>
                    {doc.name}
                  </Text>
                  <Text as="p" size="1" color="gray">
                    {slot.fileName && !slot.error ? slot.fileName : doc.hint}
                  </Text>

                  {uploading && typeof slot.progress === 'number' && (
                    <Box mt="2">
                      <Progress value={slot.progress} size="1" color="teal" />
                    </Box>
                  )}

                  {rejected && slot.rejectionReason && (
                    <Text as="p" size="1" mt="1" style={{ color: 'var(--red-11)' }}>
                      Rejected: {slot.rejectionReason}
                    </Text>
                  )}

                  {slot.error && (
                    <Text as="p" size="1" role="alert" mt="1" style={{ color: 'var(--red-11)' }}>
                      {slot.error}
                    </Text>
                  )}
                </Box>

                {/* Visually-hidden native input keeps the OS file picker and its
                    keyboard/AT behaviour; the Button is only its trigger. */}
                <input
                  ref={(el) => {
                    inputRefs.current[doc.type] = el;
                  }}
                  type="file"
                  accept={ACCEPT_ATTRIBUTE}
                  hidden
                  aria-hidden="true"
                  tabIndex={-1}
                  data-testid={`document-input-${doc.type}`}
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    // Reset so re-selecting the same file fires change again.
                    e.target.value = '';
                    if (file) void handleFile(doc, file);
                  }}
                />

                <Button
                  size="2"
                  variant={done ? 'soft' : 'solid'}
                  color={done ? 'green' : 'teal'}
                  disabled={uploading}
                  loading={uploading}
                  onClick={() => inputRefs.current[doc.type]?.click()}
                  aria-label={
                    done ? `Replace ${doc.name}` : `Upload ${doc.name}`
                  }
                  data-testid={`document-upload-${doc.type}`}
                >
                  {done ? (
                    <>
                      <Check width={14} height={14} aria-hidden="true" />
                      Uploaded
                    </>
                  ) : rejected ? (
                    <>
                      <Upload width={14} height={14} aria-hidden="true" />
                      Re-upload
                    </>
                  ) : (
                    <>
                      <Upload width={14} height={14} aria-hidden="true" />
                      Upload
                    </>
                  )}
                </Button>
              </Flex>
            </Card>
          );
        })}
      </Flex>

      <Text as="p" size="1" color="gray" mt="3">
        JPEG, PNG or PDF. Up to 10 MB each.
      </Text>

      </WizardShell>
    </Box>
  );
}
