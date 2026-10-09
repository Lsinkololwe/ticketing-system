# ET-ADM-002 · reconciliation

> **Platform configuration, feature flags and versioned settings**  
> Wave 6 · `identity-service` · subgraph `identity` · priority `should` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `absent` — 3 of 13 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-ADM-002`; gate 0/13.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `identity_feature_flags` | **constant declared, used nowhere** |
| `identity_platform_configuration` | named and used, but no `@Document` binds it |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `configurationHistory` | `absent` |
| `featureFlags` | `absent` |
| `killSwitches` | `absent` |
| `organizationConfiguration` | `absent` |
| `platformConfiguration` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `clearOrganizationConfiguration` | `absent` |
| `setFeatureFlag` | `absent` |
| `setOrganizationConfiguration` | `absent` |
| `setPlatformConfiguration` | `absent` |


### Error codes

All 2 registered in `shared-library/.../error/ErrorCode.java`.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
