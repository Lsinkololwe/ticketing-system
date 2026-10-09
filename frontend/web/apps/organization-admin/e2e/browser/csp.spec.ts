import { harnessTest as test, expect, captureConsole, realErrors } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, dashboardFixtures, eventsListFixtures, financeFixtures, settingsFixtures, teamFixtures, bookingsFixtures } from './fixtures';

/**
 * Production-build behaviour (HARNESS_MODE=start, behind the harness TLS front): the strict-dynamic
 * nonce CSP must not block Next's bootstrap, lazy chunks or the theme script, and pages must hydrate.
 * Under next dev this only proves the CSP header is present.
 */
const PAGES = ['/login', '/features', '/', '/dashboard', '/events', '/settings', '/finance'];

for (const path of PAGES) {
  test(`csp: ${path} loads without CSP violations and hydrates`, async ({ page, upstream, signInAs }) => {
    upstream.gql(baseFixtures());
    await signInAs(as('OWNER'));
    const log = captureConsole(page);
    const res = await page.goto(path);
    await page.waitForLoadState('networkidle').catch(() => undefined);
    const csp = res?.headers()['content-security-policy'] ?? '';
    expect(csp).toContain("'strict-dynamic'");
    const nonce = /'nonce-([^']+)'/.exec(csp)?.[1];
    expect(nonce, 'nonce in CSP').toBeTruthy();
    // Every script element the server sent carries the request nonce.
    const bad = await page.evaluate((n) => [...document.scripts].filter((s) => s.src === '' && s.nonce !== n && s.textContent).map((s) => s.textContent!.slice(0, 80)), nonce);
    expect(bad, 'inline scripts without the request nonce').toEqual([]);
    const violations = realErrors(log, []).filter((e) => /Content Security Policy/.test(e));
    expect(violations).toEqual([]);
    // Hydrated: React attached (a router-driven link click navigates without a full reload).
    const hydrated = await page.evaluate(() => Object.keys(document.body.firstElementChild ?? {}).some((k) => k.startsWith('__react')) || !!document.querySelector('[data-reactroot]') || Object.keys(document.documentElement).some((k) => k.startsWith('__react')) || Object.keys(document.body.querySelector('*') ?? {}).some((k) => k.startsWith('__react')));
    expect(hydrated, 'React hydrated').toBe(true);
  });
}

test('csp: client-side navigation loads lazy route chunks without violations', async ({ page, upstream, signInAs }, info) => {
  test.skip(info.project.name === 'phone', 'the drawer links are not visible on the phone; covered at 1440');
  upstream.gql({ ...baseFixtures(), ...dashboardFixtures(), ...eventsListFixtures(), ...financeFixtures(), ...settingsFixtures(), ...teamFixtures(), ...bookingsFixtures() });
  await signInAs(as('OWNER'));
  const log = captureConsole(page);
  await page.goto('/dashboard');
  for (const [name, path] of [['Events', '/events'], ['Bookings', '/bookings'], ['Payouts', '/finance'], ['Team', '/team'], ['Settings', '/settings']] as const) {
    await page.getByRole('navigation').getByRole('link', { name, exact: true }).first().click();
    await expect(page).toHaveURL(new RegExp(`${path}$`));
    await page.waitForLoadState('networkidle').catch(() => undefined);
  }
  const violations = realErrors(log, []).filter((e) => /Content Security Policy|Refused to/.test(e));
  expect(violations).toEqual([]);
});
