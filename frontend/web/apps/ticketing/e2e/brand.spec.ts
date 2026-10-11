import { expect, test } from '@playwright/test';

/**
 * The storefront wears the shared M3 buyer theme: `<html data-app="buyer">` and its own
 * `--m3-*` token values, a distinct shade of teal from the organizer/platform consoles (not a
 * different color family — see m3.themes.css's own comment). A lost `data-app` silently falls
 * back to a different app's palette, so the resolved token values are read from the running
 * document, not the stylesheet.
 */
test.describe('buyer M3 theme contract', () => {
  test('serves the buyer theme with resolved M3 tokens', async ({ page }) => {
    await page.goto('/');

    const contract = await page.evaluate(() => {
      const root = document.documentElement;
      const cs = getComputedStyle(root);
      return { app: root.getAttribute('data-app'), primary: cs.getPropertyValue('--m3-primary').trim() };
    });

    expect(contract.app, 'the theme layer hangs off this attribute').toBe('buyer');
    expect(contract.primary, 'an empty token means the M3 stylesheet did not load').not.toBe('');
  });
});
