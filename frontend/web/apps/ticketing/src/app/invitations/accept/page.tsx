import type { Metadata } from 'next';
import { InvitationClient } from '@/components/invitations/InvitationClient';

export const metadata: Metadata = { title: 'Team invitation | Showstop Tickets', robots: { index: false } };

export default async function AcceptInvitationPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token } = await searchParams;
  return <InvitationClient token={typeof token === 'string' ? token : ''} />;
}
