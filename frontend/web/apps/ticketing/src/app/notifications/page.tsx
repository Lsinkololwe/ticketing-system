import type { Metadata } from 'next';
import { bff } from '@/lib/bff';
import { NotificationsClient } from '@/components/notifications/NotificationsClient';

export const metadata: Metadata = { title: 'Notifications | Showstop Tickets', robots: { index: false } };

export default async function NotificationsPage() {
  await bff.requireSession({ returnTo: '/notifications' });
  return <NotificationsClient />;
}
