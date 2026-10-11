import { describe, expect, it, vi } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { menuItem } from '@/test/menu';
import { renderConsole } from '@/test/render';

const createAdminRefund = vi.fn(async () => ({ success: true, message: null, errorCode: null }));
const recordOutcome = vi.fn(async () => ({ success: true, message: null, errorCode: null }));
const daysAgo = (d: number) => new Date(Date.now() - d * 864e5).toISOString();

vi.mock('@pml.tickets/shared/api/admin/modules/finance', () => ({
  useFinanceDecisions: () => ({ approveRefund: vi.fn(), rejectRefund: vi.fn(), setEscrowStatus: vi.fn(), submitting: false }),
  useRefundStatusCount: () => ({ count: 1, loading: false }),
  usePayoutRequestStats: () => ({ stats: null }),
  useAdminEscrowAccounts: () => ({
    accounts: [{ id: 'a1', accountNumber: 'ESC-1001', eventTitle: 'Jazz', organization: { id: 'org-fixture', name: 'Zambezi' }, status: 'ACTIVE', lockUntil: null, currentBalance: 900, totalDeposits: 1000, totalRefunds: 100, payoutEligibleAt: null }],
    pageInfo: { totalCount: 1, pageSize: 20 }, loading: false, refetch: vi.fn(),
  }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/finance-ops', () => {
  const refund = (id: string, age: number) => ({ id, requestId: id, ticketId: 't', ticketNumber: `TK-${id}`, eventId: 'evt12345', buyerId: 'buyer1234', refundAmount: 100, refundPercentage: 100, policyApplied: 'MODERATE', status: 'PENDING', requestType: 'USER_REQUESTED', reason: 'x', requestedAt: new Date(Date.now() - age * 864e5).toISOString(), requestedById: 'b' });
  const rows = [refund('RF-1', 1), refund('RF-2', 3), refund('RF-3', 6)];
  return {
    useRefundOpsList: () => ({ refunds: rows, pageInfo: { totalCount: 3, pageSize: 20 }, loading: false, refetch: vi.fn() }),
    usePendingRefunds: () => ({ rows, total: 3, loading: false }),
    useRefundOps: () => ({ createAdminRefund, bulkApprove: vi.fn(), processRefund: vi.fn(), submitting: false }),
    useRefundOpsDetail: () => ({ refund: null }),
    useEscrowOpsDetail: () => ({ account: null, loading: false, refetch: vi.fn() }),
    useEscrowTransactions: () => ({ transactions: [], pageInfo: { totalCount: 0 }, loading: false }),
    useEscrowOps: () => ({ submitting: false }),
    useEscrowConsistencyCheck: () => vi.fn(async () => ({ rows: [], error: null })),
    useChargebackList: () => ({ chargebacks: [{ id: 'c1', chargebackId: 'CB-1', originalTransactionId: 'PAY-1', ticketId: 't', eventId: 'event123', customerId: 'cust1234', chargebackAmount: 250, chargebackFee: 0, reason: 'FRAUD', status: 'DISPUTED', recoveryStatus: 'NOT_STARTED', fundSource: 'ORGANIZER_ESCROW', receivedAt: daysAgo(1), responseDeadline: new Date(Date.now() + 864e5 * 3).toISOString(), recoveredAmount: 0 }], pageInfo: { totalCount: 1, pageSize: 20 }, loading: false, refetch: vi.fn() }),
    useChargebackQueue: () => ({ open: [{ id: 'c1', chargebackAmount: 250, responseDeadline: new Date(Date.now() + 36e5).toISOString(), status: 'DISPUTED' }], openAmount: 250, recoveryCount: 0, loaded: true }),
    useChargebackOps: () => ({ recordOutcome, receiveChargeback: vi.fn(), startReview: vi.fn(), acceptChargeback: vi.fn(), disputeChargeback: vi.fn(), submitting: false }),
  };
});

import { RefundsTab } from '../RefundsTab';
import { EscrowTab } from '../EscrowTab';
import { ChargebacksTab } from '../ChargebacksTab';

describe('RefundsTab', () => {
  it('shows escalation badges by age and action buttons', () => {
    renderConsole(<RefundsTab />);
    expect(screen.getByText('Escalated · 2 d')).toBeInTheDocument();
    expect(screen.getByText('Escalated again · 5 d')).toBeInTheDocument();
    expect(menuItem('More actions for refund RF-1', 'Approve')).toBeInTheDocument();
    expect(menuItem('More actions for refund RF-2', 'Reject…')).toBeInTheDocument();
  });
  it('validates the new refund form', async () => {
    renderConsole(<RefundsTab />);
    fireEvent.click(screen.getByRole('button', { name: /New refund/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Create refund' }));
    await waitFor(() => expect(screen.getAllByText(/is required/).length).toBeGreaterThan(0));
    expect(createAdminRefund).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Ticket ID'), { target: { value: 'tk-9' } });
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Goodwill refund' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create refund' }));
    await waitFor(() => expect(createAdminRefund).toHaveBeenCalledWith('tk-9', 'Goodwill refund'));
  });
});

describe('EscrowTab', () => {
  it('lists accounts with Open and Lock buttons', () => {
    renderConsole(<EscrowTab />);
    expect(screen.getByText('ESC-1001')).toBeInTheDocument();
    expect(menuItem('More actions for escrow ESC-1001', 'Lock')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Consistency check' })).toBeInTheDocument();
  });
});

describe('ChargebacksTab', () => {
  it('shows tiles, deadline warning and records an outcome', async () => {
    renderConsole(<ChargebacksTab />);
    expect(screen.getByText('Deadline within 24 h')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Open chargeback CB-1' }));
    fireEvent.click(screen.getByRole('button', { name: 'Record outcome…' }));
    fireEvent.click(screen.getByRole('button', { name: 'Record outcome' }));
    await waitFor(() => expect(recordOutcome).toHaveBeenCalledWith('c1', true, undefined));
  });
});
