'use client';

import { useState, type ReactNode } from 'react';
import { Button, ConfirmDialog, Dialog } from '@pml.tickets/shared/components/m3';
import { useFinanceDecisions } from '@pml.tickets/shared/api/admin/modules/finance';
import { useEscrowOps } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { ReasonDialog } from '@/components/console';
import { money } from '@/lib/format';
import { asNumber, FormDialog, useOpRunner } from './shared';

export type EscrowAction = 'lock' | 'unlock' | 'suspend' | 'reactivate' | 'eligible' | 'close';

/** The slice of an escrow account the actions need (list row or detail record). */
export interface EscrowTarget {
  id: string;
  accountNumber: string;
  status: string;
  currentBalance: number | string;
}

const futureDate = (iso: string) => {
  const d = new Date(`${iso}T23:59:59`);
  return !Number.isNaN(d.getTime()) && d.getTime() > Date.now();
};

export function useEscrowActions(onDone: () => void): { start: (a: EscrowAction, t: EscrowTarget) => void; dialogs: ReactNode } {
  const run = useOpRunner();
  const decisions = useFinanceDecisions();
  const ops = useEscrowOps();
  const [active, setActive] = useState<{ a: EscrowAction; t: EscrowTarget } | null>(null);
  const close = () => setActive(null);
  const t = active?.t;
  const a = active?.a;
  const nonZero = a === 'close' && t ? asNumber(t.currentBalance) !== 0 : false;
  const tomorrow = new Date(Date.now() + 7 * 864e5).toISOString().slice(0, 10);

  const dialogs = (
    <>
      <FormDialog
        open={a === 'lock'}
        title={t ? `Lock escrow ${t.accountNumber}` : ''}
        body="A locked account cannot release funds until the lock date."
        confirmLabel="Lock account"
        successMessage={t ? `${t.accountNumber} locked` : 'Account locked'}
        fields={[
          { id: 'until', label: 'Locked until', type: 'date', value: tomorrow, required: true },
          { id: 'reason', label: 'Reason', type: 'textarea', required: true, minLength: 5 },
        ]}
        validate={(v): Record<string, string> => (v.until && !futureDate(v.until) ? { until: 'Choose a future date' } : {})}
        onClose={close}
        onDone={onDone}
        onSubmit={(v) => (t ? ops.lockEscrow(t.id, new Date(`${v.until}T23:59:59`).toISOString(), v.reason) : Promise.resolve({ success: false, message: null, errorCode: null }))}
      />
      <ReasonDialog
        open={a === 'unlock'}
        onClose={close}
        title={t ? `Unlock escrow ${t.accountNumber}?` : ''}
        body="Funds can be released again once the account is unlocked."
        confirmLabel="Unlock"
        loading={ops.submitting}
        onConfirm={(reason) => {
          if (t) void run(ops.unlockEscrow(t.id, reason), `${t.accountNumber} unlocked`, onDone).then(close);
        }}
      />
      <ReasonDialog
        open={a === 'suspend'}
        onClose={close}
        title={t ? `Suspend escrow ${t.accountNumber}?` : ''}
        body="No deposits, refunds or payouts can run until it is reactivated."
        confirmLabel="Suspend account"
        danger
        loading={decisions.submitting}
        onConfirm={(reason) => {
          if (t) void run(decisions.setEscrowStatus(t.id, 'SUSPENDED' as never, reason), `${t.accountNumber} suspended`, onDone).then(close);
        }}
      />
      <ConfirmDialog
        open={a === 'reactivate'}
        onClose={close}
        title={t ? `Reactivate escrow ${t.accountNumber}?` : ''}
        description="Deposits, refunds and payouts can run again."
        confirmLabel="Reactivate"
        loading={decisions.submitting}
        onConfirm={() => {
          if (t) void run(decisions.setEscrowStatus(t.id, 'ACTIVE' as never, 'Reactivated'), `${t.accountNumber} reactivated`, onDone).then(close);
        }}
      />
      <ConfirmDialog
        open={a === 'eligible'}
        onClose={close}
        title={t ? `Mark ${t.accountNumber} payout eligible?` : ''}
        description="Skips the remaining hold period. The organizer can request a payout straight away."
        confirmLabel="Mark eligible"
        loading={ops.submitting}
        onConfirm={() => {
          if (t) void run(ops.markEligible(t.id), `${t.accountNumber} is payout eligible`, onDone).then(close);
        }}
      />
      <Dialog
        open={nonZero}
        onClose={close}
        title="Balance must be zero"
        actions={
          <Button variant="filled" onClick={close}>
            Close
          </Button>
        }
      >
        <p>
          {t?.accountNumber} still holds {money(asNumber(t?.currentBalance))}. Pay out or refund the balance before closing the account.
        </p>
      </Dialog>
      <ReasonDialog
        open={a === 'close' && !nonZero}
        onClose={close}
        title={t ? `Close escrow ${t.accountNumber}?` : ''}
        body="Closing is permanent. No more transactions are possible."
        confirmLabel="Close account"
        danger
        loading={ops.submitting}
        onConfirm={(reason) => {
          if (t) void run(ops.closeEscrow(t.id, reason), `${t.accountNumber} closed`, onDone).then(close);
        }}
      />
    </>
  );
  return { start: (act, target) => setActive({ a: act, t: target }), dialogs };
}
