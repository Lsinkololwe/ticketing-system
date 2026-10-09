'use client';

import { Banner, Button, Card, CardHeader, CircularProgress, KeyValue, StatusPill } from '@pml.tickets/shared/components/m3';
import { formatDateTime } from '@/lib/bookings/format';
import type { ApprovalInfo } from './types';
import { Status } from '@/components/console/Status';
import { LinkBtn } from '@/components/console/LinkBtn';
import { BLOCKER_TAB, BLOCKER_TEXT, TAB_LABELS, blockers as computeBlockers, checklist, type Blocker, type EditorForm, type TabId } from './model';

export interface PublishTabProps {
  form: EditorForm;
  /** Null while the event has not been saved yet. */
  eventId: string | null;
  status: string | null;
  rejectionReason: string | null;
  approval: ApprovalInfo | null;
  reviewHours?: number;
  onCancelSchedule?: () => void;
  dirty: boolean;
  requiresApproval?: boolean;
  onGoToTab: (tab: TabId) => void;
  onSubmit: () => void;
  onPublish: () => void;
}

function historyOf(a: ApprovalInfo | null): Array<{ label: string; at: string }> {
  if (!a) return [];
  const rows: Array<{ label: string; at: string | null }> = [
    { label: 'Submitted for approval', at: a.submittedAt },
    { label: 'Approved', at: a.approvedAt },
    { label: 'Changes requested or rejected', at: a.rejectedAt },
  ];
  return rows.filter((r): r is { label: string; at: string } => Boolean(r.at)).sort((x, y) => x.at.localeCompare(y.at));
}

const ALL_BLOCKERS: Blocker[] = ['NO_PUBLISHED_TIER', 'NO_LOCATION', 'NO_CAPACITY'];

export function PublishTab({ form, eventId, status, rejectionReason, approval, reviewHours, onCancelSchedule, dirty, requiresApproval = true, onGoToTab, onSubmit, onPublish }: PublishTabProps) {
  const items = checklist(form);
  const done = items.filter((i) => i.ok).length;
  const pct = Math.round((done / items.length) * 100);
  const bl = computeBlockers(form);
  const canSubmit = !!eventId && !dirty && (status === 'DRAFT' || status === 'CHANGES_REQUESTED');
  const canPublish = !!eventId && !dirty && (status === 'APPROVED' || (!requiresApproval && status === 'DRAFT'));
  const why = !eventId ? 'Save the draft first.' : dirty ? 'Save your changes first.' : null;

  return (
    <div className="m3-stack">
      <Card>
        <div className="m3-row">
          <CircularProgress value={pct} label="Setup progress" showValue />
          <div>
            <h3 className="m3-card__title">Publishing checklist</h3>
            <span className="m3-muted">
              {done} of {items.length} complete
            </span>
          </div>
          {status ? <Status status={status} /> : <StatusPill>Not saved</StatusPill>}
        </div>
        <ul className="m3-checklist">
          {items.map((i) => (
            <li key={i.message} data-done={i.ok ? 'true' : undefined}>
              <span className="m3-checklist__mark" aria-hidden="true">
                {i.ok ? '✓' : '!'}
              </span>
              <span>
                {i.message}
                {i.blocker ? ' · Approval blocker' : ''}
                <span className="m3-sr-only">{i.ok ? ' (complete)' : ' (incomplete)'}</span>
              </span>
              {i.ok ? null : (
                <Button size="sm" variant="text" onClick={() => onGoToTab(i.tab)} aria-label={`Fix: ${i.message} (${TAB_LABELS[i.tab]})`}>
                  Fix
                </Button>
              )}
            </li>
          ))}
        </ul>
      </Card>

      <Card>
        <CardHeader title="Approval" subtitle="This organization requires platform approval before an event goes live." actions={status ? <Status status={status} /> : null} />
        {status === 'PENDING_APPROVAL' ? <Banner tone="info">With the platform reviewer. Editing is paused until they respond.</Banner> : null}
        {status === 'CHANGES_REQUESTED' ? (
          <Banner tone="warning" title="Changes requested.">
            Fix the points in the reviewer comments, then resubmit.
          </Banner>
        ) : null}
        {status === 'REJECTED' ? (
          <Banner tone="error" title="Rejected.">
            See the reviewer comment. Duplicate the event to submit a corrected one.
          </Banner>
        ) : null}
        {status === 'APPROVED' ? (
          <Banner tone="success" title="Approved.">
            You can publish whenever you are ready.
          </Banner>
        ) : null}
        <h4 className="m3-label">Approval blockers</h4>
        <p className="m3-muted">{bl.length ? 'Admins cannot approve until these are fixed.' : 'No approval blockers.'}</p>
        <ul className="m3-checklist" aria-label="Approval blockers">
          {ALL_BLOCKERS.map((k) => {
            const open = bl.includes(k);
            return (
              <li key={k} data-done={open ? undefined : 'true'}>
                <span className="m3-checklist__mark" aria-hidden="true">
                  {open ? '!' : '✓'}
                </span>
                <span>{BLOCKER_TEXT[k]}</span>
                {open ? (
                  <Button size="sm" variant="text" onClick={() => onGoToTab(BLOCKER_TAB[k])}>
                    Fix
                  </Button>
                ) : null}
              </li>
            );
          })}
        </ul>
        <h4 className="m3-label">Reviewer comments</h4>
        {rejectionReason ? (
          <KeyValue items={[{ label: 'Latest reviewer comment', value: rejectionReason }]} />
        ) : (
          <p className="m3-muted">No comments from reviewers.</p>
        )}
        <h4 className="m3-label">Approval history</h4>
        {historyOf(approval).length ? (
          <ol className="m3-list" aria-label="Approval history">
            {historyOf(approval).map((h) => (
              <li key={h.label} className="m3-list__item">
                <b>{h.label}</b> <span className="m3-muted">{formatDateTime(h.at)}</span>
              </li>
            ))}
          </ol>
        ) : (
          <p className="m3-muted">Not submitted yet.</p>
        )}
        {status === 'PENDING_APPROVAL' && approval?.deadline ? (
          <p className="m3-muted">Review clock: a decision is expected by {formatDateTime(approval.deadline)}{reviewHours != null ? ` (typical review time ${reviewHours} hours)` : ''}.</p>
        ) : null}
        {approval?.publishScheduled && approval.publishAt ? (
          <Banner tone="info" title="Scheduled to go live." actions={onCancelSchedule ? <Button size="sm" variant="tonal" onClick={onCancelSchedule}>Cancel schedule</Button> : null}>
            This event publishes itself on {formatDateTime(approval.publishAt)}.
          </Banner>
        ) : null}
      </Card>

      <Card>
        <CardHeader title="Event controls" subtitle="Changes that affect buyers and reviewers." />
        <div className="m3-row">
          <Button variant="filled" disabled={!canSubmit} onClick={onSubmit}>
            {status === 'CHANGES_REQUESTED' ? 'Resubmit for approval' : 'Submit for approval'}
          </Button>
          <Button variant="accent" disabled={!canPublish} onClick={onPublish}>
            Publish
          </Button>
          {eventId ? (
            <LinkBtn href={`/events/${eventId}`} variant="outlined">
              Open event
            </LinkBtn>
          ) : null}
        </div>
        {why ? <p className="m3-muted">{why}</p> : null}
        {!why && !canSubmit && !canPublish ? <p className="m3-muted">Nothing to submit or publish while the event is {status ? status.toLowerCase().replace(/_/g, ' ') : 'unsaved'}.</p> : null}
      </Card>
    </div>
  );
}
