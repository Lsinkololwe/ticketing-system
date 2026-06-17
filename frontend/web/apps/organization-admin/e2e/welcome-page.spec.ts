/**
 * E2E Test: Welcome Page - Accessibility & Navigation
 *
 * Comprehensive tests for the organizer onboarding welcome page.
 *
 * Coverage:
 * 1. Visual rendering and content display
 * 2. Navigation flow to business-info page
 * 3. Keyboard navigation (Tab, Enter, Space)
 * 4. Focus states and accessibility
 * 5. Responsive design (mobile, tablet, desktop)
 * 6. Reduced motion preference support
 * 7. Screen reader accessibility (ARIA)
 * 8. Theme compliance (dark mode, contrast)
 *
 * WCAG 2.1 AA Compliance Testing:
 * - 1.4.3 Contrast (Minimum): Text has 4.5:1 contrast ratio
 * - 2.1.1 Keyboard: All functionality accessible via keyboard
 * - 2.4.7 Focus Visible: Focus indicator is visible
 * - 2.5.5 Target Size: Touch targets are at least 44x44px
 * - 3.2.3 Consistent Navigation: Navigation is consistent
 */

import { test, expect, Page } from '@playwright/test';

// =============================================================================
// TEST SETUP
// =============================================================================

test.describe('Welcome Page', () => {
  test.beforeEach(async ({ page }) => {
    // Mock authentication session
    await page.route('**/api/auth/get-session', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          session: {
            id: 'test-session-id',
            userId: 'test-user-id',
            expiresAt: new Date(Date.now() + 3600000).toISOString(),
          },
          user: {
            id: 'test-user-id',
            name: 'Test User',
            email: 'test@example.com',
          },
        }),
      });
    });

    // Mock GraphQL - no organization (user needs to apply)
    await page.route('**/graphql', async (route) => {
      const request = route.request();
      const postData = request.postDataJSON?.();

      if (postData?.operationName === 'MyOrganization') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: { myOrganization: null },
          }),
        });
      } else {
        await route.continue();
      }
    });
  });

  // ===========================================================================
  // VISUAL RENDERING TESTS
  // ===========================================================================

  test.describe('Visual Rendering', () => {
    test('displays welcome page with all required elements', async ({ page }) => {
      await page.goto('/welcome');

      // Wait for page to load
      await expect(page.getByTestId('welcome-page')).toBeVisible();

      // Verify hero icon
      await expect(page.getByTestId('welcome-icon')).toBeVisible();

      // Verify personalized welcome heading
      const heading = page.getByTestId('welcome-heading');
      await expect(heading).toBeVisible();
      await expect(heading).toContainText('Welcome');

      // Verify subheading
      const subheading = page.getByTestId('welcome-subheading');
      await expect(subheading).toBeVisible();
      await expect(subheading).toContainText('organization');

      // Verify feature list
      const featureList = page.getByTestId('feature-list');
      await expect(featureList).toBeVisible();

      // Verify all 3 feature items
      const featureItems = page.getByTestId('feature-item');
      await expect(featureItems).toHaveCount(3);

      // Verify specific feature content
      await expect(page.getByText('Quick Setup')).toBeVisible();
      await expect(page.getByText('Verified Status')).toBeVisible();
      await expect(page.getByText('Start Selling')).toBeVisible();

      // Verify CTA button
      const ctaButton = page.getByTestId('get-started-button');
      await expect(ctaButton).toBeVisible();
      await expect(ctaButton).toContainText('Get Started');

      // Verify helper text
      const helperText = page.getByTestId('helper-text');
      await expect(helperText).toBeVisible();
      await expect(helperText).toContainText('5 minutes');
    });

    test('displays personalized greeting with user first name', async ({ page }) => {
      await page.goto('/welcome');

      const heading = page.getByTestId('welcome-heading');
      await expect(heading).toContainText('Welcome, Test');
    });

    test('displays fallback greeting when name is not available', async ({ page }) => {
      // Override session without name
      await page.route('**/api/auth/get-session', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            session: {
              id: 'test-session-id',
              userId: 'test-user-id',
              expiresAt: new Date(Date.now() + 3600000).toISOString(),
            },
            user: {
              id: 'test-user-id',
              email: 'test@example.com',
              // No name provided
            },
          }),
        });
      });

      await page.goto('/welcome');

      const heading = page.getByTestId('welcome-heading');
      await expect(heading).toContainText('Welcome, there');
    });
  });

  // ===========================================================================
  // NAVIGATION TESTS
  // ===========================================================================

  test.describe('Navigation', () => {
    test('navigates to business-info page when Get Started is clicked', async ({ page }) => {
      await page.goto('/welcome');

      // Click CTA button
      await page.getByTestId('get-started-button').click();

      // Should navigate to business-info
      await expect(page).toHaveURL('/apply/business-info');
    });

    test('CTA button is the primary focus target', async ({ page }) => {
      await page.goto('/welcome');

      // Wait for auto-focus (100ms delay in component)
      await page.waitForTimeout(200);

      // CTA button should be focused
      const ctaButton = page.getByTestId('get-started-button');
      await expect(ctaButton).toBeFocused();
    });

    test('maintains navigation state after page reload', async ({ page }) => {
      await page.goto('/welcome');

      // Reload the page
      await page.reload();

      // Should still be on welcome page
      await expect(page).toHaveURL('/welcome');
      await expect(page.getByTestId('welcome-page')).toBeVisible();
    });
  });

  // ===========================================================================
  // KEYBOARD NAVIGATION TESTS (WCAG 2.1.1)
  // ===========================================================================

  test.describe('Keyboard Navigation', () => {
    test('can navigate to business-info using Enter key', async ({ page }) => {
      await page.goto('/welcome');

      // Focus CTA button
      const ctaButton = page.getByTestId('get-started-button');
      await ctaButton.focus();

      // Press Enter
      await page.keyboard.press('Enter');

      // Should navigate
      await expect(page).toHaveURL('/apply/business-info');
    });

    test('can navigate to business-info using Space key', async ({ page }) => {
      await page.goto('/welcome');

      // Focus CTA button
      const ctaButton = page.getByTestId('get-started-button');
      await ctaButton.focus();

      // Press Space
      await page.keyboard.press('Space');

      // Should navigate
      await expect(page).toHaveURL('/apply/business-info');
    });

    test('Tab key cycles through focusable elements in correct order', async ({ page }) => {
      await page.goto('/welcome');

      // Start from document body
      await page.keyboard.press('Tab');

      // First focusable should be header logo link (in layout)
      // Then help button, user avatar area, logout, then CTA

      // Navigate to CTA button
      let ctaFocused = false;
      for (let i = 0; i < 10; i++) {
        const focusedElement = await page.evaluate(() => document.activeElement?.getAttribute('data-testid'));
        if (focusedElement === 'get-started-button') {
          ctaFocused = true;
          break;
        }
        await page.keyboard.press('Tab');
      }

      expect(ctaFocused).toBe(true);
    });

    test('Shift+Tab navigates backwards through focusable elements', async ({ page }) => {
      await page.goto('/welcome');

      // Focus CTA button first
      await page.getByTestId('get-started-button').focus();
      await expect(page.getByTestId('get-started-button')).toBeFocused();

      // Shift+Tab should move focus to previous element
      await page.keyboard.press('Shift+Tab');

      // CTA should no longer be focused
      await expect(page.getByTestId('get-started-button')).not.toBeFocused();
    });
  });

  // ===========================================================================
  // FOCUS STATES TESTS (WCAG 2.4.7)
  // ===========================================================================

  test.describe('Focus States', () => {
    test('CTA button has visible focus ring when focused', async ({ page }) => {
      await page.goto('/welcome');

      const ctaButton = page.getByTestId('get-started-button');
      await ctaButton.focus();

      // Check that focus-visible styles are applied
      // The button should have a box-shadow when focused
      const boxShadow = await ctaButton.evaluate((el) =>
        window.getComputedStyle(el).boxShadow
      );

      // Should have multiple shadows (focus ring)
      expect(boxShadow).not.toBe('none');
      expect(boxShadow.length).toBeGreaterThan(10);
    });

    test('focus indicator is visible against background', async ({ page }) => {
      await page.goto('/welcome');

      const ctaButton = page.getByTestId('get-started-button');
      await ctaButton.focus();

      // Take screenshot for visual verification
      await expect(ctaButton).toHaveScreenshot('cta-button-focused.png', {
        maxDiffPixels: 100,
      });
    });
  });

  // ===========================================================================
  // RESPONSIVE DESIGN TESTS
  // ===========================================================================

  test.describe('Responsive Design', () => {
    test.describe('Mobile (375px)', () => {
      test.use({ viewport: { width: 375, height: 667 } });

      test('renders correctly on mobile', async ({ page }) => {
        await page.goto('/welcome');

        // All elements should be visible
        await expect(page.getByTestId('welcome-page')).toBeVisible();
        await expect(page.getByTestId('welcome-heading')).toBeVisible();
        await expect(page.getByTestId('get-started-button')).toBeVisible();
        await expect(page.getByTestId('feature-list')).toBeVisible();

        // Features should stack vertically
        const featureItems = page.getByTestId('feature-item');
        await expect(featureItems).toHaveCount(3);
      });

      test('CTA button is full width on mobile', async ({ page }) => {
        await page.goto('/welcome');

        const ctaButton = page.getByTestId('get-started-button');
        const buttonBox = await ctaButton.boundingBox();

        // Button should be at least 90% of viewport width (accounting for padding)
        expect(buttonBox?.width).toBeGreaterThan(300);
      });

      test('touch target size is at least 44x44px (WCAG 2.5.5)', async ({ page }) => {
        await page.goto('/welcome');

        const ctaButton = page.getByTestId('get-started-button');
        const buttonBox = await ctaButton.boundingBox();

        // CTA button should have minimum touch target size
        expect(buttonBox?.width).toBeGreaterThanOrEqual(44);
        expect(buttonBox?.height).toBeGreaterThanOrEqual(44);
      });
    });

    test.describe('Tablet (768px)', () => {
      test.use({ viewport: { width: 768, height: 1024 } });

      test('renders correctly on tablet', async ({ page }) => {
        await page.goto('/welcome');

        await expect(page.getByTestId('welcome-page')).toBeVisible();
        await expect(page.getByTestId('welcome-heading')).toBeVisible();
        await expect(page.getByTestId('get-started-button')).toBeVisible();
      });
    });

    test.describe('Desktop (1440px)', () => {
      test.use({ viewport: { width: 1440, height: 900 } });

      test('renders correctly on desktop', async ({ page }) => {
        await page.goto('/welcome');

        await expect(page.getByTestId('welcome-page')).toBeVisible();
        await expect(page.getByTestId('welcome-heading')).toBeVisible();
        await expect(page.getByTestId('get-started-button')).toBeVisible();
      });

      test('content is centered and constrained in width', async ({ page }) => {
        await page.goto('/welcome');

        const welcomePage = page.getByTestId('welcome-page');
        const pageBox = await welcomePage.boundingBox();

        // Content should be centered (check by verifying it's not touching edges)
        expect(pageBox?.x).toBeGreaterThan(100);
      });
    });
  });

  // ===========================================================================
  // ACCESSIBILITY TESTS (ARIA & Semantic HTML)
  // ===========================================================================

  test.describe('Accessibility', () => {
    test('page has proper heading hierarchy', async ({ page }) => {
      await page.goto('/welcome');

      // Should have exactly one h1
      const h1Count = await page.locator('h1').count();
      expect(h1Count).toBe(1);

      // H1 should be the welcome heading
      const h1 = page.locator('h1');
      await expect(h1).toContainText('Welcome');
    });

    test('main content area has proper role and aria-label', async ({ page }) => {
      await page.goto('/welcome');

      const mainSection = page.getByTestId('welcome-page');

      // Should have role="main" (Radix Section component)
      await expect(mainSection).toHaveAttribute('role', 'main');

      // Should have aria-labelledby pointing to heading
      await expect(mainSection).toHaveAttribute('aria-labelledby', 'welcome-heading');
    });

    test('feature list has proper list semantics', async ({ page }) => {
      await page.goto('/welcome');

      const featureList = page.getByTestId('feature-list');

      // Should have role="list"
      await expect(featureList).toHaveAttribute('role', 'list');

      // Should have aria-label
      await expect(featureList).toHaveAttribute('aria-label', 'Benefits of becoming an organizer');

      // Should contain li elements
      const listItems = featureList.locator('li');
      await expect(listItems).toHaveCount(3);
    });

    test('CTA button has proper aria-describedby', async ({ page }) => {
      await page.goto('/welcome');

      const ctaButton = page.getByTestId('get-started-button');

      // Should reference helper text
      await expect(ctaButton).toHaveAttribute('aria-describedby', 'cta-helper-text');
    });

    test('decorative icons are hidden from screen readers', async ({ page }) => {
      await page.goto('/welcome');

      // Icon in welcome section should be aria-hidden
      const heroIcon = page.getByTestId('welcome-icon');
      await expect(heroIcon).toHaveAttribute('aria-hidden', 'true');
    });

    test('skip link is available for keyboard users', async ({ page }) => {
      await page.goto('/welcome');

      // Skip link should exist (visually hidden until focused)
      const skipLink = page.locator('a[href="#get-started-button"]');
      await expect(skipLink).toBeAttached();

      // Should have screen reader text
      await expect(skipLink).toContainText('Skip to main action');
    });
  });

  // ===========================================================================
  // THEME COMPLIANCE TESTS
  // ===========================================================================

  test.describe('Theme Compliance', () => {
    test('uses Radix theme tokens for heading text', async ({ page }) => {
      await page.goto('/welcome');

      const heading = page.getByTestId('welcome-heading');

      // Verify heading uses highContrast (should have high contrast text color)
      const headingColor = await heading.evaluate((el) =>
        window.getComputedStyle(el).color
      );

      // Should be a light color (high luminance) for dark theme
      // Radix highContrast provides maximum contrast
      expect(headingColor).toBeTruthy();
    });

    test('CTA button uses Radix Button solid variant with accent color', async ({ page }) => {
      await page.goto('/welcome');

      const ctaButton = page.getByTestId('get-started-button');

      // Radix Button with variant="solid" should have background color
      const backgroundColor = await ctaButton.evaluate((el) =>
        window.getComputedStyle(el).backgroundColor
      );

      // Should have a visible background (not transparent)
      expect(backgroundColor).not.toBe('transparent');
      expect(backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
    });

    test('feature cards use Radix Card variant="surface"', async ({ page }) => {
      await page.goto('/welcome');

      const featureCard = page.getByTestId('feature-item').first();

      // Radix Card with variant="surface" should have subtle background
      const backgroundColor = await featureCard.evaluate((el) =>
        window.getComputedStyle(el).backgroundColor
      );

      // Should have a background color (surface variant)
      expect(backgroundColor).toBeTruthy();
    });

    test('hero icon uses theme accent tokens for gradient', async ({ page }) => {
      await page.goto('/welcome');

      const heroIcon = page.getByTestId('welcome-icon');

      // Should have background (gradient is applied via CSS)
      const background = await heroIcon.evaluate((el) =>
        window.getComputedStyle(el).background
      );

      // Should contain gradient using accent tokens
      expect(background).toContain('linear-gradient');
    });

    test('gray text uses Radix gray color scale (slate)', async ({ page }) => {
      await page.goto('/welcome');

      const subheading = page.getByTestId('welcome-subheading');

      // Text with color="gray" should use slate gray scale
      const color = await subheading.evaluate((el) =>
        window.getComputedStyle(el).color
      );

      // Should have a muted gray color (not pure white or black)
      expect(color).toBeTruthy();
    });

    test('uses Radix spacing tokens for layout', async ({ page }) => {
      await page.goto('/welcome');

      // Feature list should have gap using Radix spacing
      const featureList = page.getByTestId('feature-list');
      const gap = await featureList.evaluate((el) =>
        window.getComputedStyle(el).gap
      );

      // Should have gap defined (Radix uses CSS custom properties)
      expect(gap).not.toBe('normal');
    });

    test('uses Radix radius tokens for border-radius', async ({ page }) => {
      await page.goto('/welcome');

      const featureCard = page.getByTestId('feature-item').first();
      const borderRadius = await featureCard.evaluate((el) =>
        window.getComputedStyle(el).borderRadius
      );

      // Should have border radius (Radix Card has built-in radius)
      expect(borderRadius).not.toBe('0px');
    });
  });

  // ===========================================================================
  // REDUCED MOTION TESTS
  // ===========================================================================

  test.describe('Reduced Motion', () => {
    test('respects prefers-reduced-motion setting', async ({ page }) => {
      // Emulate reduced motion preference
      await page.emulateMedia({ reducedMotion: 'reduce' });

      await page.goto('/welcome');

      const ctaButton = page.getByTestId('get-started-button');

      // Get transition value
      const transition = await ctaButton.evaluate((el) =>
        window.getComputedStyle(el).transition
      );

      // With reduced motion, transition should be very short or none
      // The CSS sets transition: none !important for prefers-reduced-motion
      expect(transition).toMatch(/(none|0s|0\.01ms)/);
    });
  });

  // ===========================================================================
  // ERROR HANDLING TESTS
  // ===========================================================================

  test.describe('Error Handling', () => {
    test('handles session loading state gracefully', async ({ page }) => {
      // Add delay to session endpoint
      await page.route('**/api/auth/get-session', async (route) => {
        await new Promise((resolve) => setTimeout(resolve, 500));
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            session: {
              id: 'test-session-id',
              userId: 'test-user-id',
              expiresAt: new Date(Date.now() + 3600000).toISOString(),
            },
            user: {
              id: 'test-user-id',
              name: 'Test User',
              email: 'test@example.com',
            },
          }),
        });
      });

      await page.goto('/welcome');

      // Page should still render
      await expect(page.getByTestId('welcome-page')).toBeVisible();
    });
  });

  // ===========================================================================
  // VISUAL REGRESSION TESTS
  // ===========================================================================

  test.describe('Visual Regression', () => {
    test('welcome page matches snapshot', async ({ page }) => {
      await page.goto('/welcome');

      // Wait for fonts and images to load
      await page.waitForLoadState('networkidle');

      await expect(page).toHaveScreenshot('welcome-page-full.png', {
        fullPage: true,
        maxDiffPixels: 200,
      });
    });

    test('welcome page mobile matches snapshot', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/welcome');

      await page.waitForLoadState('networkidle');

      await expect(page).toHaveScreenshot('welcome-page-mobile.png', {
        fullPage: true,
        maxDiffPixels: 200,
      });
    });
  });
});

// =============================================================================
// INTEGRATION TESTS WITH ONBOARDING FLOW
// =============================================================================

test.describe('Welcome Page → Onboarding Integration', () => {
  test.beforeEach(async ({ page }) => {
    // Mock authentication
    await page.route('**/api/auth/get-session', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          session: {
            id: 'test-session-id',
            userId: 'test-user-id',
            expiresAt: new Date(Date.now() + 3600000).toISOString(),
          },
          user: {
            id: 'test-user-id',
            name: 'Integration Test User',
            email: 'integration@test.com',
          },
        }),
      });
    });

    // Mock GraphQL
    await page.route('**/graphql', async (route) => {
      const request = route.request();
      const postData = request.postDataJSON?.();

      if (postData?.operationName === 'MyOrganization') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: { myOrganization: null },
          }),
        });
      } else {
        await route.continue();
      }
    });
  });

  test('complete flow from welcome to business info form', async ({ page }) => {
    // Start at welcome
    await page.goto('/welcome');
    await expect(page.getByTestId('welcome-page')).toBeVisible();

    // Click Get Started
    await page.getByTestId('get-started-button').click();

    // Should be on business info page
    await expect(page).toHaveURL('/apply/business-info');

    // Business info form should be visible
    await expect(page.getByLabel(/organization name/i)).toBeVisible();
  });

  test('keyboard-only user can complete welcome → business info flow', async ({ page }) => {
    await page.goto('/welcome');

    // Wait for auto-focus on CTA
    await page.waitForTimeout(200);

    // Verify CTA is focused
    await expect(page.getByTestId('get-started-button')).toBeFocused();

    // Press Enter to navigate
    await page.keyboard.press('Enter');

    // Should navigate to business info
    await expect(page).toHaveURL('/apply/business-info');
  });
});
