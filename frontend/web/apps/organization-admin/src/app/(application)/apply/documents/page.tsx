'use client';

/**
 * Step 2: KYB documents. Files upload directly to storage via a presigned URL
 * (useDocumentUpload); nothing passes through GraphQL. Which documents are
 * required is decided by the business type saved in step 1.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useDocumentUpload, useMyOrganization } from '@pml.tickets/shared/api/organization-admin/modules/organization';
import { DocumentsView, type DocSlot } from '@/components/onboarding/DocumentsView';
import { businessTypeSpec, requiredDocuments } from '@/lib/onboarding/documents';
import { useOnboardingCatalogue } from '@/lib/onboarding/catalogue';
import { ROUTES } from '@/lib/onboarding/state';

export default function DocumentsStepPage() {
  const router = useRouter();
  const { organization, loading } = useMyOrganization({ fetchPolicy: 'network-only' });
  const organizationId = organization?.id ?? '';
  const businessType = organization?.businessType ?? null;
  const catalogue = useOnboardingCatalogue();
  const { upload, documents, refresh, isLoading } = useDocumentUpload(organizationId);
  const required = useMemo(
    () => requiredDocuments(catalogue.businessTypes, catalogue.documents, businessType),
    [catalogue.businessTypes, catalogue.documents, businessType],
  );
  const [slots, setSlots] = useState<Record<string, DocSlot>>({});

  useEffect(() => {
    if (organizationId) void refresh();
  }, [organizationId, refresh]);

  useEffect(() => {
    if (!documents.length) return;
    setSlots((prev) => {
      const next = { ...prev };
      for (const doc of documents) {
        if (next[doc.documentType]?.status === 'UPLOADING') continue;
        next[doc.documentType] = { status: doc.status, fileName: doc.fileName, rejectionReason: doc.rejectionReason };
      }
      return next;
    });
  }, [documents]);

  const setSlot = useCallback((type: string, patch: Partial<DocSlot>) => {
    setSlots((prev) => ({ ...prev, [type]: { ...(prev[type] ?? { status: 'EMPTY' }), ...patch } }));
  }, []);

  const onUpload = useCallback(
    async (type: string, file: File) => {
      setSlot(type, { status: 'UPLOADING', fileName: file.name, progress: 0 });
      try {
        await upload(type as never, file);
        setSlot(type, { status: 'PENDING', fileName: file.name, progress: 100, rejectionReason: undefined });
      } catch (err) {
        setSlot(type, { status: 'EMPTY', progress: undefined });
        throw err;
      }
    },
    [upload, setSlot]
  );

  return (
    <DocumentsView
      loading={loading || catalogue.loading || (isLoading && !documents.length)}
      businessTypeLabel={businessType ? (businessTypeSpec(catalogue.businessTypes, businessType)?.label ?? 'business') : null}
      required={required.map((d) => ({ type: d.type, name: d.name, hint: d.hint }))}
      slots={slots}
      onUpload={onUpload}
      onBack={() => router.push(ROUTES.businessInfo)}
      onNext={() => router.push(ROUTES.review)}
    />
  );
}
