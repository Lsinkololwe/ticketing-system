'use client';

import { Button, StatusPill } from '@pml.tickets/shared/components/m3';

/** Step 2 for a signed-in buyer: tickets and receipts go to the contact verified at sign-in. */
export function ContactStep({ name, onBack, onContinue }: { name: string | null; onBack: () => void; onContinue: () => void }) {
  return (
    <section className="m3-panel m3-stack" aria-labelledby="contact-title">
      <h2 className="m3-card__title" id="contact-title">
        Contact details
      </h2>
      <p className="m3-muted">We send your tickets and receipts to the contact you verified when you signed in.</p>
      <dl className="m3-kv-grid">
        <div>
          <dt>Signed in as</dt>
          <dd>{name || 'Your account'}</dd>
        </div>
      </dl>
      <p>
        <StatusPill tone="success">Verified by sign-in code</StatusPill>
      </p>
      <div className="m3-row">
        <Button onClick={onBack}>Back</Button>
        <Button variant="accent" onClick={onContinue}>
          Continue to payment
        </Button>
      </div>
    </section>
  );
}
