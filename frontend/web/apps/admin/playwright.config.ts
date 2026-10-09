import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

import { MICROCKS_URL } from '../../e2e-harness/microcks-container';

/**
 * Playwright E2E configuration for the platform admin app.
 *
 * <h2>What these tests run against</h2>
 * Real infrastructure, not mocks: the Next.js app talks to the API gateway,
 * which routes to the Apollo Router, which resolves through catalog-service into
 * the MongoDB that holds `reference_data`. Authentication is a real Keycloak
 * login against the `myticketzm-admin` realm.
 *
 * <p>That chain is the point. The defect this suite was written alongside —
 * `semantic` and `allowedTransitions` accepted by the schema, sent by the form,
 * and dropped before the database — is invisible to any test that stops short of
 * the database, because every layer above it reports success.
 *
 * <h2>Prerequisites</h2>
 * <pre>
 *   docker compose up -d            # mongo, keycloak, redis, apollo router
 *   backend/catalog-service         # mvn spring-boot:run   (8085)
 *   backend/api-gateway             # mvn spring-boot:run   (8080)
 * </pre>
 *
 * The suite skips rather than fails when the stack is absent, so a developer
 * running `nx run-many -t e2e` without infrastructure gets a clear skip instead
 * of a wall of timeouts.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  // Reference data is a single shared collection; parallel edits to the same
  // type would race each other rather than test anything.
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],

  // 60s, not Playwright's default 30s.
  //
  // These run against `next dev`, which compiles a route the first time it is
  // navigated to. A newly added route can take well over 30s to build on a cold
  // cache, and the failure looks exactly like a broken page: `page.goto` times
  // out with nothing rendered. That is an artifact of the dev server, not a
  // property of the app — a production build serves the same route instantly.
  //
  // Raising this makes first-touch compilation survivable without hiding real
  // hangs: a page that genuinely never loads still fails, just a minute later.
  globalSetup: path.join(__dirname, 'e2e', 'global-setup.ts'),
  globalTeardown: path.join(__dirname, 'e2e', 'global-teardown.ts'),
  timeout: 60_000,

  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:3030',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    // Signed out: the brand contract is read off the login page, which needs no session —
    // so it runs even where no Keycloak is up.
    { name: 'public', testMatch: /brand\.spec\.ts/, use: { ...devices['Desktop Chrome'] } },
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'chromium',
      testIgnore: /brand\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        // One real Keycloak login per run, reused by every spec. Logging in per
        // test would spend most of the suite's wall clock on redirects.
        storageState: 'apps/admin/e2e/.auth/admin.json',
      },
      dependencies: ['setup'],
    },
  ],

  webServer: {
    // Next directly, as the other two apps start: the Nx target cannot resolve its own
    // dependencies on this workspace (its lockfile parse yields no external nodes).
    command: 'npx next dev --port 3030',
    cwd: __dirname,
    url: 'http://localhost:3030',
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    // Point the console at the shared Microcks container, never at a real backend. No admin
    // artifact is imported yet: the first spec that needs data imports its own, as org-admin's do.
    env: {
      ...(process.env as Record<string, string>),
      GRAPHQL_ENDPOINT: `${MICROCKS_URL}/graphql/admin/1.0`,
      NEXT_PUBLIC_GRAPHQL_ENDPOINT: `${MICROCKS_URL}/graphql/admin/1.0`,
    },
  },
});
