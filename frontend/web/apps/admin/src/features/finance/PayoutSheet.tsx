'use client';

import { Banner, Button, KeyValue, ReadinessList, SideSheet, Skeleton, StatusPill } from '@pml.tickets/shared/components/m3';
import { useEscrowOpsDetail, usePayoutOpsDetail, type PayoutOpsRow } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { useStaff } from '@/components/console';
import { formatDate, formatDateTime, humanize, money } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { destinationOf, eventOf, type PayoutAction } from './usePayoutActions';
import { asNumber, Mono } from './shared';

export interface PayoutSheetProps {
  /** The list row the sheet was opened from; shown while the full record loads. */
  payout: PayoutOpsRow | null;
  onClose: () => void;
  onAction: (action: PayoutAction, payout: PayoutOpsRow) => void;
}

/** Right-hand payout detail with the approval checks and every status-dependent action. */
export function PayoutSheet({ payout: seed, onClose, onAction }: PayoutSheetProps) {
  const staff = useStaff();
  const detail = usePayoutOpsDetail(seed?.id ?? null);
  const p = detail.payout ?? seed;
  const escrow = useEscrowOpsDetail(p?.escrowAccountId ?? null);
  if (!seed || !p) return null;

  const st = p.status;
  const decide = staff.can('payoutDecide');
  const act = (a: PayoutAction, label: string, variant: 'filled' | 'tonal' | 'text', danger?: boolean) => (
    <Button key={a} variant={variant} danger={danger} disabled={!decide} onClick={() => onAction(a, p)}>
      {label}
    </Button>
  );
  const open = !['COMPLETED', 'REJECTED', 'CANCELLED'].includes(st);
  const balance = escrow.account ? asNumber(escrow.account.currentBalance) : null;

  const checks: Array<[boolean, string]> = [];
  if (st === 'PENDING' || st === 'APPROVED') {
    checks.push([p.requestedById !== staff.id, 'Approver differs from requester (dual control)']);
    if (p.bankAccount) checks.push([p.bankAccount.isVerified, 'Verified destination account']);
    if (escrow.account) {
      checks.push([asNumber(escrow.account.currentBalance) >= asNumber(p.requestedAmount), 'Escrow balance covers the amount']);
      checks.push([!escrow.account.payoutEligibleAt || new Date(escrow.account.payoutEligibleAt) <= new Date() || st !== 'PENDING', 'Hold period elapsed']);
    }
  }

  const footer = (
    <>
      {st === 'PENDING' ? act('approve', 'Approve', 'filled') : null}
      {st === 'APPROVED' ? act('process', 'Process payout', 'filled') : null}
      {st === 'PROCESSING' ? act('complete', 'Complete…', 'filled') : null}
      {st === 'FAILED' ? act('retry', 'Retry', 'filled') : null}
      {st === 'PROCESSING' && p.lastError ? act('resume', 'Resume', 'tonal') : null}
      {['PENDING', 'APPROVED', 'PROCESSING'].includes(st) ? (
        act('hold', 'Hold…', 'tonal')
      ) : null}
      {st === 'ON_HOLD' ? act('release', 'Release hold', 'filled') : null}
      {['PENDING', 'APPROVED'].includes(st) ? act('reject', 'Reject…', 'tonal', true) : null}
      {open ? act('escalate', 'Escalate…', 'text') : null}
      {open ? act('mark', 'Mark for review', 'text') : null}
      {open && (p.lastError || p.issueType) ? act('resolve', 'Resolve issue…', 'text') : null}
    </>
  );

  return (
    <SideSheet open onClose={onClose} title={`Payout ${p.requestId}`} subtitle={eventOf(p)} actions={footer}>
      <div className="m3-stack">
        <div className="m3-row">
          <StatusPill status={st} />
          {p.reviewStatus && p.reviewStatus !== 'NONE' ? <StatusPill status={p.reviewStatus} /> : null}
        </div>
        {!decide ? <p className="m3-muted">{needText('payoutDecide')}</p> : null}
        {detail.error && !detail.payout ? <Banner tone="warning">Showing the list copy. The full record could not be loaded.</Banner> : null}
        {p.rejectionReason ? <Banner tone="error">Rejected: {p.rejectionReason}</Banner> : null}
        {p.lastError ? (
          <Banner tone="error">
            Last error: {p.lastError}
            {p.issueType ? ` · ${humanize(p.issueType)}` : ''}
          </Banner>
        ) : null}
        {p.isStuck ? <Banner tone="warning">Stuck{p.stuckReason ? `: ${p.stuckReason}` : ''}</Banner> : null}
        <KeyValue columns
          items={[
            { label: 'Event', value: eventOf(p) },
            { label: 'Organization', value: p.organizerName ?? '—' },
            { label: 'Requested amount', value: <Mono>{money(asNumber(p.requestedAmount))}</Mono> },
            { label: 'Tax', value: <Mono>{money(asNumber(p.taxAmount))}</Mono> },
            { label: 'Settled amount', value: <Mono>{money(asNumber(p.settledAmount))}</Mono> },
            { label: 'Method', value: humanize(p.payoutMethod) },
            { label: 'Destination', value: <Mono>{destinationOf(p)}</Mono> },
            { label: 'Requested', value: formatDateTime(p.requestedAt) },
            { label: 'Approved by', value: p.approvedBy ?? '—' },
            { label: 'Payment reference', value: <Mono>{p.paymentReference ?? '—'}</Mono> },
            { label: 'Expected payout date', value: formatDate(p.expectedPayoutDate) },
            { label: 'Retry count', value: p.retryCount ?? 0 },
            {
              label: 'Escrow',
              value: escrow.loading && !escrow.account ? (
                <Skeleton width="8em" />
              ) : escrow.account ? (
                <>
                  <Mono>{escrow.account.accountNumber}</Mono> · balance {money(balance)}
                </>
              ) : (
                '—'
              ),
            },
            { label: 'Provider reference', value: <Mono>{p.externalTransactionId ?? '—'}</Mono> },
          ]}
        />
        {checks.length > 0 ? (
          <section aria-label="Approval checks">
            <h3 className="m3-card__title">Approval checks</h3>
            <ReadinessList label="Approval checks" items={checks.map(([done, label], i) => ({ id: String(i), label, done }))} />
          </section>
        ) : null}
        {p.notes || p.reviewNotes || p.resolutionNotes ? (
          <section aria-label="Notes">
            <h3 className="m3-card__title">Notes</h3>
            {[p.notes, p.reviewNotes, p.resolutionNotes].filter(Boolean).map((n, i) => (
              <p key={i} className="m3-muted">
                {n}
              </p>
            ))}
          </section>
        ) : null}
      </div>
    </SideSheet>
  );
}
