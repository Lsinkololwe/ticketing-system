import { notFound } from 'next/navigation';
import { ConfigPage } from '@/features/config/ConfigPage';

const TABS = ['rules', 'roles', 'refdata'] as const;

export default async function ConfigTabPage({ params }: { params: Promise<{ tab: string }> }) {
  const { tab } = await params;
  if (!(TABS as readonly string[]).includes(tab)) notFound();
  return <ConfigPage tab={tab as (typeof TABS)[number]} />;
}
