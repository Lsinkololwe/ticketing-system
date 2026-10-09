import { expect, realErrors, axe, type ConsoleLog, type FakeUpstream } from '../../../../e2e-harness/browser/playwright';
import type { Page } from '@playwright/test';

/**
 * Shared assertions for admin specs: no console / page errors, nothing asked of the upstream that
 * had no fixture, no serious / critical axe violations.
 */
export async function settleAdmin(page: Page, upstream: FakeUpstream, log: ConsoleLog, o: { allowErrors?: RegExp[]; a11y?: boolean; axeExclude?: string[]; strictUnhandled?: boolean } = {}) {
  await page.waitForLoadState('networkidle').catch(() => undefined);
  if (o.strictUnhandled !== false) expect(upstream.unhandled, 'requests without a fixture').toEqual([]);
  const devCsp = /violates the following Content Security Policy directive.*_next\/static\/chunks|_next\/static\/chunks.*violates/;
  // Web fonts come from Google over the real network: a flaky connection is not an app error.
  const fontNet = /Failed to load resource: net::ERR_\w+ @ https:\/\/fonts\.(googleapis|gstatic)\.com/;
  const ignore = process.env.HARNESS_MODE === 'start' ? [fontNet] : [devCsp, fontNet];
  expect(realErrors(log, [...ignore, ...(o.allowErrors ?? [])]), 'console / page errors').toEqual([]);
  if (o.a11y !== false) {
    const bad = (await axe(page, { exclude: o.axeExclude })).filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(bad, 'axe serious/critical violations').toEqual([]);
  }
}
