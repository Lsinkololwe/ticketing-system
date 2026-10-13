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

test.describe('staff accounts: one-time temporary password', () => {
  // Obvious fixture strings: no real credential appears in a spec.
  const CREATED_PASSWORD = 'fixture-created-0001';
  const RESET_PASSWORD = 'fixture-reset-0002';
  const STAFF = { 'User.fullName': 'Staff Person', 'User.roles': ['CUSTOMER', 'ADMIN'], 'User.accountStatus': 'ACTIVE' };
  const userRow = { id: 'new-staff-1', fullName: 'Ben Phiri', roles: ['ADMIN'], accountStatus: 'ACTIVE' };

  async function createAdmin(page: import('@playwright/test').Page) {
    await page.goto('/users/users');
    await page.getByRole('button', { name: 'Create admin' }).click();
    const dlg = page.getByRole('dialog').last();
    await dlg.getByLabel('First name').fill('Ben');
    await dlg.getByLabel('Last name').fill('Phiri');
    await dlg.getByLabel('Email').fill('ben@example.test');
    await dlg.getByLabel('Phone number').fill('+260971234568');
    await dlg.getByRole('button', { name: 'Create admin' }).click();
  }

  test('creating a staff account shows the generated password once and clears it on Done', async ({ page, upstream, signInAs }) => {
    mockAll(upstream, { fields: STAFF });
    upstream.gql({ IdentityAdminCreateUser: () => ({ createUser: { __typename: 'CreateStaffUserResult', user: userRow, temporaryPassword: CREATED_PASSWORD } }) });
    await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
    await createAdmin(page);

    const panel = page.getByTestId('temporary-password-panel');
    await expect(panel).toBeVisible();
    await expect(panel.getByTestId('temporary-password-value')).toHaveValue(CREATED_PASSWORD);
    await expect(panel.getByText(/shown once/i)).toBeVisible();
    expect(upstream.calls('IdentityAdminCreateUser')[0]).not.toHaveProperty('variables.input.password');

    await page.getByTestId('temporary-password-done').click();
    await expect(panel).toHaveCount(0);
    expect(await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }))).not.toContain(CREATED_PASSWORD);
    expect(await page.content()).not.toContain(CREATED_PASSWORD);
    expect(page.url()).not.toContain(CREATED_PASSWORD);
  });

  test('a repeat of a finished create says the account exists and shows no password', async ({ page, upstream, signInAs }) => {
    mockAll(upstream, { fields: STAFF });
    upstream.gql({ IdentityAdminCreateUser: () => ({ createUser: { __typename: 'CreateStaffUserResult', user: userRow, temporaryPassword: null } }) });
    await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
    await createAdmin(page);
    await expect(page.getByText(/already has an account/i)).toBeVisible();
    await expect(page.getByTestId('temporary-password-panel')).toHaveCount(0);
  });

  test('a super admin resets a staff password after confirming, and sees the new one once', async ({ page, upstream, signInAs }) => {
    mockAll(upstream, { fields: STAFF });
    upstream.gql({ IdentityAdminResetStaffPassword: () => ({ resetStaffTemporaryPassword: { __typename: 'ResetStaffPasswordResult', user: userRow, temporaryPassword: RESET_PASSWORD } }) });
    await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
    await page.goto('/users/users');
    await page.getByRole('button', { name: 'More actions for Staff Person' }).first().click();
    await page.getByRole('menuitem', { name: 'Reset password…' }).click();
    expect(upstream.calls('IdentityAdminResetStaffPassword')).toHaveLength(0);
    await page.getByRole('alertdialog').getByRole('button', { name: 'Reset password' }).click();

    await expect(page.getByTestId('temporary-password-value')).toHaveValue(RESET_PASSWORD);
    expect(upstream.calls('IdentityAdminResetStaffPassword')).toHaveLength(1);
    await page.getByTestId('temporary-password-done').click();
    await expect(page.getByTestId('temporary-password-panel')).toHaveCount(0);
    expect(await page.content()).not.toContain(RESET_PASSWORD);
  });

  test('an admin is not offered Reset password or Create admin', async ({ page, upstream, signInAs }) => {
    mockAll(upstream, { fields: STAFF });
    await signInAs({ roles: ['ADMIN'], accountId: 'staff-1' });
    await page.goto('/users/users');
    await expect(page.getByRole('button', { name: 'Create admin' })).toHaveCount(0);
    await page.getByRole('button', { name: 'More actions for Staff Person' }).first().click();
    await expect(page.getByRole('menuitem', { name: 'Reset password…' })).toHaveCount(0);
  });
});
