'use client';

/** Replaces the root layout when it fails, so it re-imports the stylesheet itself. */
import './global.css';
import { useEffect } from 'react';
import { Button } from '@pml.tickets/shared/components/m3';
import { AuthLayout } from '@pml.tickets/shared/layouts';

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    if (process.env.NODE_ENV !== 'production') console.error('Global error:', error);
  }, [error]);
  return (
    <html lang="en" data-app="platform" suppressHydrationWarning>
      <body>
        <AuthLayout product="MyTicketZM" console="Platform admin" title="The admin console hit a problem" description="Reload to try again.">
          <Button variant="filled" onClick={reset}>
            Reload
          </Button>
        </AuthLayout>
      </body>
    </html>
  );
}
