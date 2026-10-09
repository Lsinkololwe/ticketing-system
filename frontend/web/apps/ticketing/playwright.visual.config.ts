import { defineConfig, devices } from '@playwright/test';

/**
 * Page checks against a running buyer app with the backend replaced by in-test GraphQL fixtures
 * (route interception inside the specs). No containers.
 *
 *   npx nx dev @pml.tickets/ticketing   # in another shell, port 3001
 *   npx playwright test -c apps/ticketing/playwright.visual.config.ts
 * Set SHOTS_DIR to also write screenshots for side-by-side review.
 */
export default defineConfig({
  testDir: './e2e/visual',
  reporter: [['list']],
  use: { baseURL: process.env.BASE_URL ?? `http://localhost:${process.env.APP_PORT ?? 3001}` },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], channel: process.env.PW_CHANNEL ?? undefined, viewport: { width: 1280, height: 900 } } },
    { name: 'phone', use: { ...devices['Desktop Chrome'], channel: process.env.PW_CHANNEL ?? undefined, viewport: { width: 390, height: 844 }, hasTouch: true } },
  ],
});
