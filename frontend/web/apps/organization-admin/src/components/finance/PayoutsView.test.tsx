import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { PayoutsView, type PayoutsViewProps } from './PayoutsView';

const acct = (o: Record<string, unknown>) => ({
  id: 'a1', accountNumber: 'ESC-1', eventId: 'e1', eventTitle: 'Fixture Fest', currentBalance: '1000', totalDeposits: '1200',
  totalWithdrawals: '0', totalRefunds: '0', totalCommissions: '60', pendingWithdrawals: '0', currency: 'ZMW',
  status: 'PAYOUT_ELIGIBLE', lockUntil: null, payoutEligibleAt: '2026-09-19T00:00:00Z', ...o,
});
const payout = (o: Record<string, unknown>) => ({
  id: 'p1', requestId: 'PO-1', organizerId: 'u', eventId: 'e1', eventTitle: 'Fixture Fest', requestedAmount: '500', settledAmount: '500',
  currency: 'ZMW', status: 'PENDING', payoutMethod: 'BANK_TRANSFER', requestedAt: '2026-10-01T10:00:00Z', approvedAt: null, processedAt: null,
  rejectionReason: null, bankName: 'Zanaco', accountNumber: '0123456784821', bankAccountName: 'Fixture Ltd', notes: null, ...o,
});

const base = (o: Partial<PayoutsViewProps> = {}): PayoutsViewProps => ({
  accounts: [acct({}) as never, acct({ id: 'a2', eventTitle: 'Held Fest', status: 'HOLD', currentBalance: '300' }) as never],
  payouts: [payout({}) as never, payout({ id: 'p2', requestId: 'PO-2', status: 'COMPLETED', settledAmount: '200' }) as never],
  loading: false,
  orgActive: true,
  canRequest: true,
  onCancelPayout: vi.fn(),
  renderLedger: (a, close) => <div role="dialog" aria-label="ledger">{a.eventTitle}<button onClick={close}>x</button></div>,
  renderPayoutDialog: (id) => <div role="dialog" aria-label="request">{String(id)}</div>,
  ...o,
});

describe('PayoutsView', () => {
  it('shows KPIs derived from escrow and payouts', () => {
    render(<PayoutsView {...base()} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Escrow & payouts' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Held in escrow' })).toHaveTextContent('K 300.00');
    expect(screen.getByRole('group', { name: 'Withdrawable now' })).toHaveTextContent('K 1,000.00');
    expect(screen.getByRole('group', { name: 'Pending payouts' })).toHaveTextContent('K 500.00');
    expect(screen.getByRole('group', { name: 'Paid out to date' })).toHaveTextContent('K 200.00');
  });

  it('opens ledger and payout dialog from row actions, never from the row', () => {
    render(<PayoutsView {...base()} />);
    const row = within(screen.getByRole('table', { name: 'Escrow accounts' })).getByRole('row', { name: /Fixture Fest/ });
    fireEvent.click(row);
    expect(screen.queryByRole('dialog')).toBeNull();
    fireEvent.click(within(row).getByRole('button', { name: 'Ledger' }));
    expect(screen.getByRole('dialog', { name: 'ledger' })).toBeInTheDocument();
    fireEvent.click(screen.getByText('x'));
    fireEvent.click(within(row).getByRole('button', { name: 'Request payout' }));
    expect(screen.getByRole('dialog', { name: 'request' })).toHaveTextContent('a1');
  });

  it('cancels a pending payout after confirmation', () => {
    const onCancel = vi.fn();
    render(<PayoutsView {...base({ onCancelPayout: onCancel })} />);
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel payout' }));
    expect(onCancel).toHaveBeenCalledWith('p1', 'Cancelled by organizer');
  });

  it('locks requests until the organization is approved', () => {
    render(<PayoutsView {...base({ orgActive: false })} />);
    expect(screen.getByText(/unlock once your organization is approved/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Request payout' })).toBeNull();
  });

  it('shows empty, loading and error states', () => {
    const { rerender } = render(<PayoutsView {...base({ accounts: [], payouts: [] })} />);
    expect(screen.getByText('No escrow accounts match.')).toBeInTheDocument();
    expect(screen.getByText('No payout requests yet.')).toBeInTheDocument();
    rerender(<PayoutsView {...base({ accounts: [], payouts: [], loading: true })} />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    rerender(<PayoutsView {...base({ error: new Error('boom') })} />);
    expect(screen.getByRole('alert')).toHaveTextContent(/went wrong/);
  });

  it('filters escrow accounts by status', () => {
    render(<PayoutsView {...base()} />);
    fireEvent.change(screen.getByLabelText('Escrow status'), { target: { value: 'HOLD' } });
    expect(screen.queryByText('Fixture Fest', { selector: 'b' })).toBeNull();
    expect(screen.getByText('Held Fest')).toBeInTheDocument();
  });
});
