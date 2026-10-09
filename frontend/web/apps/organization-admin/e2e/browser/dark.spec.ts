import { harnessTest as test, expect, axe, captureConsole, realErrors } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, dashboardFixtures, eventsListFixtures, bookingsFixtures, notificationsFixture } from './fixtures';
import { shot } from './_kit';

/** Dark theme smoke at both widths: the page follows the system scheme, is actually dark, and passes axe. */
for (const [name, path, wait] of [['dashboard', '/dashboard', /Revenue by month/], ['events', '/events', /Lusaka Sunset Sessions/], ['bookings', '/bookings', /BK-2026-00003462/]] as const) {
  test(`dark theme: ${name}`, async ({ page, upstream, signInAs }, info) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    upstream.gql({ ...baseFixtures(), ...dashboardFixtures(), ...eventsListFixtures(), ...bookingsFixtures(), ...notificationsFixture(2, 1) });
    await signInAs(as('OWNER'));
    const log = captureConsole(page);
    await page.goto(path);
    await expect(page.getByText(wait).first()).toBeVisible({ timeout: 45_000 });
    await page.waitForLoadState('networkidle').catch(() => undefined);
    const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    const lum = bg.match(/\d+/g)!.slice(0, 3).map(Number).reduce((a, b) => a + b, 0) / 3;
    expect(lum, `body background ${bg} should be dark`).toBeLessThan(80);
    await shot(page, `dark-${name}`, info);
    const bad = (await axe(page)).filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => `${v.id}: ${v.nodes.slice(0, 3).join(' | ')} :: ${v.detail}`);
    console.log(`DARK ${name} axe`, JSON.stringify(bad));
    expect(bad).toEqual([]);
    const errs = realErrors(log, [/Content Security Policy/]);
    console.log(`DARK ${name} errors`, JSON.stringify(errs).slice(0, 500));
    expect(errs).toEqual([]);
  });
}
