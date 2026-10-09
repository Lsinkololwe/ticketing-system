import { expect, test } from '@playwright/test';

const APP_URL = process.env.BASE_URL || `http://localhost:${process.env.APP_PORT ?? 3003}`;

/**
 * The console runs on the shared M3 design system under `data-app="organizer"`.
 * Read from the running document (public landing page, no session needed) because a lost
 * attribute silently falls every token back to the default palette.
 */
test.describe('organizer M3 theme contract', () => {
  test('serves the organizer theme with resolved M3 tokens', async ({ page }) => {
    await page.goto('/');
    expect(page.url().startsWith(APP_URL)).toBe(true);

    const contract = await page.evaluate(() => {
      const root = document.documentElement;
      const cs = getComputedStyle(root);
      return { app: root.getAttribute('data-app'), primary: cs.getPropertyValue('--m3-primary').trim() };
    });

    expect(contract.app, 'the theme layer hangs off this attribute').toBe('organizer');
    expect(contract.primary, 'a non-empty primary means m3.themes.css loaded').not.toBe('');
  });
});
