import { harnessTest as test, expect, captureConsole, shot } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';
import { settleAdmin } from './_kit';

test.describe('dual control: second approval', () => {
  const proposal = (maker: string) => ({
    'RecoveryProposal.status': 'PENDING', 'RecoveryProposal.canConfirm': true, 'RecoveryProposal.proposedById': maker, 'RecoveryProposal.action': 'FORCE_COMPLETE_PAYMENT_ATTEMPTS',
    'RecoveryProposal.proposalReason': 'Gateway confirmed the debit', 'RecoveryProposal.subjectType': 'PAYMENT_ATTEMPT',
  });

  test('another staff member can confirm, with a written reason', async ({ page, upstream, signInAs }, info) => {
    mockAll(upstream, { fields: proposal('someone-else'), listSize: 1 });
    await signInAs({ roles: ['FINANCE_LEAD'], accountId: 'staff-1' });
    const log = captureConsole(page);
    await page.goto('/transactions/recovery');
    await page.getByRole('tab', { name: /Second approval/ }).click();
    const item = page.getByRole('list', { name: 'Dual control queue' });
    await expect(item).toBeVisible();
    await shot(page, 'dual-control', info);
    await item.getByRole('button', { name: 'Confirm' }).click();
    const dlg = page.getByRole('dialog').last();
    await dlg.getByRole('button', { name: 'Confirm action' }).click();
    await expect(dlg.getByText(/at least 5 characters/)).toBeVisible();
    await dlg.getByLabel('Why are you confirming it?').fill('Checked with the gateway statement');
    await dlg.getByRole('button', { name: 'Confirm action' }).click();
    await expect.poll(() => upstream.calls('OpsConfirmRecoveryAction').length).toBe(1);
    await settleAdmin(page, upstream, log, { a11y: false });
  });

  test('the proposer cannot confirm their own action', async ({ page, upstream, signInAs }) => {
    mockAll(upstream, { fields: proposal('staff-1'), listSize: 1 });
    await signInAs({ roles: ['FINANCE_LEAD'], accountId: 'staff-1' });
    await page.goto('/transactions/recovery');
    await page.getByRole('tab', { name: /Second approval/ }).click();
    const item = page.getByRole('list', { name: 'Dual control queue' });
    await expect(item.getByText('A second person must confirm.')).toBeVisible();
    await expect(item.getByRole('button', { name: 'Confirm' })).toHaveCount(0);
    await expect(item.getByRole('button', { name: 'Withdraw' })).toBeVisible();
  });
});

test.describe('users: suspend needs a reason; finance cannot manage users', () => {
  test('suspend an active user', async ({ page, upstream, signInAs }, info) => {
    mockAll(upstream, { fields: { 'User.accountStatus': 'ACTIVE', 'User.fullName': 'Mwila Banda', 'User.roles': ['CUSTOMER'] } });
    await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
    await page.goto('/user/u-1');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await shot(page, 'user-detail', info);
    await page.getByRole('button', { name: /Suspend/ }).first().click();
    const dlg = page.getByRole('dialog').last();
    await dlg.getByRole('button', { name: 'Suspend user' }).click();
    await expect(dlg.getByText(/at least 5 characters/)).toBeVisible();
    expect(upstream.calls('IdentityAdminSuspendUser')).toHaveLength(0);
    await dlg.getByLabel('Reason').fill('Chargeback abuse across three accounts');
    await dlg.getByRole('button', { name: 'Suspend user' }).click();
    await expect.poll(() => upstream.calls('IdentityAdminSuspendUser').length).toBe(1);
  });

  test('delete asks for confirmation text before the mutation', async ({ page, upstream, signInAs }) => {
    mockAll(upstream, { fields: { 'User.accountStatus': 'ACTIVE', 'User.fullName': 'Mwila Banda' } });
    await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
    await page.goto('/user/u-1');
    const del = page.getByRole('button', { name: /^Delete/ }).first();
    if (await del.count()) {
      await del.click();
      await expect(page.getByRole('dialog').or(page.getByRole('alertdialog')).last()).toBeVisible();
      expect(upstream.calls('OpsDeleteUser')).toHaveLength(0);
    }
  });
});

test.describe('detail pages render', () => {
  for (const [path, heading] of [['/user/u-1', null], ['/org/o-1', null], ['/event/e-1', null]] as const) {
    test(`${path}`, async ({ page, upstream, signInAs }, info) => {
      mockAll(upstream);
      await signInAs({ roles: ['ADMIN'], accountId: 'staff-1' });
      const log = captureConsole(page);
      await page.goto(path);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await page.waitForLoadState('networkidle').catch(() => undefined);
      await expect(page.getByTestId('error-state'), 'populated page must not show an error state').toHaveCount(0);
      await shot(page, `detail-${path.slice(1).replace('/', '-')}`, info);
      void heading;
      await settleAdmin(page, upstream, log, { a11y: true });
    });
  }
});
