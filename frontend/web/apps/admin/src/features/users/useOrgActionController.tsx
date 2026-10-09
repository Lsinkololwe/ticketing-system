'use client';

import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { z } from 'zod';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { Button, Dialog, Select, TextField, useSnackbar } from '@pml.tickets/shared/components/m3';
import { ADMIN_ORG_STATUSES, useOrgAdminActions, type AdminOrgRecord, type AdminOrgStatus } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { useSetOrganizationCommission } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { ReasonDialog, useStaff } from '@/components/console';
import { useStepUp } from '@/lib/useStepUp';
import { humanize } from '@/lib/format';
import { errorMessage, orgRateText } from './common';

export type OrgActionId = 'suspend' | 'unsuspend' | 'commission' | 'status';

const statusSchema = z.object({ status: z.enum(ADMIN_ORG_STATUSES as unknown as [AdminOrgStatus, ...AdminOrgStatus[]]) });

function StatusDialog({ org, open, loading, onClose, onSubmit }: { org: AdminOrgRecord | null; open: boolean; loading: boolean; onClose: () => void; onSubmit: (s: AdminOrgStatus) => void }) {
  const form = useZodForm(statusSchema, { defaultValues: { status: 'ACTIVE' } });
  const { register, handleSubmit, reset } = form;
  useEffect(() => {
    if (open && org) reset({ status: org.status });
  }, [open, org, reset]);
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={`Update status for ${org?.name ?? ''}`}
      actions={
        <>
          <Button variant="text" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="filled" loading={loading} onClick={handleSubmit((v) => onSubmit(v.status))}>
            Update status
          </Button>
        </>
      }
    >
      <Select label="New status" {...register('status')}>
        {ADMIN_ORG_STATUSES.map((s) => (
          <option key={s} value={s}>
            {humanize(s)}
          </option>
        ))}
      </Select>
    </Dialog>
  );
}

export const commissionSchema = z.object({
  rate: z.number({ error: 'Enter a percentage' }).min(0, 'Enter 0 or more').max(100, 'Enter 100 or fewer'),
  reason: z.string().trim(),
});

/** Per-organization commission override (setOrganizationCommissionRate). Applies to future commission records only. */
function CommissionDialog({ org, open, loading, onClose, onSubmit }: { org: AdminOrgRecord | null; open: boolean; loading: boolean; onClose: () => void; onSubmit: (rate: number, reason: string) => void }) {
  const form = useZodForm(commissionSchema, { defaultValues: { rate: 0, reason: '' } });
  const { register, handleSubmit, reset, formState } = form;
  useEffect(() => {
    if (open && org) reset({ rate: org.commissionRate ?? org.payoutConfig?.commissionRate ?? 0, reason: '' });
  }, [open, org, reset]);
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={`Commission for ${org?.name ?? ''}`}
      actions={
        <>
          <Button variant="text" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="filled" loading={loading} onClick={handleSubmit((v) => onSubmit(v.rate, v.reason))}>
            Save rate
          </Button>
        </>
      }
    >
      <div className="m3-stack">
        <p className="m3-muted">Current rate: {org ? orgRateText(org) : ''}. A different rate applies to future commission records only.</p>
        <TextField label="Commission rate (%)" type="number" inputMode="decimal" errorText={formState.errors.rate?.message} {...register('rate', { valueAsNumber: true })} />
        <TextField label="Reason (optional)" errorText={formState.errors.reason?.message} {...register('reason')} />
      </div>
    </Dialog>
  );
}

/** Dialogs and mutations behind the organization row actions (table, quick view, full page). */
export function useOrgActionController(): { run: (id: OrgActionId, o: AdminOrgRecord) => void; dialogs: ReactNode } {
  const staff = useStaff();
  const api = useOrgAdminActions();
  const commission = useSetOrganizationCommission();
  const { guard } = useStepUp();
  const snack = useSnackbar();
  const [pending, setPending] = useState<{ id: OrgActionId; org: AdminOrgRecord } | null>(null);
  const [busy, setBusy] = useState(false);
  const close = () => setPending(null);

  const attempt = useCallback(
    async (fn: () => Promise<unknown>, message: string, undo?: () => Promise<unknown>) => {
      setBusy(true);
      try {
        await fn();
        setPending(null);
        snack.show(
          undo
            ? { message, actionLabel: 'Undo', onAction: () => void undo().catch((e) => snack.show({ message: errorMessage(e), tone: 'error' })) }
            : message
        );
      } catch (e) {
        snack.show({ message: errorMessage(e), tone: 'error' });
      } finally {
        setBusy(false);
      }
    },
    [snack]
  );

  const run = useCallback(
    (id: OrgActionId, org: AdminOrgRecord) => {
      if (!staff.can('orgs')) return;
      if (id === 'unsuspend') void attempt(() => api.unsuspendOrganization(org.id), `${org.name} unsuspended`);
      else setPending({ id, org });
    },
    [api, attempt, staff]
  );

  const o = pending?.org ?? null;
  const dialogs = (
    <>
      <ReasonDialog
        open={pending?.id === 'suspend'}
        title={`Suspend ${o?.name ?? ''}?`}
        body="Events are hidden from buyers, new payouts are blocked and team members see a suspension notice."
        confirmLabel="Suspend organization"
        danger
        loading={busy}
        onClose={close}
        onConfirm={(reason) => o && attempt(() => api.suspendOrganization(o.id, reason), `${o.name} suspended`, () => api.unsuspendOrganization(o.id))}
      />
      <StatusDialog
        org={o}
        open={pending?.id === 'status'}
        loading={busy}
        onClose={close}
        onSubmit={(s) => o && attempt(() => api.updateOrganizationStatus(o.id, s), `${o.name} is now ${humanize(s).toLowerCase()}`)}
      />
      <CommissionDialog
        org={o}
        open={pending?.id === 'commission'}
        loading={busy}
        onClose={close}
        onSubmit={(rate, reason) => o && attempt(() => guard(() => commission.setRate(o.id, rate, reason)), `Commission for ${o.name} is now ${rate}%`)}
      />
    </>
  );
  return { run, dialogs };
}
