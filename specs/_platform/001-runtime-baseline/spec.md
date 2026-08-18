# ET-PLT-001 · Runtime baseline — the reactive contract, the clock, module boundaries

> **Conformance** · PDI Phase 1 project setup

## 1. Capability

The event ticketing platform is three Spring Boot services, one API gateway and one
federated graph, and every one of them is non-blocking from the router to the driver.
That is not an aesthetic choice. The platform's defining load is the on-sale minute: a
headline event goes live and five thousand reservation attempts arrive against one ticket
tier inside sixty seconds (D-16). A servlet container answers that by allocating a thread
per request and running out; a reactive one answers it by multiplexing them onto a
handful of event-loop threads. The moment one of those threads makes a blocking call,
every concurrent request that thread is carrying stalls with it — which is why "return
`Mono`" is a contract in this platform rather than a preference.

This spec stands up the runtime everything else assumes: the dependency baseline shared
by six Maven modules, the reactive contract and its one legitimate escape hatch, the
single application clock that every deadline in the platform is measured against, the
package boundaries that keep a service's internals internal, and the division of labour
between the API gateway and the subgraphs behind it.

It delivers no domain behaviour. Its success criterion is that each service starts,
serves a GraphQL query without a thread ever blocking, and reads every timestamp from an
injected clock a test can freeze.

## 2. Design decisions

**WebFlux only. `spring-boot-starter-web` is not on any classpath.** Spring Boot resolves
a servlet container in preference to Netty whenever both are present, so a single
transitive `-starter-web` silently converts a reactive service into a blocking one that
still compiles, still passes its tests, and falls over at on-sale. The dependency is
banned rather than discouraged, and the ban is a lint rule.

**Virtual threads are not the escape hatch.** Java 21 has them and they are genuinely
good, but adopting them alongside Reactor means two concurrency models in one codebase
and a per-method argument about which one applies. The platform commits to Reactor. Where
a third-party SDK is genuinely blocking — the Keycloak Admin client, an S3 presigner, a
provider SDK with no reactive binding — it is wrapped **once**, in a class under
`infrastructure/`, on `Schedulers.boundedElastic()`, and every caller sees a `Mono`. The
blocking is contained to one file per integration instead of leaking into services.

```java
// infrastructure/keycloak/KeycloakAdminAdapter.java — the only shape blocking may take
public Mono<String> createUser(NewUser user) {
    return Mono.fromCallable(() -> admin.realm(realm).users().create(user.toRep()))
               .subscribeOn(Schedulers.boundedElastic())
               .map(this::extractId);
}
```

**One application `Clock` bean at `Africa/Lusaka`.** Reservation TTLs, invitation expiry,
OTP validity, sales windows, settlement windows and payout eligibility are all
time-dependent, and every one of them has a boundary the platform gets wrong at least
once: what happens at exactly ten minutes, at exactly seven days, when a sales window
closes at midnight. A single injected bean lets a test freeze or advance time globally
rather than threading a clock through two hundred classes — and the thirty-ninth
injection site is the one that gets forgotten. **No production code calls `Instant.now()`,
`LocalDateTime.now()` or `System.currentTimeMillis()`.**

**Module boundaries are a design rule, held by review.** Each service's top-level packages
are its modules, and each module's `repository` and `service.impl` packages are internal to
it. Cross-module calls go through the exposing module's API package or through a published
event — never a direct `Impl` reference.

Nothing enforces this at build time. That is a deliberate trade: the platform carries no
framework whose only job is to check package direction, and in exchange the rule holds
exactly as well as the people applying it. It is written down here, in §4, as the module
table, so that "which package may call which" is a question with a published answer rather
than an argument in review.

**`shared-library` carries contracts, never business logic.** It holds the JWT and role
converters, the `DomainRefusal` base type and error registry enum, the shared GraphQL SDL (`@auth` and `Role`), and pure utilities. It holds no
service, no repository and no domain model. The moment a domain type lands there, two
services share a definition neither owns, and the next schema change needs a coordinated
release of both.

**The API gateway shapes traffic; it does not authorize.** It terminates CORS, applies
Redis-backed rate limits, opens circuit breakers and forwards the `Authorization` header
untouched. Every subgraph independently validates the JWT against Keycloak's JWKS. A
subgraph that trusts a header because the gateway forwarded it is a subgraph that is
wide open the day someone reaches it directly — and in a Docker network, someone can.

**Three services, fixed ports, one router.** catalog `:8085`, booking `:8082`, identity
`:8083`, gateway `:8080`, router `:4000`. Adding a fourth service is a platform decision
with a written argument, not a consequence of a feature growing awkward.

**Every host, credential and endpoint comes from the environment.** No literal
`localhost:8084`, no client secret, no connection string in any `application.yml` that is
committed. The same artifact runs in development and production, and the difference is
entirely environment.

**Rejected alternatives**

- *Virtual threads with a blocking stack, dropping Reactor.* A defensible platform in the abstract, but it discards the reactive Mongo driver, the reactive Redis client, the WebFlux gateway and DGS's reactive integration — a rewrite priced as a simplification.
- *Allowing `.block()` behind a "just this once" review exception.* Every blocking call in a reactive codebase was once just this once. The rule is only enforceable as an absolute.
- *A `Clock` injected per component rather than one bean.* Two hundred injection sites, of which the ones that matter are the ones nobody remembered.
- *Authorization at the gateway only.* Makes the gateway a single point of both failure and compromise, and leaves subgraphs unprotected against anything inside the network.
- *A per-service Spring Boot version.* Three services drifting across three Boot minors is three sets of transitive resolution to reason about at every upgrade.

## 3. Requirements

### ET-PLT-001-R1 · The stack is reactive end to end

THE SYSTEM SHALL expose every service over a non-blocking runtime, and IF a component
must call a blocking API, THEN THE SYSTEM SHALL confine that call to a single adapter on
a bounded elastic scheduler.

**Acceptance**
- [ ] No module's dependency tree contains `spring-boot-starter-web`, Tomcat, Jetty or Undertow — `mvn dependency:tree | grep -E 'starter-web|tomcat|jetty|undertow'` is empty
- [ ] Every service starts on Netty; the startup log line names `Netty` and never `Tomcat`
- [ ] No production class calls `.block()`, `.blockFirst()`, `.blockLast()` or `.toFuture().get()`
- [ ] No production class calls `.subscribe()` to trigger work whose result a caller needs
- [ ] Every blocking third-party call is inside a class under `infrastructure/`, wrapped in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`, and that class's public methods return `Mono` or `Flux`
- [ ] A search of production source finds no `.block()`, `.blockFirst()`, `.blockLast()`, `.toFuture().get()` or fire-and-forget `.subscribe()` **on any path that can execute on an event-loop thread**
- [ ] **Narrowed 2026-08-18 (approved).** `.block()` is permitted in exactly two constructs, and nowhere else: a class implementing `ApplicationRunner`/`CommandLineRunner`, and a method annotated `@EventListener(ApplicationReadyEvent.class)`. Both run on the main thread at boot, so neither stalls a Netty worker — which is the harm R1 exists to prevent. Six of the seven blocking calls found in reconciliation were already in these constructs; deleting them would have traded Spring Boot's native startup mechanism for a worse one and lost the ordering that seeders depend on. The line is drawn where BlockHound already draws it at runtime: on the event loop.
- [ ] A `BlockHound` agent is installed in the integration test profile and no test trips it

### ET-PLT-001-R2 · Versions come from one parent and one BOM set

THE SYSTEM SHALL manage every dependency version through the Spring Boot parent and the
three imported BOMs, and no module SHALL declare a version for a managed artifact.

**Acceptance**
- [ ] The parent POM and module graph are as [ET-PLT-012](../012-build-topology/) specifies — this spec consumes that topology and does not restate it
- [ ] `graphql-dgs-platform-dependencies:10.5.0` and `spring-cloud-azure-dependencies:5.19.0` are imported in `dependencyManagement`
- [ ] No `<version>` appears on any Spring, DGS, Azure or Spring Cloud artifact in any module
- [ ] No service resolves `spring-modulith-*`, `spring-boot-starter-jdbc` or the `postgresql` driver
- [ ] `<java.version>` is `21` in every module and no module compiles below it
- [ ] `mvn -q -DskipTests clean install` succeeds from `backend/`
- [ ] The three subgraphs resolve identical DGS and `graphql-java` versions — `mvn dependency:tree` shows no split

### ET-PLT-001-R3 · Time comes from one clock

THE SYSTEM SHALL read every timestamp from a single application `Clock` bean configured
to `Africa/Lusaka`.

**Acceptance**
- [ ] A `Clock` bean is declared once per service in a shared configuration class and injected wherever time is read
- [ ] No production code calls `Instant.now()`, `LocalDateTime.now()`, `LocalDate.now()`, `ZonedDateTime.now()` or `System.currentTimeMillis()`
- [ ] Every persisted timestamp is `java.time.Instant`; no document field is `LocalDateTime`, `java.util.Date` or an epoch `long`
- [ ] A test replaces the bean with `Clock.fixed(...)`, advances it, and observes a reservation expire without waiting ten minutes
- [ ] A search of production source finds no inline `Instant.now()`, `LocalDateTime.now()`, `LocalDate.now()`, `ZonedDateTime.now()` or `System.currentTimeMillis()`

### ET-PLT-001-R4 · Module boundaries are declared and respected

THE SYSTEM SHALL organise each service into the modules named in §4, and no module SHALL
reference another module's internal packages.

**Acceptance**
- [ ] Each service's top-level packages are exactly the modules listed in §4, and no other top-level package exists
- [ ] Each module's `repository` and `service.impl` packages are internal; no other module imports from them
- [ ] Cross-module communication inside a service is by published event or by an interface in the exposing module's API package — never a direct `Impl` reference
- [ ] Each module's `package-info.java` states what it exposes and which modules it may depend on, so the rule is readable at the package it governs
- [ ] The boundary is a review rule, not a build failure — this spec claims no mechanical enforcement, and a reviewer checking a cross-module import is the control

### ET-PLT-001-R5 · The shared library carries contracts, never business logic

THE SYSTEM SHALL restrict `shared-library` to cross-cutting contracts, and IF a type is
owned by one service's domain, THEN THE SYSTEM SHALL keep it in that service.

**Acceptance**
- [ ] `shared-library` contains the JWT/role converters, `DomainRefusal`, the error-code enum, `graphql/auth.graphqls`, and pure utilities — and nothing else
- [ ] `shared-library` declares no `@Document`, no `@Repository`, no `@Service` and no `@DgsComponent`
- [ ] `shared-library` has no dependency on any of the three services; the three services each depend on it
- [ ] A type used by exactly one service is not in `shared-library`

### ET-PLT-001-R6 · Configuration carries no secret and no host literal

THE SYSTEM SHALL source every credential, host and external endpoint from the
environment, and no committed configuration file SHALL contain either.

**Acceptance**
- [ ] No committed `application*.yml` contains a password, client secret, API token or connection string — every one is `${VAR}` or `${VAR:default-for-dev-only}`
- [ ] Keycloak's issuer URI, MongoDB's URI, Redis's host, the Azure Service Bus connection string and the PawaPay token are all environment-sourced in every profile
- [ ] The same built JAR runs in development and production with no rebuild
- [ ] A service fails fast at startup with a named message when a required variable is absent, rather than defaulting to a wrong endpoint
- [ ] `docker-resources/` is the only place infrastructure endpoints are enumerated

### ET-PLT-001-R7 · The gateway shapes traffic; each subgraph authorizes

THE SYSTEM SHALL apply rate limiting, CORS and circuit breaking at the gateway, and each
service SHALL independently validate every JWT it receives.

**Acceptance**
- [ ] The gateway uses `spring-cloud-starter-gateway-server-webflux` and configures under `spring.cloud.gateway.server.webflux.*`
- [ ] The gateway forwards `Authorization` untouched and adds no trust-bearing header of its own
- [ ] Each service declares a `ReactiveJwtDecoder` built from the Keycloak issuer URI and validates signature, issuer, audience and expiry
- [ ] A request carrying a forged JWT is rejected by the subgraph when sent to it directly, bypassing the gateway
- [ ] The gateway applies a Redis-backed `RequestRateLimiter` keyed on the authenticated subject where present and the client IP otherwise
- [ ] A circuit breaker on the Apollo Router route returns a structured fallback rather than a stack trace

## 4. Model

This spec defines no documents, events or GraphQL operations. Its model is topology and
configuration.

### Service topology

| Component | Port | Subgraph | Owns |
|---|---|---|---|
| `api-gateway` | 8080 | — | routing, CORS, rate limits, circuit breakers |
| Apollo Router | 4000 | — | supergraph composition and query planning |
| `booking-service` | 8082 | `booking` | tickets, reservations, payments, escrow, ledger, payouts |
| `identity-service` | 8083 | `identity` | users, organizations, teams, permissions, notifications |
| Keycloak | 8084 | — | authentication, realm roles, groups |
| `catalog-service` | 8085 | `catalog` | events, tiers, locations, categories |
| `shared-library` | — | — | contracts only |
| `keycloak-extensions` | — | — | SPI JAR deployed into Keycloak |

### Application modules per service

Declared with `package-info.java` under each top-level package.

| Service | Modules | Internal to every module |
|---|---|---|
| catalog | `event`, `tier`, `location`, `category`, `approval`, `reference` | `repository`, `service.impl` |
| booking | `reservation`, `ticket`, `payment`, `escrow`, `ledger`, `commission`, `payout`, `refund`, `reconciliation` | `repository`, `service.impl` |
| identity | `user`, `organization`, `team`, `permission`, `document`, `notification`, `audit` | `repository`, `service.impl` |

```java
/**
 * Payment — payment intents, the provider port, the PawaPay adapter.
 *
 * Exposes:      PaymentService, and the events in this package
 * May depend on: reservation, ledger, shared
 * Internal:     repository, service.impl — no other module imports from these
 */
package com.pml.booking.payment;
```

The header is the contract. There is no annotation and no verifying test: the boundary is
kept by the reviewer who reads it, which is why it is stated at the package it governs
rather than only in this table.

### Platform beans

Declared once per service in `config/PlatformConfig.java`.

| Bean | Type | Value | Consumed by |
|---|---|---|---|
| `platformClock` | `Clock` | `Clock.system(ZoneId.of("Africa/Lusaka"))` | everything that reads time |
| `reactiveJwtDecoder` | `ReactiveJwtDecoder` | `ReactiveJwtDecoders.fromIssuerLocation(issuerUri)` | ET-PLT-007 |
| `blockingScheduler` | `Scheduler` | `Schedulers.newBoundedElastic(…)`, named per service | `infrastructure/` adapters only |
| `reactiveMongoTransactionManager` | `ReactiveMongoTransactionManager` | the `ticketing` database | ET-PLT-002 |

```java
@Bean Clock platformClock() {
    return Clock.system(ZoneId.of("Africa/Lusaka"));
}
```

### Required environment variables

| Variable | Consumed by | Notes |
|---|---|---|
| `KEYCLOAK_URL`, `KEYCLOAK_REALM` | all | realm is `event-ticketing` |
| `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` | all | per-service confidential client |
| `MONGODB_URI` | all three services | must name a replica set (ET-PLT-002) |
| `REDIS_HOST`, `REDIS_PORT` | all | |
| `AZURE_SERVICEBUS_CONNECTION_STRING` | all three services | |
| `PAWAPAY_API_URL`, `PAWAPAY_API_TOKEN` | booking | ET-PAY-001 |
| `OTP_SERVICE_URL`, `OTP_CLIENT_ID`, `OTP_CLIENT_SECRET` | keycloak-extensions | ET-IDN-001 |
| `APOLLO_ROUTER_URL` | gateway | |

Absence of any variable with no dev default is a startup failure carrying the variable's
name, never a silent fallback.

### Dependency baseline

| Concern | Coordinate | Version source |
|---|---|---|
| parent and reactor | `com.pml:backend` | [ET-PLT-012](../012-build-topology/) |
| GraphQL | `graphql-dgs-spring-graphql-starter`, `graphql-dgs-extended-scalars` | `graphql-dgs-platform-dependencies:10.5.0` |
| bus | `spring-cloud-azure-stream-binder-servicebus` | `spring-cloud-azure-dependencies:5.19.0` |
| data | `spring-boot-starter-data-mongodb-reactive`, `spring-boot-starter-data-redis-reactive` | Boot parent |
| web | `spring-boot-starter-webflux` | Boot parent |
| security | `spring-boot-starter-oauth2-resource-server` | Boot parent |
| gateway | `spring-cloud-starter-gateway-server-webflux` | Spring Cloud BOM |

## 5. Tasks

- [ ] **T1 · Confirm the ET-PLT-012 build topology carries this spec's dependency baseline**
  - requirements: R2
  - files: `backend/pom.xml`, `backend/*/pom.xml`
  - verify: `mvn -q -f backend -DskipTests clean install` succeeds and every §4 baseline coordinate resolves from a managed version
  - parallel-safe: no — every pom
  - depends: — (ET-PLT-012 is a blocker of this spec, not a task of it)

- [ ] **T2 · Purge the servlet stack; add the BlockHound test agent**
  - requirements: R1
  - files: `backend/*/pom.xml`, `backend/*/src/test/resources/`
  - verify: `mvn dependency:tree | grep -E 'starter-web|tomcat'` is empty; a search of production source finds no `.block()`, `.blockFirst()`, `.blockLast()`, `.toFuture().get()` or fire-and-forget `.subscribe()`
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T3 · `PlatformConfig` with the `Clock` bean; remove every inline `now()`**
  - requirements: R3
  - files: `backend/*/src/main/java/com/pml/*/config/PlatformConfig.java`
  - verify: a search of production source finds no inline `Instant.now()`, `LocalDateTime.now()`, `LocalDate.now()`, `ZonedDateTime.now()` or `System.currentTimeMillis()`
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T4 · Declare each module's boundary in its `package-info.java`**
  - requirements: R4
  - files: `backend/*/src/main/java/com/pml/*/**/package-info.java`
  - verify: every top-level package in §4 has a header naming what it exposes, what it may depend on, and what is internal; no module imports another's `repository` or `service.impl`
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T5 · Reduce `shared-library` to contracts**
  - requirements: R5
  - files: `backend/shared-library/src/main/java/com/pml/shared/`
  - verify: `shared-library` declares no `@Document`/`@Repository`/`@Service`/`@DgsComponent`
  - parallel-safe: no — every service imports it
  - depends: T1

- [ ] **T6 · Externalise every credential and endpoint; fail fast on absence**
  - requirements: R6
  - files: `backend/*/src/main/resources/application*.yml`
  - verify: a grep for secrets across committed yml is empty; a service starts with an unset required variable and names it
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T7 · Gateway: WebFlux namespace, rate limiter, circuit breaker, header pass-through**
  - requirements: R7
  - files: `backend/api-gateway/src/main/resources/application.yml`, `.../config/`
  - verify: a forged JWT sent directly to `:8082/graphql` is rejected
  - parallel-safe: yes
  - depends: T1

- [ ] **T8 · Per-service `ReactiveJwtDecoder` validating signature, issuer, audience, expiry**
  - requirements: R7
  - files: `backend/*/src/main/java/com/pml/*/config/security/`
  - verify: `mvn -q -f backend test -Dgroups=ET-PLT-001`
  - parallel-safe: yes — one service per agent
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| MongoDB replica set, collections, indexes, money and time types in storage | [ET-PLT-002](../002-persistence-baseline/) |
| Event publication, the outbox, topics, idempotent consumers | [ET-PLT-003](../003-event-contract/) |
| Subgraph ownership, keys, `@tag` contracts, composition | [ET-PLT-004](../004-federation-contract/) |
| Error codes and typed handlers | [ET-PLT-005](../005-error-contract/) |
| Test layers, Testcontainers, WireMock | [ET-PLT-006](../006-test-harness/) |
| Realm configuration, roles, `@auth`, tenant scoping, idempotency keys | [ET-PLT-007](../007-security-and-authorization/) |
| Metrics, tracing, correlation IDs, SLOs | [ET-ADM-005](../../admin/005-observability-and-health/) |

Deliberately never in scope: **virtual threads as a concurrency model** (two models in one
codebase), and **a fourth backend service** (a platform decision needing its own argument,
not a consequence of a feature growing awkward).
