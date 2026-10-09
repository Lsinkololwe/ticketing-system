import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/test/referenceMock')).referenceModule());
import { referenceFixtures } from '@/test/referenceMock';
vi.mock('@pml.tickets/shared/api/admin/modules/identity-admin', async (orig) => (await import('@/test/referenceMock')).identityAdminWithPermissions(await orig<object>()));
referenceFixtures.NOTIFICATION_CHANNEL = [
  { code: 'EMAIL', name: 'Email' },
  { code: 'IN_APP', name: 'In-app' },
  { code: 'SMS', name: 'SMS' },
];


const state = vi.hoisted(() => ({
  config: null as null | Record<string, unknown>,
  loading: false,
  error: null as null | Error,
  update: vi.fn(),
  refetch: vi.fn(),
}));
const push = vi.hoisted(() => vi.fn());

vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn() }) }));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-config', () => ({
  usePlatformConfiguration: () => ({ config: state.config, loading: state.loading, error: state.error, refetch: state.refetch }),
  useUpdatePlatformConfiguration: () => ({ update: state.update, loading: false, error: null }),
}));

import { RulesTab } from '../RulesTab';

const fixture = {
  id: 'cfg-1',
  approvalSlaHours: 72,
  approvalWarningThresholdHours: 24,
  autoEscalationEnabled: true,
  escalationDelayHours: 6,
  escalationRecipientRole: 'ADMIN',
  escalationReminderIntervalHours: 8,
  maxEscalationReminders: 2,
  organizerNotificationChannel: 'EMAIL',
  adminNotificationChannel: 'BOTH',
  sendSlaWarningNotifications: true,
  sendEscalationNotifications: false,
  requireCommentsOnRejection: true,
  requireCommentsOnChangesRequested: true,
  allowSelfApproval: false,
  commissionDefault: 7,
  minimumPayout: '25.00',
  currency: 'ZMW',
  reservationHoldMinutes: 12,
  reservationGraceMinutes: 4,
  escrowHoldDays: 9,
  refundCutoffHours: 36,
  maxTicketsPerBooking: 6,
  rescheduleLimit: 2,
  refundPolicies: [
    { code: 'FLEXIBLE', label: 'Flexible', summary: 'Full refund early.', rules: [{ daysBefore: 1, percent: 100 }] },
    { code: 'NO_REFUNDS', label: 'No refunds', summary: 'No refunds.', rules: [] },
  ],
  version: 4,
  updatedAt: '2026-10-01T09:30:00Z',
  updatedBy: 'Test Reviewer',
};

beforeEach(() => {
  state.config = { ...fixture };
  state.loading = false;
  state.error = null;
  state.update = vi.fn().mockResolvedValue(fixture);
  push.mockClear();
});

describe('RulesTab', () => {
  it('shows real approval values, versioning note and preview computed from them', () => {
    renderConsole(<RulesTab />);
    expect(screen.getByLabelText('Approval target (hours)')).toHaveValue(72);
    expect(screen.getByText(/last saved by Test Reviewer/)).toBeInTheDocument();
    const preview = screen.getByRole('complementary', { name: 'Organizer preview and shared lists' });
    expect(preview).toHaveTextContent('Typical review time: 72 hours. Reviewers always explain requested changes.');
    expect(preview).toHaveTextContent('Commission');
  });

  it('shows money, timing and refund policies from the backend with a live preview', () => {
    renderConsole(<RulesTab />);
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getByLabelText('Default commission (%)')).toHaveValue(7);
    expect(screen.getByLabelText('Minimum payout (K)')).toHaveValue(25);
    expect(screen.getByLabelText('Reservation hold (minutes)')).toHaveValue(12);
    expect(screen.getByLabelText('Escrow hold after the event (days)')).toHaveValue(9);
    const policies = screen.getByRole('region', { name: 'Refund policies' });
    expect(within(policies).getByRole('table', { name: 'Refund policy list' })).toBeInTheDocument();
    expect(within(policies).getByText('FLEXIBLE')).toBeInTheDocument();
    expect(within(policies).getAllByText(/No refunds/).length).toBeGreaterThan(0);
    const preview = screen.getByRole('complementary', { name: 'Organizer preview and shared lists' });
    expect(preview).toHaveTextContent('7% of each ticket sale');
    expect(preview).toHaveTextContent('Flexible: Full refund early.');
    fireEvent.change(screen.getByLabelText('Default commission (%)'), { target: { value: '9' } });
    expect(preview).toHaveTextContent('9% of each ticket sale');
  });

  it('edits a refund policy in a dialog: validates, then marks the form changed for Save configuration', async () => {
    renderConsole(<RulesTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Edit Flexible policy' }));
    const dlg = await screen.findByRole('dialog', { name: 'Edit Flexible policy' });
    fireEvent.change(within(dlg).getByLabelText('Summary shown to buyers'), { target: { value: 'short' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Update policy' }));
    expect(await within(dlg).findByText(/at least 8 characters/)).toBeInTheDocument();
    fireEvent.change(within(dlg).getByLabelText('Summary shown to buyers'), { target: { value: 'Full refund until two days before.' } });
    fireEvent.change(within(dlg).getByLabelText('Rule 1: refund percent'), { target: { value: '150' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Update policy' }));
    expect(await within(dlg).findByText('Percent is at most 100')).toBeInTheDocument();
    fireEvent.change(within(dlg).getByLabelText('Rule 1: refund percent'), { target: { value: '100' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Update policy' }));
    expect(await screen.findByText(/1 unsaved change/)).toBeInTheDocument();
    expect(screen.getAllByText(/Full refund until two days before\./).length).toBeGreaterThan(0);
  });

  it('locks timing rules for admins who are not super admins', () => {
    renderConsole(<RulesTab />, { roles: ['ADMIN'] });
    expect(screen.getByLabelText('Reservation hold (minutes)')).toBeDisabled();
    expect(screen.getByLabelText('Default commission (%)')).not.toBeDisabled();
  });

  it('updates the preview live, validates, confirms and saves only changed fields', async () => {
    renderConsole(<RulesTab />);
    expect(screen.queryByRole('region', { name: 'Unsaved changes' })).toBeNull();
    fireEvent.change(screen.getByLabelText('Approval target (hours)'), { target: { value: '24' } });
    expect(screen.getByRole('complementary', { name: /Organizer preview/ })).toHaveTextContent('Typical review time: 24 hours');
    fireEvent.click(screen.getByRole('button', { name: 'Save configuration' }));
    // warning threshold 24 is not below the target 24
    expect(await screen.findByText('Must be less than the approval target')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Warn when this many hours are left'), { target: { value: '6' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save configuration' }));
    const dialog = await screen.findByRole('alertdialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save configuration' }));
    await waitFor(() => expect(state.update).toHaveBeenCalledWith({ approvalSlaHours: 24, approvalWarningThresholdHours: 6 }));
  });

  it('discards changes', () => {
    renderConsole(<RulesTab />);
    fireEvent.click(screen.getByRole('switch', { name: /Escalate automatically/ }));
    expect(screen.getByRole('region', { name: 'Unsaved changes' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Discard' }));
    expect(screen.getByRole('switch', { name: /Escalate automatically/ })).toBeChecked();
  });

  it('resets to defaults after confirmation', async () => {
    renderConsole(<RulesTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Reset to defaults' }));
    const dialog = await screen.findByRole('alertdialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Reset to defaults' }));
    await waitFor(() => expect(state.update).toHaveBeenCalledWith(expect.objectContaining({ approvalSlaHours: 48, allowSelfApproval: false })));
  });

  it('locks self-approval for admins who are not super admins', () => {
    renderConsole(<RulesTab />, { roles: ['ADMIN'] });
    expect(screen.getByRole('switch', { name: /Allow self-approval/ })).toBeDisabled();
    expect(screen.getByText('Only super admins can change self-approval.')).toBeInTheDocument();
    expect(screen.getByLabelText('Approval target (hours)')).not.toBeDisabled();
  });

  it('disables edits for roles without cfgEdit', () => {
    renderConsole(<RulesTab />, { roles: ['FINANCE'] });
    expect(screen.getByLabelText('Approval target (hours)')).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Reset to defaults' })).toBeNull();
  });

  it('shows loading, error and empty states', () => {
    state.config = null;
    state.loading = true;
    const { unmount } = renderConsole(<RulesTab />);
    expect(screen.getByLabelText('Loading platform rules')).toBeInTheDocument();
    unmount();
    state.loading = false;
    state.error = Object.assign(new Error('boom'), { extensions: { retryable: true } });
    const second = renderConsole(<RulesTab />);
    fireEvent.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(state.refetch).toHaveBeenCalled();
    second.unmount();
    state.error = null;
    renderConsole(<RulesTab />);
    expect(screen.getByText('Platform rules are not available')).toBeInTheDocument();
  });

  it('navigates from the reference data shortcuts', () => {
    renderConsole(<RulesTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Event categories' }));
    fireEvent.click(screen.getByRole('button', { name: 'Provinces and cities' }));
    fireEvent.click(screen.getByRole('button', { name: 'Banks, KYB types, reasons' }));
    expect(push.mock.calls.map((c) => c[0])).toEqual(['/events/categories', '/events/locations', '/config/refdata']);
  });

  it('offers the notification channels the platform lists, plus the contract value "both", and no other', () => {
    renderConsole(<RulesTab />);
    const select = screen.getByLabelText('Organizer notification channel') as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.textContent)).toEqual(['Email', 'In-app', 'Email and in-app']);
  });

  it('disables the channel selects while the platform list is unavailable, keeping the saved value', () => {
    referenceFixtures.NOTIFICATION_CHANNEL = 'error';
    renderConsole(<RulesTab />);
    const select = screen.getByLabelText('Organizer notification channel') as HTMLSelectElement;
    expect(select).toBeDisabled();
    expect(select.value).toBe('EMAIL');
    referenceFixtures.NOTIFICATION_CHANNEL = [{ code: 'EMAIL', name: 'Email' }, { code: 'IN_APP', name: 'In-app' }, { code: 'SMS', name: 'SMS' }];
  });
});
