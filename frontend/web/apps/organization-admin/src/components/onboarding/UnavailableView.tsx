'use client';

import { Banner, Button } from '@pml.tickets/shared/components/m3';
import { LinkBtn } from '@/components/console/LinkBtn';

export function UnavailableView({ attempts, retrying, onRetry }: { attempts: number; retrying: boolean; onRetry: () => void }) {
  return (
    <div className="m3-stack" data-testid="application-unavailable">
      <h1 className="m3-page-title">We couldn&apos;t load your application</h1>
      <Banner tone="warning" urgent>
        Your application is safe. We just couldn&apos;t reach the service to check its status, so we have not shown a blank form.
        {attempts > 0 ? ' Still unavailable. Try again in a moment.' : ''}
      </Banner>
      <div className="m3-row">
        <Button variant="filled" loading={retrying} onClick={onRetry}>
          Try again
        </Button>
        <LinkBtn href="mailto:support@myticket.zm" variant="outlined">
          Contact support
        </LinkBtn>
      </div>
    </div>
  );
}
