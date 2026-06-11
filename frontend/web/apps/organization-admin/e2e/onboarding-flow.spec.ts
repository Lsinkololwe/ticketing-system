/**
 * E2E Test: Organization Onboarding Flow
 *
 * Tests the complete user journey from application start to submission:
 * 1. User navigates to application form
 * 2. Fills in organization information
 * 3. Reviews entered data
 * 4. Accepts terms and conditions
 * 5. Submits application
 * 6. Views application status
 *
 * Also tests:
 * - Form validation
 * - Data persistence between pages
 * - Error handling
 * - Navigation (back/forward)
 */

import { test, expect } from '@playwright/test';

test.describe('Organization Onboarding Flow', () => {
  test.beforeEach(async ({ page }) => {
    // Intercept GraphQL requests and mock responses
    await page.route('**/graphql', async (route) => {
      const request = route.request();
      const postData = request.postDataJSON();

      if (postData.operationName === 'MyOrganization') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: { myOrganization: null },
          }),
        });
      } else if (postData.operationName === 'ApplyToBeOrganizer') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              applyToBeOrganizer: {
                __typename: 'Organization',
                id: 'org-test-123',
                name: postData.variables.input.name,
                ...postData.variables.input,
                status: 'DRAFT',
                canSubmitForReview: false,
                isApproved: false,
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
              },
            },
          }),
        });
      } else if (postData.operationName === 'SubmitOrganizationForReview') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              submitOrganizationForReview: {
                __typename: 'Organization',
                id: postData.variables.id,
                status: 'PENDING_REVIEW',
                submittedAt: new Date().toISOString(),
                canSubmitForReview: false,
              },
            },
          }),
        });
      } else {
        await route.continue();
      }
    });
  });

  test('complete onboarding workflow from start to submission', async ({ page }) => {
    // Navigate to business info page
    await page.goto('/apply/business-info');

    // Wait for form to load
    await expect(page.getByLabel(/organization name/i)).toBeVisible();

    // Fill in required fields
    await page.getByLabel(/organization name/i).fill('E2E Test Events Company');
    await page.getByLabel(/business email/i).fill('e2e-test@example.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    // Select province
    await page.getByLabel(/province/i).click();
    await page.getByText('Lusaka Province').click();

    // Fill optional fields
    await page.getByLabel(/tagline/i).fill('Creating amazing test events');
    await page.getByLabel(/about your organization/i).fill('We are a test organization for E2E testing');
    await page.getByLabel(/website/i).fill('https://e2e-test-events.com');

    // Submit form
    await page.getByRole('button', { name: /continue/i }).click();

    // Should navigate to review page
    await expect(page).toHaveURL('/apply/review');

    // Verify data is displayed correctly
    await expect(page.getByText('E2E Test Events Company')).toBeVisible();
    await expect(page.getByText('e2e-test@example.com')).toBeVisible();
    await expect(page.getByText('+260971234567')).toBeVisible();
    await expect(page.getByText('Lusaka')).toBeVisible();

    // Accept terms and conditions
    const checkboxes = page.getByRole('checkbox');
    await checkboxes.nth(0).check();
    await checkboxes.nth(1).check();

    // Submit application
    await page.getByRole('button', { name: /submit application/i }).click();

    // Should navigate to status page
    await expect(page).toHaveURL('/apply/status');

    // Verify status page shows pending review
    await expect(page.getByText(/under review/i)).toBeVisible();
  });

  test('validates required fields before allowing submission', async ({ page }) => {
    await page.goto('/apply/business-info');

    // Try to submit without filling fields
    await page.getByRole('button', { name: /continue/i }).click();

    // Should show validation errors
    await expect(page.getByText(/organization name is required/i)).toBeVisible();
    await expect(page.getByText(/email is required/i)).toBeVisible();
    await expect(page.getByText(/phone number is required/i)).toBeVisible();
    await expect(page.getByText(/city is required/i)).toBeVisible();
    await expect(page.getByText(/province is required/i)).toBeVisible();
  });

  test('validates email format', async ({ page }) => {
    await page.goto('/apply/business-info');

    // Fill with invalid email
    await page.getByLabel(/business email/i).fill('invalid-email');
    await page.getByRole('button', { name: /continue/i }).click();

    // Should show email validation error
    await expect(page.getByText(/please enter a valid email address/i)).toBeVisible();
  });

  test('navigates back from review to business info', async ({ page }) => {
    // Fill business info
    await page.goto('/apply/business-info');
    await page.getByLabel(/organization name/i).fill('Test Company');
    await page.getByLabel(/business email/i).fill('test@example.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    await page.getByRole('button', { name: /continue/i }).click();
    await expect(page).toHaveURL('/apply/review');

    // Click back button
    await page.getByRole('button', { name: /back/i }).click();

    // Should navigate back to business info
    await expect(page).toHaveURL('/apply/business-info');

    // Data should still be present
    await expect(page.getByLabel(/organization name/i)).toHaveValue('Test Company');
  });

  test('cannot submit without accepting terms', async ({ page }) => {
    // Fill business info
    await page.goto('/apply/business-info');
    await page.getByLabel(/organization name/i).fill('Test Company');
    await page.getByLabel(/business email/i).fill('test@example.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    await page.getByRole('button', { name: /continue/i }).click();
    await expect(page).toHaveURL('/apply/review');

    // Submit button should be disabled without terms acceptance
    const submitButton = page.getByRole('button', { name: /submit application/i });
    await expect(submitButton).toBeDisabled();

    // Check only first checkbox
    const checkboxes = page.getByRole('checkbox');
    await checkboxes.nth(0).check();

    // Should still be disabled
    await expect(submitButton).toBeDisabled();

    // Check second checkbox
    await checkboxes.nth(1).check();

    // Should now be enabled
    await expect(submitButton).toBeEnabled();
  });

  test('handles network errors gracefully', async ({ page }) => {
    // Override route to simulate network error
    await page.route('**/graphql', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          errors: [{ message: 'Network error' }],
        }),
      });
    });

    await page.goto('/apply/business-info');

    // Should show error state
    await expect(page.getByText(/unable to connect to server/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /try again/i })).toBeVisible();
  });

  test('displays step indicator with correct current step', async ({ page }) => {
    await page.goto('/apply/business-info');

    // Step 1 should be active
    await expect(page.getByText(/organization info/i)).toBeVisible();
    await expect(page.getByText(/review & submit/i)).toBeVisible();

    // Fill and continue
    await page.getByLabel(/organization name/i).fill('Test Company');
    await page.getByLabel(/business email/i).fill('test@example.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');
    await page.getByRole('button', { name: /continue/i }).click();

    // Step 2 should be active on review page
    await expect(page).toHaveURL('/apply/review');
    await expect(page.getByText(/review & submit/i)).toBeVisible();
  });
});

test.describe('Mobile Responsiveness', () => {
  test.use({ viewport: { width: 375, height: 667 } });

  test('completes onboarding on mobile', async ({ page }) => {
    await page.goto('/apply/business-info');

    // Form should be functional on mobile
    await expect(page.getByLabel(/organization name/i)).toBeVisible();

    // Fill form
    await page.getByLabel(/organization name/i).fill('Mobile Test Company');
    await page.getByLabel(/business email/i).fill('mobile@test.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    // Continue should work
    await page.getByRole('button', { name: /continue/i }).click();
    await expect(page).toHaveURL('/apply/review');
  });
});

test.describe('Keyboard Navigation', () => {
  test('can navigate form using keyboard', async ({ page }) => {
    await page.goto('/apply/business-info');

    // Focus first field
    await page.getByLabel(/organization name/i).focus();

    // Tab through fields
    await page.keyboard.press('Tab');
    await expect(page.getByLabel(/organization type/i)).toBeFocused();

    // Fill with keyboard
    await page.keyboard.type('Test Company');
  });

  test('can submit form with Enter key', async ({ page }) => {
    await page.goto('/apply/business-info');

    await page.getByLabel(/organization name/i).fill('Test Company');
    await page.getByLabel(/business email/i).fill('test@example.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    // Focus continue button and press Enter
    await page.getByRole('button', { name: /continue/i }).focus();
    await page.keyboard.press('Enter');

    await expect(page).toHaveURL('/apply/review');
  });
});

test.describe('Data Persistence', () => {
  test('preserves data when navigating back and forth', async ({ page }) => {
    // Fill business info
    await page.goto('/apply/business-info');
    await page.getByLabel(/organization name/i).fill('Persistent Test Co');
    await page.getByLabel(/tagline/i).fill('We persist');
    await page.getByLabel(/business email/i).fill('persist@test.com');
    await page.getByLabel(/phone number/i).fill('+260971234567');
    await page.getByLabel(/city/i).fill('Lusaka');

    // Go to review
    await page.getByRole('button', { name: /continue/i }).click();
    await expect(page).toHaveURL('/apply/review');

    // Go back
    await page.getByRole('button', { name: /back/i }).click();

    // Data should persist
    await expect(page.getByLabel(/organization name/i)).toHaveValue('Persistent Test Co');
    await expect(page.getByLabel(/tagline/i)).toHaveValue('We persist');
    await expect(page.getByLabel(/business email/i)).toHaveValue('persist@test.com');

    // Go forward again
    await page.getByRole('button', { name: /continue/i }).click();

    // Data should still be on review page
    await expect(page.getByText('Persistent Test Co')).toBeVisible();
    await expect(page.getByText('We persist')).toBeVisible();
  });
});
