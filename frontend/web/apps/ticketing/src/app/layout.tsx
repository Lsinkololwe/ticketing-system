import type { Metadata } from 'next';
import { Bricolage_Grotesque, DM_Sans, JetBrains_Mono } from 'next/font/google';
import './global.css';
import Providers from '@components/Providers';
import { getPublicSession } from '@/lib/server/public-session';

const display = Bricolage_Grotesque({ subsets: ['latin'], variable: '--font-display', display: 'swap' });
const body = DM_Sans({ subsets: ['latin'], variable: '--font-body', display: 'swap' });
const mono = JetBrains_Mono({ subsets: ['latin'], variable: '--font-mono', display: 'swap' });

export const metadata: Metadata = {
  title: 'Showstop Tickets',
  description:
    'Tickets for concerts, theatre, comedy, sport and festivals across Zambia. Reserve online and pay with MTN, Airtel or Zamtel mobile money.',
  openGraph: { title: 'Showstop Tickets', type: 'website', locale: 'en_ZM' },
};

// Reading the per-request nonce (and the session cookie) makes every page dynamic, which a
// nonce-based CSP requires anyway.
export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const session = await getPublicSession();
  return (
    <html lang="en" data-app="buyer" className={`${display.variable} ${body.variable} ${mono.variable}`} suppressHydrationWarning>
      <body>
        <Providers session={session}>{children}</Providers>
      </body>
    </html>
  );
}
