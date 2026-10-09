import { harnessTest as test, expect, captureConsole, realErrors } from '../../../../e2e-harness/browser/playwright';
import { catalog, signedInBase } from './fixtures';

/**
 * Production-build behaviour (HARNESS_MODE=start, behind the harness TLS front): the strict-dynamic
 * nonce CSP must not block Next's bootstrap or lazy chunks, and pages must hydrate. Under next dev
 * this only proves the CSP header is present.
 */
const PAGES = ['/', '/events/e1', '/auth', '/terms', '/privacy', '/help', '/refund-policies', '/events/e1/book'];

for (const path of PAGES) {
  test(`csp: ${path} loads without CSP violations and hydrates`, async ({ page, upstream }) => {
    upstream.gql(catalog());
    const log = captureConsole(page);
    const res = await page.goto(path);
    await page.waitForLoadState('networkidle').catch(() => undefined);
    const csp = res?.headers()['content-security-policy'] ?? '';
    expect(csp).toContain("'strict-dynamic'");
    // /refund-policies redirects, so read the nonce the final document actually carries.
    const nonce = await page.evaluate(() => [...document.scripts].map((s) => s.nonce).find(Boolean));
    expect(nonce, 'nonce on the document scripts').toBeTruthy();
    const bad = await page.evaluate((n) => [...document.scripts].filter((s) => s.nonce !== n && (s.src !== '' || s.textContent)).map((s) => s.src || s.textContent!.slice(0, 80)), nonce);
    expect(bad, 'scripts without the request nonce').toEqual([]);
    expect(realErrors(log, []).filter((e) => /Content Security Policy|Refused to/.test(e))).toEqual([]);
    const hydrated = await page.evaluate(() => Object.keys(document.documentElement).some((k) => k.startsWith('__react')) || Object.keys(document.body.querySelector('*') ?? {}).some((k) => k.startsWith('__react')));
    expect(hydrated, 'React hydrated').toBe(true);
  });
}

for (const path of ['/my-tickets', '/notifications', '/profile', '/transfers/tr1']) {
  test(`csp: signed-in ${path} loads without CSP violations`, async ({ page, upstream, signInAs }) => {
    upstream.gql(signedInBase());
    await signInAs({ roles: ['CUSTOMER'] });
    const log = captureConsole(page);
    await page.goto(path);
    await page.waitForLoadState('networkidle').catch(() => undefined);
    const nonce = await page.evaluate(() => [...document.scripts].map((s) => s.nonce).find(Boolean));
    const bad = await page.evaluate((n) => [...document.scripts].filter((s) => s.nonce !== n && (s.src !== '' || s.textContent)).map((s) => s.src || s.textContent!.slice(0, 80)), nonce);
    expect(bad, 'scripts without the request nonce').toEqual([]);
    expect(realErrors(log, []).filter((e) => /Content Security Policy|Refused to/.test(e))).toEqual([]);
  });
}

test('csp: signed-in pages and client-side navigation across lazy chunks', async ({ page, upstream, signInAs }) => {
  upstream.gql(signedInBase());
  await signInAs({ roles: ['CUSTOMER'] });
  const log = captureConsole(page);
  await page.goto('/');
  await page.getByRole('link', { name: 'My tickets' }).first().click();
  await expect(page).toHaveURL(/\/my-tickets$/);
  await page.getByRole('button', { name: 'Show QR' }).first().click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await page.getByRole('link', { name: 'Notifications' }).first().click();
  await expect(page).toHaveURL(/\/notifications$/);
  await page.goto('/profile');
  await page.getByRole('link', { name: 'Events' }).first().click();
  await expect(page).toHaveURL(/\/$/);
  await page.waitForLoadState('networkidle').catch(() => undefined);
  expect(realErrors(log, []).filter((e) => /Content Security Policy|Refused to/.test(e))).toEqual([]);
});
