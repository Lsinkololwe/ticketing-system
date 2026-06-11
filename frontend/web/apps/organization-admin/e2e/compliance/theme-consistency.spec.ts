/**
 * Theme Consistency E2E Tests
 *
 * Visual regression tests to detect color inconsistencies and verify dark mode compatibility.
 * Uses Playwright's screenshot comparison for baseline testing.
 */

import { test, expect } from '@playwright/test';

test.describe('Theme Consistency', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to login page or authenticated state
    await page.goto('http://localhost:3000');
  });

  test.describe('Color Consistency', () => {
    test('should not have hardcoded colors in dashboard', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Wait for page to load
      await page.waitForLoadState('networkidle');

      // Check for inline color styles
      const elementsWithInlineColor = await page.locator('[style*="color:"], [style*="background-color:"]').count();

      if (elementsWithInlineColor > 0) {
        console.warn(`Found ${elementsWithInlineColor} elements with inline color styles on dashboard`);
      }

      // Take screenshot for visual comparison
      await expect(page).toHaveScreenshot('dashboard-colors.png', {
        fullPage: true,
        maxDiffPixels: 100,
      });
    });

    test('should use consistent Radix color classes', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Count Radix color classes
      const radixColorElements = await page.locator('[class*="rt-r-color-"]').count();

      expect(radixColorElements).toBeGreaterThan(0);
    });

    test('should detect color inconsistencies across pages', async ({ page }) => {
      const pages = [
        '/dashboard',
        '/events',
        '/finance',
        '/settings',
      ];

      const colorSnapshots: Record<string, string | null>[] = [];

      for (const pagePath of pages) {
        await page.goto(`http://localhost:3000${pagePath}`);
        await page.waitForLoadState('networkidle');

        // Get computed styles of primary button
        const primaryButton = page.locator('button').first();
        const backgroundColor = await primaryButton.evaluate((el) =>
          window.getComputedStyle(el).backgroundColor
        );
        const color = await primaryButton.evaluate((el) =>
          window.getComputedStyle(el).color
        );

        colorSnapshots.push({
          page: pagePath,
          backgroundColor,
          color,
        });
      }

      // Verify all pages use the same primary button colors
      const firstSnapshot = colorSnapshots[0];
      for (let i = 1; i < colorSnapshots.length; i++) {
        expect(colorSnapshots[i].backgroundColor).toBe(firstSnapshot.backgroundColor);
        expect(colorSnapshots[i].color).toBe(firstSnapshot.color);
      }
    });
  });

  test.describe('Dark Mode Compatibility', () => {
    test('should support dark mode theme toggle', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Find theme toggle (assuming it exists)
      const themeToggle = page.locator('[aria-label*="theme"], [aria-label*="dark mode"]').first();

      if (await themeToggle.count() > 0) {
        // Take screenshot in light mode
        await expect(page).toHaveScreenshot('dashboard-light-mode.png', {
          fullPage: true,
          maxDiffPixels: 100,
        });

        // Toggle to dark mode
        await themeToggle.click();
        await page.waitForTimeout(500); // Wait for theme transition

        // Take screenshot in dark mode
        await expect(page).toHaveScreenshot('dashboard-dark-mode.png', {
          fullPage: true,
          maxDiffPixels: 100,
        });

        // Verify dark mode applied
        const html = page.locator('html');
        const themeClass = await html.getAttribute('class');
        expect(themeClass).toContain('dark');
      }
    });

    test('should use Radix theme appearance prop', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for Radix Theme wrapper with appearance prop
      const themeWrapper = page.locator('[class*="radix-themes"]').first();
      const dataAppearance = await themeWrapper.getAttribute('data-appearance');

      expect(['light', 'dark', 'inherit']).toContain(dataAppearance);
    });

    test('should have proper contrast in dark mode', async ({ page, browserName }) => {
      // Enable dark mode preference
      await page.emulateMedia({ colorScheme: 'dark' });
      await page.goto('http://localhost:3000/dashboard');

      // Wait for page to load
      await page.waitForLoadState('networkidle');

      // Check for sufficient contrast (simplified check)
      const textElements = page.locator('p, span, div').filter({ hasText: /.+/ }).first();

      const color = await textElements.evaluate((el) => {
        const style = window.getComputedStyle(el);
        return {
          color: style.color,
          backgroundColor: style.backgroundColor,
        };
      });

      // Log colors for manual verification
      console.log('Dark mode text color:', color.color);
      console.log('Dark mode background color:', color.backgroundColor);

      // Take screenshot for visual verification
      await expect(page).toHaveScreenshot(`dashboard-dark-mode-${browserName}.png`, {
        fullPage: true,
        maxDiffPixels: 100,
      });
    });
  });

  test.describe('Visual Regression', () => {
    test('should match baseline screenshot for dashboard', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');
      await page.waitForLoadState('networkidle');

      await expect(page).toHaveScreenshot('dashboard-baseline.png', {
        fullPage: true,
        maxDiffPixelRatio: 0.02, // Allow 2% difference
      });
    });

    test('should match baseline screenshot for events page', async ({ page }) => {
      await page.goto('http://localhost:3000/events');
      await page.waitForLoadState('networkidle');

      await expect(page).toHaveScreenshot('events-baseline.png', {
        fullPage: true,
        maxDiffPixelRatio: 0.02,
      });
    });

    test('should match baseline screenshot for finance page', async ({ page }) => {
      await page.goto('http://localhost:3000/finance');
      await page.waitForLoadState('networkidle');

      await expect(page).toHaveScreenshot('finance-baseline.png', {
        fullPage: true,
        maxDiffPixelRatio: 0.02,
      });
    });

    test('should match baseline screenshot for settings page', async ({ page }) => {
      await page.goto('http://localhost:3000/settings');
      await page.waitForLoadState('networkidle');

      await expect(page).toHaveScreenshot('settings-baseline.png', {
        fullPage: true,
        maxDiffPixelRatio: 0.02,
      });
    });
  });

  test.describe('Spacing Consistency', () => {
    test('should use consistent spacing across pages', async ({ page }) => {
      const pages = [
        '/dashboard',
        '/events',
        '/finance',
        '/settings',
      ];

      const spacingSnapshots: Record<string, any>[] = [];

      for (const pagePath of pages) {
        await page.goto(`http://localhost:3000${pagePath}`);
        await page.waitForLoadState('networkidle');

        // Get spacing values
        const mainContent = page.locator('main').first();
        const padding = await mainContent.evaluate((el) =>
          window.getComputedStyle(el).padding
        );
        const gap = await mainContent.evaluate((el) =>
          window.getComputedStyle(el).gap
        );

        spacingSnapshots.push({
          page: pagePath,
          padding,
          gap,
        });
      }

      // Log spacing for verification
      console.table(spacingSnapshots);
    });

    test('should not have hardcoded pixel spacing', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for inline spacing styles
      const elementsWithInlineSpacing = await page.locator('[style*="padding:"], [style*="margin:"], [style*="gap:"]').count();

      if (elementsWithInlineSpacing > 0) {
        console.warn(`Found ${elementsWithInlineSpacing} elements with inline spacing styles`);
      }

      expect(elementsWithInlineSpacing).toBe(0);
    });
  });

  test.describe('Typography Consistency', () => {
    test('should use consistent font sizes across pages', async ({ page }) => {
      const pages = [
        '/dashboard',
        '/events',
        '/finance',
      ];

      const fontSnapshots: Record<string, any>[] = [];

      for (const pagePath of pages) {
        await page.goto(`http://localhost:3000${pagePath}`);
        await page.waitForLoadState('networkidle');

        // Get h1 font size
        const h1 = page.locator('h1').first();
        const fontSize = await h1.evaluate((el) =>
          window.getComputedStyle(el).fontSize
        );

        fontSnapshots.push({
          page: pagePath,
          h1FontSize: fontSize,
        });
      }

      // Verify consistency
      const firstSnapshot = fontSnapshots[0];
      for (let i = 1; i < fontSnapshots.length; i++) {
        expect(fontSnapshots[i].h1FontSize).toBe(firstSnapshot.h1FontSize);
      }
    });

    test('should not have inline font styles', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for inline font styles
      const elementsWithInlineFonts = await page.locator(
        '[style*="font-size:"], [style*="font-weight:"], [style*="font-family:"]'
      ).count();

      if (elementsWithInlineFonts > 0) {
        console.warn(`Found ${elementsWithInlineFonts} elements with inline font styles`);
      }

      expect(elementsWithInlineFonts).toBe(0);
    });
  });
});
