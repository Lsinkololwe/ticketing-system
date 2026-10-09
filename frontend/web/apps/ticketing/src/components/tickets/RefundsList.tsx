'use client';

import { Button, StatusPill } from '@pml.tickets/shared/components/m3';
import type { RefundRequestRow } from '@pml.tickets/shared';
import { money, shortDate } from '@/lib/format';

const STEPS = ['PENDING', 'APPROVED', 'PROCESSING', 'COMPLETED'] as const;
const LABEL: Record<string, string> = { PENDING: 'Pending approval', APPROVED: 'Approved', PROCESSING: 'Processing', COMPLETED: 'Completed' };

/** Refund requests with a progress tracker (Pending, Approved, Processing, Completed). */
export function RefundCard({ refund: r, eventTitle, onCancel }: { refund: RefundRequestRow; eventTitle: string | null; onCancel?: () => void }) {
  const cur = STEPS.indexOf(r.status as (typeof STEPS)[number]);
  const bad = ['REJECTED', 'CANCELLED', 'FAILED'].includes(r.status);
  return (
    <article className="m3-panel buyer-bk" aria-label={`Refund ${r.requestId}`}>
      <header className="buyer-bk__head">
        <div>
          <span className="m3-muted m3-mono">{r.requestId}</span>
          <h3 className="m3-card__title">{eventTitle ?? 'Refund request'}</h3>
          <div className="m3-muted">
            Ticket {r.ticketNumber}
            {r.requestedAt ? ` · requested ${shortDate(r.requestedAt)}` : ''}
          </div>
        </div>
        <div className="buyer-bk__total">
          <b className="m3-num">{money(r.refundAmount)}</b>
          {r.refundPercentage !== null ? <span className="m3-muted">{Math.round(r.refundPercentage)}% refund</span> : null}
        </div>
      </header>
      {bad ? (
        <p className="buyer-note" data-tone={r.status === 'CANCELLED' ? undefined : 'bad'} role="status">
          This refund request was <b>{r.status.toLowerCase()}</b>.{r.rejectionReason ? ` ${r.rejectionReason}` : ''}
        </p>
      ) : (
        <ol className="buyer-trk" aria-label="Refund progress">
          {STEPS.map((s, i) => (
            <li key={s} data-state={i < cur || r.status === 'COMPLETED' ? 'done' : i === cur ? 'on' : undefined} aria-current={i === cur ? 'step' : undefined}>
              <i aria-hidden="true" />
              {LABEL[s]}
            </li>
          ))}
        </ol>
      )}
      <footer className="buyer-bk__foot">
        <span className="m3-muted">
          Reason: {r.reason}
          {r.status === 'PENDING' ? ' · a person reviews every request' : ''}
        </span>
        <span className="m3-row">
          {r.status === 'PENDING' && onCancel ? <Button size="sm" onClick={onCancel}>Cancel request</Button> : null}
          <StatusPill status={r.status} />
        </span>
      </footer>
    </article>
  );
}
