# Running the ET-PLT-002 migrations against a live database

> **The default database is `dev_ticketing`**, not `ticketing` — see `MONGODB_URI` in each
> service's `application.yml`. Point `MONGODB_URI` at whichever database you mean before
> starting anything; the migrations run against whatever that variable resolves to.

> **Do not reintroduce `directConnection=true` into `MONGODB_URI`.** It tells the driver to treat
> the server as a single node, so it never discovers the replica set and **multi-document
> transactions become silently inert** — ET-PLT-002 D-01. Nothing errors; reservations, escrow
> movements and journal pairs simply stop being atomic, and the platform oversells under load.
> The migrations here use no transactions and run correctly either way, which is exactly why the
> flag can sit in a URI unnoticed. `CollectionRegistryLintTest.noServiceUsesDirectConnection`
> fails the build if it comes back.

Three services each carry a `MigrationRunner` that fires on `ApplicationReadyEvent`. Starting a
service **is** running its migrations — there is no separate command.

## Quick start

The infrastructure is already running (`dev_mongodb`, `dev_redis`, `dev_postgres`,
`dev_keycloak`); `cd ../docker-resources && docker compose ps` if you want to confirm. If it is
not:

```bash
cd ../docker-resources && docker compose up -d dev_mongodb dev_redis dev_postgres dev_keycloak
```

Then run each service **from its own module directory**, one at a time, in this order:

```bash
# Client secrets. ET-PLT-001 R6 leaves these without defaults, so the service refuses to
# start unless they are set.
#
# Do NOT `source` docker-resources/env/ticketing.env: it is a docker-compose env file, not a shell script.
# Values there are unquoted, and at least two of them (a realm display name with spaces,
# and a block of HTML containing `<`) are a syntax error to bash. Sourcing it aborts
# part-way, exports nothing useful, and the failure is easy to miss because the shell
# reports it and then carries on to the next command.
ENVF=../../../docker-resources/env/ticketing.env
export CATALOG_SERVICE_SECRET="$(grep -m1  '^CATALOG_SERVICE_SECRET='  "$ENVF" | cut -d= -f2-)"
export BOOKING_SERVICE_SECRET="$(grep -m1  '^BOOKING_SERVICE_SECRET='  "$ENVF" | cut -d= -f2-)"
export IDENTITY_SERVICE_SECRET="$(grep -m1 '^IDENTITY_SERVICE_SECRET=' "$ENVF" | cut -d= -f2-)"

cd backend/catalog-service  && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd backend/booking-service   && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd backend/identity-service  && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Two details in that command line are load-bearing, not decoration:

- **`-Dspring-boot.run.profiles=local`.** Without it the active profile comes from the
  environment, so nothing in the command or the config says which database is about to be
  migrated.
- **One `cd`, into the module.** The profile's `application-local.yml` is what names the local
  datastore, and it lives in the module. A command that `cd`s somewhere else first is looking at
  the wrong file.

Each service can be stopped as soon as its migration block has printed — the runner fires on
`ApplicationReadyEvent`, before the service does any work, and the ledger records each step.

> **Why Claude does not run this step.** The `block-db-access.py` policy hook *does* admit the
> command above: it reads the module's `application-local.yml`, sees every host in it is loopback,
> and allows the run. That allowance is why the local profile now pins its datastore explicitly —
> point the URI at a shared environment and the hook re-arms by itself, which is the property that
> makes the allowance safe to have.
>
> What still stops it is the separate interactive permission gate on running a long-lived dev
> server, which Claude cannot grant itself. The migrations, the ordering and the verification
> queries below are prepared so that running them is a copy-paste. Prefixing the command with `!`
> in a Claude Code session puts the output back into the conversation.

Every step is recorded in a per-service ledger collection (`catalog_migrations`,
`booking_migrations`, `identity_migrations`) and skipped if already `SUCCEEDED`, so a restart is
safe.

---

## 1 · Before anything, take a backup

The collection rename is the only irreversible step in the set — `renameCollection` is atomic and
there is no undo. Everything else filters on the absence of what it sets and can be re-run, but
the rename cannot be un-run without the backup.

```bash
mongodump --uri "$MONGODB_URI" --db dev_ticketing --out ./backup-before-et-plt-002
```

## 2 · Start each service, one at a time, in this order

Order matters between services only for catalog → identity: identity's
`organization-status-semantic` step resolves statuses against catalog's reference data.

```bash
cd backend/catalog-service  && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd backend/booking-service   && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd backend/identity-service  && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Watch for these lines. A step that reports nothing has not run.

```
<service>: N migration step(s) to consider
Collection renames: 38 renamed, 0 already absent, 0 refused
Document backfill: [catalog_events._class=..., catalog_events.currency=...]
Migration 'collection-registry-rename' applied: ...
```

### If a rename is refused

`refused` means the target collection already holds documents — someone has written to the new
name. **Nothing is moved for that pair, and the source is left untouched**, so both sets survive
and you can reconcile them. This is not an error to retry; it needs a decision about which set is
authoritative.

### If a step fails

`<service>.migrations.fail-fast` defaults to `true`, so the service refuses to start rather than
serving against half-converted data. The ledger row is written before the exception propagates,
so the failure survives the process exiting. Clear that row after fixing the cause — a `FAILED`
or `RUNNING` row is refused on the next attempt rather than retried, because a half-applied step
is not necessarily safe to repeat.

---

## 3 · Verify

`mongosh` against the migrated database. Every one of these should come back empty or zero.

```javascript
// (a) No collection still sits at a pre-registry name.
//     Expect only: escrow_accounts, platform_configuration, booking_purchase_escalations,
//     approval_notifications, payout_config_audit_logs — the five awaiting a §4 decision.
db.getCollectionNames().filter(n =>
    !n.startsWith("catalog_") && !n.startsWith("booking_") && !n.startsWith("identity_"))

// (b) No stored _class is a package name. This is ET-PLT-002 BE-10's acceptance.
db.getCollectionNames().forEach(c => {
    const n = db[c].countDocuments({ _class: /^com\.pml\./ });
    if (n > 0) print(`${c}: ${n} documents still carry a package name in _class`);
})

// (c) Every versioned document has a version to start from.
["booking_tickets","booking_reservations","booking_promo_codes","booking_payment_intents",
 "booking_escrow_accounts","booking_payout_requests","catalog_events","catalog_ticket_tiers"
].forEach(c => {
    const n = db[c].countDocuments({ version: { $exists: false } });
    if (n > 0) print(`${c}: ${n} documents without a version`);
})

// (d) No money field is still a double or a string.
["booking_tickets","booking_escrow_accounts","booking_payout_requests","booking_journal_lines",
 "booking_commission_records","booking_payment_intents"
].forEach(c => {
    const n = db[c].countDocuments({ $or: [
        { amount:         { $type: ["double","string"] } },
        { currentBalance: { $type: ["double","string"] } },
        { price:          { $type: ["double","string"] } }] });
    if (n > 0) print(`${c}: ${n} money values not stored as Decimal128`);
})

// (e) Every monetary document names its currency.
["catalog_events","catalog_ticket_tiers","booking_promo_codes"].forEach(c => {
    const n = db[c].countDocuments({ currency: { $exists: false } });
    if (n > 0) print(`${c}: ${n} documents without a currency`);
})

// (f) The ledger agrees that everything ran.
["catalog_migrations","booking_migrations","identity_migrations"].forEach(c =>
    db[c].find({}, { _id: 1, status: 1, finishedAt: 1 }).forEach(printjson))
```

---

## 4 · One thing to check before you start

**Which time zone wrote the existing timestamps.**

Document timestamps moved from `LocalDateTime` to `Instant`. Both map to BSON `Date`, so no
migration converts them — but the two types disagree about what a stored value *means*.
Spring Data wrote a `LocalDateTime` by interpreting it in the JVM's default zone. Reading it back
as an `Instant` gives the instant it was stored as, which is only the intended wall-clock time if
that JVM was in `Africa/Lusaka`.

- Hosts in `Africa/Lusaka` (UTC+2, no DST): nothing moves.
- Hosts in UTC — most CI and cloud defaults: historical timestamps read two hours earlier than
  intended.

No migration can tell these apart from the data alone, because both produce a valid `Date`. If
the live database was written by hosts in UTC, a one-off `$dateAdd` of +2 hours over the affected
fields is the correction — but that is a decision about provenance, not something to infer.
