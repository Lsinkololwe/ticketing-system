import '@radix-ui/themes/styles.css';
import './global.css';
import Providers from '../components/Providers';
import { ErrorBoundary } from '../components/ErrorBoundary';

export const metadata = {
  title: 'MyTicket Zambia Admin - Event Management Platform',
  description: 'Admin dashboard for managing events, users, and system operations.',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    // suppressHydrationWarning is required for next-themes to work without hydration mismatch
    // next-themes modifies the html element's class and style attributes on the client
    <html lang="en" data-brand="admin" suppressHydrationWarning>
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
