'use client';

import { Banner, Button, Card, CardHeader, EmptyState, ErrorState, KeyValue, Skeleton } from '@pml.tickets/shared/components/m3';
import { LinkBtn } from '@/components/console/LinkBtn';
import { Status, statusLabel } from '@/components/console/Status';
import { formatEventDate } from '@/lib/format/figure';
import { TrackerSteps, WizardSteps } from './steps';

export interface StatusDoc {
  type: string;
  name: string;
  status: string;
}
export interface StatusViewProps {
  loading: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
  /** Null when the user has not started an application. */
  organization: {
    status: string;
    name: string;
    type: string | null;
    kybStatus: string | null;
    taxId: string | null;
    registrationNumber: string | null;
    submittedAt: string | null;
    reviewerNote: string | null;
  } | null;
  docs: StatusDoc[];
  commissionRate?: number | null;
}

/** Application status: tracker, reviewer comments and the next action. */
export function StatusView(p: StatusViewProps) {
  const o = p.organization;
  const header = (
    <div>
      <h1 className="m3-page-title">Organizer onboarding</h1>
      <p className="m3-page-sub">Apply, upload your documents and track the review.</p>
    </div>
  );
  if (p.error) {
    return (
      <div className="m3-stack" data-testid="status-error">
        {header}
        <ErrorState error={p.error} onRetry={p.onRetry} />
      </div>
    );
  }
  if (p.loading && !o) {
    return (
      <div className="m3-stack">
        {header}
        <div role="status" aria-label="Loading" data-testid="loading" className="m3-stack">
          <Skeleton />
          <Skeleton />
        </div>
      </div>
    );
  }
  if (!o) {
    return (
      <div className="m3-stack">
        {header}
        <EmptyState
          icon="file"
          title="No application yet"
          description="Start your organizer application to publish events and receive payouts."
          action={<LinkBtn href="/apply/business-info" variant="filled">Start application</LinkBtn>}
        />
      </div>
    );
  }
  const s = o.status;
  const active = s === 'ACTIVE' || s === 'APPROVED';
  const editing = s === 'DRAFT' || s === 'CHANGES_REQUESTED';
  return (
    <div className="m3-stack" data-testid="status-page">
      {header}
      <TrackerSteps status={s} />
      {active ? (
        <>
          <Banner tone="success" title="Your organization is active.">
            You can publish events, invite your team and request payouts.
          </Banner>
          <Card>
            <CardHeader title="Application summary" />
            <KeyValue
              columns
              items={[
                { label: 'Organization type', value: statusLabel(o.type) },
                { label: 'Organization', value: o.name },
                { label: 'KYB status', value: <Status status={o.kybStatus} /> },
                { label: 'TPIN', value: o.taxId || '—' },
                { label: 'Registration number', value: o.registrationNumber || '—' },
                { label: 'Commission rate', value: p.commissionRate != null ? `${p.commissionRate}%` : '—' },
              ]}
            />
            <h3 className="m3-card__title oc-section">KYB documents</h3>
            {p.docs.map((d) => (
              <div className="oc-spread" key={d.type}>
                <span>{d.name}</span>
                <Status status={d.status} />
              </div>
            ))}
          </Card>
          <div><LinkBtn href="/dashboard" variant="filled" data-testid="status-cta-dashboard">Go to overview</LinkBtn></div>
        </>
      ) : (
        <>
          {s === 'CHANGES_REQUESTED' ? (
            <Banner tone="warning" title="Reviewer comments:">
              {o.reviewerNote ?? 'The reviewer asked you to update your application.'}
            </Banner>
          ) : null}
          <Card>
            {s === 'PENDING_REVIEW' ? (
              <>
                <CardHeader
                  title="We are reviewing your application"
                  subtitle={`Submitted ${o.submittedAt ? formatEventDate(o.submittedAt) : 'recently'}. You will be told when a decision is made.`}
                />
                {p.docs.map((d) => (
                  <div className="oc-spread" key={d.type}>
                    <span>{d.name}</span>
                    <Status status={d.status} />
                  </div>
                ))}
                <div className="oc-section">
                  <LinkBtn href="/events/new" variant="tonal" data-testid="status-cta-draft-event">Create a draft event</LinkBtn>
                </div>
              </>
            ) : s === 'REJECTED' ? (
              <>
                <CardHeader title="Application rejected" subtitle={o.reviewerNote ?? undefined} />
                <LinkBtn href="/apply/business-info" variant="filled" data-testid="status-cta-reapply">Re-apply</LinkBtn>
                <p className="m3-muted oc-section">Re-applying keeps your details so you can correct them and submit a new application.</p>
              </>
            ) : editing ? (
              <>
                <CardHeader title="Finish your application" subtitle="Complete the steps below, then submit for review." />
                <WizardSteps current={s === 'DRAFT' ? 0 : 1} />
                <div className="m3-row oc-section">
                  <LinkBtn href="/apply/documents" variant="filled" data-testid="status-cta-resubmit">
                    {s === 'DRAFT' ? 'Continue application' : 'Update documents and resubmit'}
                  </LinkBtn>
                  <LinkBtn href="/apply/business-info" variant="outlined" data-testid="status-cta-edit-details">Edit details</LinkBtn>
                </div>
              </>
            ) : (
              <>
                <CardHeader title={statusLabel(s)} subtitle="Your organization is not available for new activity." />
                {p.onRetry ? <Button variant="tonal" onClick={p.onRetry}>Refresh</Button> : null}
              </>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
