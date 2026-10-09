import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DashboardView, type DashboardViewProps } from './DashboardView';

// Fixtures live in the test only.
const base: DashboardViewProps = {
  orgName: 'Fixture Org',
  canViewFinance: true,
  canCreateEvents: true,
  loading: false,
  stats: {
    __typename: 'OrganizerDashboardStats',
    totalRevenue: '12500',
    revenueChange: 12,
    revenueCurrency: 'ZMW',
    totalTicketsSold: 120,
    ticketsSoldChange: -4,
    activeEvents: 3,
    eventsChange: null,
    eventsEndingThisWeek: 1,
    totalAttendees: 90,
    attendeesChange: null,
    pendingPayouts: '500',
    availableBalance: '2000',
  },
  series: [{ __typename: 'RevenuePoint', periodStart: '2026-08-01', revenue: '1000', ticketsSold: 10, currency: 'ZMW' } as never],
  mix: { __typename: 'TicketMix', totalSold: 10, totalRevenue: '1', currency: 'ZMW', rows: [{ name: 'General', count: 10, revenue: '1' }] } as never,
  checkIn: { eventId: 'e1', eventTitle: 'Fixture Fest', eventDateTime: '2026-09-01T10:00:00Z', issued: 100, scanned: 80, ratePercent: 80 } as never,
  payoutWindow: { availableNow: '2000', pendingRelease: '300', currency: 'ZMW' } as never,
  upcoming: [{ id: 'e1', title: 'Fixture Fest', eventDateTime: '2026-11-01T10:00:00Z', ticketsSold: 50, totalCapacity: 200, status: 'PUBLISHED', revenue: '1', currency: 'ZMW' } as never],
  activity: [{ id: 'a1', type: 'TICKET_SALE', message: '3 tickets sold', timestamp: '2026-10-01T10:00:00Z' } as never],
  notifications: [],
  unreadNotifications: 0,
};

describe('DashboardView', () => {
  it('renders the page header, KPIs and links', () => {
    render(<DashboardView {...base} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Overview' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: /^Revenue( ·|$)/ })).toHaveTextContent('K 12,500');
    expect(screen.getByRole('group', { name: 'Tickets sold' })).toHaveTextContent('120');
    expect(screen.getByRole('link', { name: /Create event/ })).toHaveAttribute('href', '/events/new');
    expect(screen.getByRole('link', { name: 'Fixture Fest' })).toHaveAttribute('href', '/events/e1');
    expect(screen.getByRole('link', { name: 'All events' })).toHaveAttribute('href', '/events');
    expect(screen.getByRole('link', { name: 'Open payouts' })).toHaveAttribute('href', '/finance');
  });

  it('shows the empty states with no data', () => {
    render(<DashboardView {...base} series={[]} mix={null} checkIn={null} payoutWindow={null} upcoming={[]} activity={[]} />);
    expect(screen.getByText('No revenue yet')).toBeInTheDocument();
    expect(screen.getByText('No upcoming events')).toBeInTheDocument();
    expect(screen.getByText('No activity yet')).toBeInTheDocument();
    expect(screen.getByText('You are all caught up.')).toBeInTheDocument();
    expect(screen.getByTestId('not-available')).toBeInTheDocument();
  });

  it('shows a loading skeleton before the first response', () => {
    render(<DashboardView {...base} stats={null} loading />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: /^Revenue( ·|$)/ })).toBeNull();
  });

  it('hides finance regions without finance access', () => {
    render(<DashboardView {...base} canViewFinance={false} />);
    expect(screen.queryByRole('group', { name: /^Revenue( ·|$)/ })).toBeNull();
    expect(screen.queryByText('Payout window')).toBeNull();
  });

  it('lists unread notifications with a mark-all action', () => {
    const n = [{ id: 'n1', type: 'SYSTEM', title: 'Payout sent', body: '', actionUrl: null, status: 'SENT', readAt: null, createdAt: '2026-10-01T10:00:00Z' }];
    render(<DashboardView {...base} notifications={n} unreadNotifications={1} onMarkAllRead={() => undefined} />);
    const card = screen.getByText(/Notifications \(1\)/).closest('.m3-card') as HTMLElement;
    expect(within(card).getByText('Payout sent')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Mark all read' })).toBeInTheDocument();
  });
});
