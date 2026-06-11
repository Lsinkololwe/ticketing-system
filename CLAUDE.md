# Event Ticketing System - Architecture & Development Guide

This document describes the system architecture, technologies, and implementation rules for the Event Ticketing System. Claude should reference this when implementing features.

## System Overview

A microservices-based event ticketing platform for Zambia/Africa with:
- **Event Discovery**: Browse and search events
- **Ticket Purchasing**: Mobile money payments (MTN, Airtel, Zamtel)
- **Ticket Validation**: QR code scanning at venues
- **Organizer Management**: Event creation, sales tracking, payouts

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND CLIENTS                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                  │
│  │   Admin Web     │  │  Customer Web   │  │   Mobile App    │                  │
│  │   (Next.js 16)  │  │   (Next.js 16)  │  │   (Expo 54)     │                  │
│  │   Port: 3030    │  │   Port: 3001    │  │   Port: 19006   │                  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘                  │
│           │                    │                    │                           │
│           └────────────────────┼────────────────────┘                           │
│                                │                                                │
│                                ▼                                                │
│                    ┌───────────────────────┐                                    │
│                    │    API Gateway        │                                    │
│                    │    (Spring Cloud)     │                                    │
│                    │    Port: 8080         │                                    │
│                    └───────────┬───────────┘                                    │
│                                │                                                │
│                                ▼                                                │
│                    ┌───────────────────────┐                                    │
│                    │    Apollo Router      │ ◄── GraphQL Federation Gateway     │
│                    │    Port: 4000         │                                    │
│                    └───────────┬───────────┘                                    │
│                                │                                                │
├────────────────────────────────┼────────────────────────────────────────────────┤
│                         BACKEND SERVICES                                        │
├────────────────────────────────┼────────────────────────────────────────────────┤
│    ┌───────────────────────────┼───────────────────────────┐                    │
│    │                           │                           │                    │
│    ▼                           ▼                           ▼                    │
│ ┌──────────────┐        ┌──────────────┐        ┌──────────────┐               │
│ │ Catalog Svc  │        │ Booking Svc  │        │ Identity Svc │               │
│ │ Port: 8085   │        │ Port: 8082   │        │ Port: 8083   │               │
│ │              │        │              │        │              │               │
│ │ Owns:        │        │ Owns:        │        │ Owns:        │               │
│ │ - Event      │        │ - Ticket     │        │ - User       │               │
│ │ - Location   │        │ - Payment    │        │ - Organizer  │               │
│ │ - Category   │        │ - Escrow     │        │ - Role       │               │
│ │ - Pricing    │        │ - Commission │        │ - Permission │               │
│ └──────┬───────┘        └──────┬───────┘        └──────┬───────┘               │
│        │                       │                       │                        │
├────────┼───────────────────────┼───────────────────────┼────────────────────────┤
│                         DATA STORES                                             │
├────────┼───────────────────────┼───────────────────────┼────────────────────────┤
│        │                       │                       │                        │
│        ▼                       ▼                       ▼                        │
│ ┌──────────────────────────────────────────────────────────────────────┐       │
│ │                     MongoDB (Reactive)                                │       │
│ │                     Database: ticketing                               │       │
│ │                     Collections per service (prefixed)               │       │
│ └──────────────────────────────────────────────────────────────────────┘       │
│                                │                                                │
│ ┌──────────────────────────────┼───────────────────────────────────────┐       │
│ │ PostgreSQL (shared_db)       │                                        │       │
│ │ ├── modulith_events schema   │ ◄── Spring Modulith Event Publication  │       │
│ │ └── keycloak schema          │ ◄── Keycloak IAM                       │       │
│ └──────────────────────────────────────────────────────────────────────┘       │
│                                                                                 │
│ ┌──────────────────────────────────────────────────────────────────────┐       │
│ │                         Redis                                         │       │
│ │                     Caching & Sessions                                │       │
│ └──────────────────────────────────────────────────────────────────────┘       │
│                                                                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                         EXTERNAL SERVICES                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                  │
│  │    Keycloak     │  │  Azure Service  │  │    PawaPay      │                  │
│  │    Port: 8084   │  │      Bus        │  │  (Mobile Money) │                  │
│  │                 │  │                 │  │                 │                  │
│  │ Realms:         │  │ Topics:         │  │ Providers:      │                  │
│  │ - event-ticket  │  │ - catalog-evts  │  │ - MTN           │                  │
│  │ - twende-ride   │  │ - booking-evts  │  │ - Airtel        │                  │
│  │                 │  │ - identity-evts │  │ - Zamtel        │                  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Technology Stack

### Backend Services

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Runtime (LTS with virtual threads) |
| Spring Boot | 3.5.4 | Application framework |
| Spring WebFlux | - | Reactive web layer (non-blocking I/O) |
| Spring Data MongoDB Reactive | - | Reactive database access |
| Netflix DGS | 10.0.1 | GraphQL framework with Federation 2 support |
| Spring Modulith | 1.3.1 | Module boundaries & event publication |
| Spring Cloud Azure | 5.19.0 | Azure Service Bus integration |
| Spring Cloud Gateway (WebFlux) | - | API Gateway (reactive) |
| Keycloak | 26.x | Identity & Access Management |

### Frontend Applications

| Application | Technology | Purpose |
|-------------|------------|---------|
| Admin Web | Next.js 16, React 19, Apollo Client 4 | Organizer dashboard |
| Customer Web | Next.js 16, React 19, Apollo Client 4 | Ticket purchasing |
| Mobile App | Expo 54, React Native, Apollo Client 4 | Mobile ticketing |

### Infrastructure

| Component | Technology | Purpose |
|-----------|------------|---------|
| API Gateway | Spring Cloud Gateway (WebFlux) | Routing, rate limiting, circuit breaker |
| GraphQL Gateway | Apollo Router | Federation 2 composition |
| Message Broker | Azure Service Bus | Cross-service events |
| Database | MongoDB 8.x | Business data (reactive driver) |
| Database | PostgreSQL 16 | Keycloak + Modulith events |
| Cache | Redis 7.x | Sessions, caching |
| IAM | Keycloak 26.x | OAuth2/OIDC authentication |

## Service Ports

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Apollo Router | 4000 |
| Booking Service | 8082 |
| Identity Service | 8083 |
| Keycloak | 8084 |
| Catalog Service | 8085 |
| MongoDB | 27017 |
| PostgreSQL | 5432 |
| Redis | 6379 |

## Critical Architecture Decisions

### 1. Reactive Stack with Blocking Event Publication (HYBRID)

**Problem**: Spring Modulith requires blocking database driver, but we use reactive MongoDB.

**Solution**: Hybrid approach with two databases:
- **Business Data**: Reactive MongoDB (WebFlux compatible)
- **Event Publication**: PostgreSQL JDBC (blocking, for Modulith)

```yaml
# Booking Service application.yml
spring:
  # Business data - REACTIVE
  data:
    mongodb:
      uri: mongodb://localhost:27017/ticketing

  # Event publication ONLY - BLOCKING JDBC
  datasource:
    url: jdbc:postgresql://localhost:5432/shared_db?currentSchema=modulith_events
```

**Configuration Class**: `ModulithEventConfig.java`
- Defines `jdbcTransactionManager` bean (marked @Primary)
- MongoDB uses `reactiveTransactionManager` (separate bean)

### 2. Event Handling Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    EVENT HANDLING ARCHITECTURE                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  INTRA-SERVICE EVENTS (Within a single microservice)               │
│  ────────────────────────────────────────────────────               │
│  Technology: Spring Modulith + PostgreSQL                           │
│  Annotation: @ApplicationModuleListener                             │
│  Guarantees: Persisted before processing, auto-retry, republish    │
│                                                                     │
│  Example:                                                           │
│  PaymentService.publishEvent(PaymentCompletedEvent)                 │
│       │                                                             │
│       ▼                                                             │
│  PostgreSQL event_publication table (event persisted)               │
│       │                                                             │
│       ▼                                                             │
│  @ApplicationModuleListener                                         │
│  PaymentEventListener.onPaymentCompleted()                          │
│       │                                                             │
│       ▼                                                             │
│  Event marked COMPLETED (removed from table)                        │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  CROSS-SERVICE EVENTS (Between microservices)                       │
│  ────────────────────────────────────────────                       │
│  Technology: Azure Service Bus via Spring Cloud Stream              │
│  Method: StreamBridge.send()                                        │
│  Guarantees: DLQ, retry with exponential backoff                   │
│                                                                     │
│  Example:                                                           │
│  streamBridge.send("ticket-events-out-0", TicketPurchasedEvent)     │
│       │                                                             │
│       ▼                                                             │
│  Azure Service Bus Topic: booking-events                            │
│       │                                                             │
│       ▼                                                             │
│  Catalog Service / Identity Service (subscribers)                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 3. GraphQL Federation 2 Architecture

All services expose GraphQL subgraphs that Apollo Router composes:

```graphql
# Each service defines its owned types with @key
type Event @key(fields: "id") {
  id: ID!
  title: String!
  # ... fields owned by Catalog Service
}

# Other services EXTEND types they don't own
extend type Event @key(fields: "id") {
  # Booking service adds these fields
  tickets: [Ticket!]!
  ticketsSold: Int!
  revenue: BigDecimal!
}
```

**Federation Rules**:
1. Use `type X @key(fields: "id", resolvable: false)` for stub types
2. Use `extend type X @key(fields: "id")` to add fields
3. **NEVER** redefine `id` in extension blocks (causes schema errors)
4. Use `@external` for fields owned by other services
5. Use `@requires` when you need external field data to compute a value

**Supergraph Composition**:

> **See "Apollo Router & Supergraph Composition" section below for detailed instructions.**

Quick reference:
```bash
# Compose supergraph (from docker-resources/apollo-router/)
cd /path/to/docker-resources/apollo-router
rover supergraph compose --config supergraph.yaml > supergraph.graphql
```

**IMPORTANT**: The Apollo Router configuration **ALREADY EXISTS** at:
```
/Users/lazarous.sinkololwe/Documents/Software Projects/personal/docker-resources/apollo-router/ticketing/
```

Do NOT create new Apollo Router configurations inside the ticketing-system directory. Use the existing ticketing-specific configuration in `docker-resources/apollo-router/ticketing/`.

### 4. API Gateway Configuration (Spring Cloud Gateway WebFlux)

The API Gateway uses the **WebFlux-specific** Spring Cloud Gateway starter for reactive, non-blocking request handling.

**Maven Dependency**:
```xml
<!-- Use the WebFlux-specific starter (NOT the deprecated generic one) -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
```

**Configuration Namespace**: The WebFlux gateway uses the `spring.cloud.gateway.server.webflux.*` namespace:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          # CORS configuration
          globalcors:
            cors-configurations:
              '[/**]':
                allowedOrigins:
                  - "http://localhost:3000"
                allowedMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]

          # Route definitions
          routes:
            - id: graphql-federation
              uri: ${APOLLO_ROUTER_URL:http://localhost:4000}
              predicates:
                - Path=/graphql
              filters:
                - name: CircuitBreaker
                  args:
                    name: apolloRouterCircuitBreaker
                    fallbackUri: forward:/fallback/graphql

          # Default filters for all routes
          default-filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
                key-resolver: "#{@ipKeyResolver}"
```

**Important**: Do NOT use the deprecated `spring-cloud-starter-gateway` artifact. Always use `spring-cloud-starter-gateway-server-webflux` for reactive applications.

### 5. Authentication Pipeline

The system uses Keycloak as the Identity Provider with a custom Phone OTP authenticator for passwordless login.

#### Authentication Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         AUTHENTICATION FLOW                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                        OPTION 1: Phone OTP (Mobile)                       │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
│   Mobile App                   Keycloak                    Identity Service     │
│       │                           │                              │              │
│       │  1. Login request         │                              │              │
│       │────────────────────────▶│                              │              │
│       │                           │                              │              │
│       │  2. Show phone form       │                              │              │
│       │◀────────────────────────│                              │              │
│       │                           │                              │              │
│       │  3. Submit phone number   │                              │              │
│       │────────────────────────▶│                              │              │
│       │                           │  4. POST /api/internal/otp/request         │
│       │                           │─────────────────────────────▶│              │
│       │                           │                              │              │
│       │                           │  5. Generate OTP, store in Redis           │
│       │                           │                              │              │
│       │                           │  6. Send OTP via WhatsApp/SMS              │
│       │                           │                              │──▶ WhatsApp  │
│       │                           │                              │              │
│       │                           │  7. Return success           │              │
│       │                           │◀─────────────────────────────│              │
│       │                           │                              │              │
│       │  8. Show OTP form         │                              │              │
│       │◀────────────────────────│                              │              │
│       │                           │                              │              │
│       │  9. Submit OTP            │                              │              │
│       │────────────────────────▶│                              │              │
│       │                           │  10. POST /api/internal/otp/verify         │
│       │                           │─────────────────────────────▶│              │
│       │                           │                              │              │
│       │                           │  11. Verify OTP from Redis   │              │
│       │                           │                              │              │
│       │                           │  12. Return valid=true       │              │
│       │                           │◀─────────────────────────────│              │
│       │                           │                              │              │
│       │                           │  13. Find/Create user        │              │
│       │                           │  14. Issue JWT tokens        │              │
│       │                           │                              │              │
│       │  15. Return tokens        │                              │              │
│       │◀────────────────────────│                              │              │
│       │                           │                              │              │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                     OPTION 2: Username/Password (Admin)                   │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
│   Admin Web                    Keycloak                                         │
│       │                           │                                             │
│       │  1. Login with credentials│                                             │
│       │────────────────────────▶│                                             │
│       │                           │  2. Validate credentials                   │
│       │                           │  3. Issue JWT tokens                       │
│       │  4. Return tokens         │                                             │
│       │◀────────────────────────│                                             │
│       │                           │                                             │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                        API REQUEST FLOW                                   │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
│   Client                  API Gateway              Apollo Router    Services    │
│       │                       │                         │              │        │
│       │  Authorization: Bearer JWT                      │              │        │
│       │──────────────────────▶│                         │              │        │
│       │                       │  Propagate header       │              │        │
│       │                       │────────────────────────▶│              │        │
│       │                       │                         │  Forward JWT │        │
│       │                       │                         │─────────────▶│        │
│       │                       │                         │              │        │
│       │                       │                         │  Validate JWT│        │
│       │                       │                         │  via JWKS    │        │
│       │                       │                         │              │        │
│       │                       │                         │  Extract roles        │
│       │                       │                         │  from JWT    │        │
│       │                       │                         │              │        │
│       │◀──────────────────────────────────────────────────────────────│        │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### Keycloak Configuration

| Setting | Value |
|---------|-------|
| **Realm** | `event-ticketing` |
| **Admin Client** | `event-ticketing-admin` |
| **Mobile Client** | `event-ticketing-mobile` |
| **Internal Client** | `internal-service` (for service-to-service) |
| **Token URL** | `http://localhost:8084/realms/event-ticketing/protocol/openid-connect/token` |
| **JWKS URL** | `http://localhost:8084/realms/event-ticketing/protocol/openid-connect/certs` |

#### Custom Phone OTP Authenticator

A custom Keycloak SPI authenticator enables passwordless login via phone number:

**Location**: `backend/keycloak-extensions/`

**Components**:

| Class | Purpose |
|-------|---------|
| `PhoneOtpAuthenticator.java` | Main authenticator logic |
| `PhoneOtpAuthenticatorFactory.java` | Keycloak SPI factory |
| `OtpServiceClient.java` | REST client to Identity Service |

**Flow**:

```java
// 1. User submits phone number
handlePhoneSubmission(context, formData) {
    // Validate and normalize phone number
    String normalizedPhone = normalizePhoneNumber(phoneNumber);  // +260...

    // Call Identity Service to generate and send OTP
    OtpRequestResult result = otpServiceClient.requestOtp(normalizedPhone, "whatsapp");

    if (result.isSuccess()) {
        // Show OTP verification form
        context.challenge(otpVerifyForm);
    }
}

// 2. User submits OTP
handleOtpSubmission(context, formData) {
    // Verify OTP with Identity Service
    OtpVerifyResult result = otpServiceClient.verifyOtp(phoneNumber, otp);

    if (result.isValid()) {
        // Find or create user in Keycloak
        UserModel user = findOrCreateUser(context, phoneNumber);
        user.setSingleAttribute("phone_number", phoneNumber);
        user.setSingleAttribute("phone_verified", "true");

        // Grant default CUSTOMER role
        realm.getRolesStream()
            .filter(role -> "CUSTOMER".equalsIgnoreCase(role.getName()))
            .findFirst()
            .ifPresent(user::grantRole);

        context.setUser(user);
        context.success();  // Issue JWT tokens
    }
}
```

#### Identity Service OTP Endpoints

**Location**: `backend/identity-service/src/main/java/com/pml/identity/controller/InternalOtpController.java`

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/internal/otp/request` | POST | Generate & send OTP |
| `/api/internal/otp/verify` | POST | Verify OTP code |
| `/api/internal/otp/status/{phone}` | GET | Check cooldown status |
| `/api/internal/otp/{phone}` | DELETE | Invalidate OTP |

**Security**: Restricted to internal services only via:
```java
@PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
```

**OTP Storage**: Redis with TTL
```java
// OTP stored in Redis with 5-minute expiry
otpService.generateOtp(phoneNumber)  // Generates 6-digit code
    .flatMap(otp -> messagingService.sendOtp(phoneNumber, otp, "whatsapp"))
    .flatMap(sent -> otpService.setCooldown(phoneNumber))  // 60-second cooldown
```

#### OTP Delivery Channels

| Channel | Provider | Use Case |
|---------|----------|----------|
| **WhatsApp** | WhatsApp Business API | Primary (preferred in Zambia) |
| **SMS** | Africa's Talking / Twilio | Fallback |

#### JWT Token Structure

```json
{
  "exp": 1709721234,
  "iat": 1709720934,
  "jti": "abc123",
  "iss": "http://localhost:8084/realms/event-ticketing",
  "sub": "user-uuid",
  "typ": "Bearer",
  "azp": "event-ticketing-mobile",
  "preferred_username": "user_26097XXXX",
  "phone_number": "+26097XXXXXXX",
  "phone_verified": true,
  "realm_access": {
    "roles": ["CUSTOMER"]
  },
  "resource_access": {
    "event-ticketing-mobile": {
      "roles": ["user"]
    }
  },
  "scope": "openid profile phone"
}
```

#### Role Extraction (KeycloakJwtAuthenticationConverter)

**Location**: `backend/shared-library/src/main/java/com/pml/shared/security/`

The `KeycloakGrantedAuthoritiesConverter` extracts authorities from JWT:

```java
// Extracts roles from multiple locations in JWT:
// 1. realm_access.roles → ROLE_CUSTOMER, ROLE_ORGANIZER, ROLE_ADMIN
// 2. resource_access.{clientId}.roles → ROLE_user
// 3. scope → SCOPE_openid, SCOPE_profile, SCOPE_internal-read

KeycloakJwtAuthenticationConverter.reactiveConverter("booking-service")
```

#### Security Configuration Pattern

Each service implements `SecurityConfig.java`:

```java
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints
                .pathMatchers("/actuator/health/**").permitAll()
                .pathMatchers("/graphiql/**").permitAll()

                // Internal service endpoints - require internal scope
                .pathMatchers("/api/internal/**").hasAnyAuthority(
                    "SCOPE_internal-read",
                    "SCOPE_internal-write",
                    "ROLE_INTERNAL_SERVICE"
                )

                // GraphQL - auth handled at resolver level
                .pathMatchers("/graphql/**").permitAll()

                // Everything else requires authentication
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(
                        KeycloakJwtAuthenticationConverter.reactiveConverter(keycloakClientId)
                    )
                )
            )
            .build();
    }
}
```

#### User Roles

| Role | Description | Capabilities |
|------|-------------|--------------|
| `CUSTOMER` | Regular user | Browse events, purchase tickets |
| `ORGANIZER` | Event organizer | Create events, view sales, request payouts |
| `ADMIN` | Platform admin | Full access, approve organizers |
| `INTERNAL_SERVICE` | Service account | Service-to-service communication |

#### Environment Variables for Authentication

```bash
# Keycloak
KEYCLOAK_URL=http://localhost:8084
KEYCLOAK_REALM=event-ticketing
KEYCLOAK_CLIENT_ID=booking-service
KEYCLOAK_CLIENT_SECRET=<secret>

# OTP Service (for Keycloak custom authenticator)
OTP_SERVICE_URL=http://identity-service:8083
OTP_CLIENT_ID=internal-service
OTP_CLIENT_SECRET=<secret>
KEYCLOAK_TOKEN_URL=http://localhost:8084/realms/event-ticketing/protocol/openid-connect/token
```

### 6. Keycloak Extensions Module

The `keycloak-extensions` module contains custom Keycloak SPI (Service Provider Interface) implementations:
- **Phone OTP Authenticator**: Passwordless login via WhatsApp/SMS
- **UserSync EventListener**: Synchronizes Keycloak user changes to MongoDB

#### Project Structure

```
backend/keycloak-extensions/
├── pom.xml                              # Maven build configuration
├── scripts/
│   └── setup-phone-otp-flow.sh          # Keycloak flow configuration script
└── src/main/
    ├── java/com/pml/keycloak/
    │   ├── authenticator/               # Phone OTP Authenticator
    │   │   ├── PhoneOtpAuthenticator.java
    │   │   ├── PhoneOtpAuthenticatorFactory.java
    │   │   └── OtpServiceClient.java
    │   ├── client/                      # Shared REST client
    │   │   └── IdentityServiceClient.java
    │   └── eventlistener/               # User Sync EventListener
    │       ├── UserSyncEventListener.java
    │       └── UserSyncEventListenerFactory.java
    └── resources/
        ├── META-INF/services/
        │   ├── org.keycloak.authentication.AuthenticatorFactory
        │   └── org.keycloak.events.EventListenerProviderFactory
        └── theme-resources/
            ├── templates/
            │   ├── phone-otp-input.ftl
            │   └── phone-otp-verify.ftl
            └── messages/
                └── messages_en.properties
```

#### Key Dependencies (pom.xml)

```xml
<properties>
    <keycloak.version>26.0.0</keycloak.version>
</properties>

<dependencies>
    <!-- Keycloak Server SPI - PROVIDED (already in Keycloak runtime) -->
    <dependency>
        <groupId>org.keycloak</groupId>
        <artifactId>keycloak-server-spi</artifactId>
        <version>${keycloak.version}</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.keycloak</groupId>
        <artifactId>keycloak-server-spi-private</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.keycloak</groupId>
        <artifactId>keycloak-services</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- JSON Processing - MUST be shaded into JAR -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>
</dependencies>
```

#### Build Configuration

The Maven Shade Plugin creates a fat JAR with Gson bundled:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.1</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <artifactSet>
                    <includes>
                        <include>com.google.code.gson:gson</include>
                    </includes>
                </artifactSet>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### SPI Registration

The factory must be registered in `META-INF/services/org.keycloak.authentication.AuthenticatorFactory`:

```
com.pml.keycloak.authenticator.PhoneOtpAuthenticatorFactory
```

#### Build Commands

```bash
cd backend/keycloak-extensions

# Build the JAR
mvn clean package

# Output: target/keycloak-extensions-1.0.0.jar
```

#### Deployment to Keycloak

**Option 1: Docker Volume Mount (Development)**

```yaml
# docker-compose.yml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:26.0.0
    volumes:
      - ./backend/keycloak-extensions/target/keycloak-extensions-1.0.0.jar:/opt/keycloak/providers/keycloak-extensions.jar
    environment:
      OTP_SERVICE_URL: http://identity-service:8083
      OTP_CLIENT_ID: internal-service
      OTP_CLIENT_SECRET: ${OTP_CLIENT_SECRET}
      KEYCLOAK_TOKEN_URL: http://localhost:8084/realms/event-ticketing/protocol/openid-connect/token
    command: start-dev
```

**Option 2: Custom Docker Image (Production)**

```dockerfile
# Dockerfile.keycloak
FROM quay.io/keycloak/keycloak:26.0.0

COPY backend/keycloak-extensions/target/keycloak-extensions-1.0.0.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
```

**Option 3: Manual Copy**

```bash
# Copy JAR to Keycloak providers directory
cp target/keycloak-extensions-1.0.0.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak (required for production mode)
$KEYCLOAK_HOME/bin/kc.sh build

# Restart Keycloak
$KEYCLOAK_HOME/bin/kc.sh start
```

#### Configuring the Authentication Flow

After deployment, configure Keycloak to use the authenticator:

**Option 1: Admin Console (Manual)**

1. Go to **Authentication** → **Flows**
2. Create a new flow: `phone-otp-browser`
3. Add executions:
   - Cookie (ALTERNATIVE)
   - Identity Provider Redirector (ALTERNATIVE)
   - Create sub-flow `phone-otp-forms` (ALTERNATIVE):
     - Username Password Form (ALTERNATIVE)
     - Phone OTP Authentication (ALTERNATIVE)
4. Go to **Authentication** → **Bindings**
5. Set **Browser Flow** to `phone-otp-browser`

**Option 2: Setup Script (Automated)**

```bash
# Set environment variables
export KEYCLOAK_URL=http://localhost:8084
export KEYCLOAK_REALM=event-ticketing
export KEYCLOAK_ADMIN=admin
export KEYCLOAK_ADMIN_PASSWORD=admin

# Run setup script
./scripts/setup-phone-otp-flow.sh
```

#### Authentication Flow Structure

```
phone-otp-browser (Browser Flow)
├── Cookie                           [ALTERNATIVE]
├── Identity Provider Redirector     [ALTERNATIVE]
└── phone-otp-forms                  [ALTERNATIVE]
    ├── Username Password Form       [ALTERNATIVE]  ← Traditional login
    └── Phone OTP Authentication     [ALTERNATIVE]  ← Passwordless OTP
```

#### FreeMarker Templates

**Phone Input Form** (`phone-otp-input.ftl`):
- Phone number input with E.164 format
- Channel selection (WhatsApp / SMS)
- Styled radio buttons for channel selection

**OTP Verification Form** (`phone-otp-verify.ftl`):
- 6-digit OTP input with numeric keyboard
- Countdown timer for expiration
- Auto-submit on 6 digits entered
- Resend link (enabled after 60 seconds)

#### i18n Messages

Located in `messages_en.properties`:

```properties
phoneOtpTitle=Sign in with Phone
phoneNumber=Phone Number
deliveryChannel=Receive code via
sendVerificationCode=Send Verification Code
phoneOtpVerifyTitle=Enter Verification Code
verificationCode=Verification Code
verifyCode=Verify Code
codeExpiresIn=Code expires in
invalidPhoneNumber=Invalid phone number format
invalidOrExpiredOtp=Invalid or expired code
```

#### Troubleshooting

| Issue | Solution |
|-------|----------|
| Authenticator not showing in Admin Console | Ensure JAR is in `/opt/keycloak/providers/` and Keycloak is restarted |
| "Provider not found" error | Check `META-INF/services/` file contains correct factory class name |
| OTP not sending | Verify `OTP_SERVICE_URL` environment variable points to Identity Service |
| 401 on OTP endpoints | Ensure `OTP_CLIENT_ID` and `OTP_CLIENT_SECRET` are configured |
| Templates not loading | Check `theme-resources/templates/` path in JAR |

#### Development Workflow

```bash
# 1. Make changes to authenticator code
vim src/main/java/com/pml/keycloak/authenticator/PhoneOtpAuthenticator.java

# 2. Build
mvn clean package

# 3. Copy to Keycloak (if using volume mount, skip this)
docker cp target/keycloak-extensions-1.0.0.jar keycloak:/opt/keycloak/providers/

# 4. Restart Keycloak
docker restart keycloak

# 5. Test the flow
open http://localhost:8084/realms/event-ticketing/account
```

### 7. Keycloak-MongoDB User Synchronization

The system maintains bidirectional synchronization between Keycloak and MongoDB:

#### Sync Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     KEYCLOAK ↔ MONGODB SYNCHRONIZATION                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    Events     ┌────────────────────┐                     │
│  │   Keycloak   │──────────────▶│  UserSyncEvent     │                     │
│  │   Server     │  REGISTER     │  Listener          │                     │
│  │              │  UPDATE_PROFILE│  (keycloak-extensions)                   │
│  └──────────────┘  LOGIN, etc.  └─────────┬──────────┘                     │
│                                           │                                 │
│                                    REST   │ POST /api/internal/keycloak/   │
│                                    Call   │      sync/user                 │
│                                           ▼                                 │
│                              ┌────────────────────────┐                    │
│                              │  KeycloakSyncController │                    │
│                              │   (identity-service)    │                    │
│                              └─────────┬──────────────┘                    │
│                                        │                                    │
│                                        ▼                                    │
│                              ┌────────────────────────┐                    │
│                              │    UserSyncService      │                    │
│                              │    - fetchFromKeycloak  │                    │
│                              │    - upsert to MongoDB  │                    │
│                              └─────────┬──────────────┘                    │
│                                        │                                    │
│            ┌───────────────────────────┼───────────────────────────┐       │
│            ▼                           ▼                           ▼       │
│  ┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐│
│  │    MongoDB      │        │  Azure Service  │        │  Other Services ││
│  │  (User profile) │        │      Bus        │        │ (Catalog/Booking)│
│  └─────────────────┘        │ UserRegistered  │───────▶│                 ││
│                             │ OrganizerApproved│        │                 ││
│                             └─────────────────┘        └─────────────────┘│
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Events Handled

| Keycloak Event | Action in Identity Service |
|----------------|---------------------------|
| `REGISTER` | Sync new user to MongoDB |
| `UPDATE_PROFILE` | Sync profile changes |
| `UPDATE_EMAIL` | Sync email change |
| `VERIFY_EMAIL` | Update emailVerified status |
| `LOGIN` | Update lastLoginAt |
| `AdminEvent CREATE (users)` | Create user in MongoDB |
| `AdminEvent UPDATE (users)` | Update user in MongoDB |
| `AdminEvent DELETE (users)` | Delete user from MongoDB |

#### Enabling UserSync EventListener

After deploying the keycloak-extensions JAR:

1. Go to **Realm Settings** → **Events** → **Event Listeners**
2. Add `user-sync` to the list of listeners
3. Save changes

#### REST Endpoints (Identity Service)

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/api/internal/keycloak/sync/user` | POST | Internal | Sync single user |
| `/api/internal/keycloak/sync/event` | POST | Internal | Process Keycloak event |
| `/api/internal/keycloak/sync/all` | POST | Admin | Full sync (recovery) |
| `/api/internal/keycloak/sync/health` | GET | Public | Health check |

#### GraphQL Mutations (Admin)

```graphql
# Sync single user from Keycloak
mutation {
  syncUserFromKeycloak(userId: "keycloak-user-id") {
    id
    email
    firstName
    lastName
  }
}

# Full sync (async, returns immediately)
mutation {
  syncAllUsersFromKeycloak
}
```

#### Event Publishing

Events are published to Azure Service Bus on user actions:

| Event | Topic | When Published |
|-------|-------|----------------|
| `UserRegisteredEvent` | `identity-events` | New user created |
| `OrganizerApprovedEvent` | `identity-events` | Organizer approved by admin |

### 8. File Upload Architecture (REST over GraphQL)

**Critical Decision**: Use REST APIs for file uploads, not GraphQL.

**Why REST for File Uploads?**

Industry consensus (Apollo, WunderGraph, AWS) strongly recommends REST over GraphQL for file uploads due to:

| Issue | GraphQL Multipart | REST with Presigned URLs |
|-------|------------------|-------------------------|
| **Security** | CSRF vulnerabilities | No CSRF risk |
| **Performance** | Buffers entire file in memory | Streams directly to S3 |
| **Scalability** | Limited by server RAM | Unlimited concurrent uploads |
| **Progress Tracking** | Requires workarounds | Native browser support |
| **Server Load** | High (proxies file bytes) | Minimal (metadata only) |

**Architecture Flow**:

```
Frontend                     Backend (REST)              S3
   │                              │                      │
   │  1. POST /api/v1/organizations/{orgId}/documents/upload-url
   │     (fileName, mimeType, fileSize)                  │
   │──────────────────────────▶│                         │
   │                           │  Generate presigned     │
   │                           │  URL (15min expiry)     │
   │                           │─────────────────────────▶
   │                           │                         │
   │  2. { uploadUrl, fileKey }│                         │
   │◀──────────────────────────│                         │
   │                           │                         │
   │  3. PUT {uploadUrl} (file bytes with progress)      │
   │─────────────────────────────────────────────────────▶
   │                           │                         │
   │  4. POST /api/v1/organizations/{orgId}/documents   │
   │     (documentUrl, metadata)                         │
   │──────────────────────────▶│                         │
   │                           │  Save metadata to       │
   │                           │  MongoDB                │
   │                           │                         │
   │  5. { document }          │                         │
   │◀──────────────────────────│                         │
```

**Backend REST Controller**:

```java
@RestController
@RequestMapping("/api/v1")
public class VerificationDocumentRestController {

    @PostMapping("/organizations/{orgId}/documents/upload-url")
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<ResponseEntity<PresignedUploadUrlResponse>> requestUploadUrl(
            @PathVariable String orgId,
            @Valid @RequestBody UploadUrlRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        // Generate presigned URL (valid for 15 minutes)
        return fileStorageService.generatePresignedUrl(fileKey, 15);
    }

    @PostMapping("/organizations/{orgId}/documents")
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<ResponseEntity<DocumentResponse>> registerDocument(
            @PathVariable String orgId,
            @Valid @RequestBody RegisterDocumentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        // Save document metadata to MongoDB
        return documentService.upload(orgId, request);
    }
}
```

**Frontend React Hook**:

```typescript
import { useDocumentUpload } from '@/api/rest';

function DocumentUploadForm({ organizationId }) {
  const { upload, progress, isUploading, error } = useDocumentUpload(organizationId);

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      const document = await upload('BUSINESS_LICENSE', file);
      console.log('Uploaded:', document);
    } catch (err) {
      console.error('Upload failed:', err);
    }
  };

  return (
    <div>
      <input type="file" onChange={handleFileSelect} disabled={isUploading} />
      {isUploading && (
        <div className="progress-bar">
          <div style={{ width: `${progress.percentage}%` }} />
        </div>
      )}
      {error && <div className="error">{error}</div>}
    </div>
  );
}
```

**File Locations**:
- Backend: `backend/identity-service/src/main/java/com/pml/identity/web/rest/VerificationDocumentRestController.java`
- Frontend Client: `frontend/web/libs/shared/src/api/rest/documents.ts`
- Frontend Hook: `frontend/web/libs/shared/src/api/rest/useDocumentUpload.ts`
- Documentation: `docs/FILE_UPLOAD_ARCHITECTURE.md`

**References**:
- [Apollo File Upload Best Practices](https://www.apollographql.com/blog/file-upload-best-practices)
- [GraphQL File Uploads - WunderGraph](https://wundergraph.com/blog/graphql_file_uploads_evaluating_the_5_most_common_approaches)
- [AWS S3 Presigned URLs](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.md)

## Implementation Rules

### Backend Development

#### 1. Creating New Features

```java
// ALWAYS use reactive types for MongoDB operations
public Mono<Ticket> findById(String id) { ... }
public Flux<Ticket> findByEventId(String eventId) { ... }

// NEVER use blocking calls in WebFlux chain
// BAD:
.map(ticket -> repository.save(ticket).block())

// GOOD:
.flatMap(ticket -> repository.save(ticket))
```

#### 2. Event Publishing (Intra-Service)

```java
// Use ApplicationEventPublisher for internal events
@Service
public class PaymentService {
    private final ApplicationEventPublisher eventPublisher;

    public Mono<Payment> processPayment(PaymentRequest request) {
        return paymentGateway.charge(request)
            .doOnSuccess(payment -> {
                // Event persisted to PostgreSQL before listener executes
                eventPublisher.publishEvent(new PaymentCompletedEvent(payment));
            });
    }
}

// Handle with @ApplicationModuleListener for guaranteed delivery
@Service
public class PaymentEventListener {
    @ApplicationModuleListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        // This is guaranteed to run even if app crashes mid-processing
        // Event will be republished on restart if incomplete
    }
}
```

#### 3. Event Publishing (Cross-Service)

```java
// Use StreamBridge for cross-service events
@Service
public class TicketService {
    private final StreamBridge streamBridge;

    public Mono<Ticket> purchaseTicket(TicketPurchaseRequest request) {
        return createTicket(request)
            .doOnSuccess(ticket -> {
                // Publish to Azure Service Bus
                streamBridge.send("ticket-events-out-0",
                    new TicketPurchasedEvent(ticket));
            });
    }
}
```

#### 4. GraphQL Resolvers (Netflix DGS)

```java
@DgsComponent
public class EventQueryResolver {

    @DgsQuery
    public Mono<Event> event(@InputArgument String id) {
        return eventRepository.findById(id);
    }

    @DgsMutation
    public Mono<Event> createEvent(@InputArgument CreateEventInput input) {
        return eventService.create(input);
    }
}

// For Federation entity resolution
@DgsEntityFetcher(name = "Event")
public Mono<Event> resolveEvent(Map<String, Object> values) {
    String id = (String) values.get("id");
    return eventRepository.findById(id);
}
```

#### 5. GraphQL Schema Rules

```graphql
# schema.graphqls

# Federation 2 extension
extend schema @link(
    url: "https://specs.apollo.dev/federation/v2.9",
    import: ["@key", "@shareable", "@external", "@requires", "@provides", "@extends"]
)

# Stub type (can't resolve full entity, just reference)
type User @key(fields: "id", resolvable: false) {
    id: ID!
}

# Extension (add fields to type owned by another service)
extend type User @key(fields: "id") {
    # DO NOT add: id: ID! @external  <-- This causes "tried to redefine field" error
    purchasedTickets: [Ticket!]!
    totalSpent: BigDecimal!
}
```

#### 6. Security Configuration

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health/**").permitAll()
                .pathMatchers("/graphql").authenticated()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder()))
            )
            .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation(
            "http://localhost:8084/realms/event-ticketing"
        );
    }
}
```

### Frontend Development

#### 1. Apollo Client Setup

```typescript
// Use Apollo Client 4 with proper cache configuration
const client = new ApolloClient({
  uri: 'http://localhost:4000/graphql',
  cache: new InMemoryCache({
    typePolicies: {
      Event: {
        keyFields: ["id"],
      },
      Ticket: {
        keyFields: ["id"],
      },
    },
  }),
});
```

#### 2. GraphQL Code Generation (CRITICAL)

**All GraphQL types MUST come from codegen. Never define custom types for GraphQL operations.**

```bash
# Generate types from schema
cd frontend/web
npm run codegen
```

Uses `@graphql-codegen/cli` to generate TypeScript types from the federated schema.

**Workflow for adding/changing GraphQL types:**

1. **Backend First**: Add or modify the type in the backend GraphQL schema
   - Catalog Service: `backend/catalog-service/src/main/resources/graphql/schema.graphqls`
   - Identity Service: `backend/identity-service/src/main/resources/graphql/schema.graphqls`
   - Booking Service: `backend/booking-service/src/main/resources/graphql/schema.graphqls`

2. **Compose Supergraph**: Update the Apollo Router supergraph (if needed)
   ```bash
   cd docker/apollo-router
   rover supergraph compose --config supergraph.yaml > supergraph.graphql
   ```

3. **Regenerate Frontend Types**: Run codegen to update TypeScript types
   ```bash
   cd frontend/web
   npm run codegen
   ```

4. **Use Generated Types**: Import types from `@/types/graphql/schema-types` or `../../../types/graphql/schema-types`
   ```typescript
   import type { PageableInput, PagedEventResult, Event } from '../../../types/graphql/schema-types';
   ```

**Common pitfall to avoid:**
```typescript
// ❌ WRONG - Don't define custom types for GraphQL
type MyCustomPagination = {
  page: number;
  size: number;
};

// ✅ CORRECT - Use generated types
import type { PageableInput } from '../../../types/graphql/schema-types';

// Use Partial<T> if you need optional fields
function useMyHook(pageable?: Partial<PageableInput>) { ... }
```

#### 3. Authentication (Keycloak)

```typescript
// Web: Use keycloak-js
import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: 'http://localhost:8084',
  realm: 'event-ticketing',
  clientId: 'event-ticketing-admin',
});

// Mobile: Use expo-auth-session with PKCE
import * as AuthSession from 'expo-auth-session';
```

## Project Structure

```
ticketing-system/
├── backend/
│   ├── api-gateway/           # Spring Cloud Gateway
│   ├── booking-service/       # Tickets, Payments, Escrow
│   ├── catalog-service/       # Events, Locations, Categories
│   ├── identity-service/      # Users, Organizers, Roles
│   ├── shared-library/        # Shared DTOs, constants, utilities
│   └── keycloak-phone-otp/    # Custom Keycloak authenticator
│
├── frontend/
│   ├── web/
│   │   ├── apps/
│   │   │   ├── admin/         # Organizer dashboard (Next.js)
│   │   │   └── ticketing/     # Customer portal (Next.js)
│   │   └── libs/
│   │       └── shared/        # Shared components, hooks
│   └── mobile/                # React Native / Expo app
│
├── docker/
│   └── apollo-router/         # Apollo Router config & supergraph
│
└── infrastructure/
    └── docker/                # Docker compose files
```

## Common Patterns

### 1. Repository Pattern (Reactive)

```java
@Repository
public interface TicketRepository extends ReactiveMongoRepository<Ticket, String> {
    Flux<Ticket> findByEventId(String eventId);
    Mono<Long> countByEventIdAndStatus(String eventId, TicketStatus status);

    @Aggregation(pipeline = {
        "{ $match: { eventId: ?0, status: { $in: ['PURCHASED', 'CONFIRMED'] } } }",
        "{ $group: { _id: null, totalRevenue: { $sum: '$price' } } }"
    })
    Mono<BigDecimal> calculateRevenueByEventId(String eventId);
}
```

### 2. Service Layer Pattern

```java
@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StreamBridge streamBridge;

    @Transactional
    public Mono<Ticket> purchaseTicket(TicketPurchaseInput input, String buyerId) {
        return validatePurchase(input)
            .flatMap(this::createTicket)
            .flatMap(ticketRepository::save)
            .doOnSuccess(ticket -> {
                // Internal event (guaranteed delivery via PostgreSQL)
                eventPublisher.publishEvent(new TicketCreatedEvent(ticket));
                // External event (to other services via Azure Service Bus)
                streamBridge.send("ticket-events-out-0",
                    new TicketPurchasedEvent(ticket));
            });
    }
}
```

### 3. Error Handling

```java
// Define domain exceptions
public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String ticketId) {
        super("Ticket not found: " + ticketId);
    }
}

// Handle in GraphQL via @DgsExceptionHandler
@DgsComponent
public class GlobalExceptionHandler {
    @DgsExceptionHandler(TicketNotFoundException.class)
    public GraphQLError handleNotFound(TicketNotFoundException ex) {
        return GraphQLError.newError()
            .message(ex.getMessage())
            .errorType(ErrorType.NOT_FOUND)
            .build();
    }
}
```

## Testing

### Backend Testing

```java
// Use Spring Modulith test support
@ApplicationModuleTest
class BookingModuleTests {
    @Test
    void shouldPublishEventOnTicketPurchase() {
        // ...
    }
}

// Use WebFlux test support
@WebFluxTest(TicketController.class)
class TicketControllerTests {
    @Autowired
    private WebTestClient webClient;

    @Test
    void shouldReturnTicket() {
        webClient.get().uri("/tickets/{id}", "123")
            .exchange()
            .expectStatus().isOk();
    }
}
```

## Docker Resources

**IMPORTANT**: All Docker infrastructure is centralized in a **SEPARATE directory** (not inside ticketing-system):

```
/Users/lazarous.sinkololwe/Documents/Software Projects/personal/docker-resources/
```

**Directory Structure**:
```
docker-resources/
├── docker-compose.yml           # Main compose file for all services
├── apollo-router/               # Apollo Router configuration
│   ├── router.yaml              # Router runtime configuration
│   ├── supergraph.yaml          # Rover composition config
│   ├── supergraph.graphql       # Composed supergraph schema (generated)
│   └── compose-supergraph.sh    # Helper script to compose supergraph
├── postgres/                    # PostgreSQL configuration
├── mongodb/                     # MongoDB configuration
├── redis/                       # Redis configuration
└── keycloak/                    # Keycloak configuration
```

Start all services:
```bash
cd docker-resources
docker compose up -d
```

---

## Apollo Router & Supergraph Composition

> **Full documentation**: See `docs/APOLLO_FEDERATION_GUIDE.md` for comprehensive details.

**IMPORTANT**: The Apollo Router configuration is in `docker-resources/apollo-router/ticketing/`.

### Two Operating Modes

| Mode | Use Case | Start Command |
|------|----------|---------------|
| **GraphOS Mode** | Production, staging, team collaboration | `docker compose --profile ticketing up dev_ticketing_router_graphos` |
| **Local Mode** | Offline dev, schema experimentation | `docker compose --profile ticketing-local up dev_ticketing_router_local` |

### File Locations

```
docker-resources/apollo-router/ticketing/
├── router-graphos.yaml      # GraphOS managed federation config
├── router-local.yaml        # Local supergraph file config
├── supergraph.yaml          # Rover composition config
├── supergraph.graphql       # Composed supergraph (generated)
└── compose-supergraph.sh    # Helper script
```

### Quick Start

**Option A: GraphOS Mode (Recommended)**
```bash
# Router fetches supergraph from Apollo GraphOS
# Schema updates happen automatically when published via CI/CD
cd docker-resources
docker compose --profile ticketing up dev_ticketing_router_graphos
# Access: http://localhost:4000
```

**Option B: Local Mode (Offline Development)**
```bash
# 1. Compose supergraph locally
cd docker-resources/apollo-router/ticketing
./compose-supergraph.sh

# 2. Start router with local supergraph
cd ..
docker compose --profile ticketing-local up dev_ticketing_router_local
# Access: http://localhost:4001
```

### How Supergraph Updates Work

**GraphOS Mode**:
1. CI/CD runs `rover subgraph publish` on merge to main
2. GraphOS validates and composes the supergraph
3. Router polls GraphOS every 30 seconds
4. Router hot-swaps to new schema with zero downtime

**Local Mode**:
1. You modify a schema.graphqls file
2. You run `./compose-supergraph.sh` (or use `--watch` for auto)
3. Router detects file change (--hot-reload)
4. Router hot-swaps to new schema with zero downtime

**Key Insight**: You CANNOT combine both modes as a fallback. The router uses ONE source:
- Either GraphOS Uplink (managed) OR a local file (not both)

### Subgraph URL Configuration

Services running on host machine (not in Docker):
```yaml
# Router uses host.docker.internal to reach host services
override_subgraph_url:
  identity: http://host.docker.internal:8083/graphql
  catalog: http://host.docker.internal:8085/graphql
  booking: http://host.docker.internal:8082/graphql
```

Override via environment variables:
```bash
IDENTITY_SERVICE_URL=http://my-host:8083/graphql docker compose up ...
```

### CI/CD Integration

GitHub Actions workflow at `.github/workflows/graphql-schema.yml`:
- **On PR**: Runs `rover subgraph check` to validate schema changes
- **On merge to main**: Runs `rover subgraph publish` to update GraphOS

Required GitHub secrets:
- `APOLLO_KEY`: API key from Apollo Studio

### Frontend Codegen

After schema changes, regenerate frontend TypeScript types:

```bash
# Web frontend
cd frontend/web
npm run codegen

# Mobile frontend
cd frontend/mobile
npm run codegen
```

The codegen reads directly from backend `schema.graphqls` files, so it reflects schema changes immediately.

## Environment Variables

```bash
# Keycloak
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin

# PostgreSQL (shared)
POSTGRES_USER=app_user
POSTGRES_PASSWORD=app_password
POSTGRES_DB=shared_db

# MongoDB
MONGO_INITDB_ROOT_USERNAME=admin
MONGO_INITDB_ROOT_PASSWORD=admin123
MONGO_INITDB_DATABASE=ticketing

# Azure Service Bus
AZURE_SERVICEBUS_CONNECTION_STRING=<your-connection-string>
AZURE_SERVICEBUS_NAMESPACE=<your-namespace>

# PawaPay (Mobile Money)
PAWAPAY_API_URL=https://api.sandbox.pawapay.io
PAWAPAY_API_TOKEN=<your-token>
```

## Do's and Don'ts

### DO:
- Use reactive types (`Mono`, `Flux`) for all database operations
- Use `@ApplicationModuleListener` for intra-service events requiring guaranteed delivery
- Use `StreamBridge` for cross-service events via Azure Service Bus
- Define GraphQL types with `@key` directive for Federation
- Use Netflix DGS annotations (`@DgsComponent`, `@DgsQuery`, etc.)
- Validate input at the GraphQL layer using Bean Validation
- Use centralized Docker resources for infrastructure (see `docker-resources/` directory)
- Use `spring-cloud-starter-gateway-server-webflux` for API Gateway (not the deprecated generic starter)
- **Use GraphQL codegen for all TypeScript types** - Run `npm run codegen` to generate types from the federated schema
- **Use the existing Apollo Router setup** at `docker-resources/apollo-router/` - it already has `router.yaml`, `supergraph.yaml`, and composition scripts
- **Recompose supergraph after schema changes** - Run `rover supergraph compose` from `docker-resources/apollo-router/`

### DON'T:
- Don't use blocking calls (`.block()`) in reactive chains
- Don't redefine `id` field in GraphQL extension types
- Don't use Spring Modulith's MongoDB event publication (requires blocking driver)
- Don't create separate databases per service (use shared MongoDB with collection prefixes)
- Don't hardcode Keycloak URLs (use environment variables)
- Don't skip event publication for critical business operations
- Don't mix JDBC and reactive MongoDB in the same transaction
- Don't use the deprecated `spring-cloud-starter-gateway` (use `spring-cloud-starter-gateway-server-webflux`)
- Don't use `spring.cloud.gateway.*` namespace for WebFlux gateway config (use `spring.cloud.gateway.server.webflux.*`)
- **Don't create custom TypeScript types for GraphQL** - All GraphQL types must come from codegen. If a type is missing, add it to the backend schema first, then regenerate
- **Don't create new Apollo Router configurations** inside ticketing-system - use the existing setup at `docker-resources/apollo-router/ticketing/`
- **Don't assume Apollo Router doesn't exist** - check `docker-resources/apollo-router/ticketing/` first before creating any router configs
- **Use the correct router profile** - `ticketing` for GraphOS mode, `ticketing-local` for local development

## Troubleshooting

### "tried to redefine field 'id'" GraphQL Error
Remove `id: ID! @external` from `extend type` blocks. The stub type already defines `id`.

### Spring Modulith EventSerializer not found
Add `spring-modulith-starter-jdbc` dependency (not just `spring-modulith-events-jdbc`).

### Keycloak connection refused
Ensure Keycloak is running: `docker compose up -d dev_keycloak`

### MongoDB reactive driver issues
Ensure you're using `spring-boot-starter-data-mongodb-reactive`, not the blocking version.

### HikariPool connection failures
Check PostgreSQL is running and `modulith_events` schema exists in `shared_db`.

### Gateway routes not loading / "renamed configuration keys" warning
If using `spring-cloud-starter-gateway-server-webflux`, the configuration namespace is `spring.cloud.gateway.server.webflux.*`, NOT `spring.cloud.gateway.*`. Update your `application.yml` accordingly.

### "spring-cloud-starter-gateway is deprecated" warning
Replace the Maven dependency with `spring-cloud-starter-gateway-server-webflux` for reactive applications.
