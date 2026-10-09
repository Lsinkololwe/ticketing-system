import { harnessTest as test, expect, captureConsole, shot } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';
import { settleAdmin } from './_kit';

const ROLES = [
  { role: 'SUPER_ADMIN', banner: 'Super admin', kpis: ['Services healthy', 'Alerts to acknowledge', 'Platform rules', 'Staff with two-step', 'Pending work', 'GMV, last 30 days'] },
  { role: 'ADMIN', banner: 'Admin', kpis: ['Awaiting approval', 'Overdue on SLA', 'Live events', 'Accounts needing action'] },
  { role: 'FINANCE', banner: 'Finance', kpis: ['Revenue today', 'Commission today', 'Pending payouts', 'Pending refunds', 'Escrow due for release', 'Chargebacks open'] },
  { role: 'FINANCE_LEAD', banner: 'Finance lead', kpis: ['Refunds waiting 2 days', 'Refunds waiting 5 days', 'Chargebacks near deadline', 'Waiting for a second approver', 'Stuck payouts', 'Stuck transactions'] },
] as const;

for (const r of ROLES) {
  test(`dashboard as ${r.role}: banner, KPI tiles, console clean`, async ({ page, upstream, signInAs }, info) => {
    mockAll(upstream);
    await signInAs({ roles: [r.role], displayName: 'Natasha Mulenga', accountId: 'staff-1' });
    const log = captureConsole(page);
    await page.goto('/dashboard');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Natasha');
    await expect(page.getByRole('note').filter({ hasText: r.banner }).first()).toBeVisible();
    for (const k of r.kpis) await expect(page.getByRole('group', { name: k, exact: true }).first(), `KPI ${k}`).toBeVisible();
    await page.waitForLoadState('networkidle').catch(() => undefined);
    await shot(page, `dash-${r.role}`, info);
    await settleAdmin(page, upstream, log, { a11y: false });
  });
}
