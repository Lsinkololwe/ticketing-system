import { harnessTest as test, expect, captureConsole, realErrors } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';

/**
 * Production-build behaviour (HARNESS_MODE=start, behind the harness TLS front): the strict-dynamic
 * nonce CSP must not block Next's bootstrap, lazy chunks (dynamic imports on Transactions, config) or the
 * theme script, and pages must hydrate. Under `next dev` this only proves the CSP header is present.
 */
const PAGES = ['/login', '/dashboard', '/approvals/orgs', '/events/all', '/finance/payouts', '/ledger/coa', '/transactions/refdata', '/config/rules', '/analytics', '/profile'];

for (const path of PAGES) {
  test(`csp: ${path} loads without CSP violations and hydrates`, async ({ page, upstream, signInAs }, info) => {
    test.skip(info.project.name === 'phone');
    mockAll(upstream);
    await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
    const log = captureConsole(page);
    const res = await page.goto(path);
    await page.waitForLoadState('networkidle').catch(() => undefined);
    const csp = res?.headers()['content-security-policy'] ?? '';
    expect(csp).toContain("'strict-dynamic'");
    const nonce = /'nonce-([^']+)'/.exec(csp)?.[1];
    expect(nonce, 'nonce in CSP').toBeTruthy();
    const bad = await page.evaluate((n) => [...document.scripts].filter((s) => s.src === '' && s.nonce !== n && s.textContent).map((s) => s.textContent!.slice(0, 80)), nonce);
    expect(bad, 'inline scripts without the request nonce').toEqual([]);
    expect(realErrors(log, []).filter((e) => /Content Security Policy/.test(e))).toEqual([]);
    const hydrated = await page.evaluate(() => Object.keys(document.documentElement).some((k) => k.startsWith('__react')) || Object.keys(document.body.querySelector('*') ?? {}).some((k) => k.startsWith('__react')));
    expect(hydrated, 'React hydrated').toBe(true);
    // A client-side interaction proves chunks load: open the palette (lazy dialog code).
    if (path !== '/login') {
      await page.keyboard.press('Control+K');
      await expect(page.getByRole('dialog')).toBeVisible();
      expect(realErrors(log, []).filter((e) => /Content Security Policy/.test(e))).toEqual([]);
    }
  });
}
