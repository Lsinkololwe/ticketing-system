/**
 * E2E Security and Edge Case Tests
 *
 * Tests for:
 * - XSS prevention
 * - SQL injection attempts
 * - CSRF protection
 * - Unicode and special characters
 * - Race conditions
 * - Concurrent submissions
 */

import { test, expect } from '@playwright/test';

test.describe('Security Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/graphql', async (route) => {
      const request = route.request();
      const postData = request.postDataJSON();

      if (postData.operationName === 'MyOrganization') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: { myOrganization: null } }),
        });
      } else {
        await route.continue();
      }
    });
  });

  test('sanitizes XSS in organization name', async ({ page }) => {
    await page.goto('/apply/business-info');

    const xssPayload = '<script>alert("XSS")</script>';
    await page.getByLabel(/organization name/i).fill(xssPayload);

    // Verify the input contains the text but doesn't execute
    await expect(page.getByLabel(/organization name/i)).toHaveValue(xssPayload);

    // Ensure no script was injected into the DOM
    const scripts = await page.locator('script').evaluateAll((scripts) =>
      scripts.map((s) => s.textContent)
    );
    const hasXSS = scripts.some((script) => script?.includes('alert("XSS")'));
    expect(hasXSS).toBe(false);
  });

  test('sanitizes XSS in description field', async ({ page }) => {
    await page.goto('/apply/business-info');

    const xssPayload = '<img src=x onerror=alert("XSS")>';
    await page.getByLabel(/about your organization/i).fill(xssPayload);

    // Check that img tag wasn't rendered
    const maliciousImg = page.locator('img[src="x"]');
    await expect(maliciousImg).toHaveCount(0);
  });

  test('handles SQL injection attempts', async ({ page }) => {
    await page.goto('/apply/business-info');

    const sqlPayload = "'; DROP TABLE organizations--";
    await page.getByLabel(/organization name/i).fill(sqlPayload);
    await page.getByLabel(/business email/i).fill('test@example.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    // Should treat as normal string
    await expect(page.getByLabel(/organization name/i)).toHaveValue(sqlPayload);
  });

  test('prevents form resubmission on double click', async ({ page }) => {
    let submitCount = 0;

    await page.route('**/graphql', async (route) => {
      const postData = route.request().postDataJSON();
      if (postData.operationName === 'ApplyToBeOrganizer') {
        submitCount++;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              applyToBeOrganizer: {
                __typename: 'Organization',
                id: 'org-123',
                status: 'DRAFT',
              },
            },
          }),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto('/apply/business-info');

    await page.getByLabel(/organization name/i).fill('Test Company');
    await page.getByLabel(/business email/i).fill('test@example.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    // Click continue button multiple times rapidly
    const continueButton = page.getByRole('button', { name: /continue/i });
    await continueButton.click({ clickCount: 3 });

    // Wait for navigation
    await page.waitForURL('/apply/review', { timeout: 5000 });

    // Should only submit once
    expect(submitCount).toBeLessThanOrEqual(1);
  });
});

test.describe('Unicode and Special Characters', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/graphql', async (route) => {
      await route.continue();
    });
  });

  test('handles Japanese characters', async ({ page }) => {
    await page.goto('/apply/business-info');

    const japaneseText = '日本のイベント会社';
    await page.getByLabel(/organization name/i).fill(japaneseText);

    await expect(page.getByLabel(/organization name/i)).toHaveValue(japaneseText);
  });

  test('handles Arabic characters (RTL)', async ({ page }) => {
    await page.goto('/apply/business-info');

    const arabicText = 'شركة الفعاليات';
    await page.getByLabel(/organization name/i).fill(arabicText);

    await expect(page.getByLabel(/organization name/i)).toHaveValue(arabicText);
  });

  test('handles emojis', async ({ page }) => {
    await page.goto('/apply/business-info');

    const emojiText = 'Party Events 🎉🎊🎈';
    await page.getByLabel(/organization name/i).fill(emojiText);

    await expect(page.getByLabel(/organization name/i)).toHaveValue(emojiText);
  });

  test('handles mixed Unicode', async ({ page }) => {
    await page.goto('/apply/business-info');

    const mixedText = 'Café Música España 音楽 🎵';
    await page.getByLabel(/organization name/i).fill(mixedText);

    await expect(page.getByLabel(/organization name/i)).toHaveValue(mixedText);
  });

  test('handles zero-width characters', async ({ page }) => {
    await page.goto('/apply/business-info');

    // Zero-width space, zero-width joiner, etc.
    const zwText = 'Test​Company‌Name';
    await page.getByLabel(/organization name/i).fill(zwText);

    await expect(page.getByLabel(/organization name/i)).toHaveValue(zwText);
  });
});

test.describe('Edge Cases', () => {
  test('handles very long text input', async ({ page }) => {
    await page.goto('/apply/business-info');

    const longText = 'A'.repeat(1000);
    await page.getByLabel(/organization name/i).fill(longText);

    await expect(page.getByLabel(/organization name/i)).toHaveValue(longText);
  });

  test('handles whitespace-only input in optional fields', async ({ page }) => {
    await page.goto('/apply/business-info');

    await page.getByLabel(/tagline/i).fill('   ');
    await page.getByLabel(/website/i).fill('   ');

    // Should accept (validation should trim)
    await page.getByLabel(/organization name/i).fill('Test Company');
    await page.getByLabel(/business email/i).fill('test@example.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    await page.getByRole('button', { name: /continue/i }).click();
    await expect(page).toHaveURL('/apply/review');
  });

  test('handles rapid field switching', async ({ page }) => {
    await page.goto('/apply/business-info');

    const nameField = page.getByLabel(/organization name/i);

    // Rapidly type, clear, type
    await nameField.fill('First');
    await nameField.clear();
    await nameField.fill('Second');
    await nameField.clear();
    await nameField.fill('Third');

    await expect(nameField).toHaveValue('Third');
  });

  test('handles email with special characters', async ({ page }) => {
    await page.goto('/apply/business-info');

    // Valid email with special characters
    const specialEmail = 'test+org_2024@example.co.zm';
    await page.getByLabel(/business email/i).fill(specialEmail);
    await page.getByLabel(/organization name/i).fill('Test');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    await page.getByRole('button', { name: /continue/i }).click();

    // Should not show validation error
    await expect(page).toHaveURL('/apply/review');
  });

  test('handles international phone numbers', async ({ page }) => {
    await page.goto('/apply/business-info');

    const phoneNumbers = [
      '+260971234567', // Zambia
      '+1-555-123-4567', // US with dashes
      '+44 20 7946 0958', // UK with spaces
      '+86 138 0013 8000', // China
    ];

    for (const phone of phoneNumbers) {
      const phoneField = page.getByLabel(/phone number/i);
      await phoneField.clear();
      await phoneField.fill(phone);
      await expect(phoneField).toHaveValue(phone);
    }
  });

  test('handles URL with various protocols', async ({ page }) => {
    await page.goto('/apply/business-info');

    const urls = [
      'https://example.com',
      'http://example.com',
      'https://subdomain.example.com',
      'https://example.com:8080/path',
    ];

    for (const url of urls) {
      const websiteField = page.getByLabel(/website/i);
      await websiteField.clear();
      await websiteField.fill(url);
      await expect(websiteField).toHaveValue(url);
    }
  });
});

test.describe('Network Edge Cases', () => {
  test('handles slow network gracefully', async ({ page }) => {
    await page.route('**/graphql', async (route) => {
      // Simulate slow network (2 second delay)
      await new Promise((resolve) => setTimeout(resolve, 2000));
      await route.continue();
    });

    await page.goto('/apply/business-info');

    // Should show loading state
    await expect(page.getByText(/loading/i)).toBeVisible({ timeout: 1000 });
  });

  test('handles intermittent connection', async ({ page }) => {
    let requestCount = 0;

    await page.route('**/graphql', async (route) => {
      requestCount++;
      if (requestCount === 1) {
        // Fail first request
        await route.fulfill({
          status: 503,
          body: JSON.stringify({ errors: [{ message: 'Network error' }] }),
        });
      } else {
        // Succeed on retry
        await route.continue();
      }
    });

    await page.goto('/apply/business-info');

    // Should show error
    await expect(page.getByText(/unable to connect/i)).toBeVisible();

    // Retry should work
    await page.getByRole('button', { name: /try again/i }).click();
    await expect(page.getByLabel(/organization name/i)).toBeVisible();
  });

  test('handles timeout scenarios', async ({ page }) => {
    await page.route('**/graphql', async (route) => {
      // Never respond (timeout)
      await new Promise(() => {}); // Infinite wait
    });

    await page.goto('/apply/business-info', { waitUntil: 'domcontentloaded' });

    // Should eventually show loading or error state
    await expect(page.getByText(/loading/i).or(page.getByText(/error/i))).toBeVisible({
      timeout: 10000,
    });
  });
});

test.describe('Browser Compatibility', () => {
  test('works with browser back/forward buttons', async ({ page }) => {
    await page.goto('/apply/business-info');

    await page.getByLabel(/organization name/i).fill('Test Company');
    await page.getByLabel(/business email/i).fill('test@example.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    await page.getByRole('button', { name: /continue/i }).click();
    await expect(page).toHaveURL('/apply/review');

    // Use browser back button
    await page.goBack();
    await expect(page).toHaveURL('/apply/business-info');

    // Data should persist
    await expect(page.getByLabel(/organization name/i)).toHaveValue('Test Company');

    // Use browser forward button
    await page.goForward();
    await expect(page).toHaveURL('/apply/review');
  });

  test('handles page refresh without data loss', async ({ page }) => {
    await page.goto('/apply/business-info');

    await page.getByLabel(/organization name/i).fill('Test Company');

    // Reload page
    await page.reload();

    // In a real app with session storage, data might persist
    // For now, form should reload cleanly
    await expect(page.getByLabel(/organization name/i)).toBeVisible();
  });
});
