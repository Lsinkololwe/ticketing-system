'use client';

import { useEffect, useRef } from 'react';
import { useWatch } from 'react-hook-form';
import { Banner, Button, Card, CardHeader, LinearProgress, EmptyState, Skeleton } from '@pml.tickets/shared/components/m3';
import { FileRHF, Form, useZodForm } from '@pml.tickets/shared';
import { Status } from '@/components/console/Status';
import { ACCEPT_ATTRIBUTE } from '@/lib/onboarding/documents';
import { documentFileSchema } from './schemas';
import { WizardSteps } from './steps';

export interface DocSlot {
  status: 'EMPTY' | 'UPLOADING' | 'PENDING' | 'APPROVED' | 'REJECTED';
  fileName?: string;
  rejectionReason?: string;
  progress?: number;
}
export interface DocRequirement {
  type: string;
  name: string;
  hint: string;
}

export interface DocumentsViewProps {
  loading: boolean;
  /** Null when step 1 has not saved a business type yet. */
  businessTypeLabel: string | null;
  required: DocRequirement[];
  slots: Record<string, DocSlot>;
  /** Uploads the chosen file. Throw to map a failure onto that document's form. */
  onUpload: (type: string, file: File) => Promise<void>;
  onBack: () => void;
  onNext: () => void;
}

/** Submits the surrounding form as soon as a file is chosen (upload starts immediately). */
function SubmitOnFile() {
  const ref = useRef<HTMLSpanElement>(null);
  const chosen = useWatch({ name: 'file' }) as File[] | undefined;
  useEffect(() => {
    if (chosen && chosen.length > 0) ref.current?.closest('form')?.requestSubmit();
  }, [chosen]);
  return <span ref={ref} hidden />;
}

function DocRow({ doc, slot, onUpload }: { doc: DocRequirement; slot: DocSlot; onUpload: (f: File) => Promise<void> }) {
  const form = useZodForm(documentFileSchema, { defaultValues: { file: [] } });
  const has = slot.status !== 'EMPTY';
  const uploading = slot.status === 'UPLOADING';

  return (
    <div className="m3-stack" data-testid={`document-slot-${doc.type}`}>
      <div className="oc-spread">
        <div>
          <b>{doc.name}</b>
          <div className="m3-muted">{doc.hint}</div>
          {slot.fileName && slot.status !== 'EMPTY' ? <div className="m3-muted">{slot.fileName}</div> : null}
          {slot.status === 'REJECTED' && slot.rejectionReason ? (
            <div role="alert" className="m3-field__error">
              {slot.rejectionReason}
            </div>
          ) : null}
        </div>
        <div className="m3-row">
          {uploading ? <Status status="PENDING" label={`Uploading ${slot.progress ?? 0}%`} /> : has ? <Status status={slot.status} /> : null}
        </div>
      </div>
      <Form
        form={form}
        onSubmit={async ({ file }) => {
          await onUpload(file[0]);
          form.reset({ file: [] });
        }}
        guardLeave={false}
        aria-label={`${doc.name} upload`}
        disabled={uploading}
      >
        <SubmitOnFile />
        <FileRHF name="file" label={has && !uploading ? `Replace ${doc.name}` : `Choose file for ${doc.name}`} accept={ACCEPT_ATTRIBUTE} />
      </Form>
      {uploading ? <LinearProgress value={slot.progress ?? 0} label={`Uploading ${doc.name}`} /> : null}
    </div>
  );
}

/** Step 2: KYB documents with per-document upload state. */
export function DocumentsView(p: DocumentsViewProps) {
  const satisfied = (t: string) => ['PENDING', 'APPROVED'].includes(p.slots[t]?.status ?? '');
  const outstanding = p.required.filter((d) => !satisfied(d.type));
  const all = p.required.length > 0 && outstanding.length === 0;
  return (
    <div className="m3-stack" data-testid="documents-step">
      <div>
        <h1 className="m3-page-title">Organizer onboarding</h1>
        <p className="m3-page-sub">Apply, upload your documents and track the review.</p>
      </div>
      <WizardSteps current={1} />
      {p.loading ? (
        <div role="status" aria-label="Loading" data-testid="loading" className="m3-stack">
          <Skeleton />
          <Skeleton />
        </div>
      ) : p.businessTypeLabel === null ? (
        <Card data-testid="business-type-missing">
          <EmptyState
            icon="warning"
            title="We need your business type first"
            description="Which documents we ask for depends on the kind of business you run."
            action={
              <Button variant="filled" onClick={p.onBack} data-testid="back-to-business-info">
                Back to business details
              </Button>
            }
          />
        </Card>
      ) : (
        <>
          <Card>
            <CardHeader title="KYB documents" subtitle={`Required for a ${p.businessTypeLabel}. PDF, JPEG or PNG, up to 10 MB each.`} />
            <p role="status" className="m3-muted" data-testid="documents-progress-text">
              {p.required.length - outstanding.length} of {p.required.length} uploaded
            </p>
            <div className="m3-stack">
              {p.required.map((d) => (
                <DocRow key={d.type} doc={d} slot={p.slots[d.type] ?? { status: 'EMPTY' }} onUpload={(f) => p.onUpload(d.type, f)} />
              ))}
            </div>
          </Card>
          {!all ? <Banner tone="info">Still needed: {outstanding.map((d) => d.name).join(', ')}</Banner> : null}
          <div className="oc-spread">
            <Button variant="outlined" onClick={p.onBack}>
              Back
            </Button>
            <Button variant="filled" disabled={!all} onClick={p.onNext}>
              Continue
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
