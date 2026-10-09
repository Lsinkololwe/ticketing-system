import { expect, realErrors, axe, shot, type ConsoleLog } from '../../../../e2e-harness/browser/playwright';
import type { Page, TestInfo } from '@playwright/test';
import type { FakeUpstream } from '../../../../e2e-harness/browser/playwright';

/**
 * Shared assertions for every buyer page spec: no console errors / page errors, the fixtures were
 * complete (nothing nulled by `conform`) and the page asked only for fixtured operations, and no
 * serious or critical axe violations. `allowMissing` lists fixture paths a spec knowingly leaves out.
 */
export async function settle(page: Page, upstream: FakeUpstream, log: ConsoleLog, o: { allowMissing?: RegExp[]; allowErrors?: RegExp[]; a11y?: boolean; axeExclude?: string[] } = {}) {
  await page.waitForLoadState('networkidle').catch(() => undefined);
  expect(upstream.unhandled, 'requests without a fixture').toEqual([]);
  const missing = upstream.missing.filter((m) => !(o.allowMissing ?? [/imageUrl$/]).some((r) => r.test(m)));
  expect(missing, 'fixture fields the page selected but the fixture omitted').toEqual([]);
  // Dev-server only: Next dev injects chunk scripts without the CSP nonce. See the README.
  const devCsp = /violates the following Content Security Policy directive.*_next\/static\/chunks|_next\/static\/chunks.*violates/;
  // With HARNESS_MODE=start (production server) CSP errors are NOT ignored.
  const ignore = process.env.HARNESS_MODE === 'start' ? [] : [devCsp];
  expect(realErrors(log, [...ignore, ...(o.allowErrors ?? [])]), 'console / page errors').toEqual([]);
  if (o.a11y !== false) {
    const bad = (await axe(page, { exclude: o.axeExclude })).filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(bad, 'axe serious/critical violations').toEqual([]);
  }
}
export { shot };
export type { TestInfo };
