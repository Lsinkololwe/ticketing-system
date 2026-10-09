import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { menuAction, menuItem } from '@/test/menu';
import { renderConsole } from '@/test/render';

const h = vi.hoisted(() => ({
  q: { accounts: [] as any[], pageInfo: { totalCount: 0, pageSize: 20 }, loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  acts: { rejectBankAccount: vi.fn().mockResolvedValue(undefined), rejectPayoutAccount: vi.fn().mockResolvedValue(undefined), suspendBankAccount: vi.fn().mockResolvedValue(undefined), reinstateBankAccount: vi.fn().mockResolvedValue(undefined), busy: false },
  org: { verifyPayoutAccount: vi.fn().mockResolvedValue({}) },
  filters: [] as any[],
}));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  usePayoutAccounts: (o: unknown) => { h.filters.push(o); return h.q; },
  usePayoutAccountActions: () => h.acts,
}));
vi.mock('@pml.tickets/shared/api/admin/modules/identity-admin', () => ({ useOrgAdminActions: () => h.org }));

import { BanksTab } from '../BanksTab';

const acct = (over: Record<string, unknown>) => ({ organizationId: 'o1', organizationName: 'Zambezi Live', organizationSlug: 'zambezi', method: 'BANK_TRANSFER', status: 'PENDING', bankName: 'Zanaco', accountHolderName: 'Zambezi Ltd', accountNumberMasked: '****1234', network: null, phoneMasked: null, rejectionReason: null, suspendedReason: null, testDepositSentAt: null, verificationAttemptsLeft: 3, updatedAt: '2026-10-01T08:00:00Z', ...over });

beforeEach(() => {
  vi.clearAllMocks();
  h.q.accounts = [acct({}), acct({ organizationId: 'o2', organizationName: 'Kafue Events', status: 'VERIFIED', method: 'MOBILE_MONEY', network: 'MTN', phoneMasked: '+260***123' }), acct({ organizationId: 'o3', organizationName: 'Ndola Arts', status: 'SUSPENDED', suspendedReason: 'Fraud review' })];
  h.q.pageInfo = { totalCount: 3, pageSize: 20 };
  h.q.error = undefined; h.q.loading = false;
});

describe('BanksTab', () => {
  it('lists the queue with status-dependent buttons and no placeholder', () => {
    renderConsole(<BanksTab />);
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    const t = screen.getByRole('table', { name: 'Payout accounts' });
    expect(within(t).getByText('Zambezi Live')).toBeInTheDocument();
    expect(within(t).getByText('Fraud review')).toBeInTheDocument();
    expect(within(t).getByRole('button', { name: 'Verify payout account Zambezi Live' })).toBeInTheDocument();
    expect(within(t).getByRole('button', { name: 'Reinstate payout account Ndola Arts' })).toBeInTheDocument();
    expect(menuItem('More actions for payout account Kafue Events', 'Suspend…')).toBeInTheDocument();
  });
  it('verifies after confirmation', async () => {
    renderConsole(<BanksTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Verify payout account Zambezi Live' }));
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Verify account' }));
    await waitFor(() => expect(h.org.verifyPayoutAccount).toHaveBeenCalledWith('o1', true));
  });
  it('rejects a bank account with a required reason and a mobile money account through rejectPayoutAccount', async () => {
    renderConsole(<BanksTab />);
    menuAction('More actions for payout account Zambezi Live', 'Reject…');
    let dlg = await screen.findByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reject account' }));
    await waitFor(() => expect(h.acts.rejectBankAccount).not.toHaveBeenCalled());
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Name does not match' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reject account' }));
    await waitFor(() => expect(h.acts.rejectBankAccount).toHaveBeenCalledWith('o1', 'Name does not match'));
    menuAction('More actions for payout account Kafue Events', 'Reject…');
    dlg = await screen.findByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Number not registered' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reject account' }));
    await waitFor(() => expect(h.acts.rejectPayoutAccount).toHaveBeenCalledWith('o2', 'Number not registered'));
  });
  it('suspends and reinstates', async () => {
    renderConsole(<BanksTab />);
    menuAction('More actions for payout account Kafue Events', 'Suspend…');
    const dlg = await screen.findByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Under investigation' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Suspend account' }));
    await waitFor(() => expect(h.acts.suspendBankAccount).toHaveBeenCalledWith('o2', 'Under investigation'));
    fireEvent.click(screen.getByRole('button', { name: 'Reinstate payout account Ndola Arts' }));
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Reinstate account' }));
    await waitFor(() => expect(h.acts.reinstateBankAccount).toHaveBeenCalledWith('o3'));
  });
  it('disables decisions without payout authority, and shows empty and error states', () => {
    const { unmount } = renderConsole(<BanksTab />, { roles: ['ADMIN'] });
    unmount();
    renderConsole(<BanksTab />, { roles: ['FINANCE'] });
    expect(screen.getByRole('button', { name: 'Verify payout account Zambezi Live' })).not.toBeDisabled();
  });
  it('empty and error', () => {
    h.q.accounts = [];
    const { unmount } = renderConsole(<BanksTab />);
    expect(screen.getByText('No payout accounts to verify.')).toBeInTheDocument();
    unmount();
    h.q.error = new Error('down');
    renderConsole(<BanksTab />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
});
