# Shared BFF: what is still pending

All three apps (admin, organization-admin, ticketing) run on `@pml.tickets/shared/auth/bff`, and the
Better Auth code, the per-app auth containers and the legacy JTI blacklist adapter are gone. The per-app
removal steps that used to be listed here are done and were deleted. What is left:

## Pending

- Drop the Mongo `users`, `account` and `session` collections that Better Auth wrote, after the retention
  period (an operations task; no frontend code reads them any more).
- Remove the unused `mongodb` dependency from the workspace `package.json` and refresh the lockfile
  (nothing under `apps/` or `libs/` imports it).
- Optional: with the Keycloak plugin ordering change (handle-only execution before the cookie
  authenticator) the buyer app's RP-logout hop in `start` could be removed.

## Reference that still applies

Browser mutation guard: `Origin` equal to `APP_URL`, `Sec-Fetch-Site: same-origin`, and
`X-Requested-With: pml-web` on every unsafe method. The shared clients (`createBffGraphQLClient`,
`createBffApiClient`, `restRequest`) send it; there is one header and one value.

Env (all apps): `APP_URL`, `KEYCLOAK_ISSUER`, `KEYCLOAK_INTERNAL_ISSUER` (optional), `KEYCLOAK_CLIENT_ID`,
`KEYCLOAK_CLIENT_SECRET`, `REDIS_URL`, `BFF_ENC_KEYS` (`kid:base64(32 bytes)[,kid2:...]`, first encrypts, all
decrypt), `TRUST_PROXY_HOPS` (required in production, `0` is a valid explicit value; the app refuses to start
without it), `GRAPHQL_URL`, `API_BASE_URL`, `API_AUDIENCE`, `IDENTITY_BASE_URL`, `IDENTITY_CLIENT_ID`,
`IDENTITY_CLIENT_SECRET` (service account with `internal-write`). Secrets from the secret store
only; the module throws in production without `REDIS_URL` and requires an https `APP_URL`. Redis must run
`noeviction` or `volatile-*` (never `allkeys-lru`).

Keycloak client (per realm export in `docker-resources/keycloak`):
- confidential, standard flow only, direct grants off, `pkce.code.challenge.method=S256`
- `redirectUris`: `<APP_URL>/api/auth/callback` (exact)
- `attributes."backchannel.logout.url"`: `<APP_URL>/api/auth/backchannel-logout`,
  `backchannel.logout.session.required=true`
- `post.logout.redirect.uris`: `<APP_URL>/*`
- access tokens carry the resource audience (`API_AUDIENCE`) via an audience mapper, and `realm_access.roles`
- the platform account id is the `accountId` claim, with `preferred_username` as the fallback; the Keycloak
  subject is never used as the account id
- realm: `revokeRefreshToken=true`, `refreshTokenMaxReuse=0`

## Test commands

```
npx tsc --noEmit -p libs/shared/tsconfig.lib.json
npx vitest run -c libs/shared/vitest.config.ts src/auth/bff                    # unit tests, no Docker
npx vitest run -c libs/shared/vitest.bff.config.ts                             # + real Redis via testcontainers
BFF_IT_KEYCLOAK=1 npx vitest run -c libs/shared/vitest.bff.config.ts src/auth/bff/__integration__/keycloak
```
