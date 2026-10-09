'use client';

import { Icon } from '@pml.tickets/shared/components/m3';
import { LinkBtn } from '@/components/LinkBtn';
import { NotAvailable } from '@/components/NotAvailable';
import { SiteShell } from '@/components/shell/SiteShell';

/** High-demand waiting room. The platform has no virtual queue yet, so this explains and offers the normal flow. */
export function QueueClient({ eventId }: { eventId: string }) {
  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-narrow buyer-page">
        <section className="m3-panel buyer-end" aria-labelledby="queue-title">
          <span className="buyer-end__icon">
            <Icon name="users" />
          </span>
          <h2 id="queue-title" className="m3-page-title">The waiting room</h2>
          <p className="buyer-lead">For very popular events a virtual queue lets everyone reserve fairly.</p>
          <NotAvailable what="The virtual queue is not open yet. You can reserve tickets directly." />
          <div className="m3-row buyer-center">
            <LinkBtn href={`/events/${eventId}`} variant="filled">Back to the event</LinkBtn>
          </div>
        </section>
      </div>
    </SiteShell>
  );
}
