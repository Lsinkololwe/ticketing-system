import { harnessTest as test, expect, captureConsole, shot } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';
import { ROUTES } from './routes';
import { settleAdmin } from './_kit';

/** One populated capture per console route (SUPER_ADMIN), both widths: the input of the prototype comparison. */
for (const route of ROUTES) {
  test(`sweep ${route.app}`, async ({ page, upstream, signInAs }, info) => {
    mockAll(upstream);
    await signInAs({ roles: ['SUPER_ADMIN'], displayName: 'Natasha Mulenga', accountId: 'staff-1' });
    const log = captureConsole(page);
    await page.goto(route.app);
    await page.waitForLoadState('networkidle').catch(() => undefined);
    await expect(page.getByTestId('error-state'), 'populated page must not show an error state').toHaveCount(0);
    await shot(page, `sweep-${route.name}`, info);
    await settleAdmin(page, upstream, log, { a11y: false });
    expect(page.url()).toContain(route.app.split('/')[1]);
  });
}
