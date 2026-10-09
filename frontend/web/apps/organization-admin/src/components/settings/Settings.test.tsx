import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PlatformRulesSection } from './PlatformRulesSection';
import { DangerSection } from './DangerSection';
import { SettingsView, parseTab, SETTINGS_TABS } from './SettingsView';
import { flattenRules, type PlatformRulesView } from '@/lib/settings/platformRules';

const rules: PlatformRulesView = {
  updatedAt: '2026-10-03T09:00:00Z', updatedBy: 'Fixture Admin', commissionRate: 5, minimumPayout: 10, escrowHoldDays: 7, currency: 'ZMW',
  reservationHoldMinutes: 10, reservationGraceMinutes: 5, maxTicketsPerBooking: 8, refundCutoffHours: 24, rescheduleLimit: 3,
  approvalSlaHours: 48, approvalWarnHours: 36, autoEscalation: true, escalationDelayHours: 12, requireCommentsOnChangesRequested: true, requireCommentsOnRejection: true,
  refundPolicies: [{ code: 'FLEXIBLE', label: 'Flexible', summary: 'Full refund.' }], categories: ['Music'], provinces: ['Lusaka'], cities: ['Lusaka'], banks: ['Zanaco'], documentTypes: ['ID'], cancellationReasons: ['Other'],
};

describe('PlatformRulesSection', () => {
  it('renders Not available yet without data', () => {
    render(<PlatformRulesSection rules={null} />);
    expect(screen.getByTestId('not-available')).toBeInTheDocument();
  });
  it('lists values and marks changed ones New', () => {
    const seen = flattenRules({ ...rules, minimumPayout: 5 });
    render(<PlatformRulesSection rules={rules} seen={seen} />);
    expect(screen.getByText(/Set by the platform/)).toBeInTheDocument();
    expect(screen.getAllByText('New')).toHaveLength(1);
    expect(screen.getByText('K 10')).toBeInTheDocument();
    expect(screen.getByText('Full refund.')).toBeInTheDocument();
  });
});

describe('DangerSection', () => {
  const base = { organizationName: 'Fixture Org', isOwner: true, onRequestDeletion: vi.fn().mockResolvedValue(undefined), onCancelDeletion: vi.fn() };
  it('disables deletion when blocked or not the owner', () => {
    const { rerender } = render(<DangerSection {...base} blockers={{ liveEventsWithSales: 2, openPayouts: 1 }} />);
    expect(screen.getByRole('alert')).toHaveTextContent('2 live event');
    expect(screen.getByRole('button', { name: 'Delete organization' })).toBeDisabled();
    rerender(<DangerSection {...base} isOwner={false} />);
    expect(screen.getByText(/Only the owner can delete/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete organization' })).toBeDisabled();
  });
  it('requires the exact organization name before deleting', async () => {
    const user = userEvent.setup();
    render(<DangerSection {...base} />);
    await user.click(screen.getByRole('button', { name: 'Delete organization' }));
    await user.type(screen.getByLabelText('Organization name'), 'Wrong');
    await user.click(screen.getAllByRole('button', { name: 'Delete organization' }).at(-1)!);
    expect(await screen.findByText('Type the exact organization name')).toBeInTheDocument();
    expect(base.onRequestDeletion).not.toHaveBeenCalled();
    await user.clear(screen.getByLabelText('Organization name'));
    await user.type(screen.getByLabelText('Organization name'), 'Fixture Org');
    await user.click(screen.getAllByRole('button', { name: 'Delete organization' }).at(-1)!);
    await waitFor(() => expect(base.onRequestDeletion).toHaveBeenCalled());
  });
  it('shows the scheduled deletion and lets the owner cancel it', () => {
    render(<DangerSection {...base} scheduledFor="2027-01-04T00:00:00Z" />);
    expect(screen.getByText(/scheduled for deletion/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel deletion' }));
    expect(base.onCancelDeletion).toHaveBeenCalled();
  });
});

describe('SettingsView', () => {
  const panels = Object.fromEntries(SETTINGS_TABS.map((t) => [t.id, <p key={t.id}>{`panel ${t.id}`}</p>])) as never;
  it('shows tabs and switches panel; hides danger for non owners', () => {
    const onTab = vi.fn();
    render(<SettingsView tab="profile" onTab={onTab} isOwner={false} panels={panels} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Settings' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Delete organization' })).toBeNull();
    fireEvent.click(screen.getByRole('tab', { name: 'Platform rules' }));
    expect(onTab).toHaveBeenCalledWith('platform');
  });
  it('parses tabs safely', () => {
    expect(parseTab('danger', false)).toBe('profile');
    expect(parseTab('danger', true)).toBe('danger');
    expect(parseTab('nope', true)).toBe('profile');
  });
});
