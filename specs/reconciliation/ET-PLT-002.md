# ET-PLT-002 · reconciliation

> **Persistence baseline — replica set, the collection registry, money and time**  
> Wave 0 · `all` · subgraph `None` · priority `must` · spec `status: in-progress`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 56 of 67 §4 names exist.  
**Confidence** `under test` — 16 test class(es) tagged `ET-PLT-002`; gate 7/10.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_bank_accounts` | bound to an `@Document` |
| `booking_chargebacks` | bound to an `@Document` |
| `booking_chart_of_accounts` | bound to an `@Document` |
| `booking_checkin_conflicts` | bound to an `@Document` |
| `booking_checkins` | bound to an `@Document` |
| `booking_commission_records` | bound to an `@Document` |
| `booking_escrow_accounts` | bound to an `@Document` |
| `booking_escrow_transactions` | bound to an `@Document` |
| `booking_journal_entries` | bound to an `@Document` |
| `booking_journal_lines` | named and used, but no `@Document` binds it |
| `booking_migration_runs` | named and used, but no `@Document` binds it |
| `booking_outbox` | named and used, but no `@Document` binds it |
| `booking_payment_attempts` | bound to an `@Document` |
| `booking_payment_intents` | bound to an `@Document` |
| `booking_payout_requests` | bound to an `@Document` |
| `booking_platform_accounts` | bound to an `@Document` |
| `booking_promo_codes` | bound to an `@Document` |
| `booking_reconciliation_items` | **constant declared, used nowhere** |
| `booking_reconciliation_runs` | bound to an `@Document` |
| `booking_recovery_proposals` | **constant declared, used nowhere** |
| `booking_refund_requests` | bound to an `@Document` |
| `booking_reservations` | bound to an `@Document` |
| `booking_statistics_rollups` | named and used, but no `@Document` binds it |
| `booking_ticket_transfers` | **constant declared, used nowhere** |
| `booking_tickets` | bound to an `@Document` |
| `booking_tier_inventory` | named and used, but no `@Document` binds it |
| `booking_webhook_receipts` | named and used, but no `@Document` binds it |
| `catalog_approval_escalations` | bound to an `@Document` |
| `catalog_approval_timelines` | bound to an `@Document` |
| `catalog_categories` | bound to an `@Document` |
| `catalog_cities` | bound to an `@Document` |
| `catalog_events` | bound to an `@Document` |
| `catalog_locations` | bound to an `@Document` |
| `catalog_migration_runs` | named and used, but no `@Document` binds it |
| `catalog_outbox` | named and used, but no `@Document` binds it |
| `catalog_provinces` | bound to an `@Document` |
| `catalog_reference_data` | bound to an `@Document` |
| `catalog_statistics_rollups` | named and used, but no `@Document` binds it |
| `catalog_ticket_tiers` | bound to an `@Document` |
| `identity_audit_logs` | bound to an `@Document` |
| `identity_consent_records` | **constant declared, used nowhere** |
| `identity_data_exports` | **constant declared, used nowhere** |
| `identity_erasure_requests` | **constant declared, used nowhere** |
| `identity_event_access_grants` | bound to an `@Document` |
| `identity_event_reminders` | bound to an `@Document` |
| `identity_feature_flags` | **constant declared, used nowhere** |
| `identity_mass_sends` | **constant declared, used nowhere** |
| `identity_migration_runs` | named and used, but no `@Document` binds it |
| `identity_notification_preferences` | bound to an `@Document` |
| `identity_notification_templates` | **constant declared, used nowhere** |
| `identity_notifications` | bound to an `@Document` |
| `identity_organization_members` | bound to an `@Document` |
| `identity_organizations` | bound to an `@Document` |
| `identity_outbox` | named and used, but no `@Document` binds it |
| `identity_ownership_transfers` | bound to an `@Document` |
| `identity_permissions` | bound to an `@Document` |
| `identity_platform_configuration` | named and used, but no `@Document` binds it |
| `identity_review_claims` | **constant declared, used nowhere** |
| `identity_role_permissions` | bound to an `@Document` |
| `identity_statistics_rollups` | named and used, but no `@Document` binds it |
| `identity_team_invitations` | bound to an `@Document` |
| `identity_temporary_blocks` | **constant declared, used nowhere** |
| `identity_token_revocations` | bound to an `@Document` |
| `identity_user_devices` | bound to an `@Document` |
| `identity_users` | bound to an `@Document` |
| `identity_verification_documents` | bound to an `@Document` |


21 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Error codes

All 1 registered in `shared-library/.../error/ErrorCode.java`.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
