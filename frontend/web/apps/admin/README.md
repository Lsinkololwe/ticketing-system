# Platform admin

Next.js 16 console for MyTicketZM staff (realm `myticketzm-admin`, client `myticketzm-admin`).

## Authentication

Server-side BFF from `libs/shared/src/auth/bff`, configured in `src/lib/bffConfig.ts` / `src/lib/bff.ts`.
Opaque cookie `__Host-pml_admin` (SameSite=Strict, idle 15 min, absolute 8 h), tokens only in Redis, no token in the browser.
Pages and Server Actions call `bff.requireSession({ roles })`; sensitive actions (payout and refund decisions, platform
rules, user suspension, lock, deactivation and role changes) go through `useStepUp().guard()` and `/api/auth/stepup`
(sign-in no older than 5 minutes).

Environment: `APP_URL`, `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `BFF_ENC_KEYS`, `REDIS_URL`,
`GRAPHQL_URL`, `API_AUDIENCE`, `IDENTITY_BASE_URL`/`IDENTITY_CLIENT_ID`/`IDENTITY_CLIENT_SECRET` (revocation),
`TRUST_PROXY_HOPS`, optional `KEYCLOAK_INTERNAL_ISSUER`.

## Tests

- `npx vitest run` (unit)
- `BFF_IT_KEYCLOAK=1 npx vitest run -c apps/admin/vitest.it.config.ts` from `frontend/web`: login, callback, session,
  step-up, refresh, back-channel logout and logout against real Keycloak 26.5.2 and Redis (Docker).
