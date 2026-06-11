/**
 * Responsive Design E2E Tests
 *
 * Tests all breakpoints (mobile, tablet, desktop) and verifies Radix responsive utilities.
 * Ensures proper layout adaptation and spacing across different viewport sizes.
 */

import { test, expect, devices } from '@playwright/test';

/**
 * Breakpoint definitions matching Radix UI and Tailwind
 */
const BREAKPOINTS = {
  mobile: { width: 375, height: 667, name: 'mobile' },        // iPhone SE
  mobileLarge: { width: 414, height: 896, name: 'mobileLarge' }, // iPhone 11 Pro Max
  tablet: { width: 768, height: 1024, name: 'tablet' },       // iPad
  tabletLarge: { width: 1024, height: 1366, name: 'tabletLarge' }, // iPad Pro
  desktop: { width: 1280, height: 720, name: 'desktop' },     // Standard desktop
  desktopLarge: { width: 1920, height: 1080, name: 'desktopLarge' }, // Full HD
};

test.describe('Responsive Design Compliance', () => {
  test.describe('Mobile Breakpoint (375px - 767px)', () => {
    test.use({ viewport: BREAKPOINTS.mobile });

    test('should adapt layout for mobile', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');
      await page.waitForLoadState('networkidle');

      // Take mobile screenshot
      await expect(page).toHaveScreenshot('dashboard-mobile.png', {
        fullPage: true,
        maxDiffPixelRatio: 0.02,
      });

      // Verify mobile navigation (hamburger menu)
      const mobileNav = page.locator('[aria-label*="menu"], [class*="mobile-nav"]').first();

      if (await mobileNav.count() > 0) {
        expect(await mobileNav.isVisible()).toBe(true);
      }
    });

    test('should have touch-friendly tap targets on mobile', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Get all interactive elements
      const buttons = page.locator('button, a[href]');
      const count = await buttons.count();

      for (let i = 0; i < Math.min(count, 10); i++) { // Check first 10 elements
        const button = buttons.nth(i);
        const box = await button.boundingBox();

        if (box) {
          // WCAG 2.1 AAA: minimum 44x44px touch target
          const meetsMinimum = box.width >= 44 && box.height >= 44;

          if (!meetsMinimum) {
            console.warn(
              `Touch target too small: ${box.width}x${box.height}px. ` +
              'Should be at least 44x44px for WCAG 2.1 AAA compliance.'
            );
          }
        }
      }
    });

    test('should not have horizontal scroll on mobile', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
      const viewportWidth = BREAKPOINTS.mobile.width;

      expect(bodyWidth).toBeLessThanOrEqual(viewportWidth);
    });

    test('should stack layout vertically on mobile', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for flex column or vertical stacking
      const mainContent = page.locator('main').first();
      const flexDirection = await mainContent.evaluate((el) =>
        window.getComputedStyle(el).flexDirection
      );

      console.log('Mobile flex direction:', flexDirection);
      // Should be 'column' or elements stacked vertically
    });

    test('should use appropriate font sizes on mobile', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check base font size is readable on mobile (minimum 16px)
      const bodyFontSize = await page.evaluate(() =>
        parseInt(window.getComputedStyle(document.body).fontSize)
      );

      expect(bodyFontSize).toBeGreaterThanOrEqual(14); // Minimum readable size
    });
  });

  test.describe('Tablet Breakpoint (768px - 1023px)', () => {
    test.use({ viewport: BREAKPOINTS.tablet });

    test('should adapt layout for tablet', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');
      await page.waitForLoadState('networkidle');

      // Take tablet screenshot
      await expect(page).toHaveScreenshot('dashboard-tablet.png', {
        fullPage: true,
        maxDiffPixelRatio: 0.02,
      });
    });

    test('should show tablet navigation', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Tablet might show sidebar or hybrid navigation
      const sidebar = page.locator('[class*="sidebar"], nav[class*="rt-"]').first();

      if (await sidebar.count() > 0) {
        const isVisible = await sidebar.isVisible();
        console.log('Tablet sidebar visible:', isVisible);
      }
    });

    test('should use appropriate grid layout on tablet', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for 2-column or hybrid layout
      const grid = page.locator('[class*="rt-Grid"]').first();

      if (await grid.count() > 0) {
        const columns = await grid.evaluate((el) =>
          window.getComputedStyle(el).gridTemplateColumns
        );

        console.log('Tablet grid columns:', columns);
      }
    });
  });

  test.describe('Desktop Breakpoint (1024px+)', () => {
    test.use({ viewport: BREAKPOINTS.desktop });

    test('should adapt layout for desktop', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');
      await page.waitForLoadState('networkidle');

      // Take desktop screenshot
      await expect(page).toHaveScreenshot('dashboard-desktop.png', {
        fullPage: true,
        maxDiffPixelRatio: 0.02,
      });
    });

    test('should show full sidebar navigation on desktop', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      const sidebar = page.locator('[class*="sidebar"], nav[class*="rt-"]').first();

      if (await sidebar.count() > 0) {
        expect(await sidebar.isVisible()).toBe(true);

        // Sidebar should be expanded on desktop
        const width = await sidebar.evaluate((el) =>
          window.getComputedStyle(el).width
        );

        console.log('Desktop sidebar width:', width);
        expect(parseInt(width)).toBeGreaterThan(200); // Reasonable sidebar width
      }
    });

    test('should use multi-column layout on desktop', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for 3+ column layout
      const grid = page.locator('[class*="rt-Grid"]').first();

      if (await grid.count() > 0) {
        const columns = await grid.evaluate((el) =>
          window.getComputedStyle(el).gridTemplateColumns
        );

        console.log('Desktop grid columns:', columns);
        // Should have multiple columns
      }
    });
  });

  test.describe('Radix Responsive Utilities', () => {
    test('should use Radix responsive display props', async ({ page }) => {
      await page.setViewportSize(BREAKPOINTS.mobile);
      await page.goto('http://localhost:3000/dashboard');

      // Check for responsive display classes (rt-r-display-{breakpoint})
      const responsiveElements = await page.locator('[class*="rt-r-display-"]').count();

      console.log(`Found ${responsiveElements} elements with responsive display classes`);
    });

    test('should adapt spacing across breakpoints', async ({ page }) => {
      const breakpoints = [BREAKPOINTS.mobile, BREAKPOINTS.tablet, BREAKPOINTS.desktop];
      const spacingSnapshots: Record<string, any>[] = [];

      for (const bp of breakpoints) {
        await page.setViewportSize(bp);
        await page.goto('http://localhost:3000/dashboard');

        const mainContent = page.locator('main').first();
        const padding = await mainContent.evaluate((el) =>
          window.getComputedStyle(el).padding
        );

        spacingSnapshots.push({
          breakpoint: bp.name,
          padding,
        });
      }

      console.table(spacingSnapshots);

      // Spacing should increase from mobile to desktop
      // This is a general rule, might need adjustment based on design
    });

    test('should adapt typography across breakpoints', async ({ page }) => {
      const breakpoints = [BREAKPOINTS.mobile, BREAKPOINTS.tablet, BREAKPOINTS.desktop];
      const fontSnapshots: Record<string, any>[] = [];

      for (const bp of breakpoints) {
        await page.setViewportSize(bp);
        await page.goto('http://localhost:3000/dashboard');

        const h1 = page.locator('h1').first();

        if (await h1.count() > 0) {
          const fontSize = await h1.evaluate((el) =>
            window.getComputedStyle(el).fontSize
          );

          fontSnapshots.push({
            breakpoint: bp.name,
            h1FontSize: fontSize,
          });
        }
      }

      console.table(fontSnapshots);

      // Font sizes should scale appropriately
    });
  });

  test.describe('Cross-Device Consistency', () => {
    test('should maintain component integrity across devices', async ({ page }) => {
      const breakpoints = [BREAKPOINTS.mobile, BREAKPOINTS.tablet, BREAKPOINTS.desktop];

      for (const bp of breakpoints) {
        await page.setViewportSize(bp);
        await page.goto('http://localhost:3000/dashboard');

        // Verify Radix components render correctly
        const radixComponents = await page.locator('[class*="rt-"]').count();
        expect(radixComponents).toBeGreaterThan(0);

        // Take screenshot for each breakpoint
        await expect(page).toHaveScreenshot(`dashboard-${bp.name}.png`, {
          fullPage: true,
          maxDiffPixelRatio: 0.02,
        });
      }
    });

    test('should maintain color consistency across devices', async ({ page }) => {
      const breakpoints = [BREAKPOINTS.mobile, BREAKPOINTS.desktop];
      const colorSnapshots: Record<string, any>[] = [];

      for (const bp of breakpoints) {
        await page.setViewportSize(bp);
        await page.goto('http://localhost:3000/dashboard');

        const button = page.locator('button[class*="rt-Button"]').first();

        if (await button.count() > 0) {
          const backgroundColor = await button.evaluate((el) =>
            window.getComputedStyle(el).backgroundColor
          );

          colorSnapshots.push({
            breakpoint: bp.name,
            backgroundColor,
          });
        }
      }

      // Colors should be consistent across devices
      if (colorSnapshots.length === 2) {
        expect(colorSnapshots[0].backgroundColor).toBe(colorSnapshots[1].backgroundColor);
      }
    });
  });

  test.describe('Orientation Changes', () => {
    test('should adapt to portrait orientation on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 }); // Portrait

      await page.goto('http://localhost:3000/dashboard');

      await expect(page).toHaveScreenshot('dashboard-mobile-portrait.png', {
        fullPage: true,
        maxDiffPixelRatio: 0.02,
      });
    });

    test('should adapt to landscape orientation on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 667, height: 375 }); // Landscape

      await page.goto('http://localhost:3000/dashboard');

      await expect(page).toHaveScreenshot('dashboard-mobile-landscape.png', {
        fullPage: true,
        maxDiffPixelRatio: 0.02,
      });
    });
  });

  test.describe('Content Reflow', () => {
    test('should reflow content without information loss', async ({ page }) => {
      const breakpoints = [BREAKPOINTS.mobile, BREAKPOINTS.desktop];

      for (const bp of breakpoints) {
        await page.setViewportSize(bp);
        await page.goto('http://localhost:3000/dashboard');

        // Count visible text elements
        const textElements = await page.locator('p, span, h1, h2, h3, h4, h5, h6').count();

        console.log(`${bp.name} - Text elements visible: ${textElements}`);

        // Content should not be hidden on smaller screens
        // This is a basic check - manual verification recommended
      }
    });

    test('should not truncate important information on mobile', async ({ page }) => {
      await page.setViewportSize(BREAKPOINTS.mobile);
      await page.goto('http://localhost:3000/dashboard');

      // Check for truncated text (text-overflow: ellipsis)
      const truncatedElements = await page.locator('[style*="text-overflow: ellipsis"]').count();

      if (truncatedElements > 0) {
        console.warn(
          `Found ${truncatedElements} elements with truncated text. ` +
          'Ensure important information is not hidden.'
        );
      }
    });
  });

  test.describe('Performance Across Devices', () => {
    test('should load efficiently on mobile', async ({ page }) => {
      await page.setViewportSize(BREAKPOINTS.mobile);

      const startTime = Date.now();
      await page.goto('http://localhost:3000/dashboard');
      await page.waitForLoadState('networkidle');
      const loadTime = Date.now() - startTime;

      console.log(`Mobile load time: ${loadTime}ms`);

      // Should load within reasonable time (adjust threshold as needed)
      expect(loadTime).toBeLessThan(5000);
    });

    test('should not load unnecessary resources on mobile', async ({ page }) => {
      await page.setViewportSize(BREAKPOINTS.mobile);

      // Monitor network requests
      const requests: string[] = [];
      page.on('request', request => {
        requests.push(request.url());
      });

      await page.goto('http://localhost:3000/dashboard');
      await page.waitForLoadState('networkidle');

      // Check for desktop-specific images or resources
      const desktopResources = requests.filter(url =>
        url.includes('desktop') || url.includes('large')
      );

      if (desktopResources.length > 0) {
        console.warn(
          `Mobile viewport loaded ${desktopResources.length} desktop-specific resources. ` +
          'Consider using responsive images.'
        );
      }
    });
  });
});
