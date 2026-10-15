# ET-PLT-013 · reconciliation

> **The permission engine — flat module:action catalogue, role mapping, evaluation**  
> Wave 1 · `identity-service` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

> **Disposition, 2026-10-13 — ROADMAP D-37, D-54…D-60.** The measurement below predates the
> code-declared model and is kept as the 2026-08-31 baseline; read it through this note.
> - **Stands:** R1 (closed grammar, `resource:action` as in use per D-59), R2 (catalogue declared in
>   `com.pml.shared.security.Permission`, every code enforced or in the role-gated register), R5
>   (union across platform roles, fail closed).
> - **Withdrawn (D-37):** R3 stored role documents, R4 implication expander, R6 inactive roles and
>   permissions, R7 per-role cache, R8 administrator writes and change log.
> - **Collections:** `identity_permissions` and `identity_role_permissions` were dropped, and
>   `identity_role_permission_changes` is not built; "declared, used nowhere" is the intended end
>   state until P8 removes the constants.
> - **Operations:** `permissionCatalogue`, `rolePermissionMappings`, `rolePermissionHistory`,
>   `keycloakRoles` and the three `set*` mutations are **withdrawn**, not absent-to-build. As built:
>   `myPermissions` returns `{ permissions, roles }`, `currentUserPermissions`,
>   `myEffectivePermissions(organizationId, eventId)`, `permissions`, `permission(code)` and
>   `rolePermissions(role)`, all read from code.
> - **Events:** `identity.RolePermissionsChanged` is withdrawn; it is not to be built.
> - **Remaining work** is the P1…P8 build order in [`specs/tasks/ET-PLT-013.md`](../tasks/ET-PLT-013.md).

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

- [ ] Read §3 and classify **R1, R2 and R5** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent` (R3, R4, R6, R7, R8 are withdrawn)
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [x] ~~Verify each §4 index against a running database (MongoDB MCP)~~ withdrawn: the collections were dropped (D-37)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
