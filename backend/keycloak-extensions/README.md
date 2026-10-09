# keycloak-extensions

Keycloak 26.5.2 SPI JAR for buyer sign-in by verified contact. Contract: `specs/identity/004-accounts-and-contacts/CONTRACT.md` (sections 4 and 9).

| Provider | id | Purpose |
|---|---|---|
| Authenticator | `contact-otp-authenticator` | SCREEN: contact page, code page (challenge, verify, ensure with `issueHandle=false`), then sign in the Keycloak user whose `username` is the accountId. HANDOFF: `login_hint` carries a single-use login handle, redeemed server-side, no page. |
| Event listener | `user-sync` | Sends the slim CONTRACT 4.6 payload (ids and flags only) to identity-service, asynchronously, bounded retries, event id as `Idempotency-Key`. Never blocks a login. |

## Rules the code enforces

- The plugin never creates users and never grants roles. A missing or disabled Keycloak user fails with a generic message.
- Staff (realm roles `ADMIN`, `SUPER_ADMIN`, `FINANCE`, `FINANCE_LEAD`) are refused here; they use the password flow of the admin realm.
- Fail closed: without `IDENTITY_BASE_URL`, `IDENTITY_CLIENT_ID`, `IDENTITY_CLIENT_SECRET`, `KEYCLOAK_TOKEN_URL` an ERROR is logged, the authenticator refuses every attempt and the listener is a no-op. There is no unauthenticated mode.
- Timeouts 3 s connect, 5 s request. Response bodies, contacts and codes are never logged.
- Optional `CONTACT_OTP_REGION_HINT` (for example `ZM`) is sent as `regionHint`.

## Messages and templates

`theme-resources/templates/contact-input.ftl`, `contact-code.ftl` and `theme-resources/messages/messages_en.properties` (`contact.*`) are served from the JAR by Keycloak 26.5.2 (proved by the integration test), so no theme directory is needed for them. Expiry and resend-after are rendered by the server; no script is used. FreeMarker auto-escapes, so do not add `?html`.

## Build and test

```
cd backend
mvn -o -pl keycloak-extensions verify        # unit tests (surefire), package, container tests (failsafe, *IT)
mvn -o -pl keycloak-extensions test          # unit tests only
```

Tests carry `@Tag("ET-IDN-001")` and a layer tag (`layer-1-decision`, `layer-5-integration`). The IT needs Docker and the local image `quay.io/keycloak/keycloak:26.5.2`; it imports `docker-resources/keycloak/*`, starts a stub identity-service on the host (reached as `host.docker.internal`) and writes observed behaviour to `target/poc-findings.txt`. Compiling against 26.5.2 needs `keycloak-core`, `keycloak-common`, `keycloak-services` 26.5.2 in `~/.m2`.

## Deploy

Copy `target/keycloak-extensions-1.0.0.jar` to `/opt/keycloak/providers/`, set the four environment variables and import the realms from `docker-resources/keycloak` (see its README).
