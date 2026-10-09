import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { menuAction } from '@/test/menu';
import { renderConsole } from '@/test/render';

const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => '/users/orgs',
  useSearchParams: () => new URLSearchParams(),
}));

const actions = {
  suspendOrganization: vi.fn().mockResolvedValue({}),
  unsuspendOrganization: vi.fn().mockResolvedValue({}),
  updateOrganizationStatus: vi.fn().mockResolvedValue({}),
  verifyPayoutAccount: vi.fn().mockResolvedValue({}),
};
const list = { rows: [] as unknown[], total: 0, pageSize: 20, loading: false, error: undefined as Error | undefined, refetch: vi.fn() };
const orgState = { organization: null as unknown, loading: false, error: undefined as Error | undefined, refetch: vi.fn() };
const listCall = vi.fn();

vi.mock('@pml.tickets/shared/api/admin/modules/identity-admin', async (orig) => ({
  ...(await orig<object>()),
  useIdentityOrganizations: (o: unknown) => {
    listCall(o);
    return list;
  },
  useIdentityOrganization: () => orgState,
  useOrgAdminActions: () => actions,
  useOrgAdminMembers: () => ({ members: [{ id: 'm1', userId: 'u9', role: 'OWNER', status: 'ACTIVE', user: { id: 'u9', fullName: 'Owen Mulenga' } }], loading: false, refetch: vi.fn() }),
  useOrgAdminDocuments: () => ({ documents: [{ id: 'd1', documentType: 'TAX_CERT', fileName: 'tax.pdf', status: 'APPROVED', uploadedAt: '2026-02-01T00:00:00Z' }], loading: false, refetch: vi.fn() }),
  useOrgAdminEvents: () => ({ events: [{ id: 'e1', title: 'Jazz Night', status: 'PUBLISHED', eventDateTime: '2026-12-01T18:00:00Z', soldTickets: 12 }], loading: false, refetch: vi.fn() }),
}));

const setRate = vi.fn().mockResolvedValue({});
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  useSetOrganizationCommission: () => ({ setRate, loading: false }),
}));

import { UsersPage } from '../UsersPage';
import { OrgPage } from '../OrgPage';

const org = (over: Record<string, unknown> = {}) => ({
  id: 'o1', name: 'Lusaka Live', slug: 'lusaka-live', type: 'BUSINESS', status: 'ACTIVE', kybStatus: 'VERIFIED', ownerId: 'u9',
  owner: { id: 'u9', fullName: 'Owen Mulenga', email: 'owen@example.test' }, businessEmail: 'hello@lusaka.test', businessPhone: '+260971111111',
  businessAddress: { city: 'Lusaka', province: 'Lusaka' }, taxId: '1001', businessRegistrationNumber: 'R-1', verified: true, documentsVerified: true,
  payoutAccountVerified: false, memberCount: 1, totalEvents: 3, createdAt: '2026-01-01T00:00:00Z',
  payoutConfig: { commissionRate: 0.07, verified: false, isConfigured: true, bankAccount: { bankName: 'Zanaco', maskedAccountNumber: '****1234', accountHolderName: 'Lusaka Live Ltd', accountType: 'BUSINESS', verified: false } },
  ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(list, { rows: [org(), org({ id: 'o2', name: 'Copper Events', status: 'SUSPENDED', kybStatus: 'PENDING_REVIEW', payoutConfig: null })], total: 2, loading: false, error: undefined });
  orgState.organization = org();
  orgState.loading = false;
  orgState.error = undefined;
});

describe('Organizations table', () => {
  it('renders columns, commission text and navigation buttons', () => {
    renderConsole(<UsersPage tab="orgs" />);
    const t = screen.getByRole('table', { name: 'Organizations' });
    for (const h of ['Organization', 'Owner', 'Status', 'KYB', 'Commission', 'Payout account', 'Events']) expect(within(t).getByRole('columnheader', { name: h })).toBeInTheDocument();
    expect(screen.getByText('7%')).toBeInTheDocument();
    expect(screen.getByText('None')).toBeInTheDocument();
    menuAction('More actions for Lusaka Live', 'Open full page');
    expect(push).toHaveBeenCalledWith('/org/o1');
  });

  it('sends the KYB status, status and verified filters to the server', async () => {
    renderConsole(<UsersPage tab="orgs" />);
    fireEvent.change(screen.getByLabelText('KYB status'), { target: { value: 'PENDING_REVIEW' } });
    await waitFor(() => expect(listCall).toHaveBeenLastCalledWith(expect.objectContaining({ kybStatus: 'PENDING_REVIEW' })));
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'ACTIVE' } });
    fireEvent.change(screen.getByLabelText('Verified'), { target: { value: 'yes' } });
    await waitFor(() => expect(listCall).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'ACTIVE', verified: true })));
  });

  it('empty, loading and error states', () => {
    Object.assign(list, { rows: [], total: 0 });
    const a = renderConsole(<UsersPage tab="orgs" />);
    expect(screen.getByText('No organizations match.')).toBeInTheDocument();
    a.unmount();
    Object.assign(list, { loading: true });
    const b = renderConsole(<UsersPage tab="orgs" />);
    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
    b.unmount();
    Object.assign(list, { loading: false, error: new Error('x') });
    renderConsole(<UsersPage tab="orgs" />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });

  it('quick view opens with commercial info and Open full page', () => {
    renderConsole(<UsersPage tab="orgs" />);
    fireEvent.click(screen.getByRole('button', { name: 'Quick view Lusaka Live' }));
    const sheet = screen.getByRole('dialog');
    expect(within(sheet).getByText('Commercial')).toBeInTheDocument();
    fireEvent.click(within(sheet).getByRole('button', { name: 'Open full page' }));
    expect(push).toHaveBeenCalledWith('/org/o1');
  });

  it('suspend needs a reason', async () => {
    renderConsole(<UsersPage tab="orgs" />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Lusaka Live' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Suspend…' }));
    const dlg = screen.getByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Suspend organization' }));
    expect(await within(dlg).findByText(/Give a short reason/)).toBeInTheDocument();
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Fraud review' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Suspend organization' }));
    await waitFor(() => expect(actions.suspendOrganization).toHaveBeenCalledWith('o1', 'Fraud review'));
  });

  it('unsuspends and updates status', async () => {
    renderConsole(<UsersPage tab="orgs" />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Copper Events' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Unsuspend' }));
    await waitFor(() => expect(actions.unsuspendOrganization).toHaveBeenCalledWith('o2'));
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Lusaka Live' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Update status' }));
    const dlg = screen.getByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('New status'), { target: { value: 'INACTIVE' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Update status' }));
    await waitFor(() => expect(actions.updateOrganizationStatus).toHaveBeenCalledWith('o1', 'INACTIVE'));
  });

  it('commission override validates and saves the rate through the backend', async () => {
    renderConsole(<UsersPage tab="orgs" />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Lusaka Live' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Commission override' }));
    const dlg = screen.getByRole('dialog');
    expect(within(dlg).queryByText(/Not available yet/)).toBeNull();
    fireEvent.change(within(dlg).getByLabelText('Commission rate (%)'), { target: { value: '150' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Save rate' }));
    expect(await within(dlg).findByText('Enter 100 or fewer')).toBeInTheDocument();
    expect(setRate).not.toHaveBeenCalled();
    fireEvent.change(within(dlg).getByLabelText('Commission rate (%)'), { target: { value: '6.5' } });
    fireEvent.change(within(dlg).getByLabelText('Reason (optional)'), { target: { value: 'Volume deal' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Save rate' }));
    await waitFor(() => expect(setRate).toHaveBeenCalledWith('o1', 6.5, 'Volume deal'));
  });
});

describe('Organization page', () => {
  it('renders sections, payout account and team/events/documents', () => {
    renderConsole(<OrgPage id="o1" />);
    expect(screen.getByRole('heading', { level: 1, name: 'Lusaka Live' })).toBeInTheDocument();
    for (const h of ['Organization', 'Actions', 'Payout account', 'Team', 'Events', 'Verification documents']) expect(screen.getByRole('heading', { name: h })).toBeInTheDocument();
    expect(screen.getByText('Owen Mulenga', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByText('Jazz Night')).toBeInTheDocument();
    expect(screen.getByText('Tax certificate')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'View Jazz Night' }));
    expect(push).toHaveBeenCalledWith('/event/e1');
  });

  it('verifies a pending payout account', async () => {
    renderConsole(<OrgPage id="o1" />, { roles: ['ADMIN'] });
    fireEvent.click(screen.getByRole('button', { name: 'Verify' }));
    await waitFor(() => expect(actions.verifyPayoutAccount).toHaveBeenCalledWith('o1', true));
  });

  it('rejecting asks for confirmation then unverifies', async () => {
    renderConsole(<OrgPage id="o1" />);
    fireEvent.click(screen.getByRole('button', { name: 'Reject' }));
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Reject account' }));
    await waitFor(() => expect(actions.verifyPayoutAccount).toHaveBeenCalledWith('o1', false));
  });

  it('review application links to approvals for pending orgs', () => {
    orgState.organization = org({ status: 'PENDING_REVIEW' });
    renderConsole(<OrgPage id="o1" />);
    fireEvent.click(screen.getByRole('button', { name: 'Review application' }));
    expect(push).toHaveBeenCalledWith('/approvals/orgs');
  });

  it('not found, back and error states', () => {
    orgState.organization = null;
    const a = renderConsole(<OrgPage id="zz" />);
    expect(screen.getByText('Organization not found')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'All organizations' }));
    expect(push).toHaveBeenCalledWith('/users/orgs');
    a.unmount();
    orgState.error = new Error('x');
    renderConsole(<OrgPage id="zz" />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
});
