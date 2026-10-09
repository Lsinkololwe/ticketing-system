# ET-PLT-013 · reconciliation

> **The permission engine — flat module:action catalogue, role mapping, evaluation**  
> Wave 1 · `identity-service` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 6 of 17 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-PLT-013`; gate 1/19.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `identity_permissions` | bound to an `@Document` |
| `identity_role_permission_changes` | **constant declared, used nowhere** |
| `identity_role_permissions` | bound to an `@Document` |


4 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `keycloakRoles` | `absent` |
| `myPermissions` | `contradicted` — built under another name: `allPermissions`, `currentUserPermissions`, `myEffectivePermissions`, `permissions` |
| `permissionCatalogue` | `absent` |
| `rolePermissionHistory` | `absent` |
| `rolePermissionMappings` | `absent` |
| `rolePermissions` | `already-satisfied` — in SDL, resolver bound |


### Mutations

| Operation | State |
|---|---|
| `setPermissionActive` | `absent` |
| `setRolePermissionActive` | `absent` |
| `setRolePermissions` | `absent` |


### Error codes

All 3 registered in `shared-library/.../error/ErrorCode.java`.


### Events

0 of 2 wire names appear in production source. Missing: `RolePermissionsChangedEvent`, `identity.RolePermissionsChanged`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
