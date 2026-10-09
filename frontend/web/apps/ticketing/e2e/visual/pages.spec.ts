import { expect, test } from '@playwright/test';
import { mockCatalog } from './fixtures';

const shot = async (page: import('@playwright/test').Page, name: string, testInfo: import('@playwright/test').TestInfo) => {
  if (process.env.SHOTS_DIR) await page.screenshot({ path: `${process.env.SHOTS_DIR}/${testInfo.project.name}-${name}.png`, fullPage: true });
};

test.describe('buyer pages with catalog fixtures', () => {
  test('home lists featured, trending and upcoming events', async ({ page }, info) => {
    await mockCatalog(page);
    await page.goto('/');
    await expect(page.getByRole('region', { name: 'Featured events' })).toBeVisible();
    await expect(page.getByRole('list', { name: 'Upcoming events' }).getByRole('listitem')).toHaveCount(3);
    await expect(page.getByRole('list', { name: 'Trending events, ranked' })).toBeVisible();
    const chips = page.getByRole('group', { name: 'Category' });
    await chips.getByRole('button', { name: 'Comedy', exact: true }).click();
    await expect(chips.getByRole('button', { name: 'Comedy', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await shot(page, 'home', info);
  });

  test('home shows the error state when the catalog is unavailable', async ({ page }, info) => {
    await mockCatalog(page, { fail: true });
    await page.goto('/');
    await expect(page.getByTestId('error-state')).toBeVisible();
    await shot(page, 'home-error', info);
  });

  test('event page shows tiers, selects quantity and totals the order', async ({ page }, info) => {
    await mockCatalog(page);
    await page.goto('/events/e1');
    await expect(page.getByRole('heading', { level: 1, name: 'Fixture Sunset Sessions' })).toBeVisible();
    await page.getByRole('button', { name: 'Add one General ticket' }).click();
    await page.getByRole('button', { name: 'Add one General ticket' }).click();
    await expect(page.getByRole('complementary', { name: 'Your tickets' })).toContainText('K 300');
    await expect(page.getByText('Not available').first()).toBeVisible();
    await shot(page, 'event', info);
    await page.getByRole('tab', { name: 'Venue and access' }).click();
    await shot(page, 'event-venue', info);
  });

  test('signed-out checkout with nothing parked asks to choose tickets', async ({ page }, info) => {
    await mockCatalog(page);
    await page.goto('/events/e1/book');
    await expect(page.getByText('Choose your tickets first')).toBeVisible();
    await shot(page, 'checkout-empty', info);
  });

  test('sign-in, help and terms render', async ({ page }, info) => {
    await mockCatalog(page);
    await page.goto('/auth');
    await expect(page.getByRole('heading', { name: 'Sign in to Showstop' })).toBeVisible();
    await shot(page, 'auth', info);
    await page.goto('/help');
    await expect(page.getByRole('heading', { name: 'Refund policies' })).toBeVisible();
    await shot(page, 'help', info);
    await page.goto('/terms');
    await expect(page.getByTestId('legal-draft-banner')).toBeVisible();
  });

  test('guarded pages send a signed-out visitor to sign in', async ({ page }) => {
    await mockCatalog(page);
    for (const route of ['/my-tickets', '/notifications', '/profile']) {
      await page.goto(route);
      await expect(page).toHaveURL(/\/auth\?next=/);
    }
  });
});
