# ET-IDN-001 · reconciliation

> **Contact-OTP passwordless identity** (folder `001-phone-otp-identity`)
> Wave 1 · `identity-service` · subgraph `None` · priority `must` · spec `status: approved`
> Re-measured 2026-10-04 after the redesign (D-38..D-50, F-044), by code review. The previous
> 2026-08-31 name-presence measurement predates the redesign and is superseded.

**Presence** `partially-present` — the earlier phone-OTP implementation exists but targets the retired design.
**Confidence** `code review only` — 0 test classes tagged `ET-IDN-001`; `OtpService`, `InternalOtpController` and `MessagingService` have no tests at all; gate 0/14.

Presence is not conformance. The classification below comes from reading the code, not from running tests.

## Requirement classification

| Req | Class | Basis |
|---|---|---|
| R1 contact normalisation | `partially-satisfied` | phone only with a `+260` default; no email, allowlist or mobile-type check |
| R2 generate and deliver | `contradicted` | code in plain text at `otp:phone:<e164>`; Twilio body unencoded; no timeouts; fallback never fires; no email; channel trusted from the form |
| R3 throttle, attempts, lock | `contradicted` | 3 tries, no lock, check-then-act races, dead resend link, no per-IP/device/country limits |
| R4 leaks nothing | `contradicted` | `String.equals`; phone in the URL; bare enums and 500s; no logging test |
| R5 Keycloak only issuer | `contradicted` | client sends unauthenticated when credentials are missing; `PhoneOtpMutationResolver` returns a service-account token as the buyer token |
| R6 one contact, one account | `contradicted` | the authenticator creates users (`user_<last8>`), grants `CUSTOMER`; `KeycloakService` finds by email and adopts on 409; `users-schema` requires email and names; duplicate email indexes (partial + plain unique, `IdentityIndexInitializer` L283) |
| R7 privileged not contact-only | `absent` | `AccountTypeRoleMapper` lets registrants pick `ORGANIZER`; admin realm lacks the `user-sync` listener; realm profile requires email and names; one realm only |

Counts: 0 already-satisfied, 1 partially-satisfied, 5 contradicted, 1 absent.

## §4 names

New names with no counterpart in the tree: `ContactOtpAuthenticator`, `IdentityClient`, `ContactNormalizer`, `ChallengeService`, the five `/api/internal/auth/*` endpoints, Redis keys `ch:*`, `proof:*`, `handle:*`, `lim:*`, the ten new error codes. Names to delete with no shim: `/api/internal/otp/*`, GraphQL `requestPhoneOtp, verifyPhoneOtp, login, register, refreshToken, validateToken`, `KeycloakAuthService` password/service-token paths, Redis keys `otp:*`.

## Still to do for this spec

- [ ] Add executing tests tagged `ET-IDN-001` before any requirement is called satisfied
- [ ] Verify the `identity_account_events` index against a running database (MongoDB MCP) once ET-IDN-004 declares it
- [ ] Classify the frontend surface against its `.dc.html` layout contract
- [ ] Confirm which error codes already exist in `ErrorCode.java` (`NOTIFICATION_CHANNEL_UNAVAILABLE` is expected; the ten new ones are not)
