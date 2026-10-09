import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderConsole } from '@/test/render';

const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/dashboard',
}));

const counts = {
  'pending-approvals': 5,
  'organizer-applications': 3,
  'event-reviews': 2,
  'document-verification': 0,
  'payout-requests': 4,
  'refund-requests': 1,
};
const empty = { loading: false, error: undefined, refetch: vi.fn() };
const report = { totalRevenue: 1000, totalCommissions: 50, totalRefunds: 10, totalPayouts: 300, pendingPayouts: 20, escrowBalance: 400, netPlatformRevenue: 40, dataPoints: [{ period: '2026-09', revenue: 1000, commissions: 50, refunds: 10, payouts: 300, ticketsSold: 12 }] };

vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  usePendingCounts: () => ({ counts, loading: false }),
  useFinancialReport: () => ({ ...empty, data: report }),
  usePlatformConfiguration: () => ({ config: { approvalSlaHours: 48, approvalWarningThresholdHours: 24, escalationDelayHours: 72, updatedAt: '2026-09-01T10:00:00Z', updatedBy: 'root' } }),
  useUserStats: () => ({ ...empty, data: { lockedUsers: 1, suspendedUsers: 2, pendingVerificationUsers: 3 } }),
  useEventStats: () => ({ stats: { publishedEvents: 7 }, loading: false, refetch: vi.fn() }),
  useChargebackStats: () => ({ ...empty, data: { pendingCount: 2, disputedCount: 1 } }),
  usePayoutRequestStats: () => ({ stats: { pendingPayoutRequests: 3, pendingPayoutAmount: 900 }, loading: false, refetch: vi.fn() }),
  usePayoutRecoverySummary: () => ({ summary: { stuckPayoutsCount: 2 }, loading: false, refetch: vi.fn() }),
  usePendingOrganizations: () => ({ organizations: [{ id: 'o1', name: 'Acme Events', type: 'COMPANY', status: 'PENDING_REVIEW', submittedAt: '2020-01-01T00:00:00Z' }], loading: false }),
  useAdminEvents: () => ({ events: [{ id: 'e1', title: 'Summer Fest', status: 'PENDING_APPROVAL', submittedForApprovalAt: '2020-01-01T00:00:00Z', eventDateTime: new Date().toISOString(), cityName: 'Lusaka' }], pageInfo: { totalCount: 1 }, loading: false }),
  useAdminUsers: () => ({ users: [{ id: 'u1', fullName: 'Locked Person', email: 'l@x.test', accountStatus: 'LOCKED' }], loading: false }),
  useAdminEscrowAccounts: () => ({ accounts: [{ id: 'x1', eventTitle: 'Gala', organizerName: 'Org', currentBalance: 250, lockUntil: null }], pageInfo: { totalCount: 1 }, loading: false }),
  useAdminPayoutRequests: () => ({ payouts: [{ id: 'p1', requestId: 'PR-1', organizerName: 'Org', eventTitle: 'Gala', requestedAmount: 500, requestedAt: '2026-10-01T00:00:00Z' }], loading: false }),
  useAdminRefundRequests: () => ({ refunds: [{ id: 'r1', requestId: 'RF-1', ticketNumber: 'T-1', refundAmount: 40, requestedAt: '2020-01-01T00:00:00Z' }], loading: false }),
  useRecoveryQueue: () => ({ items: [{ id: 'p9', requestId: 'PR-9', requestedAmount: 70, organizerName: 'Org', status: 'FAILED' }], loading: false }),
}));


const hook = { loading: false, error: undefined, refetch: vi.fn() };
vi.mock('@pml.tickets/shared/api/admin/modules', () => ({
  useApprovalOrganizationDocuments: () => ({ organizations: [], loading: false, error: null }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  useServiceHealth: () => ({ ...hook, services: [{ name: 'mongodb', status: 'UP', latencyMillis: 9, checkedAt: '2026-10-05T08:00:00Z', detail: null }, { name: 'redis', status: 'DOWN', latencyMillis: 0, checkedAt: '2026-10-05T08:00:00Z', detail: 'timeout' }] }),
  useSystemAlerts: () => ({ ...hook, alerts: [{ id: 'a1' }, { id: 'a2' }, { id: 'a3' }] }),
  useStaffAccounts: () => ({ ...hook, staff: [{ id: 's1', fullName: 'Ada Staff', twoFactorEnabled: true }, { id: 's2', fullName: 'Bo Staff', twoFactorEnabled: false }], pageInfo: { totalCount: 2 } }),
  useAuditLogs: () => ({ ...hook, entries: [{ id: 'l1', action: 'USER_SUSPENDED', at: '2026-10-05T07:00:00Z', resourceType: 'user', actorId: 'root' }], pageInfo: { totalCount: 1 } }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/payments-ops', () => ({
  useDualControlQueue: () => ({ ...hook, proposals: [{ id: 'p1', action: 'FORCE_COMPLETE_PAYMENT_ATTEMPTS', amount: null, canConfirm: true, status: 'PENDING', proposedById: 'someone-else', proposalReason: 'Stuck', proposedAt: '2026-10-05T07:00:00Z', expiresAt: '2026-10-06T07:00:00Z', subjectIds: ['d1'], subjectType: 'PAYMENT_ATTEMPT' }] }),
  useStuckTransactions: () => ({ ...hook, items: [{ id: 't1' }], pageInfo: { totalCount: 4 } }),
  useGatewaySettlements: () => ({ ...hook, settlements: [{ settlementId: 'PAW-1', netAmount: '90.00', settlementDate: '2026-10-04T00:00:00Z' }], pageInfo: { totalCount: 1 } }),
  useDualControlActions: () => ({ confirm: vi.fn(), withdraw: vi.fn(), busy: false }),
  canConfirmProposal: (p: { canConfirm: boolean; status: string; proposedById: string }, id: string | null) => p.status === 'PENDING' && p.canConfirm && p.proposedById !== id,
}));
vi.mock('@pml.tickets/shared/api/admin/modules/finance-ops', () => ({
  useChargebackQueue: () => ({ open: [{ id: 'c1', chargebackAmount: '250', responseDeadline: new Date(Date.now() + 36e5).toISOString(), status: 'DISPUTED' }], loaded: true, loading: false }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/ledger', () => ({
  useReconciliationRuns: () => ({ ...hook, items: [{ id: 'r1', status: 'COMPLETED', type: 'GATEWAY', startedAt: '2026-10-04T00:00:00Z', unmatchedCount: 0 }], pageInfo: { totalCount: 1 } }),
  useJournalEntries: () => ({ ...hook, items: [], pageInfo: { totalCount: 3 } }),
}));

import { DashboardPage } from '../DashboardPage';

beforeEach(() => push.mockClear());

describe('DashboardPage role variants', () => {
  it('super admin: banner, KPI grid, not-available regions, pending work', async () => {
    renderConsole(<DashboardPage />, { roles: ['SUPER_ADMIN'], name: 'Ada Lovelace' });
    expect(screen.getByRole('heading', { level: 1, name: /Ada/ })).toBeInTheDocument();
    expect(screen.getByText(/Everything, including staff accounts/)).toBeInTheDocument();
    for (const l of ['Services healthy', 'Alerts to acknowledge', 'Platform rules', 'Staff with two-step', 'Pending work', 'GMV, last 30 days']) {
      expect(screen.getAllByText(l).length).toBeGreaterThan(0);
    }
    expect(screen.getByRole('group', { name: 'Pending work' })).toHaveTextContent('10');
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getByRole('group', { name: 'Services healthy' })).toHaveTextContent('1 of 2');
    expect(screen.getByRole('group', { name: 'Alerts to acknowledge' })).toHaveTextContent('3');
    expect(screen.getByRole('group', { name: 'Staff with two-step' })).toHaveTextContent('1 of 2');
    expect(screen.getByText(/User suspended/)).toBeInTheDocument();
    expect(screen.getByText(/Updates every 60 seconds/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Open Services healthy' }));
    expect(push).toHaveBeenCalledWith('/health');
    await userEvent.click(screen.getByRole('button', { name: 'Open platform configuration' }));
    expect(push).toHaveBeenCalledWith('/config/rules');
  });

  it('admin: approvals queue sorted by SLA with review buttons', async () => {
    renderConsole(<DashboardPage />, { roles: ['ADMIN'] });
    expect(screen.getByRole('group', { name: 'Awaiting approval' })).toHaveTextContent('5');
    expect(screen.getByRole('group', { name: 'Live events' })).toHaveTextContent('7');
    expect(screen.getByRole('group', { name: 'Accounts needing action' })).toHaveTextContent('6');
    const queue = screen.getByRole('list', { name: 'Approvals queue' });
    expect(within(queue).getByText('Acme Events')).toBeInTheDocument();
    expect(within(queue).getAllByText(/Overdue by/).length).toBe(2);
    await userEvent.click(within(queue).getAllByRole('button')[0]);
    expect(push).toHaveBeenCalledWith(expect.stringMatching(/^\/approvals\/(orgs|events)$/));
    await userEvent.click(screen.getByRole('button', { name: 'Open workbench' }));
    expect(push).toHaveBeenCalledWith('/approvals/orgs');
    expect(screen.getByRole('grid')).toBeInTheDocument();
  });

  it('finance: money KPIs and lists', async () => {
    renderConsole(<DashboardPage />, { roles: ['FINANCE'] });
    expect(screen.getByRole('group', { name: 'Revenue today' })).toHaveTextContent('K 1,000');
    expect(screen.getByRole('group', { name: 'Pending payouts' })).toHaveTextContent('3 · K 900');
    expect(screen.getByRole('group', { name: 'Chargebacks open' })).toHaveTextContent('3');
    expect(screen.getByText('Gala')).toBeInTheDocument();
    expect(screen.getByText('Last gateway settlement')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Open payouts' }));
    expect(push).toHaveBeenCalledWith('/finance/payouts');
  });

  it('finance lead: escalations, deadlines, second-approver queue and stuck transactions', () => {
    renderConsole(<DashboardPage />, { roles: ['FINANCE_LEAD'] });
    expect(screen.getByRole('group', { name: 'Refunds waiting 5 days' })).toHaveTextContent('1');
    expect(screen.getByRole('group', { name: 'Stuck payouts' })).toHaveTextContent('2');
    expect(screen.getByRole('list', { name: 'Escalations' })).toBeInTheDocument();
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getByRole('group', { name: 'Chargebacks near deadline' })).toHaveTextContent('1');
    expect(screen.getByRole('group', { name: 'Stuck transactions' })).toHaveTextContent('4');
    const dual = screen.getByRole('list', { name: 'Dual control queue' });
    expect(within(dual).getByRole('button', { name: 'Confirm' })).toBeInTheDocument();
  });

  it('picks the highest priority role', () => {
    renderConsole(<DashboardPage />, { roles: ['FINANCE', 'ADMIN'] });
    expect(screen.getByText(/Approvals, events, users, organizations/)).toBeInTheDocument();
  });
});
