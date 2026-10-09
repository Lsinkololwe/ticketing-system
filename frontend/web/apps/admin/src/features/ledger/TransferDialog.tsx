'use client';

import { useRef } from 'react';
import { z } from 'zod';
import { Banner, Select, TextArea, TextField, useSnackbar } from '@pml.tickets/shared/components/m3';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { useTransferBetweenPlatformAccounts } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { FormDialog } from './FormDialog';
import { humanize } from '@/lib/format';
import { useStepUp } from '@/lib/useStepUp';

export const PLATFORM_ACCOUNTS = ['OPERATING', 'RESERVE', 'TAX_HOLDING'] as const;

export const transferSchema = z
  .object({
    fromAccount: z.enum(PLATFORM_ACCOUNTS),
    toAccount: z.enum(PLATFORM_ACCOUNTS),
    amount: z.string().trim().regex(/^\d+(\.\d{1,2})?$/, 'Enter an amount such as 250 or 250.50').refine((v) => Number(v) > 0, 'Enter an amount above zero'),
    reason: z.string().trim().min(10, 'Give a clear reason (10+ characters)'),
  })
  .refine((v) => v.fromAccount !== v.toAccount, { path: ['toAccount'], message: 'Choose a different account' });

/** Platform account transfer. One idempotency key per opening of the dialog, reused on retries of the same intent. May need a second approver. */
export function TransferDialog({ onClose }: { onClose: () => void }) {
  const key = useRef(crypto.randomUUID());
  const { transfer } = useTransferBetweenPlatformAccounts();
  const { guard } = useStepUp();
  const snackbar = useSnackbar();
  const form = useZodForm(transferSchema, { defaultValues: { fromAccount: 'OPERATING', toAccount: 'RESERVE', amount: '', reason: '' } });
  const { register, formState: { errors } } = form;
  return (
    <FormDialog
      title="Record transfer between platform accounts"
      form={form}
      onClose={onClose}
      submitLabel="Record transfer"
      onSubmit={async (v) => {
        try {
          const res = await guard(() => transfer({ ...v, idempotencyKey: key.current }));
          snackbar.show(res?.requiresSecondApprover ? 'Transfer proposed. A different finance lead or super admin must confirm it.' : 'Transfer recorded');
          onClose();
        } catch (e) {
          snackbar.show((e as Error).message || 'Could not record the transfer');
        }
      }}
    >
      <Select label="From" density="form" {...register('fromAccount')} errorText={errors.fromAccount?.message}>
        {PLATFORM_ACCOUNTS.map((a) => <option key={a} value={a}>{humanize(a)}</option>)}
      </Select>
      <Select label="To" density="form" {...register('toAccount')} errorText={errors.toAccount?.message}>
        {PLATFORM_ACCOUNTS.map((a) => <option key={a} value={a}>{humanize(a)}</option>)}
      </Select>
      <TextField label="Amount (K)" density="form" inputMode="decimal" {...register('amount')} errorText={errors.amount?.message} />
      <TextArea label="Reason" rows={3} {...register('reason')} errorText={errors.reason?.message} />
      <Banner tone="info">Large transfers need a second approver. You will see them in Transactions, Transaction recovery, Second approval.</Banner>
    </FormDialog>
  );
}
