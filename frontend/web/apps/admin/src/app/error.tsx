'use client';

import { useEffect } from 'react';
import { Button, LinkButton } from '@pml.tickets/shared/components/m3';
import { AuthLayout } from '@pml.tickets/shared/layouts';

export default function RouteError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    if (process.env.NODE_ENV !== 'production') console.error('Route error:', error);
  }, [error]);
  return (
    <AuthLayout
      product="MyTicketZM"
      console="Platform admin"
      title="Something went wrong"
      description="This page could not be shown. You can try again or go back to the dashboard."
      footer={error.digest ? <span className="m3-mono">Reference {error.digest}</span> : undefined}
    >
      <div className="m3-row">
        <Button variant="filled" onClick={reset}>
          Try again
        </Button>
        <LinkButton variant="text" href="/dashboard">
          Dashboard
        </LinkButton>
      </div>
    </AuthLayout>
  );
}
