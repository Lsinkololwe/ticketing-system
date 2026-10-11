import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, within } from '@testing-library/react';
import { menuAction, menuItem } from '@/test/menu';
import { renderConsole } from '@/test/render';

const approvePayout = vi.fn(async () => ({ success: true, message: null, errorCode: null }));
const rejectPayout = vi.fn(async () => ({ success: true, message: null, errorCode: null }));
const hold = vi.fn(async () => undefined);
const release = vi.fn(async () => undefined);
let listState: { payouts: unknown[]; loading: boolean; error?: Error };

vi.mock('@pml.tickets/shared/api/admin/modules/finance', () => ({
  usePayoutRequestStats: () => ({ stats: { pendingPayoutRequests: 2, pendingPayoutAmount: 3000, approvedPayoutRequests: 1, processingPayoutRequests: 1, failedPayoutRequests: 1, completedPayoutRequests: 4 }, refetch: vi.fn() }),
  useFinanceDecisions: () => ({ approvePayout, rejectPayout, submitting: false }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/finance-ops', () => ({
  usePayoutOpsList: () => ({ ...listState, pageInfo: { totalCount: listState.payouts.length, pageSize: 20 }, refetch: vi.fn() }),
  usePayoutOpsDetail: () => ({ payout: null, loading: false, refetch: vi.fn() }),
  useEscrowOpsDetail: () => ({ account: null, loading: false }),
  usePayoutOps: () => ({ bulkRetry: vi.fn(), submitting: false }),
}));

vi.mock('@pml.tickets/shared/api/admin/modules/payments-ops', () => ({ usePayoutHoldActions: () => ({ hold, release, busy: false }) }));

import { PayoutsTab } from '../PayoutsTab';

const row = (over: Record<string, unknown>) => ({
  id: 'p1', requestId: 'PO-1', organizerId: 'o1', organization: { id: 'org-fixture', name: 'Zambezi Events' }, eventId: 'e1', eventTitle: 'Lusaka Jazz', escrowAccountId: 'esc1', bankAccountId: 'b1',
  requestedAmount: 1500, taxAmount: 0, settledAmount: 1500, currency: 'ZMW', status: 'PENDING', payoutMethod: 'BANK_TRANSFER', requestedAt: '2026-09-01T08:00:00Z',
  requestedById: 'u-other', retryCount: 0, bankName: 'Zanaco', accountNumber: '****7890', reviewStatus: 'NONE', bankAccount: { id: 'b1', isVerified: true, status: 'VERIFIED', accountHolderName: 'Z' }, ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  listState = { payouts: [row({}), row({ id: 'p2', requestId: 'PO-2', status: 'FAILED' }), row({ id: 'p3', requestId: 'PO-3', requestedById: 'staff-1' })], loading: false };
});

describe('PayoutsTab', () => {
  it('renders summary cards, headers and row action buttons', () => {
    renderConsole(<PayoutsTab />);
    expect(screen.getByText('Awaiting approval')).toBeInTheDocument();
    expect(screen.getByText('2 · K 3,000')).toBeInTheDocument();
    for (const h of ['Request', 'Event', 'Amount', 'Destination', 'Requested by', 'Status']) expect(screen.getByRole('columnheader', { name: h })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open payout PO-1' })).toBeInTheDocument();
    expect(menuItem('More actions for payout PO-1', 'Approve')).toBeInTheDocument();
    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
    expect(menuItem('More actions for payout PO-2', 'Retry')).toBeInTheDocument();
  });
  it('shows the empty, loading and error states', () => {
    listState = { payouts: [], loading: false };
    const { unmount } = renderConsole(<PayoutsTab />);
    expect(screen.getByText('No payout requests yet.')).toBeInTheDocument();
    unmount();
    listState = { payouts: [], loading: false, error: new Error('boom') };
    renderConsole(<PayoutsTab />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
  it('blocks self approval (dual control)', () => {
    renderConsole(<PayoutsTab />, { id: 'staff-1' });
    menuAction('More actions for payout PO-3', 'Approve');
    expect(screen.getByText('You cannot approve this payout')).toBeInTheDocument();
    expect(approvePayout).not.toHaveBeenCalled();
  });
  it('approves after confirmation', async () => {
    renderConsole(<PayoutsTab />);
    menuAction('More actions for payout PO-1', 'Approve');
    fireEvent.click(screen.getByRole('button', { name: 'Approve payout' }));
    await waitFor(() => expect(approvePayout).toHaveBeenCalledWith('p1'));
  });
  it('requires a reason to reject', async () => {
    renderConsole(<PayoutsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Open payout PO-1' }));
    fireEvent.click(screen.getByRole('button', { name: 'Reject…' }));
    const dlg = screen.getByRole('dialog', { name: /Reject payout PO-1/ });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reject payout' }));
    await waitFor(() => expect(rejectPayout).not.toHaveBeenCalled());
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Account mismatch' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reject payout' }));
    await waitFor(() => expect(rejectPayout).toHaveBeenCalledWith('p1', 'Account mismatch'));
  });
  it('disables decisions for roles without payoutDecide', () => {
    renderConsole(<PayoutsTab />, { roles: [] });
    expect(menuItem('More actions for payout PO-1', 'Approve')).toHaveAttribute('aria-disabled', 'true');
  });
  it('holds a payout with a required reason and releases a held one', async () => {
    listState = { payouts: [row({}), row({ id: 'p4', requestId: 'PO-4', status: 'ON_HOLD' })], loading: false };
    renderConsole(<PayoutsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Open payout PO-1' }));
    fireEvent.click(screen.getByRole('button', { name: 'Hold…' }));
    const dlg = screen.getByRole('dialog', { name: /Hold payout PO-1/ });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Hold payout' }));
    await waitFor(() => expect(hold).not.toHaveBeenCalled());
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Compliance check pending' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Hold payout' }));
    await waitFor(() => expect(hold).toHaveBeenCalledWith('p1', 'Compliance check pending'));
  });
  it('offers Release hold on an ON_HOLD payout', async () => {
    listState = { payouts: [row({ id: 'p4', requestId: 'PO-4', status: 'ON_HOLD' })], loading: false };
    renderConsole(<PayoutsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Open payout PO-4' }));
    fireEvent.click(screen.getByRole('button', { name: 'Release hold' }));
    const dlg = screen.getByRole('dialog', { name: /Release the hold on PO-4/ });
    fireEvent.click(within(dlg).getAllByRole('button', { name: 'Release hold' })[0]);
    await waitFor(() => expect(release).toHaveBeenCalledWith('p4', undefined));
  });
});
