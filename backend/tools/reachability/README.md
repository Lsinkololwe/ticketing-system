# Reachability audit tools

How the 2026-09 dead-code and single-responsibility audit was done, so it can be repeated rather than
re-derived. Report: `docs/audits/2026-09-SINGLE_RESPONSIBILITY_AUDIT.md`.

| Script | What it does |
|---|---|
| `inventory.py` | Classifies every method, type and custom config key in `src/main` as LIVE, KEEP_* (entry point, spec-named, accessor, reviewed keep), GAP (called but never executed by a test), TEST_ONLY or DEAD. Writes CSVs and a summary. |
| `prune.py` | Deletes what the inventory calls DEAD — whole files, nested types, methods with their Javadoc and annotations, dangling imports. `--member FILE:NAME` deletes a reviewed symbol the inventory did not flag. |
| `move_class.py` | Moves or renames classes between packages and rewrites every reference. Wildcard imports (`import x.*;`) must be rewritten by hand. |
| `fields.py` | Finds fields nothing reads, including the Lombok, record and builder fields PMD skips. Classifies each as UNUSED, WRITE_ONLY, WIRE (crosses a service or provider boundary: check the other side), PERSISTED or PERSISTED_UNWRITTEN (a `@Document` field: reported, never deleted). |
| `endpoints.py` | Lists every `/api/internal/**` endpoint with the modules and frontend files that call it. The inventory counts any mapping as an entry point; an internal endpoint with no caller is dead anyway. |
| `unused-code.xml` | The PMD ruleset for unused locals, parameters, private members and imports. |
| `keep.txt` | Symbols kept on purpose, each with its reason. The inventory honours it. |

`GraphQlSchemaParity` (shared-library test-jar) and each service's `GraphQlDtoSchemaParityTest` hold every
GraphQL DTO to the schema type of the same name. DGS binds by property name, so a rename on either side
fails silently; the test's known-gap list may only shrink.

## Repeat the audit

```bash
cd backend
mvn -Pcoverage install                       # tests + target/site/jacoco/jacoco.xml per module
for m in booking-service catalog-service identity-service; do
  mvn -q org.jacoco:jacoco-maven-plugin:0.8.13:report-aggregate -pl shared-library,$m
done                                         # shared-library classes as the services' tests run them
cd ..
python3 backend/tools/reachability/inventory.py --out /tmp/inv
python3 backend/tools/reachability/prune.py /tmp/inv --dry-run   # review, then without --dry-run
```

Run inventory → prune → compile until the inventory reports no DEAD rows: deleting a method can leave
the private helper it alone called without a caller. Then:

```bash
python3 backend/tools/reachability/endpoints.py                 # internal endpoints with no caller
python3 backend/tools/reachability/fields.py > /tmp/fields.tsv  # fields nothing reads
mvn -f backend -q org.apache.maven.plugins:maven-pmd-plugin:3.26.0:pmd \
    -Dpmd.rulesets=$PWD/backend/tools/reachability/unused-code.xml -Dformat=xml   # */target/pmd.xml
```

Every deletion feeds the next pass: removing an endpoint orphans its service methods, removing a
record component orphans the local that computed it.

## What the verdicts do not prove

- Matching is by simple name, so two methods sharing a name keep each other alive, and a type named
  like a live one survives with it (a dead `web/graphql/dto/ChargebackStats` hid behind
  `ChargebackService.ChargebackStats`). DEAD is a lower bound; LIVE is not proof of use.
- A field Temporal serialises in a workflow payload stays even when nothing reads it: the SDK's
  default mapper fails on unknown properties, so removing one breaks the histories already recorded.
- PMD misses a private method used only as a method reference (`Type::method`); check before deleting.
- A method a spec names (`specs/**/spec.yaml`) is kept even with no caller — planned work is not dead.
- GAP is not a deletion signal. It is a test gap: the code is reachable and nothing executes it.
- Coverage is evidence, never a gate (ET-PLT-006).
