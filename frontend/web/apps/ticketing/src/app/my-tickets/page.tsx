import type { Metadata } from 'next';
import { bff } from '@/lib/bff';
import { TicketsClient } from '@/components/tickets/TicketsClient';

export const metadata: Metadata = { title: 'My tickets | Showstop Tickets', robots: { index: false } };

/** Server-side guard: no valid server session means redirect to /auth before anything renders. */
export default async function MyTicketsPage() {
  const session = await bff.requireSession({ returnTo: '/my-tickets' });
  return <TicketsClient accountId={session.accountId as string} holder={session.displayName} />;
}
