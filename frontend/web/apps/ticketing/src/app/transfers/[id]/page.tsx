import type { Metadata } from 'next';
import { bff } from '@/lib/bff';
import { TransferAcceptClient } from '@/components/tickets/TransferAcceptClient';

export const metadata: Metadata = { title: 'Ticket transfer | Showstop Tickets', robots: { index: false } };

/** Only the signed-in recipient can answer an offer. */
export default async function TransferPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  await bff.requireSession({ returnTo: `/transfers/${id}` });
  return <TransferAcceptClient transferId={id} />;
}
