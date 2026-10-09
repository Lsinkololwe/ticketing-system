import { harnessTest as test, expect, shot } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';

/** Side sheets, quick views, dialogs, palette and bell captured for the prototype comparison (and opened/closed to prove they work). */
const FX = {
  'PayoutRequest.status': 'PENDING', 'Chargeback.status': 'RECEIVED', 'User.accountStatus': 'ACTIVE', 'Organization.status': 'ACTIVE',
};
test.beforeEach(async ({ upstream, signInAs }) => {
  mockAll(upstream, { fields: FX, listSize: 3 });
  await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1', displayName: 'Natasha Mulenga' });
});

const OPEN: Array<[string, string, string]> = [
  ['payout', '/finance/payouts', 'Open payout'],
  ['escrow', '/finance/escrow', 'Open escrow'],
  ['chargeback', '/finance/chargebacks', 'Open chargeback'],
  ['uquick', '/users/users', 'Quick view'],
  ['oquick', '/users/orgs', 'Quick view'],
  ['equick', '/events/all', 'Quick view'],
  ['refund', '/finance/refunds', 'Details refund'],
];
for (const [name, path, btn] of OPEN) {
  test(`sheet ${name}`, async ({ page }, info) => {
    await page.goto(path);
    await page.getByRole('button', { name: new RegExp(`^${btn}`) }).first().click();
    const sheet = page.getByRole('dialog').first();
    await expect(sheet).toBeVisible();
    await page.waitForTimeout(500);
    await shot(page, `sheet-${name}`, info);
    await page.keyboard.press('Escape');
    await expect(sheet).toBeHidden();
  });
}

test('dialogs: approve payout, transfer, palette, bell', async ({ page }, info) => {
  await page.goto('/finance/payouts');
  await page.getByRole('button', { name: /^More actions for payout/ }).first().click();
  await page.getByRole('menuitem', { name: 'Approve' }).click();
  await expect(page.getByRole('alertdialog').or(page.getByRole('dialog')).first()).toBeVisible();
  await page.waitForTimeout(300);
  await shot(page, 'dialog-approve-payout', info);
  await page.keyboard.press('Escape');
  await page.goto('/ledger/platform');
  await page.getByRole('button', { name: 'Record transfer' }).click();
  await expect(page.getByRole('dialog').first()).toBeVisible();
  await shot(page, 'dialog-transfer', info);
  await page.keyboard.press('Escape');
  await page.goto('/dashboard');
  await page.keyboard.press('Control+K');
  await page.getByRole('dialog').locator('input').first().fill('mw');
  await page.waitForTimeout(500);
  await shot(page, 'cmdk', info);
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: /^Notifications:/ }).click();
  await shot(page, 'bell', info);
  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Security and sessions' }).click();
  await shot(page, 'profile-security', info);
});
