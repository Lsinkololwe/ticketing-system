import './global.css';
import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { headers } from 'next/headers';
import { Bricolage_Grotesque, DM_Sans, JetBrains_Mono } from 'next/font/google';
import Providers from '../components/Providers';
import { ErrorBoundary } from '../components/ErrorBoundary';

const display = Bricolage_Grotesque({ subsets: ['latin'], variable: '--font-display', display: 'swap' });
const body = DM_Sans({ subsets: ['latin'], variable: '--font-body', display: 'swap' });
const mono = JetBrains_Mono({ subsets: ['latin'], variable: '--font-mono', display: 'swap' });

export const metadata: Metadata = {
  title: 'MyTicketZM Organizer',
  description: 'Manage your events, bookings, payouts and team.',
};

// Reading the per-request nonce makes every page dynamic, which the nonce-based CSP set by proxy.ts
// requires (prerendered pages carry no nonce, so 'strict-dynamic' would block their scripts).
export default async function RootLayout({ children }: { children: ReactNode }) {
  const nonce = (await headers()).get('x-nonce') ?? undefined;
  return (
    <html lang="en" data-app="organizer" className={`${display.variable} ${body.variable} ${mono.variable}`} suppressHydrationWarning>
      <body>
        <Providers nonce={nonce}>
          <ErrorBoundary>{children}</ErrorBoundary>
        </Providers>
      </body>
    </html>
  );
}
