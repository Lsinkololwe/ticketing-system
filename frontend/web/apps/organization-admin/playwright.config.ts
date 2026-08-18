import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

import {
  documentsMockRoot,
  graphqlMockEndpoint,
} from './e2e/microcks/client';

/**
 * Playwright E2E configuration for the organizer application app.
 *
 * <h2>The shape of this harness, and why</h2>
 *
 * The onboarding routing decision happens in `(application)/layout.tsx`, a
 * Server Component. Its GraphQL fetch is issued by the Next.js **Node**
 * process, so `page.route()` cannot see it — a suite built on browser
 * interception can assert what a screen renders but never which screen you are
 * sent to, and "which screen you are sent to" is the whole defect these tests
 * exist for.
 *
 * So rather than intercepting, this config redirects. A Microcks container
 * (Testcontainers) serves both APIs the flow depends on, and the app is pointed
 * at it:
 *
 * <ul>
 *   <li>`GRAPHQL_ENDPOINT` — read by the server, so the guard sees the mock</li>
 *   <li>`NEXT_PUBLIC_GRAPHQL_ENDPOINT` — read by the browser, so Apollo agrees</li>
 *   <li>`NEXT_PUBLIC_API_URL` — the document REST surface (spec R4 keeps
 *       documents off GraphQL entirely)</li>
 * </ul>
 *
 * Each spec declares the organization state it needs by re-importing the
 * Microcks artifacts. Because the mocks are generated from the same artifact
 * kinds the real API is described by, they cannot drift into agreeing with a
 * test while disagreeing with the backend.
 *
 * <h2>Why a real Keycloak login</h2>
 * Better Auth validates the session against MongoDB rather than trusting the
 * cookie, and the guard needs a real access token: `myOwnedOrganization` is
 * `hasRole('ORGANIZER')`-guarded, so a tokenless query returns an authorization
 * error rather than "no organization". Telling those two apart is exactly what
 * was broken, so the suite must be able to hold a genuine session.
 *
 * <h2>Prerequisites</h2>
 * <pre>
 *   docker compose up -d    # mongo (Better Auth sessions) + keycloak (8084)
 *   # Docker itself, for the Microcks container
 * </pre>
 * No Java service is needed — Microcks replaces the whole backend.
 *
 * Run:
 *   npx playwright test --config=apps/organization-admin/playwright.config.ts
 */

const MICROCKS_PORT = Number(process.env.MICROCKS_PORT ?? 18080);
const MICROCKS_URL = `http://localhost:${MICROCKS_PORT}`;
const APP_PORT = Number(process.env.APP_PORT ?? 3003);
const APP_URL = `http://localhost:${APP_PORT}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  // Microcks holds a single mutable dataset that each spec re-imports before
  // navigating. Parallel workers would overwrite each other's setup rather than
  // test anything.
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],

  globalSetup: path.join(__dirname, 'e2e', 'global-setup.ts'),
  globalTeardown: path.join(__dirname, 'e2e', 'global-teardown.ts'),

  // 60s, not Playwright's default 30s: `next dev` compiles a route the first
  // time it is navigated to, and a cold first touch of a newly added route can
  // exceed 30s. That failure looks identical to a broken page.
  timeout: 60_000,

  use: {
    baseURL: process.env.BASE_URL || APP_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // One real Keycloak login per run, reused by every spec.
        storageState: 'apps/organization-admin/e2e/.auth/organizer.json',
      },
      dependencies: ['setup'],
    },
  ],

  webServer: {
    // `next dev` directly rather than `nx dev organization-admin`.
    //
    // The `@nx/next:server` executor re-spawns node from a path it resolves
    // itself, and under Playwright's webServer that spawn fails with ENOENT
    // even though the binary exists — the executor's assumptions about its
    // environment do not survive being launched this way. Calling Next directly
    // removes the extra hop; the executor adds nothing here beyond the port,
    // which this config already owns.
    command: `npx next dev --port ${APP_PORT}`,
    cwd: __dirname,
    // 3003 is the port `nx dev organization-admin` binds (project.json). This
    // previously said 3002, so the probe waited on a port nothing served.
    url: APP_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    env: {
      // Spread process.env: supplying `env` otherwise replaces the whole
      // environment and the command cannot find `node` on PATH.
      ...(process.env as Record<string, string>),
      GRAPHQL_ENDPOINT: graphqlMockEndpoint(MICROCKS_URL),
      NEXT_PUBLIC_GRAPHQL_ENDPOINT: graphqlMockEndpoint(MICROCKS_URL),
      NEXT_PUBLIC_API_URL: documentsMockRoot(MICROCKS_URL),
    },
  },
});
