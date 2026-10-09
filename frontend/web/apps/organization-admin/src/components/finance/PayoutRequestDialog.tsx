'use client';

import { useEffect, useMemo } from 'react';
import { Banner, Dialog } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, MoneyRHF, SelectRHF, parseKwachaToMinor, useZodForm } from '@pml.tickets/shared';
import type { EscrowAccountRow, PayoutEligibilityRow } from '@/lib/api/finance';
import { blockedMessage, maskAccount } from '@/lib/finance/payouts';
import { formatMoney } from '@/lib/format/figure';
import { payoutRequestSchema, type PayoutRequestValues } from './schemas';

export interface DestinationOption {
  id: string;
  label: string;
  isDefault: boolean;
}

interface Props {
  accounts: EscrowAccountRow[];
  destinations: DestinationOption[];
  escrowId: string;
  bankId: string;
  eligibility: PayoutEligibilityRow | null;
  eligibilityLoading: boolean;
  idempotencyKey: string;
  /** Resolve on success; reject (GraphQL error) to map it onto the form. */
  onSubmit: (values: PayoutRequestValues) => Promise<unknown> | void;
  onEscrowChange: (id: string) => void;
  onClose: () => void;
}

export function destinationLabel(bankName: string | null | undefined, accountNumber: string | null | undefined, holder?: string | null): string {
  return `${bankName ?? holder ?? 'Account'} ${maskAccount(accountNumber)}`;
}

/** Request a payout: pick an escrow, see eligibility (or why not), pick a verified destination and amount. */
export function PayoutRequestDialog(p: Props) {
  const e = p.eligibility;
  const availableMinor = e?.eligible ? (parseKwachaToMinor(e.availableAmount) ?? 0) : 0;
  const minMinor = parseKwachaToMinor(e?.minimumAmount ?? '1') ?? 100;
  const schema = useMemo(
    () => payoutRequestSchema({ eligible: Boolean(e?.eligible), minMinor, maxMinor: availableMinor }),
    [e?.eligible, minMinor, availableMinor]
  );
  const form = useZodForm(schema, {
    defaultValues: { escrowId: p.escrowId, bankId: p.bankId, amount: availableMinor || undefined },
  });

  // A new eligibility result resets the amount to the whole available balance (no payout fee).
  const { setValue, watch } = form;
  useEffect(() => {
    setValue('amount', (availableMinor || undefined) as number, { shouldDirty: false });
  }, [availableMinor, setValue]);
  // Destinations can arrive after the dialog opened: adopt the default one once, unless the user already chose.
  useEffect(() => {
    if (p.bankId && !form.getValues('bankId')) setValue('bankId', p.bankId, { shouldDirty: false });
  }, [p.bankId, setValue, form]);
  const chosen = watch('escrowId');
  useEffect(() => {
    if (chosen && chosen !== p.escrowId) p.onEscrowChange(chosen);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chosen]);

  return (
    <Dialog open wide onClose={p.onClose} title="Request a payout">
      <Form form={form} aria-label="Request a payout" fieldMap={{ requestedAmount: 'amount', bankAccountId: 'bankId' }} onSubmit={async (v) => { await p.onSubmit(v); p.onClose(); }}>
        <SelectRHF
          name="escrowId"
          label="Event escrow"
          options={p.accounts.map((a) => ({ value: a.id, label: a.eventTitle ?? a.accountNumber }))}
        />
        {p.eligibilityLoading ? (
          <p role="status">Checking eligibility…</p>
        ) : e ? (
          e.eligible ? (
            <Banner tone="success">
              Eligible. Available to withdraw: <b>{formatMoney(e.availableAmount, e.currency, { decimals: 2 })}</b>. There is no payout fee.
            </Banner>
          ) : (
            <Banner tone="error" title="Not eligible yet" urgent>
              <ul>
                {e.reasons.map((r) => (
                  <li key={r}>{blockedMessage(r, e.minimumAmount)}</li>
                ))}
              </ul>
            </Banner>
          )
        ) : null}
        <SelectRHF
          name="bankId"
          label="Destination"
          placeholder={p.destinations.length ? undefined : 'No verified account'}
          options={p.destinations.map((d) => ({ value: d.id, label: d.label }))}
        />
        <MoneyRHF
          name="amount"
          label="Amount"
          helperText={e ? `Minimum payout K ${Number(e.minimumAmount).toLocaleString('en-GB')}. Up to the available balance.` : undefined}
        />
        <p className="m3-muted">Request key {p.idempotencyKey}. It stops a double tap creating two requests.</p>
        <FormActions submitLabel="Request payout" onCancel={p.onClose} />
      </Form>
    </Dialog>
  );
}
