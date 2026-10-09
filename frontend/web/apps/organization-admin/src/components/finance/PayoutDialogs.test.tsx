import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PayoutRequestDialog } from './PayoutRequestDialog';
import { EscrowLedgerSheet } from './EscrowLedgerSheet';

const accounts = [
  { id: 'a1', accountNumber: 'ESC-1', eventId: 'e1', eventTitle: 'Fixture Fest', currentBalance: '1000', totalDeposits: '1200', totalWithdrawals: '0', totalRefunds: '10', totalCommissions: '60', pendingWithdrawals: null, currency: 'ZMW', status: 'PAYOUT_ELIGIBLE', lockUntil: null, payoutEligibleAt: null },
];
const props = () => ({
  accounts,
  destinations: [{ id: 'b1', label: 'Zanaco ****4821', isDefault: true }],
  escrowId: 'a1',
  bankId: 'b1',
  eligibility: { eligible: true, reasons: [], availableAmount: '940', currency: 'ZMW', opensAt: null, minimumAmount: '10' },
  eligibilityLoading: false,
  idempotencyKey: 'idem-1',
  onEscrowChange: vi.fn(),
  onSubmit: vi.fn().mockResolvedValue(undefined),
  onClose: vi.fn(),
});

describe('PayoutRequestDialog', () => {
  it('prefills the whole available balance and submits it in ngwee', async () => {
    const user = userEvent.setup();
    const p = props();
    render(<PayoutRequestDialog {...p} />);
    expect(screen.getByRole('dialog', { name: 'Request a payout' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Amount')).toHaveValue('940.00'));
    expect(screen.getByText(/Minimum payout K 10/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Request payout' }));
    await waitFor(() => expect(p.onSubmit).toHaveBeenCalledWith({ escrowId: 'a1', bankId: 'b1', amount: 94000 }));
    await waitFor(() => expect(p.onClose).toHaveBeenCalled());
  });
  it('adopts the default destination when the bank accounts arrive after the dialog opened', async () => {
    const user = userEvent.setup();
    const p = props();
    const { rerender } = render(<PayoutRequestDialog {...p} bankId="" destinations={[]} />);
    rerender(<PayoutRequestDialog {...p} />);
    await waitFor(() => expect(screen.getByLabelText('Amount')).toHaveValue('940.00'));
    await user.click(screen.getByRole('button', { name: 'Request payout' }));
    await waitFor(() => expect(p.onSubmit).toHaveBeenCalledWith({ escrowId: 'a1', bankId: 'b1', amount: 94000 }));
  });
  it('enforces the minimum and focuses the amount', async () => {
    const user = userEvent.setup();
    const p = props();
    render(<PayoutRequestDialog {...p} />);
    const amount = screen.getByLabelText('Amount');
    await waitFor(() => expect(amount).toHaveValue('940.00'));
    await user.clear(amount);
    await user.type(amount, '5');
    await user.click(screen.getByRole('button', { name: 'Request payout' }));
    expect(await screen.findAllByText('The minimum payout is K 10.00')).not.toHaveLength(0);
    await waitFor(() => expect(amount).toHaveFocus());
    expect(p.onSubmit).not.toHaveBeenCalled();
  });
  it('enforces the available balance as the maximum', async () => {
    const user = userEvent.setup();
    const p = props();
    render(<PayoutRequestDialog {...p} />);
    const amount = screen.getByLabelText('Amount');
    await user.clear(amount);
    await user.type(amount, '1000');
    await user.click(screen.getByRole('button', { name: 'Request payout' }));
    expect((await screen.findAllByText('Maximum amount is K 940.00')).length).toBeGreaterThan(0);
    expect(p.onSubmit).not.toHaveBeenCalled();
  });
  it('lists blocked reasons and refuses to submit', async () => {
    const user = userEvent.setup();
    const p = props();
    render(<PayoutRequestDialog {...p} eligibility={{ ...p.eligibility, eligible: false, reasons: ['HOLD_NOT_ELAPSED', 'PAYOUT_ALREADY_REQUESTED'] }} />);
    expect(screen.getByText('Not eligible yet')).toBeInTheDocument();
    expect(screen.getByText(/hold after the event has not elapsed/)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Amount'), '20');
    await user.click(screen.getByRole('button', { name: 'Request payout' }));
    expect((await screen.findAllByText('This event is not eligible for a payout yet')).length).toBeGreaterThan(0);
    expect(p.onSubmit).not.toHaveBeenCalled();
  });
  it('warns when there is no verified destination', async () => {
    const user = userEvent.setup();
    const p = props();
    render(<PayoutRequestDialog {...p} destinations={[]} bankId="" />);
    await user.click(screen.getByRole('button', { name: 'Request payout' }));
    expect((await screen.findAllByText('Add and verify a bank account first')).length).toBeGreaterThan(0);
    expect(p.onSubmit).not.toHaveBeenCalled();
  });
  it('maps a server error onto the amount field and stays open', async () => {
    const user = userEvent.setup();
    const p = props();
    p.onSubmit.mockRejectedValue({ errors: [{ message: 'x', extensions: { errorCode: 'COMMAND_NOT_WELL_FORMED', classification: 'BAD_REQUEST', fields: [{ path: 'input.requestedAmount', constraint: 'Positive' }] } }] });
    render(<PayoutRequestDialog {...p} />);
    await waitFor(() => expect(screen.getByLabelText('Amount')).toHaveValue('940.00'));
    await user.click(screen.getByRole('button', { name: 'Request payout' }));
    await waitFor(() => expect(screen.getByLabelText('Amount')).toHaveAttribute('aria-invalid', 'true'));
    expect(p.onClose).not.toHaveBeenCalled();
  });
  it('guards a double submit', async () => {
    const user = userEvent.setup();
    const p = props();
    let release: () => void = () => undefined;
    p.onSubmit.mockImplementation(() => new Promise<void>((r) => (release = r)));
    render(<PayoutRequestDialog {...p} />);
    await waitFor(() => expect(screen.getByLabelText('Amount')).toHaveValue('940.00'));
    const b = screen.getByRole('button', { name: 'Request payout' });
    await user.click(b);
    await user.click(b);
    expect(p.onSubmit).toHaveBeenCalledTimes(1);
    release();
  });
  it('shows a checking state', () => {
    render(<PayoutRequestDialog {...props()} eligibility={null} eligibilityLoading />);
    expect(screen.getByRole('status')).toHaveTextContent('Checking eligibility');
  });
});

describe('EscrowLedgerSheet', () => {
  const rows = [{ id: 't1', type: 'CREDIT', category: 'TICKET_SALE', amount: '150', balanceAfter: '150', currency: 'ZMW', description: null, journalEntryId: 'JE-1', timestamp: '2026-09-01T10:00:00Z' }];
  it('renders totals and ledger rows', () => {
    render(<EscrowLedgerSheet account={accounts[0]} rows={rows} loading={false} onClose={() => undefined} />);
    expect(screen.getByRole('dialog', { name: /Escrow ledger · Fixture Fest/ })).toBeInTheDocument();
    expect(screen.getByText('K 1,000.00')).toBeInTheDocument();
    expect(screen.getByText('JE-1')).toBeInTheDocument();
    expect(screen.getByText('+K 150.00')).toBeInTheDocument();
  });
  it('shows the empty ledger', () => {
    render(<EscrowLedgerSheet account={accounts[0]} rows={[]} loading={false} onClose={() => undefined} />);
    expect(screen.getByText('No transactions yet.')).toBeInTheDocument();
  });
});
