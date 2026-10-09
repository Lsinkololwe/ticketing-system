'use client';

import { Button, Card, Icon, type IconName } from '@pml.tickets/shared/components/m3';

const FEATURES: Array<{ icon: IconName; title: string; text: string }> = [
  { icon: 'clock', title: 'Quick setup', text: 'About 5 minutes' },
  { icon: 'shield', title: 'Verification', text: 'Builds buyer trust' },
  { icon: 'ticket', title: 'Start selling', text: 'Publish once approved' },
];

export function WelcomeView({ firstName, onStart }: { firstName: string; onStart: () => void }) {
  return (
    <div className="m3-stack" data-testid="welcome-page">
      <div>
        <h1 className="m3-page-title" data-testid="welcome-heading">
          Welcome, {firstName}
        </h1>
        <p className="m3-page-sub" data-testid="welcome-subheading">
          Let&apos;s set up your organization so you can start creating and managing events on MyTicketZM.
        </p>
      </div>
      <div className="oc-cols" role="list" aria-label="Benefits of becoming an organizer" data-testid="feature-list">
        {FEATURES.map((f) => (
          <Card key={f.title}>
            <div role="listitem" className="m3-row" data-testid="feature-item">
              <Icon name={f.icon} />
              <span>
                <b>{f.title}</b>
                <span className="m3-muted"> · {f.text}</span>
              </span>
            </div>
          </Card>
        ))}
      </div>
      <div>
        <Button variant="filled" onClick={onStart} data-testid="get-started-button">
          Start setup
        </Button>
      </div>
    </div>
  );
}
