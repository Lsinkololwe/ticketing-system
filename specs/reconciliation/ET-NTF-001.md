# ET-NTF-001 · reconciliation

> **Notification transport — channels, templates, devices, delivery**  
> Wave 5 · `identity-service` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 12 of 19 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-NTF-001`; gate 0/13.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


> **Amended 2026-09-01 — identity's pagination twins were collapsed.**
> The `*OffsetPagination` / `*CursorPagination` names quoted below no longer exist: under
> [`ROADMAP.md` D-19](../ROADMAP.md) each pair became one field under its bare name, keeping the
> shape §4 declares. Read the substitutes below as `<name>` without the suffix. The classification
> is unchanged — where the shipped name still differs from §4's, the operation is still
> `contradicted`.

### Collections

| Collection | State |
|---|---|
| `identity_notification_preferences` | bound to an `@Document` |
| `identity_notification_templates` | **constant declared, used nowhere** |
| `identity_notifications` | bound to an `@Document` |
| `identity_user_devices` | bound to an `@Document` |


6 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `myDevices` | `already-satisfied` — in SDL, resolver bound |
| `myNotificationPreferences` | `already-satisfied` — in SDL, resolver bound |
| `myNotifications` | `contradicted` — built under another name: `myNotificationsCursorPagination`, `myNotificationsOffsetPagination` |
| `notificationTemplates` | `absent` |
| `unreadNotificationCount` | `already-satisfied` — in SDL, resolver bound |


### Mutations

| Operation | State |
|---|---|
| `deregisterDevice` | `absent` |
| `markAllNotificationsRead` | `already-satisfied` — in SDL, resolver bound |
| `markNotificationRead` | `already-satisfied` — in SDL, resolver bound |
| `registerDevice` | `already-satisfied` — in SDL, resolver bound |
| `resendNotification` | `absent` |
| `updateNotificationPreferences` | `already-satisfied` — in SDL, resolver bound |
| `updateNotificationTemplate` | `absent` |


### Error codes

All 2 registered in `shared-library/.../error/ErrorCode.java`.


### Events

0 of 1 wire names appear in production source. Missing: `NotificationRequestedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
