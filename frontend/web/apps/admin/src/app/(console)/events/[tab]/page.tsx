import { notFound } from 'next/navigation';
import { EventsPage, type EventsTab } from '@/features/events/EventsPage';

const TABS = ['all', 'categories', 'locations', 'media', 'stock'] as const;

export default async function EventsTabPage({ params }: { params: Promise<{ tab: string }> }) {
  const { tab } = await params;
  if (!(TABS as readonly string[]).includes(tab)) notFound();
  return <EventsPage tab={tab as EventsTab} />;
}
