import type { Metadata } from 'next';
import '@radix-ui/themes/styles.css';
import './global.css';
import Providers from '@components/Providers';

export const metadata: Metadata = {
  title: 'MyTicketZM — Every event in Zambia, one tap away',
  description: 'Discover and book tickets for the best events in Zambia. Pay the way you already do — with mobile money (MTN, Airtel, Zamtel).',
  keywords: 'events, tickets, Zambia, mobile money, MTN, Airtel, Zamtel, concerts, conferences, entertainment',
  authors: [{ name: 'MyTicketZM' }],
  openGraph: {
    title: 'MyTicketZM — Every event in Zambia, one tap away',
    description: 'Discover and book tickets for the best events in Zambia, and pay with mobile money.',
    type: 'website',
    locale: 'en_ZM',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" data-brand="ticketing" suppressHydrationWarning>
      <body>
        <Providers>
          {children}
        </Providers>
      </body>
    </html>
  )
}
