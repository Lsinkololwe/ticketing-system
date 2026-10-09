'use client';

import dynamic from 'next/dynamic';
import { Skeleton } from '@pml.tickets/shared/components/m3';
import { useStuckTransactions } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { ModuleFrame } from '@/components/console';
import { PaymentsTab } from './PaymentsTab';
import { TicketsTab } from './TicketsTab';
import { ReservationsTab } from './ReservationsTab';
import { RecoveryTab } from './RecoveryTab';
import { AuditTab } from './AuditTab';
import { AnnounceTab } from './AnnounceTab';

// Built by the config group; loaded lazily so this page does not depend on it at build time of the other tabs.
const ReferenceDataTab = dynamic(() => import('@/features/config/ReferenceDataTab').then((m) => m.ReferenceDataTab), {
  loading: () => <Skeleton width="100%" />,
});

import { TRANSACTIONS_TABS } from './tabs';
export { TRANSACTIONS_TABS };

const TABS: Record<(typeof TRANSACTIONS_TABS)[number], () => React.ReactElement> = {
  payments: PaymentsTab,
  tickets: TicketsTab,
  reservations: ReservationsTab,
  recovery: RecoveryTab,
  refdata: () => <ReferenceDataTab />,
  audit: AuditTab,
  announce: AnnounceTab,
};

export function TransactionsPage({ tab }: { tab: string }) {
  const stuck = useStuckTransactions({ size: 1 });
  const Tab = TABS[tab as (typeof TRANSACTIONS_TABS)[number]];
  return (
    <ModuleFrame
      module="transactions"
      tab={tab}
      title="Transactions"
      subtitle="Payments, tickets, reservations, recovery, reference data, audit log and announcements"
      tabCounts={{ recovery: stuck.pageInfo.totalCount }}
    >
      {Tab ? <Tab /> : null}
    </ModuleFrame>
  );
}
