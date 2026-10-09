import { render as rtlRender, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';
import type { NotificationPrefs } from '@/lib/api/settings';
import { REFERENCE_FIXTURES } from '@/test/reference-fixtures';
import { NotificationPrefsSection } from './NotificationPrefsSection';

const lists = vi.hoisted(() => ({ current: {} as Record<string, unknown[]> }));
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  // read `lists` at call time: each test sets its own
  return { ...actual, useReferenceOptions: (...a: Parameters<ReturnType<typeof fakeReferenceModule>['useReferenceOptions']>) => fakeReferenceModule(lists.current as never).useReferenceOptions(...a) };
});

const prefs = {
  emailEnabled: true, smsEnabled: true, whatsappEnabled: true, pushEnabled: true, inAppEnabled: true, ticketNotifications: true,
  eventReminders: true, eventUpdates: true, paymentNotifications: true, teamNotifications: true, marketingEmails: false,
  systemAnnouncements: true, reminderHoursBefore: 24, quietHoursStart: null, quietHoursEnd: null, timezone: 'Africa/Lusaka',
} as NotificationPrefs;

const render = (ui: React.ReactElement) => rtlRender(<SnackbarProvider>{ui}</SnackbarProvider>);

describe('NotificationPrefsSection reads its switches from the platform lists', () => {
  it('shows each listed channel and category, locks the always-on ones, and ignores a row naming no known preference', () => {
    lists.current = {
      ...REFERENCE_FIXTURES,
      NOTIFICATION_CHANNEL: [...REFERENCE_FIXTURES.NOTIFICATION_CHANNEL!, { code: 'CARRIER_PIGEON', name: 'Carrier pigeon', metadata: { preferenceKey: 'pigeonEnabled' } }],
    };
    render(<NotificationPrefsSection prefs={prefs} onSave={vi.fn()} />);
    expect(screen.getByRole('switch', { name: 'WhatsApp' })).toBeInTheDocument();
    expect(screen.queryByText('Carrier pigeon')).toBeNull();
    expect(screen.getByRole('switch', { name: 'Ticket notifications' })).toBeDisabled();
    expect(screen.getByRole('switch', { name: 'Event reminders' })).toBeEnabled();
  });

  it('says so, rather than inventing switches, when the lists cannot be read', () => {
    lists.current = { TIMEZONE: REFERENCE_FIXTURES.TIMEZONE! };
    render(<NotificationPrefsSection prefs={prefs} onSave={vi.fn()} />);
    expect(screen.getByText(/the notification channels could not be loaded/)).toBeInTheDocument();
    expect(screen.getByText(/the notification categories could not be loaded/)).toBeInTheDocument();
    expect(screen.queryByRole('switch', { name: 'WhatsApp' })).toBeNull();
  });
});
