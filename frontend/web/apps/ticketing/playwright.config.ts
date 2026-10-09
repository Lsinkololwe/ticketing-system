import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

/**
 * Playwright E2E configuration for the customer ticketing app.
 *
 * <h2>Why this app's config is not a copy of admin's</h2>
 * Two differences are load-bearing rather than cosmetic.
 *
 * <p><b>No auth project.</b> admin and org-admin both open with a real Keycloak
 * login and reuse the storage state. Discovery, event detail and checkout entry
 * are reachable signed out — that is the product — so requiring a session to
 * reach them would test a different app. Specs that need a signed-in customer
 * (my tickets, transfer) bring their own state rather than imposing it on the
 * suite.</p>
 *
 * <p><b>The brand is asserted, not assumed.</b> This is the only app on the iris
 * accent and the only one using Space Grotesk for display. Both are set once, on
 * `<html>` in the root layout, and every screen inherits them — so a regression
 * there is silent everywhere at once and looks like a slightly different shade
 * rather than a broken page. `brand.spec.ts` is the guard.</p>
 *
 * <h2>Prerequisites</h2>
 * None beyond Node. The brand suite runs against the app alone; specs that need
 * data pull in the container harness explicitly.
 *
 * Run:
 *   npx playwright test --config=apps/ticketing/playwright.config.ts
 */

const APP_PORT = Number(process.env.APP_PORT ?? 3001);
const APP_URL = `http://localhost:${APP_PORT}`;

export default defineConfig({
  testDir: './e2e',
  // Network-fixture page checks run under playwright.visual.config.ts (no containers needed).
  testIgnore: ['visual/**'],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],

  // 60s, not Playwright's default 30s: `next dev` compiles a route the first
  // time it is navigated to, and a cold first touch can exceed 30s. That failure
  // is indistinguishable from a page that never loads.
  // The shared Microcks container: the same pinned port and lifecycle as the two consoles.
  globalSetup: path.join(__dirname, 'e2e', 'global-setup.ts'),
  globalTeardown: path.join(__dirname, 'e2e', 'global-teardown.ts'),
  timeout: 60_000,

  use: {
    baseURL: process.env.BASE_URL || APP_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: {
    // `next dev` directly rather than through the Nx executor: under
    // Playwright's webServer the `@nx/next:server` executor re-spawns node from
    // a path it resolves itself and fails with ENOENT, even though the binary
    // exists. The executor contributes only the port, which this config owns.
    command: `npx next dev --port ${APP_PORT}`,
    cwd: __dirname,
    url: APP_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    env: {
      // Spread process.env: supplying `env` otherwise replaces the environment
      // wholesale and the command cannot find `node` on PATH.
      ...(process.env as Record<string, string>),
    },
  },
});
