'use client';

import { ownerLabel } from '@/lib/ownerLabel';
import { useEffect, useMemo, useState } from 'react';
import { z } from 'zod';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { useRouter } from 'next/navigation';
import {
  BulkBar,
  Button,
  Card,
  CardHeader,
  DataTable,
  Dialog,
  TextField,
  EmptyState,
  ErrorState,
  KeyValue,
  Banner,
  SideSheet,
  StatusPill,
  useSnackbar,
} from '@pml.tickets/shared/components/m3';
import {
  useApproveOrganization,
  useApproveVerificationDocument,
  useOrganizerApplications,
  usePlatformConfiguration,
  useRejectOrganization,
  useRejectVerificationDocument,
  useRequestOrganizationChanges,
  type ApprovalDocument,
  type OrganizerApplication,
} from '@pml.tickets/shared/api/admin/modules';
import { FilterBar } from '@/components/console/FilterBar';
import { ReasonDialog } from '@/components/console/ReasonDialog';
import { useStaff } from '@/components/console/StaffContext';
import { needText } from '@/lib/permissions';
import { ago, formatDateTime, humanize } from '@/lib/format';
import type { OrganizationStatus } from '@pml.tickets/shared/types/graphql';
import { CompareDialog, type CompareRow } from './CompareDialog';
import { deadlineFrom, slaOf } from './sla';
import { useClientPaging } from './useClientPaging';
import { ORGANIZATION_STATUS_LABELS } from '@/lib/enumLabels';

/** The statuses an application can be filtered by (a policy subset of `OrganizationStatus`; labels are the schema's). */
const STATUS_OPTIONS: OrganizationStatus[] = ['PENDING_REVIEW', 'CHANGES_REQUESTED', 'REJECTED', 'ACTIVE'];

const fileSize = (n: number | null) => (n == null ? '' : n >= 1_048_576 ? `${(n / 1_048_576).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`);
const cityOf = (o: OrganizerApplication) => [o.businessAddress?.city, o.businessAddress?.province].filter(Boolean).join(', ') || '—';
const docs = (o: OrganizerApplication) => o.verificationDocuments ?? [];
const approvedCount = (o: OrganizerApplication) => docs(o).filter((d) => d.status === 'APPROVED').length;
const payoutText = (o: OrganizerApplication) => {
  const p = o.payoutConfig;
  if (!p || !p.isConfigured) return 'None';
  return p.verified ? 'Verified' : 'Pending';
};

type Decision = { kind: 'reject' | 'changes'; org: OrganizerApplication } | { kind: 'doc-reject'; doc: ApprovalDocument } | null;

/** Approvals / Organizers tab: application queue with SLA clock, review sheet and decisions. */
export function OrgApplications() {
  const router = useRouter();
  const snackbar = useSnackbar();
  const staff = useStaff();
  const canDecide = staff.can('decide');
  const { applications, loading, error, refetch } = useOrganizerApplications();
  const { config } = usePlatformConfiguration();
  const { approve, loading: approving } = useApproveOrganization();
  const { reject } = useRejectOrganization();
  const { requestChanges } = useRequestOrganizationChanges();
  const { approve: approveDoc } = useApproveVerificationDocument();
  const { reject: rejectDoc } = useRejectVerificationDocument();

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('all');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [reviewId, setReviewId] = useState<string | null>(null);
  const [decision, setDecision] = useState<Decision>(null);
  const [approveFor, setApproveFor] = useState<OrganizerApplication | null>(null);
  const [blockedFor, setBlockedFor] = useState<OrganizerApplication | null>(null);
  const [compare, setCompare] = useState<[OrganizerApplication, OrganizerApplication] | null>(null);
  const [busy, setBusy] = useState(false);

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return applications
      .filter((o) => o.submittedAt)
      .filter((o) => status === 'all' || o.status === status)
      .filter((o) => !q || `${o.name} ${ownerLabel(o)} ${cityOf(o)}`.toLowerCase().includes(q))
      .sort((a, b) => (a.status === 'PENDING_REVIEW' ? 0 : 1) - (b.status === 'PENDING_REVIEW' ? 0 : 1) || (a.submittedAt! < b.submittedAt! ? -1 : 1));
  }, [applications, query, status]);
  const paging = useClientPaging(rows);
  const review = applications.find((o) => o.id === reviewId) ?? null;

  const slaFor = (o: OrganizerApplication) =>
    o.status === 'PENDING_REVIEW' ? slaOf(deadlineFrom(o.submittedAt, config?.approvalSlaHours), config?.approvalWarningThresholdHours) : null;

  const run = async (fn: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      const result = await fn();
      if (!result) throw new Error('The server did not confirm that. Try again.');
      await refetch();
      snackbar.show(message);
      return true;
    } catch (e) {
      snackbar.show({ message: e instanceof Error ? e.message : 'That did not go through. Try again.', tone: 'error' });
      return false;
    } finally {
      setBusy(false);
    }
  };

  const startApprove = (o: OrganizerApplication) => {
    const d = docs(o);
    if (d.length === 0 || d.some((x) => x.status !== 'APPROVED')) setBlockedFor(o);
    else setApproveFor(o);
  };

  const onCompare = () => {
    const picked = rows.filter((o) => selected.has(o.id));
    if (picked.length !== 2) {
      snackbar.show('Select exactly two applications to compare');
      return;
    }
    setCompare([picked[0], picked[1]]);
  };

  const compareRows = (a: OrganizerApplication, b: OrganizerApplication): CompareRow[] => {
    const r = (label: string, f: (o: OrganizerApplication) => string): CompareRow => ({ label, a: f(a), b: f(b), aKey: f(a), bKey: f(b) });
    return [
      r('Type', (o) => humanize(o.type)),
      r('City', cityOf),
      r('Owner', (o) => ownerLabel(o)),
      r('Submitted', (o) => formatDateTime(o.submittedAt)),
      r('Documents approved', (o) => `${approvedCount(o)} of ${docs(o).length}`),
      r('Payout account', payoutText),
      r('TPIN', (o) => o.taxId ?? '—'),
      r('Status', (o) => humanize(o.status)),
    ];
  };

  const sub = `Oldest pending applications first.${config ? ` The clock follows the ${config.approvalSlaHours} hour review target.` : ''}`;

  return (
    <>
      <Card>
        <CardHeader title="Organizer applications" subtitle={sub} />
        <FilterBar
          searchLabel="Search organizations"
          query={query}
          onQuery={(q) => { setQuery(q); paging.reset(); }}
          filters={[{ id: 'status', label: 'Status', options: STATUS_OPTIONS.map((s) => ({ value: s, label: ORGANIZATION_STATUS_LABELS[s] })) }]}
          values={{ status }}
          onFilter={(_, v) => { setStatus(v); paging.reset(); }}
          onClear={() => { setQuery(''); setStatus('all'); paging.reset(); }}
        />
        <BulkBar count={selected.size}>
          <Button variant="tonal" size="sm" onClick={onCompare}>Compare side by side</Button>
          <Button variant="text" size="sm" onClick={() => setSelected(new Set())}>Clear selection</Button>
        </BulkBar>
        <DataTable
          caption="Organizer applications"
          rows={paging.slice}
          getRowId={(o) => o.id}
          loading={loading && applications.length === 0}
          error={error && applications.length === 0 ? <ErrorState error={error} onRetry={() => void refetch()} /> : undefined}
          empty={<EmptyState icon="inbox" title="No organizer applications in the queue." />}
          selectable
          selectedIds={selected}
          onSelectionChange={setSelected}
          pagination={paging.pagination}
          columns={[
            { id: 'org', header: 'Organization', rowHeader: true, cell: (o) => (<><b>{o.name}</b><br /><span className="m3-muted">{humanize(o.type)} · {o.businessAddress?.city ?? '—'}</span></>) },
            { id: 'owner', header: 'Owner', cell: (o) => ownerLabel(o) },
            { id: 'submitted', header: 'Submitted', cell: (o) => formatDateTime(o.submittedAt) },
            {
              id: 'sla',
              header: 'Age and SLA',
              cell: (o) => {
                const s = slaFor(o);
                return s ? <StatusPill tone={s.tone}>{s.label}</StatusPill> : <span className="m3-muted">{ago(o.submittedAt)}</span>;
              },
            },
            { id: 'docs', header: 'Documents', cell: (o) => `${approvedCount(o)} of ${docs(o).length} approved` },
            { id: 'status', header: 'Status', cell: (o) => <StatusPill status={o.status}>{humanize(o.status)}</StatusPill> },
          ]}
          rowActions={(o) => (
            <Button variant="tonal" size="sm" onClick={() => setReviewId(o.id)}>
              Review
            </Button>
          )}
        />
      </Card>

      <SideSheet
        open={!!review}
        onClose={() => setReviewId(null)}
        title={review?.name ?? ''}
        actions={
          review ? (
            <>
              {canDecide && review.status === 'PENDING_REVIEW' ? (
                <>
                  <Button variant="filled" onClick={() => startApprove(review)}>Approve organizer</Button>
                  <Button variant="tonal" onClick={() => setDecision({ kind: 'changes', org: review })}>Request changes</Button>
                </>
              ) : null}
              {canDecide && (review.status === 'PENDING_REVIEW' || review.status === 'CHANGES_REQUESTED') ? (
                <Button variant="outlined" danger onClick={() => setDecision({ kind: 'reject', org: review })}>Reject</Button>
              ) : null}
              <Button variant="text" onClick={() => router.push(`/org/${review.id}`)}>Open organization</Button>
              {!canDecide ? <span className="m3-muted">{needText('decide')}</span> : null}
            </>
          ) : null
        }
      >
        {review ? (
          <div className="m3-stack">
            <div className="m3-row">
              <StatusPill status={review.status}>{humanize(review.status)}</StatusPill>
              {review.kybStatus ? <StatusPill status={review.kybStatus}>{`KYB: ${humanize(review.kybStatus)}`}</StatusPill> : null}
              {slaFor(review) ? <StatusPill tone={slaFor(review)!.tone}>{slaFor(review)!.label}</StatusPill> : null}
            </div>
            {review.rejectionReason ? <Banner tone="warning">{review.rejectionReason}</Banner> : null}
            <KeyValue
              items={[
                { label: 'Type', value: humanize(review.type) },
                { label: 'Owner', value: ownerLabel(review) },
                { label: 'Business email', value: review.businessEmail ?? '—' },
                { label: 'Business phone', value: review.businessPhone ?? '—' },
                { label: 'Address', value: [cityOf(review), review.businessAddress?.country].filter((x) => x && x !== '—').join(', ') || '—' },
                { label: 'TPIN', value: <span className="m3-mono">{review.taxId ?? '—'}</span> },
                { label: 'Registration number', value: <span className="m3-mono">{review.businessRegistrationNumber ?? '—'}</span> },
                { label: 'Submitted', value: formatDateTime(review.submittedAt) },
              ]}
            />
            {review.description ? <p className="m3-muted">{review.description}</p> : null}
            <section aria-labelledby="ap-docs">
              <h3 id="ap-docs">Verification documents</h3>
              {docs(review).length === 0 ? (
                <p className="m3-muted">No documents uploaded yet.</p>
              ) : (
                docs(review).map((d) => (
                  <div key={d.id} className="m3-row" style={{ justifyContent: 'space-between' }}>
                    <span>
                      <b>{humanize(d.documentType)}</b>
                      <br />
                      <span className="m3-muted">{[d.fileName, fileSize(d.fileSize), formatDateTime(d.uploadedAt)].filter(Boolean).join(' · ')}</span>
                      {d.rejectionReason ? (<><br /><span className="m3-muted">{d.rejectionReason}</span></>) : null}
                    </span>
                    <span className="m3-row">
                      <StatusPill status={d.status}>{humanize(d.status)}</StatusPill>
                      {d.status === 'PENDING' && canDecide ? (
                        <>
                          <Button variant="tonal" size="sm" disabled={busy} onClick={() => void run(() => approveDoc(d.id), `${humanize(d.documentType)} approved`)}>Approve</Button>
                          <Button variant="outlined" size="sm" danger disabled={busy} onClick={() => setDecision({ kind: 'doc-reject', doc: d })}>Reject</Button>
                        </>
                      ) : null}
                    </span>
                  </div>
                ))
              )}
              <p className="m3-muted">Document preview opens in the live service.</p>
            </section>
            <section aria-labelledby="ap-payout">
              <h3 id="ap-payout">Payout account</h3>
              <PayoutBlock org={review} />
            </section>
          </div>
        ) : null}
      </SideSheet>

      <Dialog
        open={!!blockedFor}
        onClose={() => setBlockedFor(null)}
        title="Documents need review first"
        actions={<Button variant="filled" onClick={() => setBlockedFor(null)}>Back to review</Button>}
      >
        {blockedFor ? (
          <p>
            {docs(blockedFor).length === 0
              ? 'No verification documents were uploaded.'
              : `${docs(blockedFor).filter((d) => d.status !== 'APPROVED').length} document${docs(blockedFor).filter((d) => d.status !== 'APPROVED').length > 1 ? 's are' : ' is'} not approved yet.`}{' '}
            Approve every document before approving the organizer.
          </p>
        ) : null}
      </Dialog>

      <ApproveDialog
        org={approveFor}
        loading={approving || busy}
        onClose={() => setApproveFor(null)}
        onConfirm={async (o, rate) => {
          const ok = await run(() => approve(o.id, rate), `${o.name} approved`);
          if (ok) { setApproveFor(null); setReviewId(null); }
        }}
      />

      <ReasonDialog
        open={decision?.kind === 'reject'}
        title={`Reject ${decision && decision.kind === 'reject' ? decision.org.name : ''}?`}
        body="The owner sees this reason and can apply again."
        confirmLabel="Reject application"
        danger
        loading={busy}
        onClose={() => setDecision(null)}
        onConfirm={async (reason) => {
          if (decision?.kind !== 'reject') return;
          const org = decision.org;
          if (await run(() => reject(org.id, reason), `${org.name} rejected`)) { setDecision(null); setReviewId(null); }
        }}
      />
      <ReasonDialog
        open={decision?.kind === 'changes'}
        title={`Request changes from ${decision && decision.kind === 'changes' ? decision.org.name : ''}`}
        body="Tell the owner what to fix. They can resubmit."
        reasonLabel="Message to the organizer"
        confirmLabel="Send request"
        loading={busy}
        onClose={() => setDecision(null)}
        onConfirm={async (reason) => {
          if (decision?.kind !== 'changes') return;
          const org = decision.org;
          if (await run(() => requestChanges(org.id, reason), `Changes requested from ${org.name}`)) { setDecision(null); setReviewId(null); }
        }}
      />
      <ReasonDialog
        open={decision?.kind === 'doc-reject'}
        title={`Reject ${decision && decision.kind === 'doc-reject' ? humanize(decision.doc.documentType).toLowerCase() : 'document'}?`}
        body="The organizer is told why and can upload a new file."
        confirmLabel="Reject document"
        danger
        loading={busy}
        onClose={() => setDecision(null)}
        onConfirm={async (reason) => {
          if (decision?.kind !== 'doc-reject') return;
          const doc = decision.doc;
          if (await run(() => rejectDoc(doc.id, reason), `${humanize(doc.documentType)} rejected`)) setDecision(null);
        }}
      />

      {compare ? (
        <CompareDialog
          open
          title="Compare applications"
          nameA={compare[0].name}
          nameB={compare[1].name}
          rows={compareRows(compare[0], compare[1])}
          onClose={() => setCompare(null)}
        />
      ) : null}
    </>
  );
}

function PayoutBlock({ org }: { org: OrganizerApplication }) {
  const p = org.payoutConfig;
  if (!p || !p.isConfigured) return <p className="m3-muted">No payout account added yet.</p>;
  return (
    <div className="m3-stack">
      {p.bankAccount ? (
        <div className="m3-row" style={{ justifyContent: 'space-between' }}>
          <span>{p.bankAccount.bankName} <span className="m3-mono">{p.bankAccount.maskedAccountNumber}</span></span>
          <StatusPill tone={p.bankAccount.verified ? 'success' : 'warning'}>{p.bankAccount.verified ? 'Verified' : 'Pending'}</StatusPill>
        </div>
      ) : null}
      {p.mobileMoneyAccount ? (
        <div className="m3-row" style={{ justifyContent: 'space-between' }}>
          <span>{humanize(p.mobileMoneyAccount.provider)} <span className="m3-mono">{p.mobileMoneyAccount.maskedPhoneNumber}</span></span>
          <StatusPill tone={p.mobileMoneyAccount.verified ? 'success' : 'warning'}>{p.mobileMoneyAccount.verified ? 'Verified' : 'Pending'}</StatusPill>
        </div>
      ) : null}
    </div>
  );
}

export const approveSchema = z.object({
  commissionRate: z.string().trim().refine((v) => v === '' || (Number.isFinite(Number(v)) && Number(v) >= 0 && Number(v) <= 100), 'Enter a percentage from 0 to 100'),
});

/** Approve dialog: the commission rate is optional; empty keeps the platform default. */
function ApproveDialog({ org, loading, onClose, onConfirm }: { org: OrganizerApplication | null; loading: boolean; onClose: () => void; onConfirm: (o: OrganizerApplication, commissionRate: number | null) => void }) {
  const form = useZodForm(approveSchema, { defaultValues: { commissionRate: '' } });
  const { register, handleSubmit, reset, formState } = form;
  useEffect(() => {
    if (org) reset({ commissionRate: '' });
  }, [org, reset]);
  return (
    <Dialog
      open={!!org}
      onClose={onClose}
      title={`Approve ${org?.name ?? ''}?`}
      actions={
        <>
          <Button variant="text" onClick={onClose}>Cancel</Button>
          <Button variant="filled" loading={loading} onClick={handleSubmit((v) => org && onConfirm(org, v.commissionRate === '' ? null : Number(v.commissionRate)))}>Approve organizer</Button>
        </>
      }
    >
      <p className="m3-muted">The organization becomes active and can publish events and request payouts. Set the commission rate, or leave it empty to use the platform default.</p>
      <TextField label="Commission rate (%)" inputMode="decimal" placeholder="Platform default" errorText={formState.errors.commissionRate?.message} {...register('commissionRate')} />
    </Dialog>
  );
}
