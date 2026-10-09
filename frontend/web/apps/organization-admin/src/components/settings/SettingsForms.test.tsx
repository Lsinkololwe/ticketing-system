// @vitest-environment jsdom
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';
import type { NotificationPrefs, SettingsOrganization } from '@/lib/api/settings';
import { OrgProfileSection } from './OrgProfileSection';
import { OrgSettingsSection } from './OrgSettingsSection';
import { NotificationPrefsSection } from './NotificationPrefsSection';
import { MyProfileSection } from './MyProfileSection';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});

// Fixtures live in the test only.
const org: SettingsOrganization = {
  id: 'o1', name: 'Fixture Org', slug: 'fixture-org', tagline: 'Tag', description: 'Desc', logoUrl: null, bannerUrl: null, website: 'https://x.test',
  socialLinks: { facebook: null, instagram: null, twitter: null, linkedin: null, youtube: null, tiktok: null }, businessType: 'LTD', taxId: '1002345678', businessRegistrationNumber: '12', businessPhone: '+260211000000',
  businessEmail: 'a@b.test', businessAddress: { addressLine1: 'Plot 1', addressLine2: null, city: 'Lusaka', province: 'Lusaka', country: 'Zambia', postalCode: null }, status: 'ACTIVE', settings: null,
  commissionRate: 5, deletionRequestedAt: null, deletionScheduledFor: null,
};
const flags = { requireEventApproval: true, allowMembersToInvite: false, inviteRequiresApproval: false, managersCanViewFinancials: true, adminsCanRequestPayouts: true, notifyOwnerOnMemberJoin: true, notifyOwnerOnEventCreated: true, notifyOwnerOnPayoutRequest: true };
const prefs: NotificationPrefs = { emailEnabled: true, smsEnabled: true, whatsappEnabled: true, pushEnabled: true, inAppEnabled: true, ticketNotifications: true, eventReminders: true, eventUpdates: true, paymentNotifications: true, teamNotifications: true, marketingEmails: false, systemAnnouncements: true, reminderHoursBefore: 24, quietHoursStart: '22:00', quietHoursEnd: '07:00', timezone: 'Africa/Lusaka' };
const me = { id: 'u', firstName: 'Ann', lastName: 'Lee', fullName: 'Ann Lee', email: 'a@b.test', phoneNumber: '+260977000000' };

const wrap = (ui: ReactNode) => render(<SnackbarProvider>{ui}</SnackbarProvider>);
const gqlError = (errorCode: string, classification = 'FAILED_PRECONDITION') => ({ errors: [{ message: 'x', extensions: { errorCode, classification } }] });
const saveBar = () => screen.queryByRole('region', { name: 'Unsaved changes' });

describe('OrgProfileSection form', () => {
  const setup = (over: Partial<React.ComponentProps<typeof OrgProfileSection>> = {}) => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    wrap(<OrgProfileSection organization={org} canEdit slugAvailable={null} onCheckSlug={() => undefined} onSave={onSave} {...over} />);
    return onSave;
  };

  it('shows the save bar only once edited and validates the name with focus on the first error', async () => {
    const user = userEvent.setup();
    const onSave = setup();
    expect(saveBar()).toBeNull();
    const name = screen.getByLabelText(/Organization name/);
    await user.clear(name);
    await user.type(name, 'A');
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    expect(await screen.findAllByText('Enter the organization name')).not.toHaveLength(0);
    await waitFor(() => expect(name).toHaveFocus());
    expect(onSave).not.toHaveBeenCalled();
  });

  it('submits the parsed payload', async () => {
    const user = userEvent.setup();
    const onSave = setup();
    const name = screen.getByLabelText(/Organization name/);
    await user.clear(name);
    await user.type(name, '  New name ');
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ name: 'New name', slug: 'fixture-org' })));
  });

  it('rejects an invalid logo URL', async () => {
    const user = userEvent.setup();
    const onSave = setup();
    await user.type(screen.getByLabelText('Website'), 'not a url');
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    expect((await screen.findAllByText(/valid web address/)).length).toBeGreaterThan(0);
    expect(onSave).not.toHaveBeenCalled();
  });

  it('maps SLUG_TAKEN from the server onto the slug field', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockRejectedValue(gqlError('SLUG_TAKEN'));
    setup({ onSave });
    const slug = screen.getByLabelText(/Web address/);
    await user.clear(slug);
    await user.type(slug, 'other-slug');
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('That web address is taken. Try another.')).toBeInTheDocument();
    await waitFor(() => expect(slug).toHaveFocus());
  });

  it('blocks a slug already reported taken, checks it on blur, and submits once on double click', async () => {
    const user = userEvent.setup();
    const check = vi.fn();
    const onSave = vi.fn().mockImplementation(() => new Promise((r) => setTimeout(r, 30)));
    setup({ onCheckSlug: check, slugAvailable: false, onSave });
    const slug = screen.getByLabelText(/Web address/);
    await user.clear(slug);
    await user.type(slug, 'other-slug');
    await user.tab();
    expect(check).toHaveBeenCalledWith('other-slug');
    expect(screen.getByText('Already taken')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('That web address is taken. Try another.')).toBeInTheDocument();
    expect(onSave).not.toHaveBeenCalled();
  });

  it('ignores a second submit while saving', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockImplementation(() => new Promise((r) => setTimeout(r, 40)));
    setup({ onSave });
    await user.type(screen.getByLabelText('Website'), 'https://x.test/a.png');
    const btn = await screen.findByRole('button', { name: 'Save changes' });
    await user.dblClick(btn);
    await waitFor(() => expect(onSave).toHaveBeenCalled());
    expect(onSave).toHaveBeenCalledTimes(1);
  });

  it('is read-only for non editors', () => {
    setup({ canEdit: false });
    expect(screen.getByText(/Only the owner and admins can edit/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Organization name/)).toBeDisabled();
    expect(saveBar()).toBeNull();
  });

  it('discard restores the saved values and hides the bar', async () => {
    const user = userEvent.setup();
    setup();
    const name = screen.getByLabelText(/Organization name/);
    await user.type(name, 'X');
    await user.click(await screen.findByRole('button', { name: 'Discard' }));
    await waitFor(() => expect(name).toHaveValue('Fixture Org'));
    expect(saveBar()).toBeNull();
  });
});

describe('OrgSettingsSection form', () => {
  it('toggles and saves the full flag set for the owner, locks for others', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    const { unmount } = render(<SnackbarProvider><OrgSettingsSection settings={flags} isOwner onSave={onSave} /></SnackbarProvider>);
    await user.click(screen.getByRole('switch', { name: 'Allow members to invite' }));
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ ...flags, allowMembersToInvite: true }));
    unmount();
    wrap(<OrgSettingsSection settings={flags} isOwner={false} onSave={onSave} />);
    expect(screen.getByRole('switch', { name: 'Allow members to invite' })).toBeDisabled();
  });

  it('shows a server error banner', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockRejectedValue(gqlError('RESOURCE_CONFLICT'));
    wrap(<OrgSettingsSection settings={flags} isOwner onSave={onSave} />);
    await user.click(screen.getByRole('switch', { name: 'Allow members to invite' }));
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Someone else changed this. Reload and try again.')).toBeInTheDocument();
  });
});

describe('NotificationPrefsSection form', () => {
  it('locks essential categories and saves changes with null for blank quiet hours', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    wrap(<NotificationPrefsSection prefs={prefs} onSave={onSave} />);
    expect(screen.getByRole('switch', { name: 'Ticket notifications' })).toBeDisabled();
    expect(screen.getByRole('switch', { name: 'Payment notifications' })).toBeDisabled();
    await user.click(screen.getByRole('switch', { name: 'Marketing emails' }));
    await user.clear(screen.getByLabelText('Quiet hours start'));
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ marketingEmails: true, quietHoursStart: null, quietHoursEnd: '07:00', reminderHoursBefore: 24 })));
  });
});

describe('MyProfileSection form', () => {
  it('validates the first name, focusing it', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    wrap(<MyProfileSection me={me} onSave={onSave} />);
    const first = screen.getByLabelText(/First name/);
    await user.clear(first);
    await user.type(first, 'A');
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    expect((await screen.findAllByText('Enter your first name')).length).toBeGreaterThan(0);
    await waitFor(() => expect(first).toHaveFocus());
    expect(onSave).not.toHaveBeenCalled();
  });

  it('submits trimmed names and surfaces an unavailable error as a toast', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValueOnce(undefined);
    wrap(<MyProfileSection me={me} onSave={onSave} />);
    const first = screen.getByLabelText(/First name/);
    await user.clear(first);
    await user.type(first, ' Anna ');
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith('Anna', 'Lee'));
  });

  it('maps a server error to the banner', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockRejectedValue(gqlError('ORGANIZER_NOT_APPROVED'));
    wrap(<MyProfileSection me={me} onSave={onSave} />);
    await user.type(screen.getByLabelText(/Last name/), 'x');
    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Your organization must be approved before you can do that.')).toBeInTheDocument();
  });
});

describe('OrgProfileSection extras', () => {
  const noop = () => undefined;
  it('edits tagline, website and social links, and keeps business info read-only once approved', () => {
    const onSave = vi.fn();
    wrap(<OrgProfileSection organization={org} canEdit slugAvailable={null} onCheckSlug={noop} onSave={onSave} />);
    expect(screen.getByLabelText('Tagline')).not.toHaveAttribute('readonly');
    expect(screen.getByLabelText('Website')).not.toHaveAttribute('readonly');
    expect(screen.getByLabelText('LinkedIn')).toBeInTheDocument();
    expect(screen.getByLabelText('TPIN')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('Commission rate')).toHaveValue('5%');
  });
  it('unlocks the business information while the application can still change', () => {
    wrap(<OrgProfileSection organization={{ ...org, status: 'CHANGES_REQUESTED' }} canEdit slugAvailable={null} onCheckSlug={noop} onSave={vi.fn()} />);
    expect(screen.getByLabelText('TPIN')).not.toHaveAttribute('readonly');
    expect(screen.getByLabelText('Street address')).not.toHaveAttribute('readonly');
  });
});
