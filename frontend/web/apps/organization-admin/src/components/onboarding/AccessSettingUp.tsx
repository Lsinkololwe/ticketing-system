import { Card, Icon } from '@pml.tickets/shared/components/m3';

/**
 * Shown to a member of an active organization whose platform access has not been granted yet.
 * It happens right after accepting an invitation, for about a minute. "Check again" signs in
 * again through the existing single sign-on session, which issues a token that carries the role.
 */
export function AccessSettingUp({ organizationName }: { organizationName: string | null }) {
  return (
    <main className="m3-stack" data-testid="access-setting-up" style={{ maxWidth: 560, margin: '64px auto', padding: '0 16px' }}>
      <Card>
        <div className="m3-stack">
          <span aria-hidden="true">
            <Icon name="clock" />
          </span>
          <h1 className="m3-page-title">We&apos;re setting up your access</h1>
          <p className="m3-page-sub">
            You have joined {organizationName ?? 'the organization'}. Your access to the organizer console is being switched
            on and usually takes about a minute.
          </p>
          <div>
            <a className="m3-btn m3-state" data-variant="filled" href="/login?next=%2Fdashboard">
              Check again
            </a>
          </div>
        </div>
      </Card>
    </main>
  );
}
