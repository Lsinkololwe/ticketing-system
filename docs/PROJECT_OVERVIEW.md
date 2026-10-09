# Project Overview — Event Ticketing System

> **Process mechanics (ROADMAP D-21).** Every multi-step, timed or cross-service process described below — sagas, `@Scheduled` sweeps, recovery jobs, Redis job locks, in-process event listeners — runs as a Temporal workflow or Schedule: see `specs/_platform/015-durable-execution/spec.md`, `specs/CONVENTIONS.md` §3 and §9, and `docs/architecture/DURABLE_EXECUTION.md`. Where this document and those disagree on how a process runs, they win.

> Last updated: 2026-07-09

## What It Is

A microservices-based event ticketing platform targeting Zambia and the wider African market. It covers the full lifecycle of event ticketing:

- **Event discovery** — browse and search events by category and location
- **Ticket purchasing** — paid via mobile money (MTN, Airtel, Zamtel) through PawaPay
- **Ticket validation** — QR code scanning at venues
- **Organizer management** — event creation, sales tracking, escrow, commissions, and payouts
- **Platform administration** — organizer/document approvals, analytics, refunds, audit

## High-Level Architecture

```
Clients (Admin Web · Organization Admin Web · Customer Web · Mobile)
        │
        ▼
API Gateway (Spring Cloud Gateway WebFlux, :8080)   ← rate limiting, circuit breaker, CORS
        │
        ▼
Apollo Router (:4000)                               ← GraphQL Federation 2 supergraph
        │
        ├── Catalog Service (:8085)  — Events, Locations, Categories, Pricing
        ├── Booking Service (:8082)  — Tickets, Payments, Escrow, Commissions, Payouts
        └── Identity Service (:8083) — Users, Organizers/Organizations, Roles, OTP
```

Supporting infrastructure: **Keycloak** (:8084) for OAuth2/OIDC, **MongoDB** (reactive, business data), **PostgreSQL** (Keycloak, and the self-hosted Temporal service outside development), **Temporal** (:7233, durable workflows and Schedules), **Redis** (sessions, OTP, rate limits), and **Azure Service Bus** (cross-service events).

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend runtime | Java 21, Spring Boot 3.5.x, Spring WebFlux (fully reactive) |
| GraphQL | Netflix DGS 10 with Federation 2, composed by Apollo Router |
| Data | Spring Data MongoDB Reactive, with a transactional outbox per service; no service connects to PostgreSQL |
| Events and processes | Transactional outbox in MongoDB, drained to Azure Service Bus for cross-service facts; Temporal workflows and Schedules for every multi-step, timed or cross-service process |
| Auth | Keycloak 26 with a custom Phone OTP authenticator (WhatsApp/SMS passwordless login); Better Auth on the frontend |
| Admin & Customer web | Next.js 16, React 19, Apollo Client 4, Nx monorepo |
| Mobile | Expo 54 / React Native, Apollo Client 4 |
| Payments | PawaPay (mobile money aggregator) |

## Repository Layout

```
ticketing-system/
├── backend/
│   ├── api-gateway/           # Spring Cloud Gateway (WebFlux)
│   ├── booking-service/       # Tickets, payments, escrow, payouts
│   ├── catalog-service/       # Events, locations, categories
│   ├── identity-service/      # Users, organizations, OTP endpoints, Keycloak sync
│   ├── keycloak-extensions/   # Custom Keycloak SPIs (Phone OTP, user-sync listener)
│   └── shared-library/        # Shared DTOs, JWT/role converters, utilities
├── frontend/
│   ├── web/                   # Nx monorepo
│   │   ├── apps/admin/              # Platform admin dashboard (:3030)
│   │   ├── apps/organization-admin/ # Organizer dashboard
│   │   ├── apps/ticketing/          # Customer-facing portal (:3001)
│   │   └── libs/shared/             # Shared GraphQL hooks, REST clients, components
│   └── mobile/                # Expo app
├── docs/                      # Architecture and design documentation
└── todos/                     # Working notes

# Docker infrastructure lives in a SEPARATE sibling repo:
../docker-resources/           # docker-compose, Apollo Router config, DB/Keycloak setup
```

## Key Architectural Decisions

1. **Reactive end to end** — business data and each service's outbox live in reactive MongoDB; no service holds a blocking relational connection. The build refuses Spring Modulith, JDBC and the PostgreSQL driver (ET-PLT-012 R7).

2. **Outbox plus workflows** — a fact for other services is staged in the service's `*_outbox` collection inside the business transaction and drained to Service Bus topics (`catalog-events`, `booking-events`, `identity-events`); a process within a service runs as a Temporal workflow (ET-PLT-015).

3. **GraphQL Federation 2** — each service owns its types (`@key`) and extends types owned by others; Apollo Router composes the supergraph. Schema workflow is strict: **backend schema → supergraph composition/GraphOS publish → frontend codegen**. Frontend TypeScript types are never hand-written for GraphQL.

4. **Passwordless mobile auth** — a custom Keycloak SPI authenticator sends 6-digit OTPs via WhatsApp (primary) or SMS (fallback). OTPs are generated and verified by the Identity Service and stored in Redis with a 5-minute TTL. Admins use username/password. Keycloak users sync to MongoDB via a custom event listener calling internal Identity Service endpoints.

5. **REST for file uploads, GraphQL for everything else** — document uploads (e.g. organizer business licenses) use presigned S3-style URLs via REST endpoints, avoiding GraphQL multipart pitfalls.

6. **Dashboard statistics via MongoDB aggregation pipelines** — stats services (`EventStatsService`, `TicketStatsService`, `UserStatsService`, `PayoutStatsService`) compute metrics server-side with `$match`-first pipelines, never client-side counting.

## Domain Model (Ownership by Service)

| Service | Owned Entities |
|---------|----------------|
| Catalog | Event, Location, Category, Pricing |
| Booking | Ticket, Payment, Escrow, Commission, Payout, Refund |
| Identity | User, Organizer/Organization, Role, Permission, Verification Documents |

User roles: `CUSTOMER` (browse/buy), `ORGANIZER` (create events, request payouts), `ADMIN` (approvals, full access), `INTERNAL_SERVICE` (service-to-service).

## Current State (as of this snapshot)

- Git history is young (7 commits); recent work focuses on **security hardening**, **Better Auth production configuration**, **login-flow refactoring**, and the **organization application flow**.
- A large uncommitted changeset touches security configs and `application.yml` across all four backend services, GraphQL schemas/resolvers in each subgraph, and most admin dashboard pages — consistent with the in-progress `SECURITY_REFACTORING_PLAN.md` at the repo root.
- The admin app covers analytics (revenue/users), approvals (organizers/events/documents), events (calendar/categories/locations), finance (escrow/payouts/refunds), transactions (payments/tickets/commissions), users, organizations, and system (API keys, audit).
- Deeper design docs live in `docs/` — notably `APOLLO_FEDERATION_GUIDE.md`, `FILE_UPLOAD_ARCHITECTURE.md`, `KEYCLOAK_PHONE_OTP_AUTHENTICATOR.md`, `ARCHITECTURE_REDESIGN_V3_COMPLETE.md`, and `PAYMENT_DATA_INTEGRITY_IMPLEMENTATION_PLAN.md`.

## Running It

```bash
# Infrastructure (from the sibling docker-resources repo)
cd ../docker-resources && docker compose up -d

# Apollo Router — GraphOS mode (recommended)
docker compose --profile ticketing up dev_ticketing_router_graphos

# Backend services (each service directory)
mvn spring-boot:run          # build: mvn clean package -DskipTests

# Web apps (Nx)
cd frontend/web && npx nx dev admin      # or: organization-admin, ticketing
npm run codegen                          # after any GraphQL schema change
```

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Apollo Router | 4000 |
| Booking / Identity / Keycloak / Catalog | 8082 / 8083 / 8084 / 8085 |
| Admin Web / Customer Web | 3030 / 3001 |
