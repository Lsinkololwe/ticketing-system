/**
 * Admin E2E Tests
 * 
 * Playwright tests for the admin application.
 * Uses default admin credentials from AdminUserBootstrapTest.java:
 * - Email: admin@pml.tickets
 * - Password: Admin@123456
 */

import { test, expect } from '@playwright/test';
import { DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASSWORD } from '@pml.tickets/shared/testing/test-constants';
import { LoginPage, DashboardPage, authHelpers } from '@pml.tickets/shared/testing/test-utils';

test.describe('Admin Portal E2E Tests', () => {
  test('should display login page', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.navigate();
    
    // Verify login form elements are present
    await expect(page.locator('[name="email"]')).toBeVisible();
    await expect(page.locator('[name="password"]')).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
  });

  test('should login successfully with default admin credentials', async ({ page }) => {
    const loginPage = new LoginPage(page);
    
    // Login using default admin credentials
    await loginPage.login(DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASSWORD);
    
    // Verify redirect to dashboard or home page
    await expect(page).toHaveURL(/\/dashboard|\//);
    
    // Verify no error message is displayed
    const hasError = await loginPage.hasError();
    expect(hasError).toBe(false);
  });

  test('should login via API and access protected routes', async ({ page }) => {
    // Login via API using default admin credentials
    const token = await authHelpers.loginViaAPI(page);
    expect(token).toBeTruthy();
    
    // Navigate to dashboard
    await page.goto('/dashboard');
    
    // Verify page loaded successfully
    await page.waitForLoadState('networkidle');
    
    // Verify we're on an authenticated page
    const dashboardPage = new DashboardPage(page);
    const isLoaded = await dashboardPage.isLoaded();
    expect(isLoaded).toBe(true);
  });

  test('should display error on invalid credentials', async ({ page }) => {
    const loginPage = new LoginPage(page);
    
    // Attempt login with invalid credentials
    await loginPage.navigate();
    await loginPage.fillEmail('invalid@example.com');
    await loginPage.fillPassword('WrongPassword123');
    await loginPage.clickSignIn();
    
    // Wait for error message
    await page.waitForTimeout(1000); // Wait for API response
    
    // Verify error is displayed
    const hasError = await loginPage.hasError();
    expect(hasError).toBe(true);
    
    const errorMessage = await loginPage.getErrorMessage();
    expect(errorMessage).toBeTruthy();
  });
});

