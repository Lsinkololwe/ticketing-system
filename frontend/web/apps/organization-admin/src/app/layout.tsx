import '@radix-ui/themes/styles.css';
import './global.css';
import Providers from '../components/Providers';
import { ErrorBoundary } from '../components/ErrorBoundary';

export const metadata = {
  title: 'Organization Portal - MyTicket Zambia',
  description: 'Manage your events, tickets, team, and finances. The complete platform for event organizers.',
  keywords: ['event management', 'ticketing', 'organizer portal', 'event organizer'],
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    // suppressHydrationWarning is required for next-themes to work without hydration mismatch
    // next-themes modifies the html element's class and style attributes on the client
    <html lang="en" suppressHydrationWarning>
      <body>
        <Providers>
          <ErrorBoundary>
            {children}
          </ErrorBoundary>
        </Providers>
      </body>
    </html>
  )
}
