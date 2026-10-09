'use client';

import { Banner, Button, KeyValue, SideSheet, StatusPill } from '@pml.tickets/shared/components/m3';
import { useRefundOpsDetail, type RefundOpsRow } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { useStaff } from '@/components/console';
import { formatDateTime, humanize, money } from '@/lib/format';
import { needText } from '@/lib/permissions';
import type { RefundAction } from './useRefundActions';
import { asNumber, Mono } from './shared';

/** Refund detail with the refund quote (policy, percentage, amounts) and the status-dependent actions. */
export function RefundSheet({ refund: seed, onClose, onAction }: { refund: RefundOpsRow | null; onClose: () => void; onAction: (a: RefundAction, r: RefundOpsRow) => void }) {
  const staff = useStaff();
  const detail = useRefundOpsDetail(seed?.id ?? null);
  const r = detail.refund ?? seed;
  if (!seed || !r) return null;
  const decide = staff.can('payoutDecide');
  const pct = r.refundPercentage ?? null;
  const act = (a: RefundAction, label: string, variant: 'filled' | 'tonal', danger?: boolean) => (
    <Button variant={variant} danger={danger} disabled={!decide} onClick={() => onAction(a, r)}>
      {label}
    </Button>
  );

  return (
    <SideSheet
      open
      onClose={onClose}
      title={`Refund ${r.requestId}`}
      subtitle={`Ticket ${r.ticketNumber}`}
      actions={
        <>
          {r.status === 'PENDING' ? act('approve', 'Approve', 'filled') : null}
          {r.status === 'PENDING' ? act('reject', 'Reject…', 'tonal', true) : null}
          {r.status === 'APPROVED' ? act('process', 'Process', 'filled') : null}
          {r.status === 'FAILED' ? act('process', 'Process again', 'filled') : null}
        </>
      }
    >
      <div className="m3-stack">
        <div className="m3-row">
          <StatusPill status={r.status} />
          <StatusPill status={r.requestType} />
        </div>
        {!decide ? <p className="m3-muted">{needText('payoutDecide')}</p> : null}
        {detail.error && !detail.refund ? <Banner tone="warning">Showing the list copy. The full record could not be loaded.</Banner> : null}
        <KeyValue columns
          items={[
            { label: 'Buyer', value: <Mono>{r.buyerId}</Mono> },
            { label: 'Ticket', value: <Mono>{r.ticketNumber}</Mono> },
            { label: 'Event', value: <Mono>{r.eventId}</Mono> },
            { label: 'Reason', value: r.reason },
            { label: 'Requested', value: formatDateTime(r.requestedAt) },
          ]}
        />
        <section aria-label="Refund quote">
          <h3 className="m3-card__title">Refund quote</h3>
          <KeyValue columns
            items={[
              { label: 'Original amount', value: <Mono>{r.originalTicketPrice == null ? '—' : money(asNumber(r.originalTicketPrice))}</Mono> },
              { label: 'Days before event', value: r.daysBeforeEvent ?? '—' },
              { label: 'Policy applied', value: humanize(r.policyApplied) },
              { label: 'Refund', value: <Mono>{`${money(asNumber(r.refundAmount))}${pct == null ? '' : ` (${pct}%)`}`}</Mono> },
              { label: 'Platform retains', value: <Mono>{r.platformRetains == null ? '—' : money(asNumber(r.platformRetains))}</Mono> },
              { label: 'Processing fee', value: <Mono>{money(asNumber(r.processingFee))}</Mono> },
              { label: 'Net to buyer', value: <Mono>{r.netRefundAmount == null ? '—' : money(asNumber(r.netRefundAmount))}</Mono> },
            ]}
          />
        </section>
        {r.rejectionReason ? <Banner tone="error">Rejected: {r.rejectionReason}</Banner> : null}
        {r.reviewComments ? <Banner tone="info">Review comment: {r.reviewComments}</Banner> : null}
        {pct === 0 && r.status === 'PENDING' ? <Banner tone="warning">The policy allows no refund. Approve only as a goodwill exception.</Banner> : null}
      </div>
    </SideSheet>
  );
}
