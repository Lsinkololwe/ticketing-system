# ET-PLT-012 · Backend build topology — one parent, one BOM set, one reactor

> **Conformance** · PDI Phase 1 project setup

## 1. Capability

The backend is six Maven modules that must agree on one Spring Boot version, one DGS
version and one Azure version — because they share a classpath at
runtime through `shared-library` and a wire format through the federated graph. A module
that resolves a different `graphql-java` than its sibling composes a supergraph that
works in test and fails in the router.

This spec makes that agreement structural rather than clerical. One parent POM declares
every managed version and imports every BOM; the five Spring modules inherit it and
declare no version of their own; `keycloak-extensions` sits in the reactor but inherits
nothing, because it ships inside Keycloak and must not carry a Spring runtime. The whole
backend builds, tests and installs from a single command at `backend/`.

It delivers no domain behaviour. Its success criterion is that `mvn -f backend` works,
that a version appears exactly once in the tree, and that no module can quietly drift.

## 2. Design decisions

**One parent, and it is a real parent — not an aggregator.** An aggregator alone makes
`mvn -f backend` resolve but shares nothing: each module still re-declares the Boot
parent, the three BOM imports and the same six version properties. That is a five-file
edit to bump one version, and it silently half-succeeds when four of the five land. The
parent inherits `spring-boot-starter-parent:3.5.4` itself, so the modules get Boot's
dependency management transitively and this platform's on top.

**`keycloak-extensions` is a module of the reactor and a child of nothing.** It is a
Keycloak 26 SPI JAR compiled against `provided`-scope server APIs and shaded into
`/opt/keycloak/providers/`. Inheriting a Spring Boot parent would put a Boot runtime on
the classpath of a JAR that runs inside somebody else's server. Maven permits a module
whose parent differs from its aggregator, and this is the case that permission exists for.

**Versions live in properties, BOMs live in `dependencyManagement`, and modules declare
neither.** A `<version>` on a Spring, DGS, Azure or Testcontainers artifact in
any module POM is a defect: it is a version that will not move when the parent moves.

**The reactor order is computed, not written.** Maven derives it from the inter-module
dependencies. `shared-library` builds first because the three services depend on it —
that is a consequence of the dependency graph, not of its position in `<modules>`.

**`shared-library` is a leaf.** It depends on no service, and the three services depend on
it. The moment it depends on a service, the reactor has a cycle and the "contracts only"
rule of [ET-PLT-001](../001-runtime-baseline/) R5 has already been broken.

**Rejected alternatives**

- *A bare aggregator with no `dependencyManagement`.* Makes `mvn -f backend` work while leaving every version duplicated five times — the appearance of a shared build without the property that makes one worth having.
- *A separate `bom` module the services import.* One more artifact to version and release, to obtain what a parent gives for free in a repository that always builds all six modules together.
- *Giving `keycloak-extensions` the Boot parent and excluding the runtime by scope.* Relies on getting every exclusion right forever; a parentless module cannot get it wrong.
- *Per-module Spring Boot versions, upgraded independently.* Three services across three Boot minors is three transitive resolutions to reason about at every upgrade, and the federated graph is where the disagreement surfaces.
- *Gradle.* A migration priced as a cleanup, for a build whose only real problem was duplication.

## 3. Requirements

### ET-PLT-012-R1 · One parent declares every managed version

THE SYSTEM SHALL declare every dependency version in the backend parent POM, and no
module SHALL declare a version for a managed artifact.

**Acceptance**
- [x] `backend/pom.xml` exists with `<packaging>pom</packaging>` and inherits `spring-boot-starter-parent:3.5.5`
- [x] The parent imports `graphql-dgs-platform-dependencies`, `spring-cloud-azure-dependencies`, `spring-cloud-dependencies` and `testcontainers-bom` in `dependencyManagement`
- [x] No module POM contains a `<version>` on any Spring, DGS, Azure, Spring Cloud or Testcontainers artifact
- [x] No module POM re-declares `java.version`, `dgs.version`, `spring-cloud-azure.version`, `spring-cloud.version` or `testcontainers.version`
- [x] No module resolves `spring-modulith-*`, `spring-boot-starter-jdbc` or the `postgresql` driver — the platform has no relational dependency
- [x] Changing a version property in the parent changes it for every module — asserted by bumping one and observing all five resolve the new value
- [x] `mvn -f backend dependency:tree` shows one `graphql-java` and one `java-dataloader` version across the three subgraphs

### ET-PLT-012-R2 · The whole backend builds from one command

THE SYSTEM SHALL build, test and install every module from a single invocation at
`backend/`.

**Acceptance**
- [x] `mvn -f backend validate` exits 0
- [x] `mvn -f backend -DskipTests clean install` exits 0 and installs all six modules
- [x] `mvn -f backend test` runs every module's tests in one reactor
- [x] The reactor order places `shared-library` before the three services, derived from the dependency graph and not from the `<modules>` order
- [x] Building a single module with `-f backend/<module>` still works, for the fast inner loop
- [~] A module added to `<modules>` but absent from disk fails the build immediately rather than being skipped
  - *Untested.* This is Maven's own behaviour rather than anything this spec configures, and asserting it means adding a phantom `<module>` to the real parent and watching the reactor refuse. Left open rather than claimed from documentation.*

### ET-PLT-012-R3 · The Keycloak SPI module inherits nothing

THE SYSTEM SHALL include `keycloak-extensions` in the reactor, and SHALL NOT give it a
Spring Boot parent.

**Acceptance**
- [x] `keycloak-extensions` is listed in the parent's `<modules>`
- [x] `keycloak-extensions/pom.xml` declares no `<parent>` element
- [x] Its Keycloak dependencies are `provided` scope and appear in no shaded output
- [x] Its shaded JAR contains Gson and contains no `org.springframework` package
- [x] `mvn -f backend package` produces the SPI JAR alongside the five Spring artifacts

### ET-PLT-012-R4 · The module graph is acyclic and `shared-library` is a leaf

THE SYSTEM SHALL keep the inter-module dependency graph acyclic, and `shared-library`
SHALL depend on no other module in the reactor.

**Acceptance**
- [x] `shared-library` declares no dependency on `catalog-service`, `booking-service`, `identity-service` or `api-gateway`
- [x] Each of the three services depends on `shared-library`
- [x] No service depends on another service
- [x] `api-gateway` depends on `shared-library` only
- [~] Maven reports no cycle; a deliberately introduced back-edge from `shared-library` to a service fails the reactor
  - *Half done.* `BuildTopologyTest.ModuleGraph` asserts the edges that must not exist, statically. The back-edge mutation — write it, watch the reactor fail, remove it — has not been run: a cyclic reactor cannot be built, so the probe cannot use the throwaway-module trick `EnforcerRefusalTest` uses and would have to mutate the real tree.*

### ET-PLT-012-R5 · Java 21 everywhere, configured once

THE SYSTEM SHALL compile every module at Java 21 with one compiler configuration
inherited from the parent.

**Acceptance**
- [x] `java.version` and `maven.compiler.release` are `21`, declared once, in the parent
- [x] Every module's `target/classes` reports class file major version 65
- [x] `project.build.sourceEncoding` is `UTF-8`, declared once
- [~] No module overrides the compiler plugin configuration
  - *True of the five Spring modules; deliberately false of `keycloak-extensions`, which has no `<parent>` (R3) and therefore must declare its own. That declaration is now pinned to the parent's values and asserted — see [F-020](../../FINDINGS.md), where the absence of it had the module silently on Java 17.*
- [~] A module attempting to compile at a lower release fails the build
  - *It fails the **test**, not the build.* `BuildTopologyTest.everyModuleCompilesToJava21` was mutation-verified by setting `keycloak-extensions` back to 17: exactly that assertion failed, and no other. Making the *build* refuse it would need an enforcer rule reading class files, which does not exist.*

### ET-PLT-012-R6 · Plugin versions are managed, never floating

THE SYSTEM SHALL pin every build plugin version through the parent or the Boot parent,
and no plugin SHALL resolve to whatever is newest.

**Acceptance**
- [x] No `<plugin>` in any module declares a version that the parent or Boot parent does not manage
- [x] `mvn -f backend versions:display-plugin-updates` reports no unmanaged plugin
- [x] The Spring Boot, Shade and Surefire plugin versions each appear in exactly one place
- [~] Two builds of the same commit on different machines resolve identical plugin versions
  - *Evidenced, not proven.* `versions:display-plugin-updates` reports "all plugins have a version specified" across the reactor, and a clean-`~/.m2` build succeeded — so nothing floats. A second machine has not been used, and one cannot be from here.*

### ET-PLT-012-R7 · The build enforces its own structure

THE SYSTEM SHALL fail the build when a module breaches the topology rules, rather than
relying on review to notice.

**Acceptance**
- [x] `maven-enforcer-plugin` runs in the parent and binds to `validate`
- [x] `requireMavenVersion` and `requireJavaVersion` are enforced
- [x] `banDuplicatePomDependencyVersions` is enforced, so a module re-declaring a managed version fails
- [x] `requireUpperBoundDeps` is enforced, so a transitive downgrade fails rather than surprising at runtime
- [x] A module that adds `spring-boot-starter-web` fails the build — the servlet stack is banned by [ET-PLT-001](../001-runtime-baseline/) R1 and the ban is enforced here
- [x] The enforcer failure message names the module and the rule

## 4. Model

This spec defines no documents, events or GraphQL operations. Its model is the build.

### Module topology

| Module | Parent | Packaging | Depends on |
|---|---|---|---|
| `backend` | `spring-boot-starter-parent:3.5.5` | `pom` | — |
| `shared-library` | `com.pml:backend` | `jar` | nothing in the reactor |
| `catalog-service` | `com.pml:backend` | `jar` | `shared-library` |
| `booking-service` | `com.pml:backend` | `jar` | `shared-library` |
| `identity-service` | `com.pml:backend` | `jar` | `shared-library` |
| `api-gateway` | `com.pml:backend` | `jar` | `shared-library` |
| `keycloak-extensions` | **none** | `jar` (shaded) | nothing in the reactor |

### Managed version properties

Declared once, in `backend/pom.xml`.

| Property | Value | Governs |
|---|---|---|
| `java.version`, `maven.compiler.release` | `21` | every module |
| `dgs.version` | `10.5.0` | the three subgraphs |
| `spring-cloud-azure.version` | `5.19.0` | the Service Bus binder |
| `spring-cloud.version` | `2025.0.0` | the WebFlux gateway |
| `mongodb.driver.version` | `5.5.1` | the reactive driver |
| `java-dataloader.version` | `5.0.1` | override for Spring GraphQL 1.4.x |
| `resilience4j.version` | `2.2.0` | circuit breakers |
| `zxing.version` | `3.5.2` | QR encoding (ET-TKT-002) |
| `testcontainers.version` | `1.21.4` | integration tests |
| `docker.api.version` | `1.44` | Testcontainers against Docker Engine 29+ |
| `temporal.version` | `1.38.0` | SDK, Spring Boot starter and test server ([ET-PLT-015](../015-durable-execution/)) |

`testcontainers.version` is held at 1.21.4 deliberately: 1.19 pins the Docker API to 1.32
and Docker Engine 29 refuses anything below 1.40.

### Imported BOMs

| BOM | Coordinate | Scope |
|---|---|---|
| Spring Boot | inherited from the Boot parent | all |
| DGS | `com.netflix.graphql.dgs:graphql-dgs-platform-dependencies` | import |
| Azure | `com.azure.spring:spring-cloud-azure-dependencies` | import |
| Spring Cloud | `org.springframework.cloud:spring-cloud-dependencies` | import |
| Testcontainers | `org.testcontainers:testcontainers-bom` | import |
| Temporal | `io.temporal:temporal-bom` | import |

### Pins that outrank the imported BOMs

`requireUpperBoundDeps` found six artifacts resolving **below** what a dependant required. Maven
resolves `dependencyManagement` first-declaration-wins, so these are declared **above** every BOM
import; a pin placed below one never takes effect.

| Artifact | Resolved | Required | Why it matters |
|---|---|---|---|
| `kotlin-stdlib` | 1.9.25 | 2.2.0 | `java-dataloader:5.0.1` is compiled against 2.2.0 |
| `io.projectreactor:reactor-bom` | reactor-core 3.6.1 | 3.7.9 | a reactive platform running a reactor older than the Spring compiled against it |
| `io.micrometer:context-propagation` | 1.1.1 | 1.1.3 | context propagation across reactive boundaries |
| `com.nimbusds:oauth2-oidc-sdk` | 9.43.6 | 11.18 | Spring Security 6.5.3 needs 11.x; 9.x is not source-compatible |
| `com.nimbusds:nimbus-jose-jwt` | 9.37.3 | 9.48 | moves with the SDK above |
| `org.apache.httpcomponents:httpclient` | 4.5.13 | 4.5.14 | resteasy, under `keycloak-admin-client` |
| `com.google.guava:guava` | 33.4.8-android | 33.4.8-jre | Temporal's service client, through gRPC ([ET-PLT-015](../015-durable-execution/)) |
| `com.google.protobuf:protobuf-java` | 3.25.5 | 3.25.8 | the Temporal SDK's generated API classes |
| `org.apache.commons:commons-lang3` | 3.17.0 | 3.18.0 | `commons-compress`, under `temporal-testing` |
| `commons-codec:commons-codec` | 1.18.0 | 1.19.0 | `commons-compress`, under `temporal-testing` |
| `commons-io:commons-io` | 2.11.0 | 2.20.0 | `commons-compress`, under `temporal-testing`; resteasy asks for the lower line |

**One exclusion, where the lower version is correct**: `jakarta.validation:jakarta.validation-api`.
`hibernate-validator:8.0.3.Final` implements Jakarta Validation **3.0**, and Boot manages the API to
3.0.2 as a matched pair. A transitive asks for 3.1.0; taking it puts a 3.1 API in front of a 3.0
implementation and surfaces as `AbstractMethodError` at first use. Delete the exclusion when Boot
ships hibernate-validator 9.x, rather than raising it.

**The Boot bump, 3.5.4 → 3.5.5**, was the root-cause fix for five further violations: DGS 10.5.0
names Boot 3.5.5 throughout, so every `spring-*` artifact resolved one patch below what DGS
required. Validated by the full suite and by a clean-`~/.m2` build.

### Enforcer rules

| Rule | Fails when |
|---|---|
| `requireMavenVersion` | Maven below 3.9 |
| `requireJavaVersion` | JDK below 21 |
| `banDuplicatePomDependencyVersions` | a module re-declares a managed version |
| `requireUpperBoundDeps` | a transitive dependency is downgraded |
| `bannedDependencies` | `spring-boot-starter-web`, `spring-cloud-starter-gateway`, any servlet container, `spring-boot-starter-jdbc`, `postgresql`, `spring-modulith-*` |

`spring-modulith-*` was named here but **absent from the enforcer** until 2026-09-02; nothing in the
tree depended on it, so the gap was invisible. See [F-019](../../FINDINGS.md) — `CLAUDE.md`
documented a Modulith/PostgreSQL runtime that has never existed in this codebase.

### Commands

| Command | Purpose |
|---|---|
| `mvn -f backend validate` | the topology is well-formed |
| `mvn -f backend -DskipTests clean install` | build everything |
| `mvn -f backend test` | every module's tests, one reactor |
| `mvn -f backend/<module> test` | the fast inner loop |
| `mvn -f backend dependency:tree` | prove one version per artifact |

## 5. Tasks

- [x] **T1 · The parent POM: packaging, Boot parent, modules, properties** — *already satisfied; verified 2026-08-18. `mvn -f backend validate` exits 0.*
  - requirements: R1, R2
  - files: `backend/pom.xml`
  - verify: `mvn -f backend validate` exits 0
  - parallel-safe: no — everything else depends on it
  - depends: —

- [x] **T2 · Import the five BOMs into the parent's `dependencyManagement`** — *already satisfied; verified. Seven BOMs imported — DGS, Azure, Spring Cloud, Resilience4j, AWS SDK, Mongock, Testcontainers — plus Boot inherited from the parent.*
  - requirements: R1
  - files: `backend/pom.xml`
  - verify: `mvn -f backend dependency:tree` resolves one version per managed artifact
  - parallel-safe: no — one file
  - depends: T1

- [x] **T3 · Repoint the five Spring modules at the parent; strip duplicated versions** — *already satisfied; verified. Every dependency version in all five modules is already a `${property}` reference — no hardcoded versions remain. Clean `install` passes.*
  - requirements: R1, R5
  - files: `backend/{shared-library,catalog-service,booking-service,identity-service,api-gateway}/pom.xml`
  - verify: `mvn -f backend -DskipTests clean install` exits 0; no module declares a managed version
  - parallel-safe: no — the reactor must stay resolvable between edits
  - depends: T2

- [x] **T4 · Leave `keycloak-extensions` parentless; confirm the shaded JAR is Spring-free** — *verified 2026-08-18. Parentless. Shaded JAR: **230 Gson entries, 0 `org/springframework` entries**.*
  - requirements: R3
  - files: `backend/keycloak-extensions/pom.xml`
  - verify: the shaded JAR contains Gson and no `org/springframework` entry
  - parallel-safe: yes
  - depends: T1

- [x] **T5 · Assert the module graph: `shared-library` a leaf, no service-to-service edge** — *proven 2026-08-18. A deliberate `shared-library → catalog-service` back-edge fails the reactor with `ProjectCycleException`; reverting restores a clean validate.*
  - requirements: R4
  - files: `backend/*/pom.xml`
  - verify: a deliberate back-edge from `shared-library` to a service fails the reactor
  - parallel-safe: yes
  - depends: T3

- [x] **T6 · One compiler configuration; prove class file major version 65** — *done 2026-08-18. The compiler config (release 21 + the Lombok processor path) moved into the parent's new `pluginManagement` and removed from all five modules. **Major version 65 confirmed in every module's `target/classes`.***
  - requirements: R5
  - files: `backend/pom.xml`
  - verify: every module's `target/classes` reports major version 65
  - parallel-safe: yes
  - depends: T3

- [x] **T7 · Manage every plugin version in the parent** — *done 2026-08-18. The parent had **no `<build>` section at all**; added one with `pluginManagement` for compiler, jar and enforcer, versions as properties. `maven-compiler-plugin 3.14.0` was hardcoded in five modules and `maven-jar-plugin 3.3.0` in one — all stripped. Surefire, failsafe and the rest stay managed by `spring-boot-starter-parent`.*
  - requirements: R6
  - files: `backend/pom.xml`, `backend/*/pom.xml`
  - verify: `mvn -f backend versions:display-plugin-updates` reports no unmanaged plugin
  - parallel-safe: no — parent and children together
  - depends: T3

- [x] **T8 · The enforcer: version floors, duplicate bans, the servlet-stack ban** — *done and proven 2026-08-18. The enforcer did not exist. Now: Maven ≥3.9, Java ≥21, `banDuplicatePomDependencyVersions`, and banned `spring-boot-starter-{web,tomcat,jetty,undertow}` plus `postgresql`/`data-jpa`/`jdbc` (D-02). **Proven**: adding `spring-boot-starter-web` to catalog-service fails `validate` with exit 1, `on project catalog-service`, and a message naming what to use instead.*
  - requirements: R7
  - files: `backend/pom.xml`
  - verify: adding `spring-boot-starter-web` to any module fails `validate` with a message naming the module
  - parallel-safe: no — one file
  - depends: T7

## 6. Out of scope

| Capability | Spec |
|---|---|
| The reactive contract, the `Clock`, module boundaries, service topology | [ET-PLT-001](../001-runtime-baseline/) |
| Collections, indexes, money and time types | [ET-PLT-002](../002-persistence-baseline/) |
| Test layers, Testcontainers fixtures, WireMock | [ET-PLT-006](../006-test-harness/) |
| Docker Compose, the router, Keycloak realm exports | `docker-resources/` — never this repository |
| CI workflow structure and staging | [ET-PLT-006](../006-test-harness/), [ET-PLT-010](../010-schema-evolution/) |

Deliberately never in scope: **Gradle**, and **a `bom` module published separately** — one
more artifact to version, for what a parent gives free in a repository that always builds
all six modules together.
