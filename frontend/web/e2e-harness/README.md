# e2e-harness

Two things live here.

1. **Container harness** (`microcks-container.ts`, `subgraph-container.ts`, `brand.ts`): the older Microcks / subgraph helpers used by the apps' container specs.
2. **Browser verification harness** (`browser/`): runs a real app in real Chrome against a fake backend, so wired pages (including pages behind login) can be seen, screenshotted, axe-checked and crawled. Used by all three apps; the buyer app is the first user.

## What the browser harness is

```
Chrome (system channel) ──► next dev / next start on a free port  ──► BFF ──► fake upstream (Node http, in the test process)
                                         │                                     answers GraphQL by operationName, REST by "METHOD /path"
                                         └──► real Redis (testcontainers redis:7-alpine) holding BFF sessions
```

| File | Role |
| --- | --- |
| `browser/fake-upstream.ts` | Fake GraphQL/REST server. Fixtures are registered per test with `upstream.gql({ Op: data \| fn })` and `upstream.rest('POST', '/path', ...)`. Data is shaped to the query's selection set (`conform`): fields the fixture omits become `null` and are listed in `upstream.missing`; operations nobody fixtured are answered `NOT_FIXTURED` and listed in `upstream.unhandled`. Helpers: `gqlErrors`, `httpFailure`, `delayed`. Server-side BFF calls without `operationName` are matched by parsing the document. |
| `browser/redis.ts` | Starts `redis:7-alpine` (Ryuk disabled; the harness stops it). Docker must be running. |
| `browser/session.ts` | Seeds sessions with the shared BFF module's own `SessionService` + `RedisStore` (`libs/shared/src/auth/bff`). `signInAs({ roles, accountId, displayName, authTime, ext })` returns a cookie for the browser context. |
| `browser/app-server.ts` | Starts the app (`next dev`, or `next start` when `HARNESS_MODE=start` after a build) on a free port with `GRAPHQL_URL`, `API_BASE_URL`, `IDENTITY_BASE_URL`, `IDENTITY_TOKEN_URL`, `KEYCLOAK_ISSUER`, `REDIS_URL`, `BFF_ENC_KEYS`, `APP_URL` pointing at the harness. Kills the process group on stop. |
| `browser/playwright.ts` | `defineHarnessConfig({ app, testDir })` (projects `desktop` 1440x900 and `phone` 390x844, system Chrome via `PW_CHANNEL`, default `chrome`, one worker) and `harnessTest` (fixtures `upstream`, `signInAs`, `baseURL`; worker-scoped `stack`). Re-exports everything in `helpers.ts` and `fake-upstream.ts`. |
| `browser/helpers.ts` | `shot()` full-page screenshot to `e2e-harness/shots/<app>/<project>-<name>.png` (override with `SHOTS_DIR`), `captureConsole()`/`realErrors()`, `axe()` (axe-core from the pnpm store, WCAG 2.x A/AA), `measure()` computed metrics, `crawlClickables()` (clicks every link/tab/button in a scope and records where it lands). |

## IMPORTANT: this bypasses Keycloak

Sessions are written straight into Redis. No login redirect, code exchange, id_token, refresh, step-up or back-channel logout happens, and the seeded access token is an opaque string the fake upstream accepts. The real OIDC/BFF flow is covered by the existing BFF integration tests (`libs/shared/src/auth/bff/__integration__`, real Keycloak + Redis). Use this harness to see and test pages, not to test authentication.

Fixture data lives only in test files (for the buyer app: `apps/ticketing/e2e/browser/fixtures.ts` and the specs). App code ships no sample data.

## Running

Prerequisites: Docker running, Google Chrome installed, `pnpm install` done.

Buyer app (port is free-picked, env is set by the harness):

```bash
cd frontend/web
PW_CHANNEL=chrome npx playwright test -c apps/ticketing/playwright.browser.config.ts                 # both widths
PW_CHANNEL=chrome npx playwright test -c apps/ticketing/playwright.browser.config.ts --project=desktop tickets
SHOTS_DIR=/some/dir PW_CHANNEL=chrome npx playwright test -c apps/ticketing/playwright.browser.config.ts
```

The first request compiles routes under `next dev`; the app start allows 4 minutes and tests 90 seconds. Run one job at a time: it starts a Next server and a container, and both are stopped at the end of the run.

Env the harness sets for each app (from `apps/<app>/src/lib/server/env.ts`): `APP_URL=http://localhost:<port>`, `GRAPHQL_URL=<upstream>/graphql`, `API_BASE_URL=<upstream>`, `IDENTITY_BASE_URL=<upstream>`, `IDENTITY_TOKEN_URL=<upstream>/protocol/openid-connect/token`, `KEYCLOAK_ISSUER=<upstream>/realms/harness`, `KEYCLOAK_CLIENT_SECRET`, `IDENTITY_CLIENT_ID/SECRET`, `REDIS_URL=<container>`, `BFF_ENC_KEYS` (matches the seeder). Extra variables can be passed through `startApp(..., { extraEnv })`.

### Organizer and admin apps

`APPS` in `browser/app-server.ts` already maps `organization-admin` (BFF app `organizer`, cookie `pml_org`) and `admin` (`admin`, cookie `pml_admin`). To use them:

1. Create `apps/<app>/playwright.browser.config.ts`: `export default defineHarnessConfig({ app: 'organization-admin', testDir: './e2e/browser' })` (import from `../../e2e-harness/browser/playwright`).
2. Write specs with `harnessTest`, seeding roles the pages need: `signInAs({ roles: ['ORGANIZER'], accountId: 'acct-1' })`, or `['ADMIN']` for admin. Pages that require fresh authentication use `authTime` (default: now, i.e. fresh).
3. Check `apps/<app>/src/lib/**/env.ts` for any app-specific variables and pass them with `extraEnv`. Cookie names: `COOKIE_BASE` in `browser/session.ts`.

The organizer app runs through the harness (`apps/organization-admin/playwright.browser.config.ts`, specs in `apps/organization-admin/e2e/browser`: pages, events, bookings, roles, onboarding, crawl, csp). Fixtures built through a GraphQL fragment need the right `__typename` (the harness defaults to `Object`, so the fragment would not match). The admin app has not been run yet.

## Writing a spec

```ts
import { harnessTest as test, expect, captureConsole } from '../../../../e2e-harness/browser/playwright';
import { settle, shot } from './_kit';          // buyer helpers: no console errors, complete fixtures, no axe serious/critical

test('my tickets', async ({ page, upstream, signInAs }, info) => {
  upstream.gql({ Me: { me: { id: 'u1', firstName: 'A' } }, BuyerMyBookings: { myBookings: { data: [], pagination: { totalElements: 0, hasNext: false } } } });
  await signInAs({ roles: ['CUSTOMER'] });
  const log = captureConsole(page);
  await page.goto('/my-tickets');
  await shot(page, 'my-tickets', info);
  await settle(page, upstream, log);
});
```

### Platform admin (`apps/admin/e2e/browser`)

`apps/admin/playwright.browser.config.ts` + specs. Fixtures are schema-driven: `mock-upstream.ts` executes every GraphQL operation against the composed supergraph SDL (`node_modules/.cache/pml-supergraph.graphql`, written by `scripts/codegen-local.sh`) with a deterministic generator (realistic names/amounts/dates from field names). `mockAll(upstream, { fields: { 'Type.field': value }, listSize })` then override single operations with `upstream.gql`. Specs: `sweep` (every route, both widths, screenshots for the prototype comparison), `dashboard` (4 roles), `gating` (role nav matrix, No access, login/unauthorized, Ctrl+K, bell, theme), `approvals` (decision dialogs need a written reason), `flows` (dual control second approval, suspend, detail pages), `stepup` (stale `authTime` -> `/api/auth/stepup`), `states` (populated+axe, empty, loading, error per route), `crawl` (click-everything per role), `csp` (`HARNESS_MODE=start` after `cd apps/admin && npx next build`), `measure` (prints computed metrics: `MEASURE="/dashboard|SUPER_ADMIN|.sel;;.sel"`).

## Known limits

- Under `next dev`, Next injects chunk scripts without the CSP nonce, so Chrome logs "violates the following Content Security Policy" errors for `_next/static/chunks`. `settle()` ignores exactly that message; `HARNESS_MODE=start` runs the production server (`cd apps/<app> && npx next build` first; nx build writes to dist, next start reads .next). The BFF refuses a non-https `APP_URL` in production, so the harness puts a TEST-ONLY TLS front (self-signed certificate made with openssl, forwards with x-forwarded-proto https) in front of it, seeds the `__Host-` cookie name and the Playwright projects use `ignoreHTTPSErrors`. In that mode CSP errors are not ignored; `apps/organization-admin/e2e/browser/csp.spec.ts` asserts none.
- A transport failure (HTTP 503, offline) has no GraphQL `extensions`, so the shared error contract offers no "Try again" button; tests reload instead.
- Screenshots use `caret: 'initial'` because Playwright's default caret hiding injects an inline style that React reports as a hydration mismatch when a shot lands mid-hydration.
