import { harnessTest as test, expect, captureConsole, shot } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';

test('smoke: dashboard as super admin', async ({ page, upstream, signInAs }, info) => {
  mockAll(upstream);
  await signInAs({ roles: ['SUPER_ADMIN'], displayName: 'Natasha Mulenga' });
  const log = captureConsole(page);
  await page.goto('/dashboard');
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'smoke-dashboard', info);
  console.log('ERR', log.errors.slice(0, 5), 'UNH', upstream.unhandled.slice(0, 10), 'MISS', upstream.missing.length);
  expect(page.url()).toContain('/dashboard');
});
