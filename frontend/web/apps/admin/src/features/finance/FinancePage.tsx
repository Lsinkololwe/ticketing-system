'use client';

import { useRefundStatusCount, usePayoutRequestStats } from '@pml.tickets/shared/api/admin/modules/finance';
import { useChargebackQueue } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { ModuleFrame } from '@/components/console';
import { BanksTab } from './BanksTab';
import { ChargebacksTab } from './ChargebacksTab';
import { EscrowTab } from './EscrowTab';
import { PayoutsTab } from './PayoutsTab';
import { RefundsTab } from './RefundsTab';

export type FinanceTab = 'payouts' | 'refunds' | 'escrow' | 'chargebacks' | 'banks';

/** /finance/[tab]: payout requests, refunds, escrow, chargebacks, payout accounts. */
export function FinancePage({ tab }: { tab: FinanceTab }) {
  const payouts = usePayoutRequestStats();
  const refunds = useRefundStatusCount('PENDING' as never);
  const chargebacks = useChargebackQueue();
  return (
    <ModuleFrame
      module="finance"
      tab={tab}
      title="Finance"
      subtitle="Payouts, refunds, escrow, chargebacks and payout accounts"
      tabCounts={{
        payouts: payouts.stats?.pendingPayoutRequests ?? undefined,
        refunds: refunds.count ?? undefined,
        chargebacks: chargebacks.loaded ? chargebacks.open.length : undefined,
      }}
    >
      {tab === 'payouts' ? <PayoutsTab /> : tab === 'refunds' ? <RefundsTab /> : tab === 'escrow' ? <EscrowTab /> : tab === 'chargebacks' ? <ChargebacksTab /> : <BanksTab />}
    </ModuleFrame>
  );
}
