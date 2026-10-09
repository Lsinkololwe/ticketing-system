'use client';

import { useStepUp } from '@/lib/useStepUp';
import { useState, type ReactNode } from 'react';
import { Button, ConfirmDialog, Dialog } from '@pml.tickets/shared/components/m3';
import { useFinanceDecisions } from '@pml.tickets/shared/api/admin/modules/finance';
import { useRefundOps, type RefundOpsRow } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { ReasonDialog, useStaff } from '@/components/console';
import { money } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { asNumber, useOpRunner } from './shared';

export type RefundAction = 'approve' | 'reject' | 'process';

/** Hours-based escalation: 0 none, 1 after 2 days, 2 after 5 days. Only pending refunds escalate. */
export function refundEscalation(r: Pick<RefundOpsRow, 'status' | 'requestedAt'>, now: Date = new Date()): 0 | 1 | 2 {
  if (r.status !== 'PENDING' || !r.requestedAt) return 0;
  const days = (now.getTime() - new Date(r.requestedAt).getTime()) / 864e5;
  return days >= 5 ? 2 : days >= 2 ? 1 : 0;
}

/** Shared refund decisions for the table and the detail sheet. */
export function useRefundActions(onDone: () => void): { start: (a: RefundAction, r: RefundOpsRow) => void; dialogs: ReactNode } {
  const staff = useStaff();
  const run = useOpRunner();
  const { guard } = useStepUp();
  const decisions = useFinanceDecisions();
  const ops = useRefundOps();
  const [active, setActive] = useState<{ a: RefundAction; r: RefundOpsRow } | null>(null);
  const close = () => setActive(null);
  const r = active?.r;
  const a = active?.a;

  const blocked: { title: string; text: ReactNode } | null =
    a === 'approve' && r && r.requestType === 'ADMIN_INITIATED'
      ? r.requestedById === staff.id
        ? { title: 'Another approver is needed', text: 'You created this admin-initiated refund, so a different person must approve it.' }
        : !staff.can('secondApprove')
          ? { title: 'A Finance lead must approve', text: `Admin-initiated refunds need a second approval from a Finance lead or Super admin. ${needText('secondApprove')}` }
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
        open={a === 'approve' && !!r && !blocked}
        onClose={close}
        title={r ? `Approve refund ${r.requestId}?` : ''}
        description={
          r
            ? `${money(asNumber(r.refundAmount))} goes back to the buyer by mobile money once processed. ${
                r.refundPercentage === 0 ? 'The policy allows no refund, so this is a goodwill exception. ' : ''
              }Escrow is reduced and commission is clawed back.`
            : undefined
        }
        confirmLabel="Approve refund"
        loading={decisions.submitting}
        onConfirm={() => {
          if (r) void run(guard(() => decisions.approveRefund(r.id)), `Refund ${r.requestId} approved`, onDone).then(close);
        }}
      />
      <ReasonDialog
        open={a === 'reject'}
        onClose={close}
        title={r ? `Reject refund ${r.requestId}?` : ''}
        body="The buyer is told why."
        confirmLabel="Reject refund"
        danger
        loading={decisions.submitting}
        onConfirm={(reason) => {
          if (r) void run(decisions.rejectRefund(r.id, reason), `Refund ${r.requestId} rejected`, onDone).then(close);
        }}
      />
      <ConfirmDialog
        open={a === 'process'}
        onClose={close}
        title={r ? `Process refund ${r.requestId}?` : ''}
        description={r ? `Sends ${money(asNumber(r.netRefundAmount ?? r.refundAmount))} to the buyer through PawaPay.` : undefined}
        confirmLabel="Process"
        loading={ops.submitting}
        onConfirm={() => {
          if (r) void run(guard(() => ops.processRefund(r.id)), `Refund ${r.requestId} sent to PawaPay`, onDone).then(close);
        }}
      />
    </>
  );
  return { start: (act, row) => setActive({ a: act, r: row }), dialogs };
}
