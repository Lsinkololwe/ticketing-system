'use client';

import { useMyBankAccounts, useMyPayouts } from '@pml.tickets/shared/api/organization-admin/modules/finance';
import { useOrgContext } from '@/lib/api/org-context';
import { useSession } from '@/lib/session';
import { useStepUp } from '@/lib/useStepUp';
import { useCancelPayout, useEscrowTransactions, useMyEscrowAccounts } from '@/lib/api/finance';
import { PayoutsView } from '@/components/finance/PayoutsView';
import { EscrowLedgerSheet } from '@/components/finance/EscrowLedgerSheet';
import { PayoutFlow } from '@/components/finance/PayoutFlow';
import { useState } from 'react';

function Ledger({ account, onClose }: { account: Parameters<typeof EscrowLedgerSheet>[0]['account']; onClose: () => void }) {
  const { rows, loading, error } = useEscrowTransactions(account?.id ?? null, 0, 100);
  return <EscrowLedgerSheet account={account} rows={rows} loading={loading} error={error} onClose={onClose} />;
}

export default function FinancePage() {
  const { data: session } = useSession();
  const organizerId = session?.user?.id ?? null;
  const { organization, role, capabilities } = useOrgContext();
  const esc = useMyEscrowAccounts();
  const po = useMyPayouts(organizerId);
  const banks = useMyBankAccounts(organizerId);
  const { cancelPayout } = useCancelPayout();
  const ensureFresh = useStepUp();
  const [cancelError, setCancelError] = useState<Error | null>(null);
  const status = organization?.status;

  return (
    <PayoutsView
      accounts={esc.accounts}
      payouts={po.payouts}
      loading={esc.loading || po.loading}
      error={esc.error ?? cancelError}
      onRetry={() => { void esc.refetch(); void po.refetch(); }}
      orgActive={status === 'ACTIVE' || status === 'APPROVED'}
      canRequest={role ? capabilities.canRequestPayout : true}
      onCancelPayout={async (id, reason) => {
        try { if (!(await ensureFresh())) return; await cancelPayout(id, reason); await po.refetch(); } catch (e) { setCancelError(e as Error); }
      }}
      renderLedger={(account, close) => <Ledger account={account} onClose={close} />}
      renderPayoutDialog={(escrowId, close) => (
        <PayoutFlow
          organizerId={organizerId}
          accounts={esc.accounts}
          banks={banks.bankAccounts}
          initialEscrowId={escrowId}
          onDone={() => { void esc.refetch(); void po.refetch(); }}
          onClose={close}
        />
      )}
    />
  );
}
