import { notFound } from 'next/navigation';
import { TransactionsPage } from '@/features/transactions/TransactionsPage';
import { TRANSACTIONS_TABS } from '@/features/transactions/tabs';

export default async function TransactionsTabPage({ params }: { params: Promise<{ tab: string }> }) {
  const { tab } = await params;
  if (!TRANSACTIONS_TABS.includes(tab as (typeof TRANSACTIONS_TABS)[number])) notFound();
  return <TransactionsPage tab={tab} />;
}
