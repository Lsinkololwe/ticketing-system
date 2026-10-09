import { notFound } from 'next/navigation';
import { ApprovalsPage } from '@/features/approvals/ApprovalsPage';
import { APPROVALS_TABS, type ApprovalsTab } from '@/features/approvals/tabs';

export default async function ApprovalsTabPage({ params }: { params: Promise<{ tab: string }> }) {
  const { tab } = await params;
  if (!APPROVALS_TABS.includes(tab as ApprovalsTab)) notFound();
  return <ApprovalsPage tab={tab as ApprovalsTab} />;
}
