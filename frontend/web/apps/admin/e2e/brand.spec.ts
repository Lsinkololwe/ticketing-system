import { expect, test } from '@playwright/test';

/**
 * The console wears the shared M3 platform theme: `<html data-app="platform">` and the
 * `--m3-*` token layer. A lost `data-app` silently falls back to the buyer palette, so the
 * resolved token values are read from the running document, not the stylesheet.
 */
test.describe('admin theme contract', () => {
  test('serves the platform theme on the sign-in page', async ({ page }) => {
    await page.goto('/login');

    const { app, primary, heading } = await page.evaluate(() => {
      const root = document.documentElement;
      const cs = getComputedStyle(root);
      return {
        app: root.dataset.app,
        primary: cs.getPropertyValue('--m3-primary').trim(),
        heading: document.querySelector('h1')?.textContent ?? '',
      };
    });

    expect(app).toBe('platform');
    expect(primary, 'an empty token means the M3 stylesheet did not load').not.toBe('');
    expect(heading).toMatch(/platform admin/i);
  });
});
