'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Banner,
  BulkBar,
  Button,
  Card,
  CardHeader,
  CommentThread,
  ConfirmDialog,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  KeyValue,
  ReadinessList,
  SideSheet,
  Skeleton,
  StatusPill,
  Timeline,
  useSnackbar,
} from '@pml.tickets/shared/components/m3';
import {
  useApprovalTimeline,
  useApprovalWorkflow,
  useEventDecisions,
  usePendingApprovalEvents,
  usePlatformConfiguration,
  useReviewerCandidates,
  type PendingEventRow,
  type PendingTimelineRow,
} from '@pml.tickets/shared/api/admin/modules';
import { z } from 'zod';
import { Form, FormActions } from '@pml.tickets/shared/forms/Form';
import { SelectRHF } from '@pml.tickets/shared/forms/fields';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { RowActions } from '@/components/console/RowActions';
import { FilterBar } from '@/components/console/FilterBar';
import { ReasonDialog } from '@/components/console/ReasonDialog';
import { useStaff } from '@/components/console/StaffContext';
import { needText } from '@/lib/permissions';
import { formatDate, formatDateTime, humanize, money } from '@/lib/format';
import { CompareDialog, type CompareRow } from './CompareDialog';
import { slaOf } from './sla';
import { useClientPaging } from './useClientPaging';

const BLOCKERS: Array<{ key: string; label: string }> = [
  { key: 'NO_PUBLISHED_TIER', label: 'At least one published ticket tier' },
  { key: 'NO_LOCATION', label: 'Venue and location set' },
  { key: 'NO_CAPACITY', label: 'Capacity set' },
];
const blockerText = (k: string) => humanize(k);

interface Row {
  event: PendingEventRow;
  tl: PendingTimelineRow | undefined;
}

type Reason = { kind: 'reject' | 'changes' | 'escalate'; row: Row } | null;

/** Approvals / Events tab: event approval queue with SLA, reviewer claim, blockers and decisions. */
export function EventApprovals() {
  const router = useRouter();
  const snackbar = useSnackbar();
  const staff = useStaff();
  const canDecide = staff.can('decide');
  const { events, timelines, loading, error, refetch } = usePendingApprovalEvents();
  const { config } = usePlatformConfiguration();
  const decisions = useEventDecisions();
  const wf = useApprovalWorkflow();
  const { reviewers } = useReviewerCandidates('ADMIN');

  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [reviewId, setReviewId] = useState<string | null>(null);
  const [reason, setReason] = useState<Reason>(null);
  const [confirm, setConfirm] = useState<Row | null>(null);
  const [blocked, setBlocked] = useState<Row | null>(null);
  const [assign, setAssign] = useState<Row | null>(null);
  const [bulk, setBulk] = useState<{ ok: Row[]; skip: Row[] } | null>(null);
  const [compare, setCompare] = useState<[Row, Row] | null>(null);
  const [busy, setBusy] = useState(false);

  const all: Row[] = useMemo(() => {
    const byId = new Map(timelines.map((t) => [t.eventId, t]));
    return events.map((event) => ({ event, tl: byId.get(event.id) }));
  }, [events, timelines]);

  const slaFor = (r: Row) => slaOf(r.tl?.slaDeadline, config?.approvalWarningThresholdHours);
  const mine = (r: Row) => !!r.tl?.assignedReviewerId && r.tl.assignedReviewerId === staff.id;

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return all
      .filter((r) => !q || `${r.event.title} ${r.event.organizerName ?? ''} ${r.tl?.assignedReviewerName ?? ''}`.toLowerCase().includes(q))
      .filter((r) => !filters.rev || filters.rev === 'all' || (filters.rev === 'mine' ? mine(r) : !r.tl?.assignedReviewerId))
      .filter((r) => !filters.sla || filters.sla === 'all' || slaFor(r)?.kind === filters.sla)
      .filter((r) => !filters.blk || filters.blk === 'all' || (r.event.approvalBlockers.length > 0) === (filters.blk === 'yes'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [all, query, filters, config, staff.id]);
  const paging = useClientPaging(rows);
  const review = all.find((r) => r.event.id === reviewId) ?? null;

  const run = async (fn: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      const res = await fn();
      if (res && typeof res === 'object' && 'success' in res && !(res as { success: boolean }).success) {
        throw new Error((res as { message?: string | null }).message ?? 'The server refused that.');
      }
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

  const claim = (r: Row) => run(() => wf.assign(r.event.id, staff.id, staff.name), `You are now the reviewer for ${r.event.title}`);
  const release = (r: Row) => run(() => wf.unassign(r.event.id), 'Claim released');
  const startApprove = (r: Row) => (r.event.approvalBlockers.length ? setBlocked(r) : setConfirm(r));

  const onBulk = () => {
    const picked = rows.filter((r) => selected.has(r.event.id));
    setBulk({ ok: picked.filter((r) => !r.event.approvalBlockers.length), skip: picked.filter((r) => r.event.approvalBlockers.length) });
  };
  const onCompare = () => {
    const picked = rows.filter((r) => selected.has(r.event.id));
    if (picked.length !== 2) {
      snackbar.show('Select exactly two events to compare');
      return;
    }
    setCompare([picked[0], picked[1]]);
  };

  const compareRows = (a: Row, b: Row): CompareRow[] => {
    const r = (label: string, f: (x: Row) => string): CompareRow => ({ label, a: f(a), b: f(b), aKey: f(a), bKey: f(b) });
    return [
      r('Organization', (x) => x.event.organizerName ?? '—'),
      r('Category', (x) => x.event.category?.name ?? '—'),
      r('City', (x) => x.event.cityName ?? '—'),
      r('Date', (x) => formatDate(x.event.eventDateTime)),
      r('Capacity', (x) => (x.event.totalCapacity ? String(x.event.totalCapacity) : '—')),
      r('Lowest ticket price', (x) => (x.event.minTicketPrice != null ? money(x.event.minTicketPrice) : 'None')),
      r('Blockers', (x) => x.event.approvalBlockers.map(blockerText).join(', ') || 'None'),
      r('SLA', (x) => slaFor(x)?.label ?? '—'),
      r('Submitted', (x) => formatDateTime(x.event.submittedForApprovalAt)),
      r('Reviewer', (x) => x.tl?.assignedReviewerName ?? 'Unassigned'),
      r('Submission', (x) => String(x.tl?.submissionCount ?? 1)),
    ];
  };

  const doApproveBulk = async () => {
    if (!bulk) return;
    const { ok, skip } = bulk;
    setBusy(true);
    let done = 0;
    for (const r of ok) {
      const res = await decisions.approve(r.event.id, 'Bulk approval');
      if (res.success) done += 1;
    }
    await refetch();
    setBusy(false);
    setBulk(null);
    setSelected(new Set());
    if (done) snackbar.show(`${done} event${done === 1 ? '' : 's'} approved${skip.length ? `, ${skip.length} skipped` : ''}`);
    else if (ok.length) snackbar.show({ message: 'None of the selected events could be approved.', tone: 'error' });
  };

  const reasonRequired = (k: 'reject' | 'changes') => (k === 'reject' ? config?.requireCommentsOnRejection : config?.requireCommentsOnChangesRequested) ?? true;

  const rowActions = (r: Row) => (
    <RowActions
      name={r.event.title}
      primary={{ label: 'Review', onSelect: () => setReviewId(r.event.id) }}
      items={
        canDecide
          ? [
              mine(r)
                ? { id: 'release', label: 'Release claim', disabled: busy, onSelect: () => void release(r) }
                : { id: 'claim', label: 'Claim', disabled: busy, onSelect: () => void claim(r) },
              { id: 'assign', label: 'Assign reviewer', onSelect: () => setAssign(r) },
              ...(!r.tl?.escalation ? [{ id: 'escalate', label: 'Escalate…', onSelect: () => setReason({ kind: 'escalate', row: r }) }] : []),
            ]
          : []
      }
    />
  );

  return (
    <>
      <Card>
        <CardHeader
          title="Events awaiting approval"
          subtitle="Approval is refused while a blocker is outstanding. Rejecting or requesting changes is never blocked."
        />
        <FilterBar
          searchLabel="Search events"
          query={query}
          onQuery={(q) => { setQuery(q); paging.reset(); }}
          filters={[
            { id: 'rev', label: 'Reviewer', options: [{ value: 'mine', label: 'Assigned to me' }, { value: 'none', label: 'Unassigned' }] },
            { id: 'sla', label: 'SLA', options: [{ value: 'overdue', label: 'Overdue' }, { value: 'warn', label: 'Due soon' }, { value: 'ok', label: 'On track' }] },
            { id: 'blk', label: 'Blockers', options: [{ value: 'yes', label: 'Has blockers' }, { value: 'no', label: 'Clear' }] },
          ]}
          values={filters}
          onFilter={(id, v) => { setFilters((f) => ({ ...f, [id]: v })); paging.reset(); }}
          onClear={() => { setQuery(''); setFilters({}); paging.reset(); }}
        />
        <BulkBar count={selected.size}>
          <Button variant="tonal" size="sm" disabled={!canDecide} onClick={onBulk}>Approve selected</Button>
          <Button variant="tonal" size="sm" onClick={onCompare}>Compare side by side</Button>
          <Button variant="text" size="sm" onClick={() => setSelected(new Set())}>Clear selection</Button>
          {!canDecide ? <span>{needText('decide')}</span> : null}
        </BulkBar>
        <DataTable
          caption="Events awaiting approval"
          rows={paging.slice}
          getRowId={(r) => r.event.id}
          loading={loading && events.length === 0}
          error={error && events.length === 0 ? <ErrorState error={error} onRetry={() => void refetch()} /> : undefined}
          empty={<EmptyState icon="inbox" title="No events are waiting for approval." />}
          selectable
          selectedIds={selected}
          onSelectionChange={setSelected}
          pagination={paging.pagination}
          columns={[
            { id: 'event', header: 'Event', rowHeader: true, cell: (r) => (<><b>{r.event.title}</b><br /><span className="m3-muted">{r.event.organizerName ?? '—'} · {formatDate(r.event.eventDateTime)}</span></>) },
            {
              id: 'submitted',
              header: 'Submitted',
              cell: (r) => (
                <>
                  {formatDateTime(r.event.submittedForApprovalAt)}
                  {(r.tl?.submissionCount ?? 1) > 1 ? (<><br /><span className="m3-muted">Submission {r.tl?.submissionCount}</span></>) : null}
                </>
              ),
            },
            { id: 'sla', header: 'SLA clock', cell: (r) => { const s = slaFor(r); return s ? <StatusPill tone={s.tone}>{s.label}</StatusPill> : <span className="m3-muted">—</span>; } },
            { id: 'rev', header: 'Reviewer', cell: (r) => r.tl?.assignedReviewerName ?? <span className="m3-muted">Unassigned</span> },
            {
              id: 'blk',
              header: 'Blockers',
              cell: (r) =>
                r.event.approvalBlockers.length ? (
                  <span className="m3-row">{r.event.approvalBlockers.map((b) => <StatusPill key={b} tone="error">{blockerText(b)}</StatusPill>)}</span>
                ) : (
                  <StatusPill tone="success">None</StatusPill>
                ),
            },
            {
              id: 'esc',
              header: 'Escalation',
              cell: (r) => (r.tl?.escalation ? <StatusPill status={r.tl.escalation.status}>{`Escalated · ${humanize(r.tl.escalation.status)}`}</StatusPill> : <span className="m3-muted">—</span>),
            },
          ]}
          rowActions={rowActions}
        />
      </Card>

      <ReviewSheet
        row={review}
        canDecide={canDecide}
        mine={review ? mine(review) : false}
        busy={busy}
        recipientRole={config?.escalationRecipientRole}
        slaLabel={review ? slaFor(review) : null}
        onClose={() => setReviewId(null)}
        onApprove={startApprove}
        onChanges={(r) => setReason({ kind: 'changes', row: r })}
        onReject={(r) => setReason({ kind: 'reject', row: r })}
        onClaim={(r) => void claim(r)}
        onRelease={(r) => void release(r)}
        onEscalate={(r) => setReason({ kind: 'escalate', row: r })}
        onAcknowledge={(id) => void run(() => wf.acknowledge(id), 'Escalation acknowledged')}
        onComment={async (r, text) => { await run(() => wf.comment(r.event.id, text), 'Comment posted'); }}
        onOpen={(r) => router.push(`/event/${r.event.id}`)}
      />

      <ConfirmDialog
        open={!!confirm}
        onClose={() => setConfirm(null)}
        title={`Approve ${confirm?.event.title ?? ''}?`}
        description="The organizer is notified and can publish the event."
        confirmLabel="Approve event"
        loading={busy}
        onConfirm={async () => {
          if (!confirm) return;
          const r = confirm;
          if (await run(() => decisions.approve(r.event.id), `${r.event.title} approved`)) { setConfirm(null); setReviewId(null); }
        }}
      />

      <Dialog
        open={!!blocked}
        onClose={() => setBlocked(null)}
        title="Cannot approve yet"
        actions={
          <>
            <Button variant="text" onClick={() => setBlocked(null)}>Close</Button>
            <Button variant="tonal" onClick={() => { const r = blocked; setBlocked(null); if (r) setReason({ kind: 'changes', row: r }); }}>Request changes</Button>
          </>
        }
      >
        <p>Approval is refused while these blockers are outstanding:</p>
        <ReadinessList label="Outstanding blockers" items={(blocked?.event.approvalBlockers ?? []).map((b) => ({ id: b, label: blockerText(b), done: false }))} />
        <p className="m3-muted">You can still request changes or reject the event.</p>
      </Dialog>

      {assign ? (
        <AssignDialog
          title={assign.event.title}
          initial={assign.tl?.assignedReviewerId ?? staff.id}
          options={[{ value: staff.id, label: `${staff.name} (me)` }, ...reviewers.filter((x) => x.id !== staff.id).map((x) => ({ value: x.id, label: x.fullName }))]}
          onClose={() => setAssign(null)}
          onAssign={async (id, name) => {
            if (await run(() => wf.assign(assign.event.id, id, name), `${name} is now reviewing ${assign.event.title}`)) setAssign(null);
          }}
        />
      ) : null}

      <ReasonDialog
        open={reason?.kind === 'reject'}
        title={`Reject ${reason?.row.event.title ?? ''}?`}
        body="The organizer sees your comments."
        reasonLabel="Comments for the organizer"
        confirmLabel="Reject event"
        danger
        required={reasonRequired('reject')}
        loading={busy}
        onClose={() => setReason(null)}
        onConfirm={async (text) => {
          const r = reason?.row;
          if (!r) return;
          if (await run(() => decisions.reject(r.event.id, text), `${r.event.title} rejected`)) { setReason(null); setReviewId(null); }
        }}
      />
      <ReasonDialog
        open={reason?.kind === 'changes'}
        title={`Request changes for ${reason?.row.event.title ?? ''}`}
        body="The SLA clock pauses until the organizer resubmits."
        reasonLabel="What should change?"
        confirmLabel="Request changes"
        required={reasonRequired('changes')}
        loading={busy}
        onClose={() => setReason(null)}
        onConfirm={async (text) => {
          const r = reason?.row;
          if (!r) return;
          if (await run(() => decisions.requestChanges(r.event.id, text), `Changes requested for ${r.event.title}`)) { setReason(null); setReviewId(null); }
        }}
      />
      <ReasonDialog
        open={reason?.kind === 'escalate'}
        title={`Escalate ${reason?.row.event.title ?? ''}?`}
        body={`The ${humanize(config?.escalationRecipientRole).toLowerCase()} is notified${config ? ` and reminded every ${config.escalationReminderIntervalHours} hours` : ''}.`}
        reasonLabel="Why is this escalated?"
        confirmLabel="Escalate"
        loading={busy}
        onClose={() => setReason(null)}
        onConfirm={async (text) => {
          const r = reason?.row;
          if (!r) return;
          if (await run(() => wf.escalate(r.event.id, text, config?.escalationRecipientRole ?? 'SUPER_ADMIN'), `Escalated to ${humanize(config?.escalationRecipientRole).toLowerCase()}`)) setReason(null);
        }}
      />

      <Dialog
        open={!!bulk}
        onClose={() => setBulk(null)}
        title={`Approve ${bulk?.ok.length ?? 0} event${bulk?.ok.length === 1 ? '' : 's'}?`}
        actions={
          <>
            <Button variant="text" onClick={() => setBulk(null)}>Cancel</Button>
            <Button variant="filled" loading={busy} onClick={() => (bulk?.ok.length ? void doApproveBulk() : setBulk(null))}>
              {bulk?.ok.length ? 'Approve' : 'Close'}
            </Button>
          </>
        }
      >
        <p>
          {bulk?.ok.length
            ? `Each organizer is notified.${bulk.skip.length ? ` ${bulk.skip.length} selected event${bulk.skip.length > 1 ? 's have' : ' has'} blockers and will be skipped: ${bulk.skip.map((r) => r.event.title).join(', ')}.` : ''}`
            : 'Every selected event has blockers, so nothing can be approved.'}
        </p>
      </Dialog>

      {compare ? (
        <CompareDialog open title="Compare events" nameA={compare[0].event.title} nameB={compare[1].event.title} rows={compareRows(compare[0], compare[1])} onClose={() => setCompare(null)} />
      ) : null}
    </>
  );
}

interface ReviewSheetProps {
  row: Row | null;
  canDecide: boolean;
  mine: boolean;
  busy: boolean;
  recipientRole?: string;
  slaLabel: ReturnType<typeof slaOf>;
  onClose: () => void;
  onApprove: (r: Row) => void;
  onChanges: (r: Row) => void;
  onReject: (r: Row) => void;
  onClaim: (r: Row) => void;
  onRelease: (r: Row) => void;
  onEscalate: (r: Row) => void;
  onAcknowledge: (escalationId: string) => void;
  onComment: (r: Row, text: string) => Promise<void>;
  onOpen: (r: Row) => void;
}

function ReviewSheet(p: ReviewSheetProps) {
  const { row } = p;
  const { timeline, loading, error, refetch } = useApprovalTimeline(row?.event.id ?? null);
  const e = row?.event;
  const blockers = e?.approvalBlockers ?? [];
  const escalation = timeline?.escalation ?? row?.tl?.escalation ?? null;
  const entries = timeline?.timelineEvents ?? [];
  const comments = entries.filter((t) => t.action === 'COMMENT_ADDED' && t.comments);

  return (
    <SideSheet
      open={!!row}
      onClose={p.onClose}
      title={e?.title ?? ''}
      actions={
        row ? (
          <>
            {p.canDecide ? (
              <>
                <Button variant="filled" onClick={() => p.onApprove(row)}>Approve event</Button>
                <Button variant="tonal" onClick={() => p.onChanges(row)}>Request changes</Button>
                <Button variant="outlined" danger onClick={() => p.onReject(row)}>Reject</Button>
                {p.mine ? (
                  <Button variant="text" disabled={p.busy} onClick={() => p.onRelease(row)}>Release claim</Button>
                ) : (
                  <Button variant="text" disabled={p.busy} onClick={() => p.onClaim(row)}>Claim</Button>
                )}
                {!escalation ? <Button variant="text" onClick={() => p.onEscalate(row)}>Escalate</Button> : null}
              </>
            ) : (
              <span className="m3-muted">{needText('decide')}</span>
            )}
          </>
        ) : null
      }
    >
      {row && e ? (
        <div className="m3-stack">
          <div className="m3-row">
            <StatusPill status={e.status}>{humanize(e.status)}</StatusPill>
            {p.slaLabel ? <StatusPill tone={p.slaLabel.tone}>{p.slaLabel.label}</StatusPill> : null}
            {escalation ? <StatusPill status={escalation.status}>{`Escalation: ${humanize(escalation.status)}`}</StatusPill> : null}
          </div>
          <KeyValue
            items={[
              { label: 'Organization', value: e.organizerName ?? '—' },
              { label: 'Category', value: e.category?.name ?? '—' },
              { label: 'Date', value: formatDateTime(e.eventDateTime) },
              { label: 'Venue', value: e.locationName ?? '—' },
              { label: 'Location', value: e.cityName ?? '—' },
              { label: 'Capacity', value: e.totalCapacity ? String(e.totalCapacity) : '—' },
              { label: 'Reviewer', value: row.tl?.assignedReviewerName ?? 'Unassigned' },
              { label: 'Submissions', value: String(row.tl?.submissionCount ?? '—') },
            ]}
          />
          <section aria-labelledby="ev-chk">
            <h3 id="ev-chk">Approval checklist</h3>
            <ReadinessList
              label="Approval checklist"
              items={BLOCKERS.map((b) => ({ id: b.key, label: b.label, done: !blockers.includes(b.key), hint: `(${blockerText(b.key)})` }))}
            />
          </section>
          {escalation ? (
            <Banner
              tone="warning"
              actions={
                escalation.status === 'PENDING' && p.canDecide ? (
                  <Button variant="tonal" size="sm" disabled={p.busy} onClick={() => p.onAcknowledge(escalation.id)}>Acknowledge</Button>
                ) : undefined
              }
            >
              <b>Escalation {humanize(escalation.status).toLowerCase()}</b> since {formatDateTime(escalation.triggeredAt)}.
              {p.recipientRole ? ` Sent to ${humanize(p.recipientRole).toLowerCase()}.` : ''}
            </Banner>
          ) : null}
          {loading && !timeline ? (
            <Skeleton width="100%" />
          ) : error && !timeline ? (
            <ErrorState error={error} onRetry={() => void refetch()} />
          ) : (
            <>
              <section aria-labelledby="ev-tl">
                <h3 id="ev-tl">Approval timeline</h3>
                {entries.length ? (
                  <Timeline
                    label="Approval timeline"
                    items={entries.map((t) => ({ id: t.id, title: humanize(t.action), time: formatDateTime(t.timestamp), detail: [t.actorName, t.description].filter(Boolean).join(' · ') }))}
                  />
                ) : (
                  <p className="m3-muted">No activity recorded yet.</p>
                )}
              </section>
              <CommentThread
                label="Comments"
                comments={comments.map((t) => ({ id: t.id, author: t.actorName, time: formatDateTime(t.timestamp), body: t.comments }))}
                emptyText="No comments yet."
                disabled={!p.canDecide}
                onSubmit={p.canDecide ? async (text) => { await p.onComment(row, text); await refetch(); } : undefined}
              />
            </>
          )}
          <div>
            <Button variant="tonal" size="sm" onClick={() => p.onOpen(row)}>Open full page</Button>
          </div>
        </div>
      ) : null}
    </SideSheet>
  );
}

const assignSchema = z.object({ reviewer: z.string().min(1, 'Choose a reviewer') });

function AssignDialog({ title, initial, options, onClose, onAssign }: {
  title: string;
  initial: string;
  options: Array<{ value: string; label: string }>;
  onClose: () => void;
  onAssign: (id: string, name: string) => Promise<void>;
}) {
  const form = useZodForm(assignSchema, { defaultValues: { reviewer: initial } });
  return (
    <Dialog open onClose={onClose} title="Assign reviewer">
      <Form
        form={form as never}
        guardLeave={false}
        aria-label="Assign reviewer"
        onSubmit={(async (v: z.output<typeof assignSchema>) => {
          await onAssign(v.reviewer, options.find((o) => o.value === v.reviewer)?.label.replace(/ \(me\)$/, '') ?? v.reviewer);
        }) as never}
      >
        <p className="m3-muted">{title}</p>
        <SelectRHF name="reviewer" label="Reviewer" options={options} />
        <FormActions submitLabel="Assign" onCancel={onClose} />
      </Form>
    </Dialog>
  );
}
