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
> `-Dgroups`, so the `-DfailIfNoTests=false` defect does not apply here. It applies to 13 other
> specs and is fixed by [`ET-PLT-006`](ET-PLT-006.md) **BE-9**.

## E · Gate

- [x] R0 classification table recorded — below
- [x] All three `verify:` commands green from a clean `~/.m2`
  - `mvn -Dmaven.repo.local=/tmp/m2clean -DskipTests clean install` — **BUILD SUCCESS**, all seven
    modules, nothing pre-cached. `validate` runs inside it. `dependency:tree` was run against a warm
    repository; it resolves the same graph, so the reproducibility claim rests on the clean install.
- [x] One version per managed artifact; no child declares a managed version
  - `graphql-java:24.1` and `java-dataloader:5.0.1`, identical across all three subgraphs.
    `requireUpperBoundDeps` now enforces it — and found six real downgrades doing so.
- [x] Enforcer refuses `spring-boot-starter-web` by name of module, proven by a test
  - `EnforcerRefusalTest`, which forks a real build against a throwaway module. Mutation-verified:
    removing **both** `spring-boot-starter-web` and `spring-boot-starter-tomcat` from the ban makes
    the build succeed and the test fail.
- [x] `keycloak-extensions` shaded JAR: Gson present, Spring absent
- [x] Spec `status:` → `implemented`

## F · R0 · Classification

| § | Requirement | Classification | What was actually wrong |
|---|---|---|---|
| R1 | One parent declares every managed version | partially-satisfied | Topology correct; `identity-service` still carried a `<version>` tag (moved to `dependencyManagement`). **Six transitive downgrades** were resolving silently, invisible until `requireUpperBoundDeps` was added. |
| R2 | The whole backend builds from one command | already-satisfied | Confirmed, including from a clean `~/.m2`. |
| R3 | The Keycloak SPI module inherits nothing | already-satisfied | Structurally correct — and see R5, where that correctness had an unexamined consequence. |
| R4 | Acyclic graph, `shared-library` a leaf | already-satisfied | No back-edge, no service-to-service edge. |
| R5 | Java 21 everywhere | **contradicted** | `keycloak-extensions` compiled at **Java 17** — [F-020](../FINDINGS.md). |
| R6 | Plugin versions managed, never floating | partially-satisfied | Surefire in `keycloak-extensions` resolved to Maven's default 3.2.5 against 3.5.3 everywhere else. |
| R7 | The build enforces its own structure | partially-satisfied | `requireUpperBoundDeps` absent; `spring-modulith-*` named by §4 but missing from the banned list — [F-019](../FINDINGS.md). |

**The header note was right and understated.** It said `CLAUDE.md` claims DGS 10.0.1 where
CONVENTIONS.md pins 10.5.0. `CLAUDE.md` was also describing an entire Spring Modulith + PostgreSQL
event-publication runtime that has never existed in this tree. Both corrected.

### What this cost, and what it bought

Seven requirements, six of them not fully satisfied, in the one spec every other spec depends on —
and the whole thing was previously 0/6 because **nothing checked it**. Adding one enforcer rule
found six version downgrades including a reactor two minor versions behind the Spring compiled
against it. Reading one class file found a module a full Java release behind.

None of it was failing. That is the point: a build is the one place where being wrong and being
green are entirely compatible.

### Evidence

- `BuildTopologyTest` — 14 cases across R1, R3, R4, R5, R6, R7. Mutation-verified: reverting
  `keycloak-extensions` to Java 17 fails `everyModuleCompilesToJava21` and nothing else.
- `EnforcerRefusalTest` — 1 case, forks a real `mvn validate`. Mutation-verified as above.
- Full suite **570 green** (555 before), reactor **BUILD SUCCESS**.
- `TestLayerLintTest` caught `EnforcerRefusalTest` mislabelled: it references nothing heavy and read
  as L1, because layer inference cannot see across a `ProcessBuilder` fork. `ProcessBuilder` is now
  an L4 marker — the lint was right, and is now right for one more reason.
