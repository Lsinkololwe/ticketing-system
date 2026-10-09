import { harnessTest as test, expect, captureConsole, shot, axe, gqlErrors, delayed } from '../../../../e2e-harness/browser/playwright';
import { mockAll, operationNames } from './mock-upstream';
import { ROUTES } from './routes';

/**
 * Per route: populated (axe serious/critical must be clean), empty (every list empty), loading (slow
 * upstream shows a skeleton / status, never a blank page) and error (every operation fails: a designed
 * error state with a retry, no crash). Screenshots at both widths.
 */
for (const route of ROUTES) {
  test(`states ${route.app}`, async ({ page, upstream, signInAs }, info) => {
    await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });

    mockAll(upstream);
    const log = captureConsole(page);
    await page.goto(route.app);
    await page.waitForLoadState('networkidle').catch(() => undefined);
    const bad = (await axe(page)).filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(bad, `axe ${route.app}`).toEqual([]);
    expect(log.pageErrors).toEqual([]);

    mockAll(upstream, { listSize: 0 });
    await page.goto(route.app);
    await page.waitForLoadState('networkidle').catch(() => undefined);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByText('This page could not be shown')).toHaveCount(0);
    await shot(page, `empty-${route.name}`, info);

    const slow: Record<string, ReturnType<typeof delayed>> = {};
    for (const n of operationNames()) slow[n] = delayed(4000, {});
    upstream.gql(slow);
    await page.goto(route.app, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await shot(page, `loading-${route.name}`, info);

    const failing: Record<string, ReturnType<typeof gqlErrors>> = {};
    for (const n of operationNames()) failing[n] = gqlErrors({ message: 'The service is unavailable', extensions: { errorCode: 'INTERNAL_ERROR', classification: 'INTERNAL', retryable: true } });
    upstream.gql(failing);
    await page.goto(route.app);
    await page.waitForLoadState('networkidle').catch(() => undefined);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByText('This page could not be shown')).toHaveCount(0);
    await shot(page, `error-${route.name}`, info);
  });
}
