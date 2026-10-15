import { harnessTest as test, expect, captureConsole, shot } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';
import { settleAdmin } from './_kit';
import { ROUTES } from './routes';

const NAV: Record<string, string[]> = {
  SUPER_ADMIN: ['Dashboard', 'Approvals', 'Events', 'Users & orgs', 'Finance', 'Ledger', 'Transactions', 'Analytics', 'Health', 'Settings'],
  ADMIN: ['Dashboard', 'Approvals', 'Events', 'Users & orgs', 'Finance', 'Ledger', 'Transactions', 'Analytics', 'Health', 'Settings'],
  FINANCE: ['Dashboard', 'Finance', 'Ledger', 'Transactions', 'Analytics'],
  FINANCE_LEAD: ['Dashboard', 'Finance', 'Ledger', 'Transactions', 'Analytics'],
};

test.describe('role gating', () => {
  for (const [role, items] of Object.entries(NAV)) {
    test(`${role}: navigation shows exactly its modules; other routes show "No access"`, async ({ page, upstream, signInAs }, info) => {
      test.skip(info.project.name === 'phone', 'drawer is a bottom bar on phones; covered on desktop');
      mockAll(upstream);
      // The identity realm makes the finance lead a composite of finance, so its token carries both.
      await signInAs({ roles: role === 'FINANCE_LEAD' ? ['FINANCE', 'FINANCE_LEAD'] : [role], accountId: 'staff-1' });
      await page.goto('/dashboard');
      const nav = page.getByRole('navigation', { name: 'Main navigation' });
      const names = (await nav.getByRole('link').allInnerTexts()).map((t) => t.split('\n')[0].trim());
      expect(names).toEqual(items);
      for (const r of ROUTES.filter((x) => !x.roles.includes(role) && x.app !== '/profile').slice(0, 4)) {
        await page.goto(r.app);
        await expect(page.getByRole('heading', { level: 1, name: 'No access' }), r.app).toBeVisible();
        await expect(page.getByRole('button', { name: 'Back to my dashboard' })).toBeVisible();
      }
      await shot(page, `denied-${role}`, info);
    });
  }

  test('FINANCE sees the five transaction tabs its permissions reach and no more', async ({ page, upstream, signInAs }) => {
    mockAll(upstream);
    await signInAs({ roles: ['FINANCE'], accountId: 'staff-1' });
    await page.goto('/transactions/payments');
    const tabs = await page.getByRole('tab').allInnerTexts();
    expect(tabs.map((t) => t.replace(/\d+$/, '').trim())).toEqual(['Payments', 'Tickets', 'Reservations', 'Transaction recovery', 'Audit log']);
    await page.goto('/transactions/announce');
    await expect(page.getByRole('heading', { level: 1, name: 'No access' })).toBeVisible();
  });

  test('the roles matrix is read-only for every role that can open it', async ({ page, upstream, signInAs }) => {
    for (const role of ['SUPER_ADMIN', 'ADMIN']) {
      mockAll(upstream);
      await signInAs({ roles: [role], accountId: 'staff-1' });
      await page.goto('/config/roles');
      await expect(page.getByRole('table', { name: 'Areas in the navigation drawer' })).toBeVisible();
      await expect(page.getByRole('table', { name: 'Actions inside pages' })).toBeVisible();
      await expect(page.getByRole('button', { name: /Preview as this role|Edit|Save|Grant|Revoke/ })).toHaveCount(0);
      await expect(page.getByRole('switch')).toHaveCount(0);
    }
  });

  test('FINANCE cannot open the roles matrix', async ({ page, upstream, signInAs }) => {
    mockAll(upstream);
    await signInAs({ roles: ['FINANCE'], accountId: 'staff-1' });
    await page.goto('/config/roles');
    await expect(page.getByRole('heading', { level: 1, name: 'No access' })).toBeVisible();
  });

  test('signed out and non-staff', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({});
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { level: 1, name: 'MyTicketZM platform admin' })).toBeVisible();
    await expect(page.getByTestId('admin-login-sso-button')).toHaveAttribute('href', /\/api\/auth\/start\?next=/);
    await shot(page, 'login', info);
    await page.goto('/login?error=FORBIDDEN');
    await expect(page.getByText('This account does not hold a platform staff role.')).toBeVisible();
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/unauthorized|\/login/);
    await page.goto('/unauthorized');
    await expect(page.getByRole('heading', { level: 1, name: 'You do not have access' })).toBeVisible();
    await shot(page, 'unauthorized', info);
  });

  test('palette (Ctrl+K), bell, theme toggle', async ({ page, upstream, signInAs }, info) => {
    test.skip(info.project.name === 'phone');
    mockAll(upstream);
    await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
    const log = captureConsole(page);
    await page.goto('/dashboard');
    await page.keyboard.press('Control+K');
    const dlg = page.getByRole('dialog');
    await expect(dlg).toBeVisible();
    await shot(page, 'palette', info);
    await page.keyboard.press('Escape');
    await expect(dlg).toBeHidden();
    await page.getByRole('button', { name: /Search everything/ }).click();
    await expect(dlg).toBeVisible();
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: /^Notifications:/ }).click();
    await expect(page.getByRole('menuitem').first()).toBeVisible();
    await shot(page, 'bell', info);
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: 'Light or dark' }).click();
    await expect(page.locator('html')).toHaveClass(/dark|light/);
    await settleAdmin(page, upstream, log, { a11y: false });
  });
});

test('search opens from every module page (analytics included)', async ({ page, upstream, signInAs }, info) => {
  test.skip(info.project.name === 'phone');
  mockAll(upstream);
  await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
  for (const path of ['/analytics', '/health', '/config/rules']) {
    await page.goto(path);
    await page.getByRole('button', { name: /Search everything/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.keyboard.press('Escape');
  }
});
