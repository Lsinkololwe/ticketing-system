import { harnessTest as test, expect, captureConsole, httpFailure } from '../../../../e2e-harness/browser/playwright';
import { catalog, EVENTS } from './fixtures';
import { settle, shot } from './_kit';

test.describe('home', () => {
  test('populated, signed out: featured, trending, upcoming, filters', async ({ page, upstream }, info) => {
    upstream.gql(catalog());
    const log = captureConsole(page);
    await page.goto('/');
    await expect(page.getByRole('region', { name: 'Featured events' })).toBeVisible();
    await expect(page.getByRole('list', { name: 'Upcoming events' }).getByRole('listitem')).toHaveCount(3);
    await expect(page.getByRole('list', { name: 'Trending events, ranked' })).toBeVisible();
    await shot(page, 'home', info);
    const chips = page.getByRole('group', { name: 'Category' });
    await chips.getByRole('button', { name: 'Comedy', exact: true }).click();
    await expect(chips.getByRole('button', { name: 'Comedy', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect.poll(() => upstream.calls('DiscoverEvents').some((c) => JSON.stringify(c.variables).includes('Comedy') || JSON.stringify(c.variables).includes('c2') || JSON.stringify(c.variables).includes('COMEDY'))).toBe(true);
    await settle(page, upstream, log);
  });

  test('empty: no events', async ({ page, upstream }, info) => {
    upstream.gql(catalog({ events: [] }));
    const log = captureConsole(page);
    await page.goto('/');
    await expect(page.getByRole('list', { name: 'Upcoming events' })).toHaveCount(0);
    await shot(page, 'home-empty', info);
    await settle(page, upstream, log);
  });

  test('error: catalogue unavailable shows the error state with retry', async ({ page, upstream }, info) => {
    upstream.gql({ ...catalog(), DiscoverEvents: httpFailure(503), BuyerTrendingEvents: httpFailure(503) });
    await page.goto('/');
    await expect(page.getByTestId('error-state').first()).toBeVisible();
    await shot(page, 'home-error', info);
    // A transport failure carries no `retryable` flag, so the platform error contract offers no Try again button: a reload is the way out.
    upstream.gql(catalog());
    await page.reload();
    await expect(page.getByRole('list', { name: 'Upcoming events' }).getByRole('listitem')).toHaveCount(EVENTS.length);
  });

  test('offline: the browser loses the network after load', async ({ page, context, upstream }, info) => {
    upstream.gql(catalog());
    await page.goto('/');
    await expect(page.getByRole('list', { name: 'Upcoming events' })).toBeVisible();
    await context.setOffline(true);
    await page.getByRole('group', { name: 'Category' }).getByRole('button', { name: 'Comedy', exact: true }).click();
    await expect(page.getByTestId('error-state').first()).toBeVisible();
    await shot(page, 'home-offline', info);
    await context.setOffline(false);
  });
});
