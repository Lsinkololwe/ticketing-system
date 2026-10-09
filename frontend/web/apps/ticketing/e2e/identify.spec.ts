import { expect, test } from '@playwright/test';

/**
 * Happy path of the passwordless identify step at /auth, against mocked network only
 * (no Keycloak, no identity-service). The browser-side routes are fulfilled by Playwright
 * and the final redirect to /api/auth/start is intercepted, so this proves the UI sequence
 * contact -> code -> welcome -> sign-in redirect without any backend.
 */
test('contact -> code -> welcome -> redirect to /api/auth/start', async ({ page }) => {
  await page.route('**/api/identity/challenge', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        challengeId: '123e4567-e89b-12d3-a456-426614174000',
        contactType: 'EMAIL',
        maskedContact: 'j***@gmail.com',
        channel: 'EMAIL',
        expiresInSeconds: 300,
        resendAfterSeconds: 60,
      }),
    })
  );
  await page.route('**/api/identity/verify', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ verified: true, maskedContact: 'j***@gmail.com', expiresInSeconds: 120 }),
    })
  );
  await page.route('**/api/identity/ensure', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'ACTIVE', isNew: true, next: '/api/auth/start' }),
    })
  );
  let startUrl: string | null = null;
  await page.route('**/api/auth/start**', (route) => {
    startUrl = route.request().url();
    return route.fulfill({ status: 200, contentType: 'text/html', body: '<h1>redirected</h1>' });
  });

  await page.goto('/auth?next=/events/e1/book');
  await page.getByRole('radio', { name: 'Email' }).click();
  await page.getByTestId('identify-email').fill('j@gmail.com');
  await page.getByTestId('identify-send').click();

  await expect(page.getByTestId('identify-readback')).toContainText('j***@gmail.com');
  await page.getByTestId('identify-code').fill('123456');

  await expect(page.getByTestId('identify-welcome')).toContainText('We created your free account');
  await page.getByTestId('identify-continue').click();
  await expect.poll(() => startUrl).toContain('/api/auth/start?next=%2Fevents%2Fe1%2Fbook');
});

test('no token is readable from browser JavaScript', async ({ page }) => {
  await page.goto('/auth');
  const exposed = await page.evaluate(() => ({
    cookies: document.cookie,
    local: Object.keys(localStorage),
    session: Object.keys(sessionStorage),
  }));
  expect(exposed.cookies).not.toMatch(/token|pml_buyer/i);
  expect(exposed.local.join()).not.toMatch(/token|kc-callback/i);
});
