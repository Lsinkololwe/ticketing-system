import { notFound } from 'next/navigation';
import { UsersPage } from '@/features/users/UsersPage';

const TABS = ['users', 'orgs'];

export default async function UsersTabPage({ params }: { params: Promise<{ tab: string }> }) {
  const { tab } = await params;
  if (!TABS.includes(tab)) notFound();
  return <UsersPage tab={tab} />;
}
