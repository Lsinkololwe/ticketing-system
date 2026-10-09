import { describe, expect, it, vi } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const push = vi.fn();
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn() }), usePathname: () => '/finance/payouts', useSearchParams: () => new URLSearchParams() }));
vi.mock('@pml.tickets/shared/api/admin/modules/finance', () => ({
  usePayoutRequestStats: () => ({ stats: { pendingPayoutRequests: 3 } }),
  useRefundStatusCount: () => ({ count: 2 }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/finance-ops', () => ({ useChargebackQueue: () => ({ loaded: true, open: [1, 2] }) }));
vi.mock('../PayoutsTab', () => ({ PayoutsTab: () => <div>payouts-tab</div> }));
vi.mock('../RefundsTab', () => ({ RefundsTab: () => <div>refunds-tab</div> }));
vi.mock('../EscrowTab', () => ({ EscrowTab: () => <div>escrow-tab</div> }));
vi.mock('../ChargebacksTab', () => ({ ChargebacksTab: () => <div>cb-tab</div> }));

import { FinancePage } from '../FinancePage';

describe('FinancePage', () => {
  it('shows pending counts on tabs and navigates between tabs', () => {
    renderConsole(<FinancePage tab="payouts" />);
    expect(screen.getByText('payouts-tab')).toBeInTheDocument();
    expect(screen.getByLabelText('3 pending')).toBeInTheDocument();
    for (const [name, path] of [['Refund requests', 'refunds'], ['Escrow accounts', 'escrow'], ['Chargebacks', 'chargebacks'], ['Payout accounts', 'banks']] as const) {
      fireEvent.click(screen.getByRole('tab', { name: new RegExp(name) }));
      expect(push).toHaveBeenLastCalledWith(`/finance/${path}`);
    }
  });
  it('denies roles outside finance tabs', () => {
    renderConsole(<FinancePage tab="payouts" />, { roles: [] });
    expect(screen.getByText('No access')).toBeInTheDocument();
  });
});
