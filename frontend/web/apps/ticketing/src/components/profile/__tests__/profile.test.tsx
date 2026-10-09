// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

const logout = vi.fn();
const saveMe = vi.fn().mockResolvedValue({});
const savePrefs = vi.fn().mockResolvedValue({});
const requestDeletion = vi.fn().mockResolvedValue({});
const cancelDeletion = vi.fn().mockResolvedValue({});
let me: { me: unknown; loading: boolean; error: unknown } = { me: null, loading: false, error: null };
let prefsState: { prefs: unknown; loading: boolean; error: unknown } = { prefs: null, loading: false, error: null };

vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/__tests__/referenceMock')).referenceModule());
vi.mock('next/link', () => ({ default: ({ href, children, ...r }: { href: string; children: React.ReactNode }) => <a href={href} {...r}>{children}</a> }));
vi.mock('@/components/shell/SiteShell', () => ({ SiteShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock('@/components/contacts/SignInContacts', () => ({ SignInContacts: () => <section data-testid="sign-in-contacts" aria-label="Sign-in contacts" /> }));
vi.mock('@/lib/auth/session-context', () => ({ useBuyerAuth: () => ({ authenticated: true, user: { id: 'u1', givenName: 'Chanda', familyName: 'Mwansa', email: '' }, logout }) }));
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  useMe: () => ({ ...me, refetch: vi.fn(), saving: false, save: saveMe, deleting: false, requestDeletion, cancelDeletion }),
  useNotificationPrefs: () => ({ ...prefsState, refetch: vi.fn(), saving: false, saveError: null, save: savePrefs }),
}));

import { ProfileClient } from '../ProfileClient';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';

const PREFS = {
  emailEnabled: false, smsEnabled: true, whatsappEnabled: true, pushEnabled: false, inAppEnabled: true, ticketNotifications: true, eventReminders: true,
  eventUpdates: true, paymentNotifications: true, teamNotifications: false, marketingEmails: false, systemAnnouncements: true, reminderHoursBefore: 24,
  quietHoursStart: '22:00', quietHoursEnd: '07:00', timezone: 'Africa/Lusaka',
};
const mount = () => render(<SnackbarProvider><ProfileClient /></SnackbarProvider>);

beforeEach(() => {
  vi.clearAllMocks();
  me = { me: { id: 'u1', firstName: 'Chanda', lastName: 'Mwansa', displayName: 'Chanda', fullName: 'Chanda Mwansa' }, loading: false, error: null };
  prefsState = { prefs: PREFS, loading: false, error: null };
});

describe('ProfileClient', () => {
  it('builds the channel and category switches from the platform lists, keeps locked ones always on, ignores unknown ones', () => {
    mount();
    for (const name of [/WhatsApp/, /SMS/, /Push notifications/, /Email/, /In-app/, /Event reminders/, /Event updates/, /Marketing emails/]) {
      expect(screen.getByRole('switch', { name })).toBeInTheDocument();
    }
    const ticket = screen.getByRole('switch', { name: 'Ticket notifications' });
    expect(ticket).toBeChecked();
    expect(ticket).toBeDisabled();
    expect(screen.getByRole('switch', { name: 'Payment notifications' })).toBeDisabled();
    expect(screen.queryByRole('switch', { name: /Not on this form/ })).not.toBeInTheDocument();
  });

  it('lays out header, details, sign-in contacts, preferences and account actions', () => {
    mount();
    expect(screen.getByRole('heading', { name: 'Profile and settings' })).toBeInTheDocument();
    expect(screen.getByLabelText('First name')).toHaveValue('Chanda');
    expect(screen.getByTestId('sign-in-contacts')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /WhatsApp/ })).toBeChecked();
    expect(screen.getByRole('switch', { name: /Ticket notifications/ })).toBeDisabled();
    expect(screen.getByLabelText('Event reminder')).toHaveValue('24');
    expect(screen.queryByRole('region', { name: 'Unsaved changes' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Request account deletion' })).toBeEnabled();
  });
  it('shows the unsaved-changes bar, saves details and preferences, and can discard', async () => {
    mount();
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Banda' } });
    fireEvent.click(screen.getByRole('switch', { name: /Marketing emails/ }));
    const bar = screen.getByRole('region', { name: 'Unsaved changes' });
    fireEvent.click(within(bar).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(saveMe).toHaveBeenCalledWith({ firstName: 'Chanda', lastName: 'Banda' }));
    await waitFor(() => expect(savePrefs).toHaveBeenCalledWith(expect.objectContaining({ marketingEmails: true, ticketNotifications: true, paymentNotifications: true })));
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'X' } });
    fireEvent.click(within(screen.getByRole('region', { name: 'Unsaved changes' })).getByRole('button', { name: 'Discard' }));
    expect(screen.getByLabelText('Last name')).toHaveValue('Mwansa');
  });
  it('validates the name before saving', async () => {
    mount();
    fireEvent.change(screen.getByLabelText('First name'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Enter your name.')).toBeInTheDocument();
    expect(saveMe).not.toHaveBeenCalled();
  });
  it('confirms sign out', () => {
    mount();
    fireEvent.click(screen.getAllByRole('button', { name: 'Sign out' })[0]);
    fireEvent.click(within(screen.getByRole('alertdialog', { name: 'Sign out?' })).getByRole('button', { name: 'Sign out' }));
    expect(logout).toHaveBeenCalled();
  });
  it('shows loading and error states for the details and preferences', () => {
    me = { me: null, loading: true, error: null };
    prefsState = { prefs: null, loading: true, error: null };
    const a = mount();
    expect(screen.getByLabelText('Loading your details')).toBeInTheDocument();
    expect(screen.getByLabelText('Loading your preferences')).toBeInTheDocument();
    a.unmount();
    me = { me: null, loading: false, error: { message: 'x' } };
    prefsState = { prefs: null, loading: false, error: { message: 'y' } };
    mount();
    expect(screen.getAllByTestId('error-state')).toHaveLength(2);
  });
  it('requests account deletion after confirming, and cancels a pending request', async () => {
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Request account deletion' }));
    await screen.findByText('Request account deletion?');
    fireEvent.click(screen.getByRole('button', { name: 'Request deletion' }));
    await waitFor(() => expect(requestDeletion).toHaveBeenCalledTimes(1));
  });
  it('shows a pending deletion with a way to cancel it', async () => {
    me = { me: { id: 'u1', firstName: 'Chanda', lastName: 'Mwansa', displayName: 'Chanda', fullName: 'Chanda Mwansa', deletionRequestedAt: '2026-10-01T10:00:00Z', deletionScheduledFor: '2026-10-31T10:00:00Z' }, loading: false, error: null };
    mount();
    expect(screen.getByText('Pending deletion')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel deletion request' }));
    await waitFor(() => expect(cancelDeletion).toHaveBeenCalledTimes(1));
  });
});
