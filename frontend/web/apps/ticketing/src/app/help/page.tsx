import type { Metadata } from 'next';
import { HelpPage } from '@/components/help/HelpPage';

export const metadata: Metadata = { title: 'Help | Showstop Tickets' };

export default function Help() {
  return <HelpPage />;
}
