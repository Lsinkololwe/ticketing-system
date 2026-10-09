import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderConsole } from '@/test/render';
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/test/referenceMock')).referenceModule());
import { referenceFixtures } from '@/test/referenceMock';
referenceFixtures.REPORT_PERIOD = [
  { code: '7d', name: 'Last 7 days', metadata: { days: 7, bucket: 'DAY' } },
  { code: '30d', name: 'Last 30 days', metadata: { days: 30, bucket: 'DAY' } },
  { code: '90d', name: 'Last 90 days', metadata: { days: 90, bucket: 'WEEK' } },
  { code: '12m', name: 'Last 12 months', metadata: { days: 365, bucket: 'MONTH' } },
];


const reportArgs: Array<{ groupBy?: string }> = [];
const state = { reportError: false };
const refetch = vi.fn();
const report = { totalRevenue: 2000, totalRefunds: 100, totalCommissions: 150, totalPayouts: 900, pendingPayouts: 50, escrowBalance: 700, netPlatformRevenue: 140, dataPoints: [{ period: '2026-09', revenue: 2000, commissions: 150, refunds: 100, payouts: 900, ticketsSold: 25 }] };
const exportReport = vi.fn();

vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  useFinancialReport: (a: { groupBy?: string }) => {
    reportArgs.push(a);
    return state.reportError ? { data: null, loading: false, error: new Error('boom'), refetch } : { data: report, loading: false, refetch };
  },
  useUserStats: () => ({ data: { totalUsers: 100, activeUsers: 90, lockedUsers: 2, suspendedUsers: 3 }, loading: false, refetch }),
  useEventStats: () => ({ stats: { totalEvents: 20, publishedEvents: 8, pendingApprovalEvents: 2, approvedNotPublishedEvents: 0, draftEvents: 5, completedEvents: 4, cancelledEvents: 1, rejectedEvents: 0 }, loading: false, refetch }),
  useTicketStats: () => ({ data: { totalTickets: 50, ticketsByStatus: [{ status: 'ISSUED', count: 40, percentage: 80 }, { status: 'REFUNDED', count: 10, percentage: 20 }] }, loading: false, refetch }),
  usePayoutRequestStats: () => ({ stats: { pendingPayoutRequests: 2, approvedPayoutRequests: 1, processingPayoutRequests: 0, completedPayoutRequests: 5, failedPayoutRequests: 0 }, loading: false, refetch }),
  useChargebackStats: () => ({ data: { wonCount: 3, lostCount: 1, pendingCount: 2, disputedCount: 1 }, loading: false, refetch }),
  useExportFinancialReport: () => ({ exportReport, loading: false }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/payments-ops', () => ({
  usePaymentRiskSummary: () => ({ summary: { high: 4, medium: 6, low: 20, evaluated: 30, flagged: 10, amountAtRisk: '900', topFlags: [], windowHours: 24 }, loading: false, error: undefined, refetch: vi.fn() }),
  usePurchasesByDayAndHour: () => ({ cells: [{ dayOfWeek: 5, hour: 19, purchases: 12, tickets: 20, revenue: '500' }], loading: false, error: undefined, refetch: vi.fn() }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  useUserGrowthSeries: (v: { role: string }) => ({ points: [{ bucketStart: '2026-09-01T00:00:00Z', newUsers: v.role === 'CUSTOMER' ? 30 : 5, cumulative: 100 }], loading: false, error: undefined, refetch: vi.fn() }),
}));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }), usePathname: () => '/analytics' }));

import { AnalyticsPage, reportLines } from '../AnalyticsPage';

beforeEach(() => {
  reportArgs.length = 0;
  state.reportError = false;
  exportReport.mockReset();
});

describe('AnalyticsPage', () => {
  it('renders KPIs, charts, heatmap, growth and the financial report', () => {
    renderConsole(<AnalyticsPage />, { roles: ['FINANCE'] });
    expect(screen.getByRole('heading', { level: 1, name: 'Analytics and statistics' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'User statistics' })).toHaveTextContent('100 accounts');
    expect(screen.getByRole('group', { name: 'Chargeback statistics' })).toHaveTextContent('3 of 4 won');
    expect(screen.getByRole('group', { name: 'Payment risk' })).toHaveTextContent('4 high risk');
    expect(screen.getByRole('table', { name: /Financial report/ })).toHaveTextContent('Net platform revenue');
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getByLabelText('Purchases by day and hour')).toBeInTheDocument();
    expect(screen.getByText('Fri')).toBeInTheDocument();
    expect(screen.getAllByText(/Stacked columns of new accounts by type per month/).length).toBeGreaterThan(0);
  });

  it('period control switches the report range', async () => {
    renderConsole(<AnalyticsPage />, { roles: ['SUPER_ADMIN'] });
    await userEvent.click(screen.getByRole('tab', { name: 'Last 12 months' }));
    expect(reportArgs.some((a) => a.groupBy === 'MONTH')).toBe(true);
    expect(screen.getByRole('tab', { name: 'Last 12 months' })).toHaveAttribute('aria-selected', 'true');
  });

  it('copies a JSON summary', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    renderConsole(<AnalyticsPage />, { roles: ['ADMIN'] });
    await userEvent.click(screen.getByRole('button', { name: 'JSON' }));
    expect(JSON.parse(writeText.mock.calls[0][0])['Gross ticket sales']).toBe(2000);
  });

  it('asks the backend for PDF exports', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    exportReport.mockResolvedValue({ downloadUrl: 'https://files.test/r.pdf', fileName: 'r.pdf', errorMessage: null });
    renderConsole(<AnalyticsPage />, { roles: ['ADMIN'] });
    await userEvent.click(screen.getByRole('button', { name: 'PDF' }));
    expect(exportReport).toHaveBeenCalled();
    expect(open).toHaveBeenCalledWith('https://files.test/r.pdf', '_blank', 'noopener,noreferrer');
  });

  it('shows the designed error state for the report', () => {
    state.reportError = true;
    renderConsole(<AnalyticsPage />, { roles: ['ADMIN'] });
    expect(screen.getAllByTestId('error-state').length).toBeGreaterThan(0);
  });

  it('reportLines marks the net line', () => {
    expect(reportLines(null)).toEqual([]);
    expect(reportLines(report as never).at(-1)).toMatchObject({ line: 'Net platform revenue', last: true });
  });

  it('offers the report periods the platform lists, and says so when it lists none', () => {
    const view = renderConsole(<AnalyticsPage />, { roles: ['FINANCE'] });
    const tabs = screen.getByRole('tablist', { name: 'Period' });
    expect(Array.from(tabs.querySelectorAll('[role="tab"]')).map((t) => t.textContent)).toEqual(['Last 7 days', 'Last 30 days', 'Last 90 days', 'Last 12 months']);
    view.unmount();
    const saved = referenceFixtures.REPORT_PERIOD;
    referenceFixtures.REPORT_PERIOD = [];
    renderConsole(<AnalyticsPage />, { roles: ['FINANCE'] });
    expect(screen.getByText('Report periods are not available.')).toBeInTheDocument();
    referenceFixtures.REPORT_PERIOD = saved;
  });
});
