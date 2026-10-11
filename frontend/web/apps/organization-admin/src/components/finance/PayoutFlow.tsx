'use client';

import { useMemo, useState } from 'react';
import { useMutation } from '@apollo/client/react';
import { useSnackbar } from '@pml.tickets/shared/components/m3';
import { CREATE_PAYOUT_REQUEST, type BankAccountVM } from '@pml.tickets/shared/api/organization-admin/modules/finance';
import { usePayoutEligibility, type EscrowAccountRow } from '@/lib/api/finance';
import { minorToKwachaString, useIdempotencyKey } from '@pml.tickets/shared';
import { useStepUp } from '@/lib/useStepUp';
import { PayoutRequestDialog, destinationLabel } from './PayoutRequestDialog';
import type { PayoutRequestValues } from './schemas';

interface Props {
  organizerId: string | null;
  accounts: EscrowAccountRow[];
  banks: BankAccountVM[];
  initialEscrowId: string | null;
  onDone: () => void;
  onClose: () => void;
}

/** Wires the payout dialog to eligibility and the create mutation. */
export function PayoutFlow({ organizerId, accounts, banks, initialEscrowId, onDone, onClose }: Props) {
  const snackbar = useSnackbar();
  const ensureFresh = useStepUp();
  const [createPayout] = useMutation(CREATE_PAYOUT_REQUEST);
  const eligibleFirst = accounts.find((a) => a.status === 'PAYOUT_ELIGIBLE') ?? accounts[0];
  const [escrowId, setEscrowId] = useState(initialEscrowId ?? eligibleFirst?.id ?? '');
  const verified = useMemo(() => banks.filter((b) => b.isVerified || b.status === 'VERIFIED'), [banks]);
  const bankId = (verified.find((b) => b.isDefault) ?? verified[0])?.id ?? '';
  // Keyed by the escrow account being paid out, persisted so a reload mid-submission reuses it.
  const [key] = useIdempotencyKey(escrowId ? `payout:${escrowId}` : null);
  const account = accounts.find((a) => a.id === escrowId) ?? null;
  const { eligibility, loading: checking } = usePayoutEligibility(account?.eventId ?? null);

  const submit = async (v: PayoutRequestValues) => {
    if (!organizerId || !account || !eligibility?.eligible) throw new Error('This event is not eligible for a payout yet');
    // Payouts need a recent interactive login; otherwise the user is sent through step-up.
    if (!(await ensureFresh())) return;
    // A rejection carries the server error contract; the dialog's <Form> maps it.
    await createPayout({
      variables: {
        input: {
          organizerId,
          eventId: account.eventId,
          escrowAccountId: account.id,
          bankAccountId: v.bankId,
          requestedAmount: minorToKwachaString(v.amount),
          currency: eligibility.currency,
          payoutMethod: 'BANK_TRANSFER' as never,
          idempotencyKey: key,
          notes: null,
          metadata: null,
        },
      },
    });
    snackbar.show(`Payout requested for ${account.eventTitle ?? 'your event'}`);
    onDone();
  };

  return (
    <PayoutRequestDialog
      accounts={accounts}
      destinations={verified.map((b) => ({ id: b.id, isDefault: b.isDefault, label: destinationLabel(b.bankName, b.accountNumber, b.accountHolderName) }))}
      escrowId={escrowId}
      bankId={bankId}
      eligibility={eligibility}
      eligibilityLoading={checking}
      idempotencyKey={key}
      onEscrowChange={setEscrowId}
      onSubmit={submit}
      onClose={onClose}
    />
  );
}
