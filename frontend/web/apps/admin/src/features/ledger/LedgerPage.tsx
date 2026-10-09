'use client';

import { ModuleFrame } from '@/components/console';
import { useJournalEntries } from '@pml.tickets/shared/api/admin/modules/ledger';
import { CoaTab } from './CoaTab';
import { JournalTab } from './JournalTab';
import { TrialBalanceTab } from './TrialBalanceTab';
import { PlatformTab } from './PlatformTab';
import { CommissionTab } from './CommissionTab';
import { ReconTab } from './ReconTab';

import { LEDGER_TABS, type LedgerTabId } from './tabs';
export { LEDGER_TABS };
export type { LedgerTabId };

const TABS: Record<LedgerTabId, () => React.ReactElement> = {
  coa: CoaTab,
  journal: JournalTab,
  tb: TrialBalanceTab,
  platform: PlatformTab,
  commission: CommissionTab,
  recon: ReconTab,
};

export function LedgerPage({ tab }: { tab: string }) {
  // Draft count on the "Journal entries" tab, as in the prototype.
  const drafts = useJournalEntries({ status: 'DRAFT' }, 0, 1);
  const Tab = TABS[tab as LedgerTabId];
  return (
    <ModuleFrame
      module="ledger"
      tab={tab}
      title="Ledger"
      subtitle="Chart of accounts, journal entries, trial balance, commission and reconciliation"
      tabCounts={{ journal: drafts.pageInfo.totalCount }}
    >
      {Tab ? <Tab /> : null}
    </ModuleFrame>
  );
}
