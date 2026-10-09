import { notFound } from 'next/navigation';
import { LedgerPage } from '@/features/ledger/LedgerPage';
import { LEDGER_TABS } from '@/features/ledger/tabs';

export default async function LedgerTabPage({ params }: { params: Promise<{ tab: string }> }) {
  const { tab } = await params;
  if (!LEDGER_TABS.includes(tab as (typeof LEDGER_TABS)[number])) notFound();
  return <LedgerPage tab={tab} />;
}
