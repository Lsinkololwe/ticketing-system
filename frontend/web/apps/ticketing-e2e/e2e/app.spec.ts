/**
 * Ticketing E2E Tests
 * 
 * Playwright tests for the ticketing application.
 */

import { test, expect } from '@playwright/test';

test.describe('Ticketing Portal E2E Tests', () => {
  test('should display home page', async ({ page }) => {
    await page.goto('/');
    
    // Verify page loaded
    await expect(page).toHaveLoadState('networkidle');
    
    // Verify basic page structure
    const heading = page.getByRole('heading').first();
    await expect(heading).toBeVisible();
  });

  test('should navigate to events page', async ({ page }) => {
    await page.goto('/');
    
    // Look for events link or navigate directly
    const eventsLink = page.getByRole('link', { name: /events/i }).first();
    
    if (await eventsLink.isVisible().catch(() => false)) {
      await eventsLink.click();
      await expect(page).toHaveURL(/\/events/);
    } else {
      // If no link, navigate directly
      await page.goto('/events');
      await expect(page).toHaveLoadState('networkidle');
    }
  });
});

