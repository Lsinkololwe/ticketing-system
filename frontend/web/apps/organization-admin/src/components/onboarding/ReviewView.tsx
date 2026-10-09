'use client';

import { Banner, Button, Card, CardHeader, KeyValue, Skeleton } from '@pml.tickets/shared/components/m3';
import { CheckboxRHF, Form, FormActions, useZodForm } from '@pml.tickets/shared';
import { Status } from '@/components/console/Status';
import { reviewSchema } from './schemas';
import { WizardSteps } from './steps';

export interface ReviewSummary {
  type: string;
  name: string;
  tpin: string;
  registrationNumber: string;
  phone: string;
  email: string;
  address: string;
}
export interface ReviewDoc {
  type: string;
  name: string;
  fileName?: string;
  uploaded: boolean;
}

export interface ReviewViewProps {
  loading: boolean;
  summary: ReviewSummary | null;
  docs: ReviewDoc[];
  missingFields: string[];
  resubmit?: boolean;
  onBack: () => void;
  onEdit: () => void;
  /** Submit for review. Throw to map a server error onto the form. */
  onSubmit: () => Promise<void>;
}

/** Step 3: review details and documents, accept terms, submit. */
export function ReviewView(p: ReviewViewProps) {
  const form = useZodForm(reviewSchema, { defaultValues: { confirm: false } });
  const missingDocs = p.docs.filter((d) => !d.uploaded).map((d) => d.name);
  const blocked = p.missingFields.length > 0 || missingDocs.length > 0;
  const s = p.summary;
  return (
    <div className="m3-stack" data-testid="review-step">
      <div>
        <h1 className="m3-page-title">Organizer onboarding</h1>
        <p className="m3-page-sub">Apply, upload your documents and track the review.</p>
      </div>
      <WizardSteps current={2} />
      {p.loading || !s ? (
        <div role="status" aria-label="Loading" data-testid="loading" className="m3-stack">
          <Skeleton />
          <Skeleton />
        </div>
      ) : (
        <>
          {p.missingFields.length ? <Banner tone="warning" urgent>Missing: {p.missingFields.join(', ')}</Banner> : null}
          <Form form={form} onSubmit={() => p.onSubmit()} guardLeave={false} aria-label="Submit application" disabled={blocked} className="m3-stack">
          <Card>
            <CardHeader
              title="Review and submit"
              subtitle="Check your details. After you submit, our team reviews them (usually 2 working days)."
              actions={<Button size="sm" variant="tonal" onClick={p.onEdit} data-testid="review-edit">Edit</Button>}
            />
            <KeyValue
              columns
              items={[
                { label: 'Type', value: s.type || '—' },
                { label: 'Organization', value: s.name || '—' },
                { label: 'TPIN', value: s.tpin || '—' },
                { label: 'Registration number', value: s.registrationNumber || '—' },
                { label: 'Phone', value: s.phone || '—' },
                { label: 'Email', value: s.email || '—' },
                { label: 'Address', value: s.address || '—' },
              ]}
            />
            <div className="m3-stack oc-section">
              {p.docs.map((d) => (
                <div className="oc-spread" key={d.type}>
                  <span>
                    {d.name}
                    {d.fileName ? <span className="m3-muted"> · {d.fileName}</span> : null}
                  </span>
                  {d.uploaded ? <Status status="PENDING" /> : <span className="m3-pill" data-tone="error">Missing</span>}
                </div>
              ))}
            </div>
            <div className="oc-section">
              <CheckboxRHF
                name="confirm"
                label="I confirm these details and documents are accurate, and I accept the Terms of Service and Privacy Policy."
              />
            </div>
          </Card>
          <FormActions
            submitLabel={p.resubmit ? 'Resubmit for review' : 'Submit for review'}
            align="between"
            leading={
              <Button variant="outlined" type="button" onClick={p.onBack}>
                Back
              </Button>
            }
          />
          </Form>
        </>
      )}
    </div>
  );
}
