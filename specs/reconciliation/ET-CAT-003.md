# ET-CAT-003 · reconciliation

> **Venues, geography, categories and discovery**  
> Wave 2 · `catalog-service` · subgraph `catalog` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 9 of 20 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-CAT-003`; gate 0/13.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `catalog_categories` | bound to an `@Document` |
| `catalog_cities` | bound to an `@Document` |
| `catalog_locations` | bound to an `@Document` |
| `catalog_provinces` | bound to an `@Document` |
| `catalog_reference_data` | bound to an `@Document` |


6 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `categories` | `contradicted` — built under another name: `activeEventCategoriesCursorPagination`, `activeEventCategoriesOffsetPagination`, `eventCategoriesCursorPagination`, `eventCategoriesOffsetPagination` |
| `cities` | `contradicted` — built under another name: `citiesByCountryCursorPagination`, `citiesByCountryOffsetPagination`, `citiesByProvinceCursorPagination`, `citiesByProvinceOffsetPagination` |
| `location` | `already-satisfied` — in SDL, resolver bound |
| `locations` | `contradicted` — built under another name: `locationsByCityCursorPagination`, `locationsByCountryCursorPagination`, `locationsCursorPagination`, `locationsNearbyCursorPagination` |
| `provinces` | `contradicted` — built under another name: `provincesByCountryCursorPagination`, `provincesByCountryOffsetPagination`, `provincesCursorPagination`, `provincesOffsetPagination` |


### Mutations

| Operation | State |
|---|---|
| `createCategory` | `contradicted` — built under another name: `createEventCategory` |
| `createCity` | `already-satisfied` — in SDL, resolver bound |
| `createLocation` | `absent` |
| `deactivateCategory` | `contradicted` — built under another name: `deactivateEventCategory` |
| `deactivateCity` | `absent` |
| `deactivateLocation` | `absent` |
| `updateCategory` | `contradicted` — built under another name: `updateEventCategory` |
| `updateCity` | `already-satisfied` — in SDL, resolver bound |
| `updateLocation` | `absent` |


### Error codes

All 1 registered in `shared-library/.../error/ErrorCode.java`.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
