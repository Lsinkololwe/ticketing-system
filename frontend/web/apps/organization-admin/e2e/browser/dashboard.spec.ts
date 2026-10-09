import { harnessTest as test, expect, captureConsole, delayed, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, dashboardFixtures, notificationsFixture } from './fixtures';
import { settle, shot } from './_kit';

const allow = { allowMissing: [/imageUrl$/, /logoUrl$/, /bannerUrl$/] };

test('dashboard populated', async ({ page, upstream, signInAs }, info) => {
  upstream.gql({ ...baseFixtures(), ...dashboardFixtures(), ...notificationsFixture(2, 2) });
  await signInAs(as('OWNER'));
  const log = captureConsole(page);
  await page.goto('/dashboard');
  await expect(page.getByText('168,937').first()).toBeVisible();
  await shot(page, 'dashboard', info);
  await settle(page, upstream, log, allow);
});

test('dashboard empty', async ({ page, upstream, signInAs }, info) => {
  upstream.gql({
    ...baseFixtures(),
    ...dashboardFixtures(),
    MyDashboardStats: { myDashboardStats: { totalRevenue: 0, revenueChange: null, revenueCurrency: 'ZMW', totalTicketsSold: 0, ticketsSoldChange: null, activeEvents: 0, eventsChange: null, eventsEndingThisWeek: 0, totalAttendees: 0, attendeesChange: null, pendingPayouts: 0, availableBalance: 0 } },
    MyRevenueSeries: { myRevenueSeries: [] },
    MyTicketMix: { myTicketMix: null },
    MyCheckInRate: { myCheckInRate: null },
    MyPayoutWindow: { myPayoutWindow: null },
    MyUpcomingEvents: { myUpcomingEvents: [] },
    MyRecentActivity: { myRecentActivity: [] },
  });
  await signInAs(as('OWNER'));
  await page.goto('/dashboard');
  await page.waitForLoadState('networkidle');
  await shot(page, 'dashboard-empty', info);
  await expect(page.getByRole('heading', { name: /overview/i }).first()).toBeVisible();
});

test('dashboard loading', async ({ page, upstream, signInAs }, info) => {
  upstream.gql({ ...baseFixtures(), ...dashboardFixtures(), MyDashboardStats: delayed(8000, dashboardFixtures().MyDashboardStats as never) });
  await signInAs(as('OWNER'));
  await page.goto('/dashboard');
  await page.waitForTimeout(1500);
  await shot(page, 'dashboard-loading', info);
});

test('dashboard error', async ({ page, upstream, signInAs }, info) => {
  upstream.gql({ ...baseFixtures(), ...dashboardFixtures(), MyDashboardStats: gqlErrors({ message: 'Backend unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await signInAs(as('OWNER'));
  await page.goto('/dashboard');
  await page.waitForLoadState('networkidle');
  await shot(page, 'dashboard-error', info);
});
