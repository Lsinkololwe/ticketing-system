# ET-PLT-012 · Build topology — tasks

> **Spec** [`specs/_platform/012-build-topology/spec.md`](../_platform/012-build-topology/spec.md) · **Wave 0** · `blocked_by:` *(none — this is the first task in the corpus)*
> **Screens** — none. The build has no UI.
> **Verify** `mvn -q -f backend validate` · `mvn -q -f backend -DskipTests clean install` · `mvn -q -f backend dependency:tree`

Nothing in the corpus compiles until this lands. `backend/pom.xml` already declares the six
modules and `testcontainers.version` 1.21.4; the work is to make the parent the *only* place a
version is decided, and to make the enforcer refuse the mistakes §3 names.

## R0 · Reconcile *(do this first)*

Classify every §3 requirement against the tree as `already-satisfied` / `partially-satisfied` /
`contradicted` / `absent`. Specifically: read all seven `pom.xml` files and record which of the
§4 managed properties are already declared in the parent, which are duplicated in a child, and
which module declares a version the parent should own. **Do not edit until the table exists** —
BE-3 is a deletion task, and deleting the wrong version is how a reactor stops resolving.

- Output: a table in the PR body, one row per §3 requirement.
- Note explicitly: `CLAUDE.md` claims DGS 10.0.1; [CONVENTIONS.md §0](../CONVENTIONS.md) pins
  **10.5.0** and says `CLAUDE.md` is stale. The spec wins.

## A · Backend

### BE-1 · The parent POM — packaging, Boot parent, modules, properties
- **Spec** R1, R2 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Files** `backend/pom.xml`
- Declare `packaging: pom`, parent `spring-boot-starter-parent:3.5.4`, the six `<modules>`, and
  every §4 managed property — `java.version`/`maven.compiler.release` 21, `dgs.version` 10.5.0,
  `spring-cloud-azure.version` 5.19.0, `spring-cloud.version` 2025.0.0,
  `mongodb.driver.version` 5.5.1, `java-dataloader.version` 5.0.1, `resilience4j.version` 2.2.0,
  `zxing.version` 3.5.2, `testcontainers.version` 1.21.4, `docker.api.version` 1.44.
- **Do not "upgrade" `testcontainers.version`.** §4 holds it at 1.21.4 deliberately: 1.19 pins
  the Docker API at 1.32 and Docker Engine 29+ refuses anything below 1.40. Bumping it blindly
  breaks every L3 test in the corpus.
- **Acceptance** `mvn -f backend validate` exits 0.

### BE-2 · Import the five BOMs
- **Spec** R1 · **§5** T2 · **depends** BE-1 · **parallel-safe** no *(one file)*
- **Files** `backend/pom.xml`
- Boot (inherited from the parent), DGS `graphql-dgs-platform-dependencies`, Azure
  `spring-cloud-azure-dependencies`, Spring Cloud `spring-cloud-dependencies`, Testcontainers —
  all as `import` scope in `dependencyManagement`.
- **Acceptance** `mvn -f backend dependency:tree` resolves exactly one version per managed
  artifact. Two versions of the same artifact is a failure, not a warning.

### BE-3 · Repoint the five Spring modules; strip duplicated versions
- **Spec** R1, R5 · **§5** T3 · **depends** BE-2 · **parallel-safe** no *(the reactor must stay resolvable between edits)*
- **Files** `backend/{shared-library,catalog-service,booking-service,identity-service,api-gateway}/pom.xml`
- **Acceptance** `mvn -f backend -DskipTests clean install` exits 0 **and** no module declares a
  version the parent manages.
- Work one module at a time and keep the reactor green between edits. Batch-deleting versions
  across five poms and then debugging the result is the slow path.

### BE-4 · Leave `keycloak-extensions` parentless; prove the shaded JAR is Spring-free
- **Spec** R3 · **§5** T4 · **depends** BE-1 · **parallel-safe** yes
- **Files** `backend/keycloak-extensions/pom.xml`
- It loads inside Keycloak's classloader, not Boot's. It inherits nothing and shades Gson only.
- **Acceptance** the shaded JAR contains Gson and **no** `org/springframework` entry.

### BE-5 · Assert the module graph — `shared-library` a leaf, no service-to-service edge
- **Spec** R4 · **§5** T5 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** a deliberate back-edge from `shared-library` to a service fails the reactor.
  Write that back-edge, watch it fail, remove it — an assertion nobody has seen fail is an
  assumption.

### BE-6 · One compiler configuration; class file major version 65
- **Spec** R5 · **§5** T6 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** every module's `target/classes` reports major version 65 (Java 21).

### BE-7 · Manage every plugin version in the parent
- **Spec** R6 · **§5** T7 · **depends** BE-3 · **parallel-safe** no *(parent and children together)*
- **Acceptance** `mvn -f backend versions:display-plugin-updates` reports no unmanaged plugin.

### BE-8 · The enforcer — version floors, duplicate bans, the servlet-stack ban
- **Spec** R7 · **§5** T8 · **depends** BE-7 · **parallel-safe** no *(one file)*
- **Acceptance** adding `spring-boot-starter-web` to any module fails `validate` with a message
  **naming the module**. A generic failure is not enough; the point is that the next person
  knows which pom to fix.
- This is the mechanical guard behind [CONVENTIONS.md §1](../CONVENTIONS.md) — WebFlux only, no
  servlet stack on any classpath.

## B · Contract

None. This spec exposes no GraphQL.

## C · Frontend

None. Run **Track F0** in parallel — it is independent of every backend wave and is the long
pole for the frontend half of the corpus.

## D · Tests

### TS-1 · Reactor and enforcer tests
- **Spec** R1–R7 · **depends** BE-8 · **parallel-safe** yes
- **L1** — none; there is no domain code here.
- **L4 (contract)** — a build-level test that asserts: single resolved version per managed
  artifact; class file major version 65 per module; the shaded Keycloak JAR has no Spring entry;
  `shared-library` has no inbound service edge.
- **Negative tests are the whole value of this spec.** Each of the three enforcer rules gets a
  test that adds the banned thing and asserts the build fails with the naming message.
- Tag `@Tag("ET-PLT-012")`; `@DisplayName` names the requirement (`ET-PLT-012-R7`).

> The `verify:` block for this spec is `validate` / `install` / `dependency:tree` — no
> `-Dgroups`, so the `-DfailIfNoTests=true` defect does not apply here. It applies to 13 other
> specs and is fixed by [`ET-PLT-006`](ET-PLT-006.md) **BE-9**.

## E · Gate

- [ ] R0 classification table recorded
- [ ] All three `verify:` commands green from a clean `~/.m2` (a build that only works with a warm cache is not reproducible)
- [ ] One version per managed artifact; no child declares a managed version
- [ ] Enforcer refuses `spring-boot-starter-web` by name of module, proven by a test
- [ ] `keycloak-extensions` shaded JAR: Gson present, Spring absent
- [ ] Spec `status:` → `implemented`
