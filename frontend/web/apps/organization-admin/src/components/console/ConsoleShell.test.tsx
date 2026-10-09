import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

let status = 'ACTIVE';
let scheduled: string | null = null;
const ALL = { isOwner: false, canViewBookings: true, canManageMedia: true, canViewFinance: true, canViewTeam: true };
let caps: Record<string, boolean> = ALL;
let role = 'MANAGER';
vi.mock('@/lib/api/org-context', () => ({
  useOrgContext: () => ({
    organization: { id: 'o1', name: 'Fixture Org', status, deletionScheduledFor: scheduled },
    organizationId: 'o1',
    role,
    capabilities: caps,
  }),
}));
vi.mock('@/lib/api/settings', () => ({ useOrganizationDeletion: () => ({ cancelDeletion: vi.fn() }) }));
vi.mock('@/lib/session', () => ({ signOut: vi.fn(), useSession: () => ({ data: { user: { name: 'Test User' } } }) }));
vi.mock('next-themes', () => ({ useTheme: () => ({ resolvedTheme: 'light', setTheme: vi.fn() }) }));

import { ConsoleShell } from './ConsoleShell';

describe('ConsoleShell', () => {
  it('hides destinations the membership role cannot use (marketer: no bookings, finance or team)', () => {
    status = 'ACTIVE';
    role = 'MARKETER';
    caps = { ...ALL, canViewBookings: false, canViewFinance: false, canViewTeam: false };
    render(<ConsoleShell><p>content</p></ConsoleShell>);
    const nav = screen.getAllByRole('navigation', { name: 'Main navigation' })[0];
    for (const hidden of ['Bookings', 'Payouts', 'Banks', 'Transactions', 'Team']) expect(within(nav).queryByRole('link', { name: hidden })).toBeNull();
    for (const shown of ['Overview', 'Events', 'Media', 'Settings']) expect(within(nav).getByRole('link', { name: shown })).toBeInTheDocument();
    role = 'MANAGER';
    caps = ALL;
  });

  it('renders the drawer destinations, create action and org card', () => {
    status = 'ACTIVE';
    render(<ConsoleShell><p>content</p></ConsoleShell>);
    const nav = screen.getAllByRole('navigation', { name: 'Main navigation' })[0];
    for (const l of ['Overview', 'Events', 'Bookings', 'Media', 'Payouts', 'Banks', 'Transactions', 'Team', 'Settings']) {
      expect(within(nav).getByRole('link', { name: l })).toBeInTheDocument();
    }
    expect(within(nav).queryByRole('link', { name: 'Onboarding' })).toBeNull();
    expect(screen.getByRole('link', { name: /Create event/ })).toHaveAttribute('href', '/events/new');
    expect(screen.getByText('Fixture Org')).toBeInTheDocument();
    expect(screen.getByText('content')).toBeInTheDocument();
    expect(screen.queryByText('Application under review.')).toBeNull();
  });
  it('shows Onboarding and the stage banner while under review', () => {
    status = 'PENDING_REVIEW';
    render(<ConsoleShell><p>x</p></ConsoleShell>);
    expect(screen.getAllByRole('link', { name: 'Onboarding' })[0]).toHaveAttribute('href', '/apply/status');
    expect(screen.getByText('Application under review.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View status' })).toHaveAttribute('href', '/apply/status');
  });
  it('shows the signed-in role, not an assumed owner', () => {
    status = 'ACTIVE';
    render(<ConsoleShell><p>x</p></ConsoleShell>);
    expect(screen.getByText('Manager')).toBeInTheDocument();
    expect(screen.queryByText('Owner')).toBeNull();
  });
  it('shows the scheduled deletion without a cancel button for non-owners', () => {
    status = 'ACTIVE';
    scheduled = '2027-01-04T00:00:00Z';
    render(<ConsoleShell><p>x</p></ConsoleShell>);
    expect(screen.getByText('Organization scheduled for deletion.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel deletion' })).toBeNull();
    scheduled = null;
  });
});
