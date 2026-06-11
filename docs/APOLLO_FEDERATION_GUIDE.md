# Apollo Federation Guide

## Event Ticketing System - GraphQL Federation Architecture

This guide provides comprehensive documentation for the Apollo Federation setup in the Event Ticketing System. It covers architecture, configuration, workflows, and best practices.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Understanding the Two Modes](#2-understanding-the-two-modes)
3. [Configuration Files](#3-configuration-files)
4. [How Supergraph Updates Work](#4-how-supergraph-updates-work)
5. [Local Development Guide](#5-local-development-guide)
6. [CI/CD Integration](#6-cicd-integration)
7. [Multiple Routers for Multiple Projects](#7-multiple-routers-for-multiple-projects)
8. [Subgraph URL Configuration](#8-subgraph-url-configuration)
9. [Troubleshooting](#9-troubleshooting)
10. [Best Practices](#10-best-practices)

---

## 1. Architecture Overview

### 1.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         APOLLO FEDERATION ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                         APOLLO GRAPHOS (CLOUD)                           │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐          │   │
│  │  │ Schema Registry │  │    Supergraph   │  │   Schema Checks │          │   │
│  │  │   (Source of    │  │   Composition   │  │   & Linting     │          │   │
│  │  │    Truth)       │  │                 │  │                 │          │   │
│  │  └────────┬────────┘  └────────┬────────┘  └─────────────────┘          │   │
│  │           │                    │                                         │   │
│  │           │    Subgraph        │   Supergraph                           │   │
│  │           │    Schemas         │   (Composed)                           │   │
│  │           │                    │                                         │   │
│  └───────────┼────────────────────┼─────────────────────────────────────────┘   │
│              │                    │                                              │
│              ▼                    ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                    APOLLO ROUTER (SELF-HOSTED)                          │    │
│  │                         Port 4000                                        │    │
│  │  ┌─────────────────────────────────────────────────────────────────┐   │    │
│  │  │ • Query planning and execution                                   │   │    │
│  │  │ • Header propagation (Authorization, x-correlation-id)          │   │    │
│  │  │ • Subgraph URL override for local development                   │   │    │
│  │  │ • Automatic Persisted Queries (APQ)                             │   │    │
│  │  └─────────────────────────────────────────────────────────────────┘   │    │
│  └───────────────────────────┬─────────────────────────────────────────────┘    │
│                              │                                                   │
│              ┌───────────────┼───────────────┐                                  │
│              │               │               │                                  │
│              ▼               ▼               ▼                                  │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐                       │
│  │   Identity    │  │   Catalog     │  │   Booking     │                       │
│  │   Subgraph    │  │   Subgraph    │  │   Subgraph    │                       │
│  │   :8083       │  │   :8085       │  │   :8082       │                       │
│  └───────────────┘  └───────────────┘  └───────────────┘                       │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Subgraph Ownership

| Subgraph | Port | Owned Entities |
|----------|------|----------------|
| **Identity** | 8083 | User, Organization, Organization, OrganizationMember, Role, Permission |
| **Catalog** | 8085 | Event, Location, EventCategory, Province, City, TicketTier, VirtualQueue |
| **Booking** | 8082 | Ticket, FinancialTransaction, EventEscrowAccount, RefundRequest, PromoCode |

### 1.3 Federation Version

The system uses **Federation 2.9**, the latest stable version with features including:
- `@key` directive for entity identification
- `@shareable` for shared field resolution
- `@external` and `@requires` for cross-service field dependencies
- `@provides` for query optimization hints

---

## 2. Understanding the Two Modes

### 2.1 GraphOS Managed Mode (Production)

**When to use**: Production, staging, team collaboration

In this mode, the Apollo Router fetches the supergraph schema from Apollo GraphOS:

```
┌──────────────┐      Poll every 30s      ┌──────────────┐
│ Apollo       │ ◄──────────────────────► │ Apollo       │
│ GraphOS      │      Supergraph          │ Router       │
│ (Cloud)      │      Updates             │ (Self-hosted)│
└──────────────┘                          └──────────────┘
```

**How it works**:
1. Router starts with `APOLLO_KEY` and `APOLLO_GRAPH_REF`
2. Router polls GraphOS Uplink every 30 seconds for schema updates
3. When you publish a new subgraph schema, GraphOS recomposes the supergraph
4. Router receives the new supergraph on next poll (within 30 seconds)
5. Router hot-swaps to the new schema with zero downtime

**Start command**:
```bash
docker compose up dev_ticketing_router_graphos
# Or with profile:
docker compose --profile ticketing up
```

### 2.2 Local Supergraph Mode (Development)

**When to use**: Offline development, schema experimentation, CI/CD validation

In this mode, the Apollo Router uses a locally composed supergraph file:

```
┌──────────────┐      Watch file         ┌──────────────┐
│ supergraph   │ ◄─────────────────────► │ Apollo       │
│ .graphql     │      Hot-reload         │ Router       │
│ (Local file) │      on change          │ (Self-hosted)│
└──────────────┘                          └──────────────┘
```

**How it works**:
1. You compose the supergraph locally using `rover supergraph compose`
2. Router starts with `--supergraph supergraph.graphql --hot-reload`
3. When you recompose the supergraph, router detects the file change
4. Router hot-reloads the new schema with zero downtime

**Start command**:
```bash
docker compose --profile ticketing-local up dev_ticketing_router_local
```

### 2.3 Comparison Table

| Aspect | GraphOS Mode | Local Mode |
|--------|-------------|------------|
| Supergraph source | Apollo GraphOS cloud | Local file |
| Update mechanism | Polls Uplink (30s) | Watches file |
| Requires internet | Yes | No |
| Schema registry | Yes (Apollo Studio) | No |
| Schema checks | Yes (on publish) | Manual only |
| Best for | Production, staging | Development, testing |
| Port (default) | 4000 | 4001 |

### 2.4 Important: No Fallback Between Modes

**You cannot configure both modes simultaneously as a fallback.** The router uses ONE source for the supergraph:
- Either GraphOS Uplink (managed)
- Or a local file

If you need resilience:
- Use **Graph Artifacts** (immutable, versioned supergraph packages)
- Or ensure GraphOS has high availability for your region

---

## 3. Configuration Files

### 3.1 File Locations

```
docker-resources/
├── apollo-router/
│   ├── router.yaml                    # Legacy (backward compatible)
│   ├── supergraph.yaml               # Legacy composition config
│   ├── supergraph.graphql            # Legacy composed supergraph
│   └── ticketing/                    # Ticketing-specific configs
│       ├── router-graphos.yaml       # GraphOS mode config
│       ├── router-local.yaml         # Local mode config
│       ├── supergraph.yaml           # Rover composition config
│       ├── supergraph.graphql        # Composed supergraph
│       └── compose-supergraph.sh     # Helper script
```

### 3.2 Router Configuration (router-graphos.yaml)

Key sections explained:

```yaml
# Where the router listens
supergraph:
  listen: 0.0.0.0:4000
  introspection: true  # Disable in production

# GraphOS polling configuration
uplink:
  poll_interval: 30s   # How often to check for schema updates
  timeout: 30s

# Header propagation - CRITICAL for authentication
headers:
  all:
    request:
      - propagate:
          named: authorization      # JWT token
      - propagate:
          named: x-correlation-id   # Distributed tracing

# Override subgraph URLs for local development
override_subgraph_url:
  identity: ${IDENTITY_SERVICE_URL:-http://host.docker.internal:8083/graphql}
  catalog: ${CATALOG_SERVICE_URL:-http://host.docker.internal:8085/graphql}
  booking: ${BOOKING_SERVICE_URL:-http://host.docker.internal:8082/graphql}
```

### 3.3 Supergraph Composition Config (supergraph.yaml)

```yaml
federation_version: =2.9.0

subgraphs:
  identity:
    routing_url: http://identity-service:8083/graphql  # Docker internal URL
    schema:
      file: ../../../ticketing-system/backend/identity-service/src/main/resources/graphql/schema.graphqls

  catalog:
    routing_url: http://catalog-service:8085/graphql
    schema:
      file: ../../../ticketing-system/backend/catalog-service/src/main/resources/graphql/schema.graphqls

  booking:
    routing_url: http://booking-service:8082/graphql
    schema:
      file: ../../../ticketing-system/backend/booking-service/src/main/resources/graphql/schema.graphqls
```

**Key distinction**:
- `routing_url`: Used at runtime to route requests (can be overridden in router.yaml)
- `schema.file`: Used at composition time to read the schema

---

## 4. How Supergraph Updates Work

### 4.1 GraphOS Mode Update Flow

```
Developer               GitHub Actions        Apollo GraphOS         Apollo Router
    │                        │                      │                      │
    │ 1. Push schema change  │                      │                      │
    │───────────────────────►│                      │                      │
    │                        │                      │                      │
    │                        │ 2. rover subgraph    │                      │
    │                        │    publish           │                      │
    │                        │─────────────────────►│                      │
    │                        │                      │                      │
    │                        │                      │ 3. Validate &        │
    │                        │                      │    Compose           │
    │                        │                      │                      │
    │                        │                      │ 4. Update supergraph │
    │                        │                      │    in registry       │
    │                        │                      │                      │
    │                        │                      │◄─────────────────────│
    │                        │                      │ 5. Poll (every 30s)  │
    │                        │                      │─────────────────────►│
    │                        │                      │                      │
    │                        │                      │ 6. Return new        │
    │                        │                      │    supergraph        │
    │                        │                      │◄─────────────────────│
    │                        │                      │                      │
    │                        │                      │ 7. Hot-swap schema   │
    │                        │                      │    (zero downtime)   │
    │                        │                      │                      │
```

**Timeline**: Schema changes are live within ~30 seconds of publishing.

### 4.2 Local Mode Update Flow

```
Developer                  Rover CLI              supergraph.graphql      Apollo Router
    │                          │                         │                      │
    │ 1. Modify schema.graphqls │                        │                      │
    │                          │                         │                      │
    │ 2. Run compose-supergraph.sh                       │                      │
    │─────────────────────────►│                         │                      │
    │                          │                         │                      │
    │                          │ 3. Read all subgraph    │                      │
    │                          │    schemas              │                      │
    │                          │                         │                      │
    │                          │ 4. Compose supergraph   │                      │
    │                          │────────────────────────►│                      │
    │                          │                         │                      │
    │                          │                         │◄─────────────────────│
    │                          │                         │ 5. Watch file change │
    │                          │                         │    (--hot-reload)    │
    │                          │                         │                      │
    │                          │                         │ 6. Hot-swap schema   │
    │                          │                         │    (zero downtime)   │
    │                          │                         │                      │
```

**Timeline**: Schema changes are live immediately after composition completes.

### 4.3 Automatic vs Manual Updates

| Mode | Update Trigger | Automatic? |
|------|---------------|------------|
| GraphOS | `rover subgraph publish` | Yes (router polls) |
| Local | `rover supergraph compose` | Yes (with --hot-reload) |

---

## 5. Local Development Guide

### 5.1 Prerequisites

1. **Install Rover CLI**:
   ```bash
   curl -sSL https://rover.apollo.dev/nix/latest | sh
   export PATH="$HOME/.rover/bin:$PATH"
   ```

2. **Start backend services** (on host machine):
   ```bash
   # In separate terminals or use a process manager
   cd backend/identity-service && ./mvnw spring-boot:run
   cd backend/catalog-service && ./mvnw spring-boot:run
   cd backend/booking-service && ./mvnw spring-boot:run
   ```

### 5.2 Option A: Using GraphOS Mode (Recommended)

Best when you have internet access and want schema observability.

```bash
# 1. Ensure APOLLO_KEY is set (already in docker-compose.yml)

# 2. Start the router
cd docker-resources
docker compose --profile ticketing up dev_ticketing_router_graphos

# 3. Access GraphQL Sandbox
open http://localhost:4000
```

### 5.3 Option B: Using Local Mode (Offline)

Best when working offline or experimenting with schema changes.

```bash
# 1. Compose the supergraph
cd docker-resources/apollo-router/ticketing
./compose-supergraph.sh

# 2. Start the router
cd docker-resources
docker compose --profile ticketing-local up dev_ticketing_router_local

# 3. Access GraphQL Sandbox
open http://localhost:4001
```

### 5.4 Watch Mode for Continuous Development

For rapid iteration, use watch mode:

```bash
# Terminal 1: Watch and auto-compose
cd docker-resources/apollo-router/ticketing
./compose-supergraph.sh --watch

# Terminal 2: Router with hot-reload (already configured in docker-compose)
docker compose --profile ticketing-local up dev_ticketing_router_local
```

---

## 6. CI/CD Integration

### 6.1 GitHub Actions Workflow

The workflow at `.github/workflows/graphql-schema.yml` handles:

| Trigger | Action |
|---------|--------|
| PR to main/develop | Schema check (validates against GraphOS) |
| Push to main | Publish schemas to GraphOS |
| Manual dispatch | Check, publish, or compose |

### 6.2 Required Secrets

Set in GitHub repository settings:

| Secret | Description | Example |
|--------|-------------|---------|
| `APOLLO_KEY` | API key from Apollo Studio | `user:gh.xxxxx:yyyyyy` |

### 6.3 Required Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `APOLLO_GRAPH_REF` | Graph reference | `event-ticketing-graph@dev` |
| `IDENTITY_ROUTING_URL` | Identity service URL | `http://identity-service:8083/graphql` |
| `CATALOG_ROUTING_URL` | Catalog service URL | `http://catalog-service:8085/graphql` |
| `BOOKING_ROUTING_URL` | Booking service URL | `http://booking-service:8082/graphql` |

### 6.4 Manual Publishing

To manually publish all schemas:

```bash
# Using Rover CLI
export APOLLO_KEY="your-apollo-key"

rover subgraph publish event-ticketing-graph@dev \
  --name identity \
  --schema backend/identity-service/src/main/resources/graphql/schema.graphqls \
  --routing-url http://identity-service:8083/graphql

rover subgraph publish event-ticketing-graph@dev \
  --name catalog \
  --schema backend/catalog-service/src/main/resources/graphql/schema.graphqls \
  --routing-url http://catalog-service:8085/graphql

rover subgraph publish event-ticketing-graph@dev \
  --name booking \
  --schema backend/booking-service/src/main/resources/graphql/schema.graphqls \
  --routing-url http://booking-service:8082/graphql
```

---

## 7. Multiple Routers for Multiple Projects

### 7.1 Architecture for Multi-Project Setup

The `docker-resources` directory supports multiple projects with isolated router configurations:

```
docker-resources/
├── apollo-router/
│   ├── ticketing/                  # Event Ticketing System
│   │   ├── router-graphos.yaml
│   │   ├── router-local.yaml
│   │   └── supergraph.yaml
│   ├── twende-ride/               # Twende Ride-Hailing (future)
│   │   ├── router-graphos.yaml
│   │   ├── router-local.yaml
│   │   └── supergraph.yaml
│   └── delight-store/             # Delight Beauty Store (future)
│       ├── router-graphos.yaml
│       └── supergraph.yaml
```

### 7.2 Adding a New Project Router

1. **Create project folder**:
   ```bash
   mkdir -p docker-resources/apollo-router/new-project
   ```

2. **Copy and modify configuration**:
   ```bash
   cp docker-resources/apollo-router/ticketing/*.yaml docker-resources/apollo-router/new-project/
   # Edit files to point to new project's services
   ```

3. **Add to docker-compose.yml**:
   ```yaml
   newproject_router_graphos:
     image: ghcr.io/apollographql/router:v1.61.2
     container_name: newproject_router_graphos
     profiles:
       - newproject
     environment:
       APOLLO_KEY: ${NEWPROJECT_APOLLO_KEY}
       APOLLO_GRAPH_REF: ${NEWPROJECT_GRAPH_REF:-new-project-graph@current}
       # ... service URLs
     ports:
       - "4002:4000"
       - "8090:8088"
     volumes:
       - ./apollo-router/new-project/router-graphos.yaml:/dist/config/router.yaml:ro
   ```

4. **Start the router**:
   ```bash
   docker compose --profile newproject up newproject_router_graphos
   ```

### 7.3 Port Allocation

| Project | GraphOS Router | Local Router | Health Check |
|---------|---------------|--------------|--------------|
| Ticketing | 4000 | 4001 | 8088/8089 |
| Twende Ride | 4002 | 4003 | 8090/8091 |
| Delight Store | 4004 | 4005 | 8092/8093 |

---

## 8. Subgraph URL Configuration

### 8.1 Understanding host.docker.internal

When the Apollo Router runs in Docker but your services run on the host machine:

```
┌─────────────────────────────────────────┐
│              HOST MACHINE               │
│  ┌─────────────────────────────────┐   │
│  │ Identity Service (localhost:8083) │   │
│  │ Catalog Service  (localhost:8085) │   │
│  │ Booking Service  (localhost:8082) │   │
│  └───────────────────┬─────────────┘   │
│                      │                  │
│  ┌───────────────────▼─────────────┐   │
│  │         DOCKER NETWORK          │   │
│  │  ┌───────────────────────────┐  │   │
│  │  │      Apollo Router        │  │   │
│  │  │                           │  │   │
│  │  │ host.docker.internal:8083 │──┼───┼──► Identity
│  │  │ host.docker.internal:8085 │──┼───┼──► Catalog
│  │  │ host.docker.internal:8082 │──┼───┼──► Booking
│  │  └───────────────────────────┘  │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

`host.docker.internal` is a special DNS name that resolves to the host machine's IP from within a Docker container.

### 8.2 When Services Run in Docker

If your services also run in Docker on the same network:

```yaml
override_subgraph_url:
  identity: http://identity-service:8083/graphql  # Docker service name
  catalog: http://catalog-service:8085/graphql
  booking: http://booking-service:8082/graphql
```

### 8.3 Environment Variable Override

You can override URLs via environment variables:

```bash
# In .env file or shell
IDENTITY_SERVICE_URL=http://my-custom-host:8083/graphql
CATALOG_SERVICE_URL=http://my-custom-host:8085/graphql
BOOKING_SERVICE_URL=http://my-custom-host:8082/graphql

docker compose up dev_ticketing_router_graphos
```

---

## 9. Troubleshooting

### 9.1 Router Can't Connect to Subgraphs

**Symptom**: Queries fail with connection errors.

**Diagnosis**:
```bash
# Check if services are running
curl http://localhost:8083/actuator/health
curl http://localhost:8085/actuator/health
curl http://localhost:8082/actuator/health

# Check router logs
docker logs dev_ticketing_router_graphos
```

**Solutions**:
- Ensure services are running on host
- Verify `host.docker.internal` resolution (run `ping host.docker.internal` from container)
- Check firewall settings

### 9.2 Schema Composition Fails

**Symptom**: `rover supergraph compose` returns errors.

**Common errors and fixes**:

| Error | Cause | Fix |
|-------|-------|-----|
| `INVALID_FIELD_SHARING` | Same field defined in multiple subgraphs without `@shareable` | Add `@shareable` directive |
| `INVALID_GRAPHQL` | Input type used as output field | Use proper output type |
| `ENUM_VALUE_MISMATCH` | Enum has different values in different subgraphs | Synchronize enum definitions |

### 9.3 Router Doesn't Pick Up Schema Changes

**GraphOS Mode**:
- Wait up to 30 seconds for poll
- Check Apollo Studio for successful composition
- Verify `APOLLO_KEY` and `APOLLO_GRAPH_REF`

**Local Mode**:
- Ensure `--hot-reload` flag is set
- Verify supergraph.graphql was updated
- Check file permissions

### 9.4 Authentication Not Working

**Symptom**: JWT not reaching subgraphs.

**Check**:
```yaml
# Ensure header propagation is configured
headers:
  all:
    request:
      - propagate:
          named: authorization
```

**Verify**:
```bash
# Send a request with auth header and check subgraph logs
curl -X POST http://localhost:4000/graphql \
  -H "Authorization: Bearer your-jwt" \
  -H "Content-Type: application/json" \
  -d '{"query":"{ me { id } }"}'
```

---

## 10. Best Practices

### 10.1 Schema Design

1. **Clear entity ownership**: One service owns each entity
2. **Use stubs for references**: `@key(fields: "id", resolvable: false)`
3. **Document federation directives**: Comment why `@external`, `@requires` are used
4. **Keep schemas in sync**: Use shared types consistently

### 10.2 Development Workflow

1. **Test locally first**: Use local mode to validate changes
2. **Check before publish**: Run `rover subgraph check` in CI
3. **Atomic changes**: Publish subgraph changes one at a time
4. **Monitor after publish**: Watch for errors in Apollo Studio

### 10.3 Production Deployment

1. **Deploy services first**: Ensure new resolvers are available
2. **Then publish schema**: So GraphOS has valid endpoints
3. **Use graph variants**: `@staging`, `@production`
4. **Set up alerts**: Monitor error rates in Apollo Studio

### 10.4 Security

1. **Disable introspection in production**:
   ```yaml
   supergraph:
     introspection: false
   ```

2. **Use environment variables for credentials**:
   ```yaml
   APOLLO_KEY: ${APOLLO_KEY}  # Never hardcode
   ```

3. **Restrict sandbox**:
   ```yaml
   sandbox:
     enabled: false
   ```

---

## Quick Reference

### Commands Cheat Sheet

```bash
# Compose supergraph locally
cd docker-resources/apollo-router/ticketing
./compose-supergraph.sh

# Start GraphOS mode router
docker compose --profile ticketing up dev_ticketing_router_graphos

# Start local mode router
docker compose --profile ticketing-local up dev_ticketing_router_local

# Check schema against GraphOS
rover subgraph check event-ticketing-graph@dev \
  --name identity \
  --schema backend/identity-service/src/main/resources/graphql/schema.graphqls

# Publish schema to GraphOS
rover subgraph publish event-ticketing-graph@dev \
  --name identity \
  --schema backend/identity-service/src/main/resources/graphql/schema.graphqls \
  --routing-url http://identity-service:8083/graphql

# View router health
curl http://localhost:8088/health
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `APOLLO_KEY` | GraphOS API key | Required for GraphOS mode |
| `APOLLO_GRAPH_REF` | Graph reference | `event-ticketing-graph@dev` |
| `IDENTITY_SERVICE_URL` | Identity subgraph URL | `http://host.docker.internal:8083/graphql` |
| `CATALOG_SERVICE_URL` | Catalog subgraph URL | `http://host.docker.internal:8085/graphql` |
| `BOOKING_SERVICE_URL` | Booking subgraph URL | `http://host.docker.internal:8082/graphql` |
| `APOLLO_ROUTER_LOG` | Log level | `info` |

---

## Sources

- [Apollo Router Configuration Overview](https://www.apollographql.com/docs/graphos/routing/configuration/overview)
- [Apollo Deployment Best Practices](https://www.apollographql.com/docs/graphos/platform/production-readiness/deployment-best-practices)
- [Apollo Router GitHub](https://github.com/apollographql/router)
- [Federation 2 Specification](https://www.apollographql.com/docs/federation/)
