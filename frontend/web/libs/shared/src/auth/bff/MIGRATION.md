# Migrating the apps to `@pml.tickets/shared/auth/bff`

Nothing under `apps/` was changed by this work. Better Auth code and the legacy JTI blacklist are still in place.
Use this list per app. Order: platform admin first (smallest), organizer, buyer.

Decisions applied: one shared BFF (no Better Auth in staff apps); per-device encrypted `id_token` hint (10 h) for the
pre-handoff RP logout; jose + fetch; identity-service listener is the primary owner of Keycloak-logout to revocation
(the BFF back-channel route deletes sessions by `sid` and makes the idempotent `/api/internal/revocations/logout` call);
buyer realm `refreshTokenMaxReuse` stays 0; admin idle 15 min; SameSite=Strict for platform admin, Lax elsewhere;
backend `acr`/`auth_time` enforcement is out of scope.

## Common to every app

Add `apps/<app>/src/lib/bff.ts` (about 30 lines):

```ts
import 'server-only';
import { createBff } from '@pml.tickets/shared/auth/bff';

export const bff = createBff({
  app: 'organizer',                       // 'admin' | 'organizer' | 'buyer'
  appUrl: process.env.APP_URL!,
  oidc: { issuer: process.env.KEYCLOAK_ISSUER!, internalIssuer: process.env.KEYCLOAK_INTERNAL_ISSUER,
          clientId: process.env.KEYCLOAK_CLIENT_ID!, clientSecret: process.env.KEYCLOAK_CLIENT_SECRET! },
  access: { roles: ['ORGANIZER', 'ADMIN', 'SUPER_ADMIN'], audience: process.env.API_AUDIENCE },
  guarded: [{ prefix: '/dashboard', roles: ['ORGANIZER', 'ADMIN'] }],
  publicPaths: ['/login', '/unauthorized'],
  redis: { url: process.env.REDIS_URL },
  encKeys: process.env.BFF_ENC_KEYS!,
  trustProxyHops: Number(process.env.TRUST_PROXY_HOPS ?? 0),
  upstream: { graphql: process.env.GRAPHQL_URL, rest: process.env.API_BASE_URL },
  identity: { baseUrl: process.env.IDENTITY_BASE_URL!, tokenUrl: `${process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/token`,
              clientId: process.env.IDENTITY_CLIENT_ID!, clientSecret: process.env.IDENTITY_CLIENT_SECRET! },
});
```

Routes (all one-liners):

| File | Content |
|---|---|
| `src/app/api/auth/[action]/route.ts` | `import { bff } from '@/lib/bff'; export const GET = bff.handlers.GET; export const POST = bff.handlers.POST; export const dynamic = 'force-dynamic';` |
| `src/app/api/graphql/route.ts` | `export const { POST } = bff.upstream.graphql();` |
| `src/app/api/rest/[...path]/route.ts` | `export const { GET, POST, PUT, PATCH, DELETE } = bff.upstream.rest();` |
| `src/proxy.ts` | `import { bff } from '@/lib/bff'; export const proxy = bff.proxy; export const config = { matcher: [{ source: '/((?!api\|_next/static\|_next/image\|favicon.ico).*)', missing: [{ type: 'header', key: 'next-router-prefetch' }, { type: 'header', key: 'purpose', value: 'prefetch' }] }] };` |

Pages and actions:
- Top of every protected page / layout segment that reads data, and every Server Action and route handler:
  `await bff.requireSession({ roles: [...] })` (`kind: 'action'` throws `BffAuthError` instead of redirecting;
  `freshAuthSec: 300` sends the user through `/api/auth/stepup`).
- `login` page: link or redirect to `/api/auth/start?next=<path>`. Error codes arrive as `?error=` (`SIGN_IN_FAILED`,
  `FORBIDDEN`, `LOGIN_FAILED`, `SERVICE_UNAVAILABLE`, `LOGIN_HANDLE_INVALID`, plus whatever `postLogin` returns).
- Logout button: `<form method="post" action="/api/auth/logout">`.
- Client side: `useSession()` reads `GET /api/auth/session`. Apollo uses `createBffGraphQLClient()` and TanStack uses
  `createBffApiClient()` from `@pml.tickets/shared/api` (same origin, `x-pml-csrf: 1`, no token getter).
- `next.config`: `experimental.serverActions.allowedOrigins = [<public origin>]`. Remove `X-XSS-Protection` and any
  hand-written security headers/CSP (the proxy sets them).

Env (all apps): `APP_URL`, `KEYCLOAK_ISSUER`, `KEYCLOAK_INTERNAL_ISSUER` (optional), `KEYCLOAK_CLIENT_ID`,
`KEYCLOAK_CLIENT_SECRET`, `REDIS_URL`, `BFF_ENC_KEYS` (`kid:base64(32 bytes)[,kid2:...]`, first encrypts, all decrypt),
`TRUST_PROXY_HOPS`, `GRAPHQL_URL`, `API_BASE_URL`, `API_AUDIENCE`, `IDENTITY_BASE_URL`, `IDENTITY_CLIENT_ID`,
`IDENTITY_CLIENT_SECRET` (service account with `internal-write`), `LOG_LEVEL`. Secrets from the secret store only; the
module throws in production without `REDIS_URL` and requires an https `APP_URL`.
Redis must run `noeviction` or `volatile-*` (never `allkeys-lru`).

Keycloak client (all three, per realm export in `docker-resources/keycloak`):
- confidential, standard flow only, direct grants off, `pkce.code.challenge.method=S256`
- `redirectUris`: `<APP_URL>/api/auth/callback` (exact)
- `attributes."backchannel.logout.url"`: `<APP_URL>/api/auth/backchannel-logout`,
  `backchannel.logout.session.required=true`
- `post.logout.redirect.uris`: `<APP_URL>/*` (the module returns to `/` after logout and to
  `<loginPath>?error=...` after a refused sign-in)
- access tokens carry the resource audience (`API_AUDIENCE`) via an audience mapper, and `realm_access.roles`
- realm: `revokeRefreshToken=true`, `refreshTokenMaxReuse=0` (do not relax; single-flight refresh makes it safe)
- the app must be reachable by Keycloak at the back-channel URL (egress allow-list is optional, the route is rate limited)

## apps/admin (platform admin, realm `myticketzm-admin`, client `myticketzm-admin`)

Config: `app: 'admin'` (cookie `__Host-pml_admin`, SameSite=Strict, idle 15 min, absolute 8 h, start limited to
10 per minute per IP), `access.roles: ['ADMIN','SUPER_ADMIN']`, optional `oidc.authorizeParams: { acr_values: ... }`,
`guarded: [{ prefix: '/', roles: ['ADMIN','SUPER_ADMIN'] }]` with `publicPaths: ['/login','/unauthorized','/logged-out']`.
Use `requireSession({ roles: [...], freshAuthSec: 300 })` in actions that move money or change roles.

Delete:
- `src/lib/auth/{container,client,index,dal}.ts`, `src/lib/auth/services/**`, `src/lib/auth/interfaces/**`
- `src/app/api/auth/[...all]/route.ts`, `src/app/api/auth/logout/route.ts`, `src/app/api/auth/backchannel-logout/route.ts`
- Better Auth provider wiring and the Apollo `tokenGetter` in `src/components/Providers.tsx`
- `better-auth`, `mongodb` (if only auth used it) from the app's dependencies; `src/test/setup.ts` Better Auth mocks
- env: `AUTH_KEYCLOAK_*`, `MONGODB_*`, `BETTER_AUTH_*`, `REDIS_HOST/PORT/PASSWORD` (use `REDIS_URL`),
  `ENABLE_REDIS_SECONDARY_STORAGE`, `NEXT_PUBLIC_KEYCLOAK_*`

Add: `src/lib/bff.ts`, `src/app/api/auth/[action]/route.ts`, `src/app/api/graphql/route.ts`,
`src/app/api/rest/[...path]/route.ts` (if REST is used), rewrite `src/proxy.ts`. Adapt `login/page.tsx`,
`logout/page.tsx`, `unauthorized/page.tsx`, `(console)/layout.tsx` to `requireSession`.
Keycloak: redirect URI changes from `/api/auth/callback/keycloak` to `/api/auth/callback` in deployed realms
(`docker-resources/keycloak/myticketzm-admin-realm.json` already has the new URI and back-channel URL). Existing admin
sessions end at cutover (cookie names differ).

## apps/organization-admin (realm `myticketzm`, client `TICKETING_ORGANIZER_CLIENT_ID`)

Config: `app: 'organizer'` (cookie `__Host-pml_org`, SameSite=Lax, idle 30 min, absolute 8 h),
`access.roles: ['ORGANIZER','ADMIN','SUPER_ADMIN']`, `guarded` for `/dashboard`, `/finance`, `/team`, `/apply` as needed.
Organizer onboarding routing (`OrganizationService`) is not auth: move it to organizer domain code and have it call
GraphQL through `bff.getAccessToken()` (server only) or the `/api/graphql` proxy; use `postLogin` only if sign-in must
be refused for a state.

Delete:
- `src/lib/auth/{container,client,index,dal,revocation}.ts`, `src/lib/auth/services/{SessionService,TokenService}.ts`,
  `src/lib/auth/interfaces/**`, the README files there
- keep (move) `src/lib/auth/services/OrganizationService.ts` and `IOrganizationService.ts` into the organizer domain
- `src/app/api/auth/[...all]/route.ts`, `logout/route.ts`, `backchannel-logout/route.ts`
- Better Auth wiring in `src/components/Providers.tsx`, `ConsoleShell.tsx`, `TeamPage.tsx` (replace `authClient` use
  with `useSession()` and the logout form)
- `better-auth`, `mongodb` dependencies; env as for admin (`AUTH_KEYCLOAK_*`, `MONGODB_*`, `NEXT_PUBLIC_KEYCLOAK_*`, ...)

Add: same set as admin. Keycloak: redirect URI `/api/auth/callback`; the back-channel URL is already declared in the
realm export (`TICKETING_ORGANIZER_BACKCHANNEL_LOGOUT`), confirm it equals `<APP_URL>/api/auth/backchannel-logout`.

## apps/ticketing (buyer, realm `myticketzm`, client `myticketzm-web`)

Config: `app: 'buyer'`, `login: { mode: 'handoff', loginPath: '/auth' }` (cookie `__Host-pml_buyer`, SameSite=Lax, idle
30 min, absolute 8 h), `access: { accountClaim: 'accountId', audience }` (the account id is the claim, never `sub`),
`guarded: [{ prefix: '/my-tickets' }, { prefix: '/profile' }]`. `postLogin` calls identity-service
`GET /api/internal/auth/accounts/{id}/status`: `ACTIVE` returns `{ ok: true, accountId }`, `SUSPENDED` returns
`{ ok: false, error: 'ACCOUNT_SUSPENDED', endSso: true }`, anything else `{ ok: false, error: 'SETUP', endSso: true }`
(the app currently maps that to `/auth?setup=1`), unreachable returns `SERVICE_UNAVAILABLE`.

Identify routes stay in the app (`/api/identity/{challenge,verify,ensure}` talk to identity-service). Changes:
- `ensure` hands the handle to the module instead of its own store:
  `const { setCookie } = await bff.flow.attachHandle(req, { value, accountId: data.accountId, isNew, ttlSec })` and
  appends the returned `Set-Cookie` strings. Proof storage: `bff.flow.update(req, rec => ({ ...rec, proof: ... }))` and read
  with `bff.flow.read(req)`. The cart moves to `rec.ext.cart` and ends up in `session.record.ext.cart`.
- apply the limiter policies `challenge`, `verify`, `ensure`, `contacts`, `contacts_challenge` (see `ratelimit.ts`; expose
  with `await bff.rateLimit(policy, { ip: bff.clientIp(req), contact, flow, device, account })` (returns `{ allowed, retryAfterSec, unavailable }`)) instead of `guard.ts`.
  Contacts must be passed as `HMAC`ed by the module (it hashes every subject before keying Redis).
- `/auth` page starts the handoff with `GET /api/auth/start?next=...` after `ensure`; the handle is never in a URL.

Delete: `src/lib/server/{oidc,session,store,cookies,guard}.ts` (keep `env.ts` trimmed to identity/graphql/terms values,
`identity.ts`, `contacts.ts`, `cart.ts`), `src/lib/csp.ts`, `src/app/api/auth/{start,callback,logout}/route.ts`
(replaced by `[action]`), the old `src/proxy.ts` body, the unprefixed `pml_buyer` cookie acceptance, and
`app/api/graphql/route.ts` body (use `bff.upstream.graphql()`).
Add: `src/lib/bff.ts`, `app/api/auth/[action]/route.ts`.
Realm: register `post.logout.redirect.uris` including `<APP_URL>/api/auth/start?resume=1` (or `<APP_URL>/*`), set
`TICKETING_BUYER_BACKCHANNEL_LOGOUT=<APP_URL>/api/auth/backchannel-logout`. The Keycloak plugin ordering change from
POC-FINDINGS (handle-only execution before the cookie authenticator) would let the RP-logout hop be removed later.

## After all apps have moved

- Delete `libs/shared/src/auth/better-auth/**`, `JtiBlacklistService`, `LegacyJtiBlacklistAdapter`, and (after confirming
  no importer) `libs/shared/src/auth/{keycloak-provider,protected-route,keycloak-config}.ts*`.
- Deprecate `createGraphQLClient({ tokenGetter })` and the token getters in the REST client.
- Drop the Mongo `users`, `account`, `session` collections after the retention period.

## Test commands

```
npx tsc --noEmit -p libs/shared/tsconfig.lib.json
npx vitest run -c libs/shared/vitest.config.ts src/auth/bff                    # unit tests, no Docker
npx vitest run -c libs/shared/vitest.bff.config.ts                             # + real Redis via testcontainers
BFF_IT_KEYCLOAK=1 npx vitest run -c libs/shared/vitest.bff.config.ts src/auth/bff/__integration__/keycloak
```
