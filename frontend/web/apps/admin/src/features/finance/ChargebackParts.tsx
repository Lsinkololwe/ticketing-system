'use client';

import { useState, type ReactNode } from 'react';
import { Banner, Button, KeyValue, SideSheet, StatusPill } from '@pml.tickets/shared/components/m3';
import { useChargebackOps, type ChargebackRow } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { ReasonDialog, useStaff } from '@/components/console';
import { formatDateTime, humanize, money } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { asNumber, FormDialog, Mono, useOpRunner } from './shared';
import { CHARGEBACK_REASON_LABELS, CHARGEBACK_STATUS_LABELS, enumValues } from '@/lib/enumLabels';

export const CB_REASONS = enumValues(CHARGEBACK_REASON_LABELS);
export const CB_STATUSES = enumValues(CHARGEBACK_STATUS_LABELS);
export const CB_FUNDS = ['ORGANIZER_ESCROW', 'ORGANIZER_FUTURE', 'PLATFORM_RESERVE', 'WRITE_OFF'];

export type ChargebackAction = 'review' | 'accept' | 'dispute' | 'outcome' | 'record';

/** Deadline copy: auto-accepted once past, a warning inside 24 h, the date otherwise. */
export function Deadline({ c, now = new Date() }: { c: Pick<ChargebackRow, 'status' | 'responseDeadline'>; now?: Date }) {
  if (['WON', 'LOST', 'ACCEPTED'].includes(c.status)) return null;
  const hours = (new Date(c.responseDeadline).getTime() - now.getTime()) / 36e5;
  if (hours < 0) return <span className="m3-pill" data-tone="error">Auto-accepted at deadline</span>;
  if (hours <= 24) return <span className="m3-pill" data-tone="error">Deadline in {Math.max(1, Math.round(hours))} h</span>;
  return <span className="m3-muted">{formatDateTime(c.responseDeadline)}</span>;
}

/** Chargeback lifecycle dialogs shared by the sheet and the Record button. */
export function useChargebackActions(onDone: () => void): { start: (a: ChargebackAction, c?: ChargebackRow) => void; dialogs: ReactNode } {
  const run = useOpRunner();
  const ops = useChargebackOps();
  const [active, setActive] = useState<{ a: ChargebackAction; c?: ChargebackRow } | null>(null);
  const close = () => setActive(null);
  const a = active?.a;
  const c = active?.c;
  const none = () => Promise.resolve({ success: false, message: null, errorCode: null });

  const dialogs = (
    <>
      <ReasonDialog
        open={a === 'review'}
        onClose={close}
        title={c ? `Start review of ${c.chargebackId}?` : ''}
        body="Moves the chargeback to under review."
        confirmLabel="Start review"
        required={false}
        reasonLabel="Note"
        loading={ops.submitting}
        onConfirm={(note) => {
          if (c) void run(ops.startReview(c.id, note || undefined), `${c.chargebackId} is under review`, onDone).then(close);
        }}
      />
      <ReasonDialog
        open={a === 'accept'}
        onClose={close}
        title={c ? `Accept chargeback ${c.chargebackId}` : ''}
        body="Accepting concedes the dispute. The funds are taken from the fund source on record."
        confirmLabel="Accept chargeback"
        danger
        required={false}
        reasonLabel="Note"
        loading={ops.submitting}
        onConfirm={(note) => {
          if (c) void run(ops.acceptChargeback(c.id, note || undefined), `${c.chargebackId} accepted`, onDone).then(close);
        }}
      />
      <ReasonDialog
        open={a === 'dispute'}
        onClose={close}
        title={c ? `Dispute chargeback ${c.chargebackId}` : ''}
        body="Evidence is sent to the provider before the deadline."
        confirmLabel="Submit dispute"
        reasonLabel="Evidence summary"
        loading={ops.submitting}
        onConfirm={(notes) => {
          if (c) void run(ops.disputeChargeback(c.id, notes), `${c.chargebackId} disputed`, onDone).then(close);
        }}
      />
      <FormDialog
        open={a === 'outcome'}
        title={c ? `Record outcome for ${c.chargebackId}` : ''}
        confirmLabel="Record outcome"
        successMessage={c ? `Outcome recorded for ${c.chargebackId}` : 'Outcome recorded'}
        fields={[
          { id: 'out', label: 'Outcome', type: 'select', value: 'WON', options: [{ value: 'WON', label: 'Won' }, { value: 'LOST', label: 'Lost' }] },
          { id: 'note', label: 'Note (optional)', type: 'textarea' },
        ]}
        onClose={close}
        onDone={onDone}
        onSubmit={(v) => (c ? ops.recordOutcome(c.id, v.out === 'WON', v.note || undefined) : none())}
      />
      <FormDialog
        open={a === 'record'}
        title="Record chargeback"
        body="Logged when the provider notifies us of a dispute."
        confirmLabel="Record"
        successMessage="Chargeback recorded"
        fields={[
          { id: 'chargebackId', label: 'Provider chargeback reference', required: true },
          { id: 'originalTransactionId', label: 'Original transaction ID', required: true },
          { id: 'ticketId', label: 'Ticket ID', required: true },
          { id: 'eventId', label: 'Event ID', required: true },
          { id: 'organizerId', label: 'Organizer ID', required: true },
          { id: 'customerId', label: 'Buyer ID', required: true },
          { id: 'originalAmount', label: 'Original amount (K)', type: 'number', min: 1, required: true },
          { id: 'chargebackAmount', label: 'Chargeback amount (K)', type: 'number', min: 1, required: true },
          { id: 'chargebackFee', label: 'Provider fee (K)', type: 'number', min: 0, value: '0', required: true },
          { id: 'reason', label: 'Reason', type: 'select', value: 'FRAUD', options: CB_REASONS.map((r) => ({ value: r, label: humanize(r) })) },
          { id: 'deadline', label: 'Provider deadline', type: 'date', required: true, value: new Date(Date.now() + 7 * 864e5).toISOString().slice(0, 10) },
        ]}
        onClose={close}
        onDone={onDone}
        onSubmit={(v) =>
          ops.receiveChargeback({
            chargebackId: v.chargebackId,
            originalTransactionId: v.originalTransactionId,
            ticketId: v.ticketId,
            eventId: v.eventId,
            organizerId: v.organizerId,
            customerId: v.customerId,
            originalAmount: Number(v.originalAmount),
            chargebackAmount: Number(v.chargebackAmount),
            chargebackFee: Number(v.chargebackFee),
            currency: 'ZMW',
            reason: v.reason,
            responseDeadline: new Date(`${v.deadline}T06:00:00`).toISOString(),
          })
        }
      />
    </>
  );
  return { start: (act, row) => setActive({ a: act, c: row }), dialogs };
}

/** Chargeback detail with lifecycle actions. Recovery updates have no backend operation yet. */
export function ChargebackSheet({ chargeback: c, onClose, onAction }: { chargeback: ChargebackRow | null; onClose: () => void; onAction: (a: ChargebackAction, c: ChargebackRow) => void }) {
  const staff = useStaff();
  if (!c) return null;
  const decide = staff.can('payoutDecide');
  const s = c.status;
  const act = (a: ChargebackAction, label: string, variant: 'filled' | 'tonal') => (
    <Button variant={variant} disabled={!decide} onClick={() => onAction(a, c)}>
      {label}
    </Button>
  );
  return (
    <SideSheet
      open
      onClose={onClose}
      title={`Chargeback ${c.chargebackId}`}
      actions={
        <>
          {s === 'RECEIVED' ? act('review', 'Start review', 'filled') : null}
          {s === 'UNDER_REVIEW' ? act('accept', 'Accept…', 'filled') : null}
          {s === 'UNDER_REVIEW' ? act('dispute', 'Dispute…', 'tonal') : null}
          {s === 'DISPUTED' ? act('outcome', 'Record outcome…', 'filled') : null}
          {['ACCEPTED', 'LOST'].includes(s) && !['RECOVERED', 'WRITTEN_OFF'].includes(c.recoveryStatus) ? (
            <Button variant="tonal" disabled title="Needs updateChargebackRecovery on the backend">
              Update recovery…
            </Button>
          ) : null}
        </>
      }
    >
      <div className="m3-stack">
        <div className="m3-row">
          <StatusPill status={s} />
          <StatusPill status={c.recoveryStatus}>Recovery: {humanize(c.recoveryStatus)}</StatusPill>
        </div>
        {!decide ? <p className="m3-muted">{needText('payoutDecide')}</p> : null}
        <Deadline c={c} />
        <KeyValue columns
          items={[
            { label: 'Payment', value: <Mono>{c.originalTransactionId}</Mono> },
            { label: 'Ticket', value: <Mono>{c.ticketId}</Mono> },
            { label: 'Buyer', value: <Mono>{c.customerId}</Mono> },
            { label: 'Event', value: <Mono>{c.eventId}</Mono> },
            { label: 'Amount', value: <Mono>{money(asNumber(c.chargebackAmount))}</Mono> },
            { label: 'Provider fee', value: <Mono>{money(asNumber(c.chargebackFee))}</Mono> },
            { label: 'Reason', value: humanize(c.reason) },
            { label: 'Fund source', value: humanize(c.fundSource) },
            { label: 'Recovered', value: <Mono>{money(asNumber(c.recoveredAmount))}</Mono> },
            { label: 'Received', value: formatDateTime(c.receivedAt) },
            { label: 'Provider deadline', value: formatDateTime(c.responseDeadline) },
          ]}
        />
        {c.evidenceSubmitted ? <Banner tone="info" title="Evidence submitted">{c.evidenceSubmitted}</Banner> : null}
      </div>
    </SideSheet>
  );
}
