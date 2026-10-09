import { notFound } from 'next/navigation';
import { FinancePage, type FinanceTab } from '@/features/finance/FinancePage';

const TABS: readonly FinanceTab[] = ['payouts', 'refunds', 'escrow', 'chargebacks', 'banks'];

export default async function FinanceTabPage({ params }: { params: Promise<{ tab: string }> }) {
  const { tab } = await params;
  if (!(TABS as readonly string[]).includes(tab)) notFound();
  return <FinancePage tab={tab as FinanceTab} />;
}
