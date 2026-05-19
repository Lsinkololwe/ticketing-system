import type { Metadata } from 'next';
import '@radix-ui/themes/styles.css';
import './global.css';
import Providers from '@components/Providers';

export const metadata: Metadata = {
  title: 'Event Ticketing - Your Gateway to Amazing Events',
  description: 'Discover and book tickets for the best events in Zambia. From concerts to conferences, we have it all.',
  keywords: 'events, tickets, Zambia, concerts, conferences, entertainment',
  authors: [{ name: 'Event Ticketing' }],
  openGraph: {
    title: 'Event Ticketing - Your Gateway to Amazing Events',
    description: 'Discover and book tickets for the best events in Zambia',
    type: 'website',
    locale: 'en_ZA',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body>
        <Providers>
          {children}
        </Providers>
      </body>
    </html>
  )
}
