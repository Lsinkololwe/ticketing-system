'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Banner,
  Button,
  Card,
  CardHeader,
  CommentThread,
  DataTable,
  EmptyState,
  ErrorState,
  KeyValue,
  KpiCard,
  KpiGrid,
  LinearProgress,
  Skeleton,
  StatusPill,
  Tabs,
  Timeline,
  useSnackbar,
} from '@pml.tickets/shared/components/m3';
import { useEventDecisions } from '@pml.tickets/shared/api/admin/modules/event';
import { usePlatformConfiguration } from '@pml.tickets/shared/api/admin/modules/platform-config';
import {
  useAddApprovalComment,
  useAdminEventDetail,
  useEventApprovalTimeline,
  useEventEscrow,
  useEventPayouts,
  useEventRefunds,
  useFeatureEvent,
  type AdminEventDetail,
} from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { ModuleFrame, ReasonDialog, useStaff } from '@/components/console';
import { canOpenModule } from '@/config/navigation';
import { needText } from '@/lib/permissions';
import { formatDate, formatDateTime, formatNumber, humanize, money } from '@/lib/format';
import { EventMedia } from './EventMedia';
import { EventChargebacks, EventCommission } from './EventMoneyExtras';
import { ApprovalChecklist } from './ApprovalChecklist';
import { BuyerPreview } from './BuyerPreview';
import { CancelEventDialog } from './CancelEventDialog';
import { APPROVAL_STEPS, CANCELLABLE, approvalStep, percent } from './helpers';

type TabId = 'overview' | 'tiers' | 'approval' | 'money' | 'media' | 'activity';
const TABS: Array<{ id: TabId; label: string }> = [
  { id: 'overview', label: 'Overview' },
  { id: 'tiers', label: 'Ticket tiers' },
  { id: 'approval', label: 'Approval' },
  { id: 'money', label: 'Money' },
  { id: 'media', label: 'Media' },
  { id: 'activity', label: 'Activity' },
];

type Decision = 'approve' | 'changes' | 'reject';

/** /event/[id]: everything about one event, in tabs, with the admin actions. */
export function EventDetailPage({ eventId }: { eventId: string }) {
  const router = useRouter();
  const { event, loading, error, refetch } = useAdminEventDetail(eventId);
  const back = () => router.push('/events/all');
  const frame = { module: 'events' as const, onBack: back };

  if (!event) {
    return (
      <ModuleFrame {...frame} title="Event" subtitle="">
        {error ? <ErrorState error={error} onRetry={refetch} /> : loading ? <Skeleton /> : <Card><EmptyState title="Event not found" description="It may have been removed." action={<Button variant="filled" onClick={back}>All events</Button>} /></Card>}
      </ModuleFrame>
    );
  }
  return <Detail event={event} frame={frame} />;
}

function Detail({ event, frame }: { event: AdminEventDetail; frame: { module: 'events'; onBack: () => void } }) {
  const router = useRouter();
  const snackbar = useSnackbar();
  const staff = useStaff();
  const [tab, setTab] = useState<TabId>('overview');
  const [preview, setPreview] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [decision, setDecision] = useState<Decision | null>(null);
  const { feature, loading: featuring } = useFeatureEvent();
  const decisions = useEventDecisions();
  const { config } = usePlatformConfiguration();
  const { timeline } = useEventApprovalTimeline(event.id);
  const financeOk = canOpenModule(staff.roles, 'finance');
  const { escrow } = useEventEscrow(event.id, !financeOk);

  const pending = event.status === 'PENDING_APPROVAL';
  const canDecide = staff.can('decide') && canOpenModule(staff.roles, 'approvals');
  const canFeature = staff.can('featureEvent');
  const blocked = event.approvalBlockers.length > 0;
  const step = approvalStep(event.status);

  const toggleFeature = async () => {
    const res = await feature(event.id, !event.featured);
    snackbar.show({
      message: res.success ? (event.featured ? `${event.title} is no longer featured` : `${event.title} is now featured on the buyer home page`) : (res.message ?? 'Could not change the featured flag'),
      tone: res.success ? 'neutral' : 'error',
    });
  };

  const decide = async (kind: Decision, text: string) => {
    const res = kind === 'approve' ? await decisions.approve(event.id, text || undefined) : kind === 'reject' ? await decisions.reject(event.id, text) : await decisions.requestChanges(event.id, text);
    snackbar.show({
      message: res.success ? (kind === 'approve' ? `${event.title} approved` : kind === 'reject' ? `${event.title} rejected` : 'Changes requested') : (res.message ?? 'The decision was not saved'),
      tone: res.success ? 'neutral' : 'error',
    });
    if (res.success) setDecision(null);
  };

  const actions = (
    <>
      <Button variant="outlined" aria-pressed={preview} onClick={() => setPreview((p) => !p)}>
        Buyer preview
      </Button>
    </>
  );

  const actionRow = (
    <div className="m3-row">
      {pending && canDecide ? (
        <>
          <Button variant="filled" size="sm" disabled={blocked} onClick={() => setDecision('approve')}>
            Approve
          </Button>
          <Button variant="tonal" size="sm" onClick={() => setDecision('changes')}>
            Request changes
          </Button>
          <Button variant="outlined" size="sm" danger onClick={() => setDecision('reject')}>
            Reject
          </Button>
        </>
      ) : null}
      {event.status === 'PUBLISHED' && canFeature ? (
        <Button variant="tonal" size="sm" loading={featuring} onClick={() => void toggleFeature()}>
          {event.featured ? 'Remove feature' : 'Feature event'}
        </Button>
      ) : null}
      {event.featured && canFeature ? (
        <Button variant="tonal" size="sm" disabled title="Needs the stock images catalog">
          Banner override
        </Button>
      ) : null}
      {escrow && financeOk ? (
        <Button variant="tonal" size="sm" onClick={() => router.push(`/finance/escrow?event=${event.id}`)}>
          Escrow account
        </Button>
      ) : null}
      {CANCELLABLE.includes(event.status) && canFeature ? (
        <Button variant="outlined" size="sm" danger onClick={() => setCancelOpen(true)}>
          Cancel event
        </Button>
      ) : null}
    </div>
  );

  return (
    <ModuleFrame
      {...frame}
      title={event.title}
      subtitle={`${event.organizerName} · ${event.cityName ?? ''} · ${formatDate(event.eventDateTime)}`}
      actions={
        <>
          <Button variant="outlined" onClick={() => router.push('/events/all')}>
            All events
          </Button>
          {actions}
        </>
      }
    >
      <KpiGrid>
        <KpiCard label="Status" value={<StatusPill status={event.status} />} caption={timeline && !timeline.isOverdue && timeline.hoursUntilDeadline != null && pending ? `${timeline.hoursUntilDeadline} h left in review SLA` : event.isOverdue ? 'Past its review deadline' : event.featured ? 'Featured on the buyer home page' : undefined} />
        <KpiCard label="Sold" value={`${formatNumber(event.soldTickets)} of ${formatNumber(event.totalCapacity)}`} caption={<LinearProgress value={percent(event.soldTickets, event.totalCapacity)} label="Tickets sold" />} />
        <KpiCard label="Revenue" value={escrow ? money(escrow.totalDeposits) : financeOk ? '-' : 'Finance only'} caption={escrow ? `Commission ${money(escrow.totalCommissions)}` : undefined} />
        <KpiCard label="In escrow" value={escrow ? money(escrow.currentBalance) : '-'} caption={escrow ? humanize(escrow.status) : 'No account yet'} />
        <KpiCard label="Approval progress" value={`${step} of 4`} caption={APPROVAL_STEPS[Math.max(0, Math.min(3, step - 1))]} />
      </KpiGrid>

      <Tabs label="Event sections" variant="seg" tabs={TABS} value={tab} onChange={(id) => setTab(id as TabId)} />
      <div className={preview ? 'm3-split' : undefined}>
        <div className="m3-stack">
          {tab === 'overview' ? <Overview event={event} timelineReviewer={timeline?.assignedReviewerName} submissions={timeline?.submissionCount} actionRow={actionRow} /> : null}
          {tab === 'tiers' ? <Tiers event={event} /> : null}
          {tab === 'approval' ? <Approval event={event} actionRow={actionRow} canDecide={canDecide} pending={pending} /> : null}
          {tab === 'money' ? <Money eventId={event.id} financeOk={financeOk} /> : null}
          {tab === 'media' ? <EventMedia event={event} /> : null}
          {tab === 'activity' ? <Activity eventId={event.id} /> : null}
        </div>
        {preview ? <BuyerPreview event={event} /> : null}
      </div>

      <CancelEventDialog event={cancelOpen ? { id: event.id, title: event.title, soldTickets: event.soldTickets } : null} onClose={() => setCancelOpen(false)} />
      <ReasonDialog
        open={decision === 'approve'}
        title={`Approve ${event.title}?`}
        body="The organizer is told and can publish the event."
        confirmLabel="Approve event"
        reasonLabel="Comment for the organizer"
        required={false}
        loading={decisions.submitting}
        onClose={() => setDecision(null)}
        onConfirm={(t) => void decide('approve', t)}
      />
      <ReasonDialog
        open={decision === 'changes'}
        title={`Request changes to ${event.title}?`}
        body="The organizer sees your comments and can resubmit."
        confirmLabel="Request changes"
        reasonLabel="Comments for the organizer"
        required={config?.requireCommentsOnChangesRequested ?? true}
        loading={decisions.submitting}
        onClose={() => setDecision(null)}
        onConfirm={(t) => void decide('changes', t)}
      />
      <ReasonDialog
        open={decision === 'reject'}
        title={`Reject ${event.title}?`}
        body="This is final for this submission. The organizer is told why."
        confirmLabel="Reject event"
        danger
        reasonLabel="Reason shown to the organizer"
        required={config?.requireCommentsOnRejection ?? true}
        loading={decisions.submitting}
        onClose={() => setDecision(null)}
        onConfirm={(t) => void decide('reject', t)}
      />
    </ModuleFrame>
  );
}

function Overview({ event, timelineReviewer, submissions, actionRow }: { event: AdminEventDetail; timelineReviewer?: string | null; submissions?: number; actionRow: React.ReactNode }) {
  const router = useRouter();
  return (
    <Card>
      <CardHeader title="Event details" actions={actionRow} />
      {event.status === 'REJECTED' && event.rejectionReason ? <Banner tone="error">Rejected: {event.rejectionReason}</Banner> : null}
      <KeyValue
        columns
        items={[
          {
            label: 'Organization',
            value: event.organizationId ? (
              <Button variant="text" size="sm" onClick={() => router.push(`/org/${event.organizationId}`)}>
                {event.organizerName}
              </Button>
            ) : (
              event.organizerName
            ),
          },
          { label: 'Category', value: event.category?.name ?? '-' },
          { label: 'Date', value: formatDateTime(event.eventDateTime) },
          { label: 'Venue', value: event.locationName || '-' },
          { label: 'Location', value: [event.location?.city ?? event.cityName, event.location?.province].filter(Boolean).join(', ') || '-' },
          { label: 'Capacity', value: event.totalCapacity ? formatNumber(event.totalCapacity) : '-' },
          { label: 'Refund policy', value: event.refundPolicy || '-' },
          { label: 'Reviewer', value: timelineReviewer ?? 'Unassigned' },
          { label: 'Submissions', value: submissions ?? '-' },
        ]}
      />
      <p className="m3-muted">{event.description}</p>
    </Card>
  );
}

function Tiers({ event }: { event: AdminEventDetail }) {
  const tiers = event.ticketTiers ?? [];
  return (
    <>
      <Card>
        <CardHeader title="Ticket tiers" subtitle="Read only. Organizers manage tiers in their own app." />
        <DataTable
          caption="Ticket tiers"
          rows={tiers}
          getRowId={(t) => t.id}
          empty={<EmptyState title="No ticket tiers yet." description="The organizer must publish one before approval." />}
          columns={[
            { id: 'tier', header: 'Tier', rowHeader: true, cell: (t) => <><b>{t.name}</b>{t.earlyBirdPrice != null ? <> <StatusPill tone="neutral">Early bird</StatusPill></> : null}</> },
            { id: 'price', header: 'Price', align: 'end', cell: (t) => money(t.price) },
            { id: 'sold', header: 'Sold', cell: (t) => <><LinearProgress value={percent(t.soldQuantity, t.quantity)} label={`${t.name} sold`} /><span className="m3-muted">{formatNumber(t.soldQuantity)} / {formatNumber(t.quantity)}</span></> },
            { id: 'status', header: 'Status', cell: (t) => <StatusPill status={t.isActive ? (t.isHidden ? 'HIDDEN' : 'ACTIVE') : 'INACTIVE'} /> },
          ]}
        />
      </Card>
      <Card>
        <CardHeader title="Refund policy" />
        <p>{event.refundPolicy || 'No refund policy set.'}</p>
      </Card>
    </>
  );
}

function Approval({ event, actionRow, canDecide, pending }: { event: AdminEventDetail; actionRow: React.ReactNode; canDecide: boolean; pending: boolean }) {
  const { timeline, loading, error, refetch } = useEventApprovalTimeline(event.id);
  const { add } = useAddApprovalComment();
  const snackbar = useSnackbar();
  const entries = timeline?.timelineEvents ?? [];
  const comments = entries.filter((t) => t.comments);
  return (
    <>
      <Card>
        <CardHeader title="Approval checklist" subtitle={timeline?.isOverdue ? 'Past its review deadline' : timeline?.slaDeadline ? `Review due ${formatDateTime(timeline.slaDeadline)}` : undefined} />
        <ApprovalChecklist blockers={event.approvalBlockers} />
        {timeline?.hasActiveEscalation ? <Banner tone="warning">Escalation {humanize(timeline.escalation?.status ?? 'PENDING').toLowerCase()}.</Banner> : null}
        {actionRow}
        {!pending ? <p className="m3-muted">{canDecide ? 'Only events waiting for approval can be decided.' : needText('decide')}</p> : null}
      </Card>
      <Card>
        <CardHeader title="Approval timeline" />
        {error && !timeline ? <ErrorState error={error} onRetry={refetch} /> : loading && !timeline ? <Skeleton /> : entries.length === 0 ? <p className="m3-muted">No approval activity recorded yet.</p> : (
          <Timeline label="Approval timeline" items={entries.map((t) => ({ id: t.id, title: humanize(t.action), time: formatDateTime(t.timestamp), detail: [t.actorName, t.actorRole && humanize(t.actorRole)].filter(Boolean).join(' · ') }))} />
        )}
      </Card>
      <Card>
        <CardHeader title="Reviewer comments" />
        <CommentThread
          label="Reviewer comments"
          comments={comments.map((c) => ({ id: c.id, author: c.actorName, time: formatDateTime(c.timestamp), body: c.comments }))}
          emptyText="No comments yet."
          disabled={!canDecide}
          onSubmit={
            canDecide
              ? async (text) => {
                  const res = await add(event.id, text);
                  snackbar.show({ message: res.success ? 'Comment posted' : (res.message ?? 'Could not post the comment'), tone: res.success ? 'neutral' : 'error' });
                }
              : undefined
          }
        />
      </Card>
    </>
  );
}

function Money({ eventId, financeOk }: { eventId: string; financeOk: boolean }) {
  const router = useRouter();
  const { escrow } = useEventEscrow(eventId, !financeOk);
  const { payouts } = useEventPayouts(eventId, !financeOk);
  const { refunds, total } = useEventRefunds(eventId, !financeOk);
  if (!financeOk) return <Card><p className="m3-muted">Payouts and refunds are visible to roles with the Finance area.</p></Card>;
  return (
    <>
      {escrow ? (
        <Card>
          <CardHeader
            title="Escrow account"
            subtitle={`${escrow.accountNumber} · ${humanize(escrow.status)}`}
            actions={<Button variant="tonal" size="sm" onClick={() => router.push(`/finance/escrow?event=${eventId}`)}>Open account</Button>}
          />
          <KeyValue
            columns
            items={[
              { label: 'Available', value: money(escrow.currentBalance) },
              { label: 'Deposits', value: money(escrow.totalDeposits) },
              { label: 'Paid out', value: money(escrow.totalWithdrawals) },
              { label: 'Commission', value: money(escrow.totalCommissions) },
              { label: 'Refunded', value: money(escrow.totalRefunds) },
              { label: 'Reserved for payouts', value: escrow.pendingWithdrawals != null ? money(escrow.pendingWithdrawals) : '-' },
            ]}
          />
        </Card>
      ) : (
        <Card><p className="m3-muted">No escrow account yet. One is opened with the first sale.</p></Card>
      )}
      <Card>
        <CardHeader title="Commission" subtitle="Platform commission on this event's sales" />
        <EventCommission eventId={eventId} />
      </Card>
      <Card>
        <CardHeader title="Payout requests" subtitle={`${payouts.length} for this event`} />
        {payouts.length === 0 ? <p className="m3-muted">None</p> : (
          <ul>
            {payouts.map((p) => (
              <li key={p.id}>
                {p.requestId} · {money(p.requestedAmount)} · <StatusPill status={p.status} />
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card>
        <CardHeader title="Refunds and chargebacks" subtitle={total ? `${total} refund requests` : undefined} />
        {refunds.length === 0 ? <p className="m3-muted">None</p> : (
          <ul>
            {refunds.map((r) => (
              <li key={r.id}>
                {r.requestId} · {money(r.refundAmount)} · <StatusPill status={r.status} />
              </li>
            ))}
          </ul>
        )}
        <EventChargebacks eventId={eventId} />
      </Card>
    </>
  );
}

function Activity({ eventId }: { eventId: string }) {
  const { timeline, loading, error, refetch } = useEventApprovalTimeline(eventId);
  const entries = [...(timeline?.timelineEvents ?? [])].sort((a, b) => b.timestamp.localeCompare(a.timestamp));
  return (
    <Card>
      <CardHeader title="Activity timeline" subtitle="Everything that happened to this event, newest first" />
      {error && !timeline ? <ErrorState error={error} onRetry={refetch} /> : loading && !timeline ? <Skeleton /> : entries.length === 0 ? <p className="m3-muted">No activity recorded yet.</p> : (
        <Timeline label="Activity timeline" items={entries.map((t) => ({ id: t.id, title: humanize(t.action), time: formatDateTime(t.timestamp), detail: [t.actorName, t.description].filter(Boolean).join(' · ') }))} />
      )}
    </Card>
  );
}
