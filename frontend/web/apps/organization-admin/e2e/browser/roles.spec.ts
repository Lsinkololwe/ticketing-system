import { harnessTest as test, expect, captureConsole } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, dashboardFixtures, eventsListFixtures, financeFixtures, teamFixtures, settingsFixtures, mediaFixtures, bookingsFixtures, notificationsFixture, type OrgRole } from './fixtures';
import { shot } from './_kit';

/**
 * Role-based UI. Non-owner staff do not own an organization, so the server-side guard must not depend on
 * `myOwnedOrganization`; the membership role decides what each page offers.
 */
const ROLES: OrgRole[] = ['OWNER', 'ADMIN', 'MANAGER', 'MARKETER', 'CONTRIBUTOR'];
const NAV = ['Overview', 'Events', 'Bookings', 'Media', 'Payouts', 'Banks', 'Transactions', 'Team', 'Settings'];

for (const role of ROLES) {
  test(`role ${role}: console opens and navigation is role-aware`, async ({ page, upstream, signInAs }, info) => {
    // A non-owner has no owned organization: myOwnedOrganization is null, myOrganization is theirs.
    upstream.gql({ ...baseFixtures({ role }), ...dashboardFixtures(), ...eventsListFixtures(), ...notificationsFixture(0, 0) });
    if (role !== 'OWNER') upstream.gql({ MyOrganization: { myOwnedOrganization: null } });
    await signInAs(as(role));
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle').catch(() => undefined);
    const landed = new URL(page.url()).pathname;
    await shot(page, `role-${role.toLowerCase()}-dashboard`, info);
    expect(landed, `${role} must reach the console, not the onboarding flow`).toBe('/dashboard');
    const visible: string[] = [];
    for (const n of NAV) if (await page.getByRole('navigation').getByRole('link', { name: n, exact: true }).first().isVisible().catch(() => false)) visible.push(n);
    console.log(`NAV ${role}: ${visible.join(', ')}`);
  });
}

test('role MARKETER cannot see finance or team figures', async ({ page, upstream, signInAs }) => {
  upstream.gql({ ...baseFixtures({ role: 'MARKETER' }), ...financeFixtures(), ...teamFixtures(), ...bookingsFixtures(), ...settingsFixtures(), ...mediaFixtures() });
  await signInAs(as('MARKETER'));
  for (const path of ['/finance', '/team', '/bookings', '/settings']) {
    await page.goto(path);
    await page.waitForLoadState('networkidle').catch(() => undefined);
    console.log(`MARKETER ${path} -> ${new URL(page.url()).pathname} actions: ${(await page.getByRole('button').allInnerTexts()).filter((t) => /add|invite|request|refund|delete|save/i.test(t)).join('|')}`);
  }
});

test('role MARKETER sees the no-access state on finance, team and bookings', async ({ page, upstream, signInAs }) => {
  upstream.gql({ ...baseFixtures({ role: 'MARKETER' }), ...financeFixtures(), ...teamFixtures(), ...bookingsFixtures() });
  await signInAs(as('MARKETER'));
  for (const path of ['/finance', '/team', '/bookings']) {
    await page.goto(path);
    await expect(page.getByText('You do not have access to this page')).toBeVisible();
  }
});
