'use client';

import { useStepUp } from '@/lib/useStepUp';
import { useState, type ReactNode } from 'react';
import { Button, ConfirmDialog, Dialog } from '@pml.tickets/shared/components/m3';
import { useFinanceDecisions } from '@pml.tickets/shared/api/admin/modules/finance';
import { usePayoutOps, type PayoutIssueType, type PayoutOpsRow, type PayoutResolutionType } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { usePayoutHoldActions } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { ReasonDialog, useStaff } from '@/components/console';
import { humanize, money } from '@/lib/format';
import { asNumber, FormDialog, useOpRunner } from './shared';
import { PAYOUT_ISSUE_TYPE_LABELS, PAYOUT_RESOLUTION_TYPE_LABELS, enumValues } from '@/lib/enumLabels';

export type PayoutAction = 'approve' | 'reject' | 'process' | 'complete' | 'retry' | 'resume' | 'escalate' | 'mark' | 'resolve' | 'hold' | 'release';

export const ISSUE_TYPES: PayoutIssueType[] = enumValues(PAYOUT_ISSUE_TYPE_LABELS);
export const RESOLUTIONS: PayoutResolutionType[] = enumValues(PAYOUT_RESOLUTION_TYPE_LABELS);

export const destinationOf = (p: PayoutOpsRow) => [p.bankName, p.accountNumber].filter(Boolean).join(' ') || '—';
export const eventOf = (p: PayoutOpsRow) => p.eventTitle ?? '—';

/**
 * One controller for every payout action so the table buttons and the side
 * sheet run the same dialogs. `start` opens the right dialog; `dialogs` must be
 * rendered once by the caller.
 */
export function usePayoutActions(onDone: () => void): { start: (action: PayoutAction, payout: PayoutOpsRow) => void; dialogs: ReactNode; busy: boolean } {
  const staff = useStaff();
  const run = useOpRunner();
  const { guard } = useStepUp();
  const decisions = useFinanceDecisions();
  const ops = usePayoutOps();
  const holds = usePayoutHoldActions();
  const attempt = async (fn: () => Promise<unknown>) => {
    try {
      await fn();
      return { success: true, message: null, errorCode: null };
    } catch (e) {
      return { success: false, message: (e as Error).message || null, errorCode: null };
    }
  };
  const [active, setActive] = useState<{ action: PayoutAction; payout: PayoutOpsRow } | null>(null);
  const close = () => setActive(null);
  const p = active?.payout;
  const a = active?.action;
  const done = () => {
    onDone();
  };

  const blocked: { title: string; text: ReactNode } | null =
    a === 'approve' && p
      ? p.requestedById === staff.id
        ? {
            title: 'You cannot approve this payout',
            text: (
              <>
                Payouts use dual control. <b>{p.requestId}</b> was requested by you, so a different finance or admin user must approve it.
              </>
            ),
          }
        : p.bankAccount && !p.bankAccount.isVerified
          ? {
              title: 'Destination not verified',
              text: <>{p.organizerName ?? 'This organization'} has no verified payout account. Verify one in Finance, Payout accounts first.</>,
            }
          : null
      : null;

  const dialogs = (
    <>
      <Dialog
        open={!!blocked}
        onClose={close}
        title={blocked?.title ?? ''}
        actions={
          <Button variant="filled" onClick={close}>
            Understood
          </Button>
        }
      >
        <p>{blocked?.text}</p>
      </Dialog>

      <ConfirmDialog
        open={a === 'approve' && !!p && !blocked}
        onClose={close}
        title={p ? `Approve payout ${p.requestId}?` : ''}
        description={p ? `${money(asNumber(p.requestedAmount))} to ${destinationOf(p)} for ${eventOf(p)}. You are the second approver.` : undefined}
        confirmLabel="Approve payout"
        loading={decisions.submitting}
        onConfirm={() => {
          if (!p) return;
          void run(guard(() => decisions.approvePayout(p.id)), `Payout ${p.requestId} approved`, done).then(close);
        }}
      />

      <ReasonDialog
        open={a === 'reject'}
        onClose={close}
        title={p ? `Reject payout ${p.requestId}?` : ''}
        body="The reserve is released back to the escrow account and the organizer is told why."
        confirmLabel="Reject payout"
        danger
        loading={decisions.submitting}
        onConfirm={(reason) => {
          if (!p) return;
          void run(decisions.rejectPayout(p.id, reason), `Payout ${p.requestId} rejected`, done).then(close);
        }}
      />

      <ConfirmDialog
        open={a === 'process'}
        onClose={close}
        title={p ? `Send payout ${p.requestId} for processing?` : ''}
        description={p ? `${money(asNumber(p.requestedAmount))} is sent to ${destinationOf(p)}. The bank confirms later.` : undefined}
        confirmLabel="Process"
        loading={ops.submitting}
        onConfirm={() => {
          if (!p) return;
          void run(guard(() => ops.processPayout(p.id)), `Payout ${p.requestId} sent for processing`, done).then(close);
        }}
      />

      <ConfirmDialog
        open={a === 'retry'}
        onClose={close}
        title={p ? `Retry payout ${p.requestId}?` : ''}
        description={p ? `${money(asNumber(p.requestedAmount))} is reserved again and sent for processing. Retry count becomes ${(p.retryCount ?? 0) + 1}.` : undefined}
        confirmLabel="Retry payout"
        loading={ops.submitting}
        onConfirm={() => {
          if (!p) return;
          void run(ops.retryPayout(p.id), `Payout ${p.requestId} retried`, done).then(close);
        }}
      />

      <ConfirmDialog
        open={a === 'resume'}
        onClose={close}
        title={p ? `Resume payout ${p.requestId}?` : ''}
        description="Continues from the last successful step without creating a new transfer."
        confirmLabel="Resume"
        loading={ops.submitting}
        onConfirm={() => {
          if (!p) return;
          void run(ops.resumePayout(p.id), `Payout ${p.requestId} resumed`, done).then(close);
        }}
      />

      <ReasonDialog
        open={a === 'hold'}
        onClose={close}
        title={p ? `Hold payout ${p.requestId}?` : ''}
        body="The payout stops moving until someone releases the hold. The organizer is not charged and the reserve stays in place."
        confirmLabel="Hold payout"
        danger
        loading={holds.busy}
        onConfirm={(reason) => {
          if (!p) return;
          void run(attempt(() => holds.hold(p.id, reason)), `Payout ${p.requestId} put on hold`, done).then(close);
        }}
      />

      <ReasonDialog
        open={a === 'release'}
        onClose={close}
        title={p ? `Release the hold on ${p.requestId}?` : ''}
        body="The payout goes back to the state it was in before the hold."
        confirmLabel="Release hold"
        required={false}
        reasonLabel="Note"
        loading={holds.busy}
        onConfirm={(note) => {
          if (!p) return;
          void run(attempt(() => holds.release(p.id, note || undefined)), `Hold released on ${p.requestId}`, done).then(close);
        }}
      />

      <ReasonDialog
        open={a === 'escalate'}
        onClose={close}
        title={p ? `Escalate payout ${p.requestId}?` : ''}
        body="Sent to the finance lead for a decision."
        confirmLabel="Escalate"
        loading={ops.submitting}
        onConfirm={(reason) => {
          if (!p) return;
          void run(ops.escalatePayout(p.id, reason), `Payout ${p.requestId} escalated`, done).then(close);
        }}
      />

      <FormDialog
        open={a === 'complete'}
        title={p ? `Complete payout ${p.requestId}` : ''}
        body="Enter the reference from the bank or mobile money statement."
        confirmLabel="Mark completed"
        successMessage={p ? `Payout ${p.requestId} completed` : 'Payout completed'}
        fields={[{ id: 'ref', label: 'Bank reference', required: true, pattern: /^[A-Za-z0-9-]{6,32}$/, patternMessage: '6 to 32 letters, digits or dashes' }]}
        onClose={close}
        onDone={done}
        onSubmit={(v) => (p ? guard(() => ops.completePayout(p.id, v.ref)) : Promise.resolve({ success: false, message: null, errorCode: null }))}
      />

      <FormDialog
        open={a === 'mark'}
        title={p ? `Mark ${p.requestId} for review` : ''}
        body="Flags the payout for a second look. Nothing is sent or reversed."
        confirmLabel="Mark for review"
        successMessage={p ? `Payout ${p.requestId} marked for review` : 'Marked for review'}
        fields={[
          { id: 'issue', label: 'Issue type', type: 'select', value: p?.issueType ?? 'OTHER', options: ISSUE_TYPES.map((i) => ({ value: i, label: humanize(i) })) },
          { id: 'note', label: 'Note (optional)', type: 'textarea' },
        ]}
        onClose={close}
        onDone={done}
        onSubmit={(v) => (p ? ops.markForReview(p.id, v.issue as PayoutIssueType, v.note || undefined) : Promise.resolve({ success: false, message: null, errorCode: null }))}
      />

      <FormDialog
        open={a === 'resolve'}
        title={p ? `Resolve issue on ${p.requestId}` : ''}
        confirmLabel="Resolve"
        successMessage={p ? `Issue resolved on ${p.requestId}` : 'Issue resolved'}
        fields={[
          { id: 'resolution', label: 'Resolution', type: 'select', value: 'MANUAL_APPROVAL', options: RESOLUTIONS.map((i) => ({ value: i, label: humanize(i) })) },
          { id: 'note', label: 'Resolution note', type: 'textarea', required: true, minLength: 5 },
        ]}
        onClose={close}
        onDone={done}
        onSubmit={(v) => (p ? ops.resolveIssue(p.id, v.resolution as PayoutResolutionType, v.note) : Promise.resolve({ success: false, message: null, errorCode: null }))}
      />
    </>
  );

  return { start: (action, payout) => setActive({ action, payout }), dialogs, busy: decisions.submitting || ops.submitting || holds.busy };
}
