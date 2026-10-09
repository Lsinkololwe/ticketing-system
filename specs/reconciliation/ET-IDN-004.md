# ET-IDN-004 · reconciliation

> **Accounts and contacts**
> Wave 1 · `identity-service` · subgraph `None` · priority `must` · spec `status: approved`
> Measured 2026-10-04 by code review against the working tree.

**Presence** `mostly-absent` — the collections `identity_contacts`, `identity_consents`, `identity_account_events`, the workflows and the Schedule do not exist; `identity_users` exists in the legacy shape.
**Confidence** `code review only` — 0 test classes tagged `ET-IDN-004`; gate 0/12.

| Req | Class | Basis |
|---|---|---|
| R1 types and states | `contradicted` | registrants can pick `ORGANIZER` through `AccountTypeRoleMapper`; only legacy `AccountStatus`; `users-schema` requires email and names |
| R2 identity-first creation | `contradicted` | the Keycloak authenticator creates users; no `AccountEnsureWorkflow`; `KeycloakService` finds users by email and adopts on 409 |
| R3 contact uniqueness | `partially-satisfied` | a sparse unique index on `phoneNumber` exists; no contacts collection or encryption; duplicate email indexes (partial and plain unique, `IdentityIndexInitializer` L283) |
| R4 adoption and repair | `satisfied-by-test` (2026-10-04, repair part; adoption through the repair D2) | was `contradicted`:  `UserSyncServiceImpl` trusts user-editable attributes, overwrites and hard-deletes; the admin realm has no `user-sync` listener |
| R5 contact change | `satisfied-by-test` (2026-10-04) | `ContactChangeWorkflow`, `ContactChangeService`, GraphQL CONTRACT §14; `ContactChangeWorkflowTest` (L3) and `ContactChangeEndToEndTest` (L2, real Keycloak) |
| R6 merge | `absent` | — |
| R7 deletion hook | `contradicted` | hard delete on a Keycloak delete event |
| R8 consistency and gates | `absent` | no markers, max ages or consent gate |

Counts: 0 already-satisfied, 1 partially-satisfied, 5 contradicted, 2 absent.

### Error codes

`CONTACT_ALREADY_CLAIMED, ACCOUNT_SUSPENDED, ACCOUNT_MERGING, ACCOUNT_NOT_ACTIVE` do not exist in `ErrorCode.java` and need ET-PLT-005 §4 rows.

### Indexes

9 indexes declared in §4. Definitions are not checked here; verify against a running database with the MongoDB MCP, per F-002.

## Still to do for this spec

- [ ] Add executing tests tagged `ET-IDN-004` before any requirement is called satisfied
- [ ] Add the four collections to the ET-PLT-002 §4 registry (collection registry lint)
- [ ] Add the `identity-account` queue to the ET-PLT-015 queue registry together with the code
- [ ] Decide the open items in F-044 (quarantine length, merge policy, recovery proofs)

## Update 2026-10-04 · contact management

R3 (add, remove, primary, quarantine, last-verified rule) and R5 (change, re-proof, claim-before-release, sessions, notices) now have executing tests: `ContactChangeWorkflowTest` (L3), `ContactChangeEndToEndTest` and `BuyerSignInEndToEndTest` (L2, real Redis, MongoDB, Temporal, Keycloak 26.5.2), `ContactRulesTest`, `ContactChangeRulesTest` (L1). R2 gained the real-Keycloak cases (crash after claim, orphan user adopted, Keycloak 5xx). Decisions F-044 (a) to (d) are taken for implementation. New error codes: `CONTACT_UNKNOWN`, `CONTACT_CHANGE_IN_PROGRESS`, `LAST_VERIFIED_CONTACT`, `NO_VERIFIED_CONTACT`. New events: `identity.ContactAdded`, `ContactChanged`, `ContactRemoved`. Also 2026-10-04: R4 repair is built and tested (`AccountRepair`, Schedule `identity-account-repair`, D1..D9, `AccountRepairEndToEndTest`); resend of contact codes (`resendContactCode`). Not done: merge (BE-7), deletion (BE-8), gates (BE-9).
