import { harnessTest as test, expect } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';

const RULES = { 'PlatformConfiguration.approvalSlaHours': 48, 'PlatformConfiguration.approvalWarningThresholdHours': 36, 'PlatformConfiguration.escalationDelayHours': 12, 'PlatformConfiguration.escalationReminderIntervalHours': 12, 'PlatformConfiguration.maxEscalationReminders': 3, 'PlatformConfiguration.autoEscalationEnabled': true, 'PlatformConfiguration.escalationRecipientRole': 'SUPER_ADMIN', 'PlatformConfiguration.commissionDefault': 5, 'PlatformConfiguration.minimumPayout': 10, 'PlatformConfiguration.reservationHoldMinutes': 10, 'PlatformConfiguration.reservationGraceMinutes': 5, 'PlatformConfiguration.escrowHoldDays': 7, 'PlatformConfiguration.refundCutoffHours': 24, 'PlatformConfiguration.maxTicketsPerBooking': 8, 'PlatformConfiguration.rescheduleLimit': 3, '*.percent': 50, '*.daysBefore': 7 };

/** Sensitive saves re-check "signed in recently" on the server (checkFreshAuth): stale -> /api/auth/stepup, fresh -> the mutation runs. */
async function saveRules(page: import('@playwright/test').Page) {
  await page.goto('/config/rules');
  const money = page.getByLabel('Approval target');
  await expect(money).toBeVisible();
  await money.fill('72');
  await page.getByRole('button', { name: 'Save configuration' }).first().click();
  const dlg = page.getByRole('alertdialog');
  if (await dlg.isVisible().catch(() => false)) await dlg.getByRole('button', { name: 'Save configuration' }).click();
}

test('stale sign-in is sent to step-up before a platform rules save', async ({ page, upstream, signInAs }) => {
  mockAll(upstream, { fields: RULES });
  await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1', authTime: Math.floor(Date.now() / 1000) - 3600 });
  const stepup = page.waitForRequest((r) => r.url().includes('/api/auth/stepup'), { timeout: 20_000 });
  await saveRules(page);
  const req = await stepup;
  expect(new URL(req.url()).searchParams.get('returnTo') ?? req.url()).toContain('config');
  expect(upstream.calls('UpdatePlatformConfiguration')).toHaveLength(0);
});

test('fresh sign-in saves without step-up', async ({ page, upstream, signInAs }) => {
  mockAll(upstream, { fields: RULES });
  await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
  let stepped = false;
  page.on('request', (r) => { if (r.url().includes('/api/auth/stepup')) stepped = true; });
  await saveRules(page);
  await expect.poll(() => upstream.calls('UpdatePlatformConfiguration').length, { timeout: 20_000 }).toBeGreaterThan(0);
  expect(stepped).toBe(false);
});
