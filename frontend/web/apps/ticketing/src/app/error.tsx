'use client';

import { useEffect, useState } from 'react';
import { Button, EmptyState } from '@pml.tickets/shared/components/m3';
import { LinkBtn } from '@/components/LinkBtn';
import { SiteShell } from '@/components/shell/SiteShell';

/** Route error boundary. Distinguishes "you are offline" from "something broke". */
export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const [offline, setOffline] = useState(false);
  useEffect(() => {
    setOffline(typeof navigator !== 'undefined' && navigator.onLine === false);
  }, [error]);
  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-page">
        <EmptyState
          icon={offline ? 'warning' : 'error'}
          title={offline ? 'You are offline' : 'Something went wrong'}
          description={offline ? 'Check your internet connection and try again.' : `We could not show this page.${error.digest ? ` Reference ${error.digest}.` : ''}`}
          action={
            <div className="m3-row">
              <Button variant="filled" onClick={reset}>Try again</Button>
              <LinkBtn href="/">Back to events</LinkBtn>
            </div>
          }
        />
      </div>
    </SiteShell>
  );
}
