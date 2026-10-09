# ET-FIN-001 · reconciliation

> **Per-event escrow, the chart of accounts and double-entry**  
> Wave 3 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 20 of 27 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-FIN-001`; gate 0/15.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


> **Amended 2026-09-01 — booking's pagination twins were collapsed.**
> The `*OffsetPagination` / `*CursorPagination` names quoted below no longer exist: under
> [`ROADMAP.md` D-19](../ROADMAP.md) each pair became one field under its bare name, keeping
> the offset form. Read the substitutes below as `<name>` without the suffix. The
> classification itself is unchanged — these operations are still `contradicted`, because the
> shipped name still differs from the one §4 declares; only the shipped name has changed.

### Collections

| Collection | State |
|---|---|
| `booking_chart_of_accounts` | bound to an `@Document` |
| `booking_escrow_accounts` | bound to an `@Document` |
| `booking_escrow_transactions` | bound to an `@Document` |
| `booking_journal_entries` | bound to an `@Document` |
| `booking_journal_lines` | named and used, but no `@Document` binds it |
| `booking_platform_accounts` | bound to an `@Document` |


6 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `chartOfAccounts` | `already-satisfied` — in SDL, resolver bound |
| `escrowAccount` | `already-satisfied` — in SDL, resolver bound |
| `escrowTransactions` | `contradicted` — built under another name: `escrowTransactionsByAccount`, `escrowTransactionsByTicket`, `escrowTransactionsUnlinked` |
| `journalEntries` | `contradicted` — built under another name: `journalEntriesByAccountCode`, `journalEntriesByCorrelationId`, `journalEntriesOffsetPagination`, `pendingJournalEntriesOffsetPagination` |
| `myEscrowAccounts` | `contradicted` — built under another name: `escrowAccountsByOrganizerCursorPagination`, `escrowAccountsByOrganizerOffsetPagination`, `escrowAccountsCursorPagination`, `escrowAccountsOffsetPagination` |
| `platformAccounts` | `already-satisfied` — in SDL, resolver bound |
| `trialBalance` | `contradicted` — **in SDL, no resolver; fails at runtime** |


### Mutations

| Operation | State |
|---|---|
| `closeEscrowAccount` | `already-satisfied` — in SDL, resolver bound |
| `postManualAdjustment` | `absent` |
| `reactivateEscrowAccount` | `absent` |
| `suspendEscrowAccount` | `absent` |
| `transferToOperations` | `absent` |


### Error codes

All 5 registered in `shared-library/.../error/ErrorCode.java`.


### Events

4 of 4 wire names appear in production source.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
