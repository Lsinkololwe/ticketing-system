'use client';

import {
  Banner,
  Button,
  Card,
  CardHeader,
  KeyValue,
  ReadinessList,
  Timeline,
  type ReadinessItem,
} from '@pml.tickets/shared/components/m3';
import type { OrgEventDetail } from '@/lib/api/events';
import { formatEventDate } from '@/lib/format/figure';
import { Status } from '@/components/console/Status';
import { formatDateTime } from '@/lib/bookings/format';
import { actionsFor, BLOCKER_TEXT, blockersOf, type ApprovalBlocker, type RowAction } from './eventLogic';

const LIFE: Array<{ id: RowAction['id']; title: string; desc: string; off: (e: OrgEventDetail) => string | null; danger?: boolean }> = [
  { id: 'submit', title: 'Submit for approval', desc: 'Sends the event to a platform reviewer.', off: (e) => (['DRAFT', 'CHANGES_REQUESTED'].includes(e.status) ? null : `Only drafts, or events where changes were requested, can be submitted. Current status: ${e.status.toLowerCase().replace(/_/g, ' ')}.`) },
  { id: 'publish', title: 'Publish', desc: 'Puts the event on sale and starts escrow collection.', off: (e) => (e.status === 'APPROVED' ? null : e.status === 'PUBLISHED' ? 'Already live.' : 'Only approved events can be published.') },
  { id: 'unpublish', title: 'Unpublish', desc: 'Hides the event. Not possible once tickets have sold.', off: (e) => (e.status !== 'PUBLISHED' ? 'Only live events can be unpublished.' : e.soldTickets ? `This one has ${e.soldTickets} tickets sold.` : null) },
  { id: 'reschedule', title: 'Reschedule', desc: 'Move the date. Ticket holders are told.', off: (e) => (['APPROVED', 'PUBLISHED'].includes(e.status) ? null : 'Only approved or live events can be rescheduled.') },
  { id: 'cancel', title: 'Cancel event', desc: 'Stops sales, refunds every ticket and tells ticket holders. A reason is required.', danger: true, off: (e) => (['APPROVED', 'PUBLISHED'].includes(e.status) ? null : 'Only approved or live events can be cancelled.') },
  { id: 'duplicate', title: 'Duplicate', desc: 'Creates a new draft with these details and tiers.', off: () => null },
  { id: 'delete', title: 'Delete draft', desc: 'Removes the draft and its tiers.', danger: true, off: (e) => (e.status === 'DRAFT' ? null : 'Only drafts can be deleted.') },
];

export function OverviewTab({ event: e, onAction, onGoTab }: { event: OrgEventDetail; onAction: (a: RowAction['id']) => void; onGoTab: (id: string) => void }) {
  const bl = blockersOf(e);
  const items: ReadinessItem[] = [
    { id: 'tier', label: 'At least one active ticket tier', done: !bl.includes('NO_PUBLISHED_TIER'), hint: 'Add a tier.' },
    { id: 'venue', label: 'A venue', done: !bl.includes('NO_LOCATION'), hint: 'Set the venue in the editor.' },
    { id: 'cap', label: 'A capacity above zero', done: !bl.includes('NO_CAPACITY'), hint: 'Set the capacity in the editor.' },
    { id: 'desc', label: 'A description', done: e.description.trim().length > 0 },
  ];
  const done = items.filter((i) => i.done).length;
  const allowed = new Set(actionsFor(e.status).map((a) => a.id));
  const history = [
    e.createdAt && { id: 'c', title: 'Created', time: formatEventDate(e.createdAt) },
    e.submittedForApprovalAt && { id: 's', title: 'Submitted for approval', time: formatEventDate(e.submittedForApprovalAt) },
    e.approvedAt && { id: 'a', title: 'Approved', time: formatEventDate(e.approvedAt) },
    e.rejectedAt && { id: 'r', title: 'Rejected', time: formatEventDate(e.rejectedAt) },
    e.publishedAt && { id: 'p', title: 'Published', time: formatEventDate(e.publishedAt) },
  ].filter(Boolean) as Array<{ id: string; title: string; time: string }>;

  const approvalHistory = [
    { label: 'Submitted for approval', at: e.submittedForApprovalAt },
    { label: 'Approved', at: e.approvedAt },
    { label: 'Changes requested or rejected', at: e.rejectedAt },
  ]
    .filter((h): h is { label: string; at: string } => Boolean(h.at))
    .sort((a, b) => a.at.localeCompare(b.at));
  return (
    <div className="m3-stack">
      <Card>
        <CardHeader
          title="Publishing checklist"
          subtitle={`${done} of ${items.length} complete`}
          actions={<Status status={e.status} />}
        />
        <ReadinessList items={items} label="Publishing checklist" />
        {bl.length ? (
          <Button size="sm" variant="tonal" onClick={() => onGoTab('tiers')}>Fix ticket tiers</Button>
        ) : null}
      </Card>

      <Card>
        <CardHeader title="Approval" subtitle="This organization requires platform approval before an event goes live." actions={<Status status={e.status} />} />
        {e.status === 'PENDING_APPROVAL' ? <Banner tone="info">With the platform reviewer.</Banner> : null}
        {e.status === 'CHANGES_REQUESTED' ? <Banner>Changes requested. Fix the points in the reviewer comments, then resubmit.</Banner> : null}
        {e.status === 'REJECTED' ? <Banner tone="error">Rejected. See the reviewer comment. Duplicate the event to submit a corrected one.</Banner> : null}
        {e.status === 'APPROVED' ? <Banner tone="success">Approved. You can publish whenever you are ready.</Banner> : null}
        <h4>Approval blockers</h4>
        <p className="m3-muted">{bl.length ? 'Admins cannot approve until these are fixed.' : 'No approval blockers.'}</p>
        <ReadinessList
          label="Approval blockers"
          items={(['NO_PUBLISHED_TIER', 'NO_LOCATION', 'NO_CAPACITY'] as ApprovalBlocker[]).map((b) => ({ id: b, label: BLOCKER_TEXT[b], done: !bl.includes(b) }))}
        />
        <h4>Reviewer comments</h4>
        {e.rejectionReason ? (
          <blockquote data-testid="reviewer-comment">{e.rejectionReason}</blockquote>
        ) : (
          <p className="m3-muted">No comments from reviewers.</p>
        )}
        <h4>Approval history</h4>
        {approvalHistory.length ? (
          <ol className="m3-list" aria-label="Approval history">
            {approvalHistory.map((h) => (
              <li key={h.label} className="m3-list__item">
                <b>{h.label}</b> <span className="m3-muted">{formatDateTime(h.at)}</span>
              </li>
            ))}
          </ol>
        ) : (
          <p className="m3-muted">Not submitted yet.</p>
        )}
      </Card>

      <Card>
        <CardHeader title="Event controls" subtitle="Changes that affect buyers and reviewers." />
        <ul className="m3-list">
          {LIFE.map((l) => {
            const why = l.off(e);
            const offered = allowed.has(l.id) || l.id === 'submit' || l.id === 'delete';
            const disabled = Boolean(why) || !offered;
            return (
              <li key={l.id} className="m3-list__item">
                <div className="m3-list__main">
                  <b>{l.title}</b>
                  <span className="m3-list__support">{disabled && why ? why : l.desc}</span>
                </div>
                <Button size="sm" variant={l.danger ? 'outlined' : 'tonal'} danger={l.danger} disabled={disabled} onClick={() => onAction(l.id)}>
                  {l.title}
                </Button>
              </li>
            );
          })}
        </ul>
      </Card>

      <Card>
        <CardHeader title="Change history" />
        {history.length ? <Timeline label="Change history" items={history} /> : <p className="m3-muted">No changes yet.</p>}
      </Card>

      <Card>
        <CardHeader title="Details" />
        <KeyValue
          columns
          items={[
            { label: 'Starts', value: formatEventDate(e.eventDateTime) },
            { label: 'Ends', value: formatEventDate(e.endDateTime) },
            { label: 'Venue', value: e.locationName ?? '—' },
            { label: 'City', value: e.cityName ?? '—' },
            { label: 'Category', value: e.category?.name ?? '—' },
            { label: 'Refund policy', value: e.refundPolicy ?? '—' },
          ]}
        />
      </Card>
    </div>
  );
}
