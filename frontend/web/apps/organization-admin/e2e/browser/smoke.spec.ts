import { harnessTest as test, expect, captureConsole } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures } from './fixtures';
import { shot } from './_kit';

test('smoke: dashboard renders behind a seeded session', async ({ page, upstream, signInAs }, info) => {
  upstream.gql(baseFixtures());
  await signInAs(as('OWNER'));
  const log = captureConsole(page);
  await page.goto('/dashboard');
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'smoke-dashboard', info);
  console.log('UNHANDLED', JSON.stringify([...new Set(upstream.unhandled)]));
  console.log('MISSING', JSON.stringify([...new Set(upstream.missing)]));
  console.log('ERR', JSON.stringify(log.errors?.slice?.(0, 5) ?? log));
  expect(page.url()).toContain('/dashboard');
});
