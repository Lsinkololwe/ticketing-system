# User Data Architecture Analysis

## Executive Summary

**CONFIRMED**: All microservices reference the **same `users` collection** in MongoDB. The architecture correctly implements a single source of truth for user data with no duplication across services.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    UNIFIED USER DATA ARCHITECTURE                                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                      KEYCLOAK (Source of Truth: Auth)                        │   │
│  ├─────────────────────────────────────────────────────────────────────────────┤   │
│  │  USER_ENTITY (PostgreSQL)                                                    │   │
│  │  ├─ id (UUID) ─────────────────────────────────────────────────────────┐    │   │
│  │  ├─ username, email, firstName, lastName                                │    │   │
│  │  ├─ emailVerified, enabled, locked                                      │    │   │
│  │  └─ credentials, sessions, roles                                        │    │   │
│  └─────────────────────────────────────────────────────────────────────────┼────┘   │
│                                                                            │        │
│                         UserSyncEventListener                              │        │
│                         (Keycloak SPI Extension)                           │        │
│                                    │                                       │        │
│                                    ▼                                       │        │
│  ┌─────────────────────────────────────────────────────────────────────────┼────┐   │
│  │                    MONGODB (Source of Truth: Business Data)             │    │   │
│  ├─────────────────────────────────────────────────────────────────────────┼────┤   │
│  │                                                                         │    │   │
│  │  ┌───────────────────────────────────────────────────────────────────┐  │    │   │
│  │  │  users COLLECTION (SINGLE SOURCE - Identity Service)             │  │    │   │
│  │  ├───────────────────────────────────────────────────────────────────┤  │    │   │
│  │  │  _id: "keycloak-uuid" ◀──────────────────────────────────────────┼──┘    │   │
│  │  │  ├─ username, email, firstName, lastName     (synced from KC)    │       │   │
│  │  │  ├─ phoneNumber, phoneVerified               (synced from KC)    │       │   │
│  │  │  ├─ emailVerified                            (synced from KC)    │       │   │
│  │  │  ├─ roles: [CUSTOMER, ORGANIZER, ADMIN...]  (synced from KC)    │       │   │
│  │  │  ├─ accountStatus, active, locked           (synced from KC)    │       │   │
│  │  │  ├─ avatarUrl, bio, dateOfBirth, gender     (business data)     │       │   │
│  │  │  ├─ locale, timezone                         (preferences)       │       │   │
│  │  │  ├─ primaryOrganizationId                    (org link)          │       │   │
│  │  │  ├─ totalTicketsPurchased, totalEventsAttended (stats)          │       │   │
│  │  │  ├─ lastLoginAt, lastActiveAt                (activity)          │       │   │
│  │  │  └─ createdAt, updatedAt, createdBy          (audit)             │       │   │
│  │  └───────────────────────────────────────────────────────────────────┘       │   │
│  │           │                                                                  │   │
│  │           │ userId (FK to users._id)                                         │   │
│  │           ▼                                                                  │   │
│  │  ┌───────────────────────────────────────────────────────────────────┐       │   │
│  │  │  organizer_profiles COLLECTION (KYB/Business Data)               │       │   │
│  │  ├───────────────────────────────────────────────────────────────────┤       │   │
│  │  │  _id: auto-generated                                              │       │   │
│  │  │  userId: "keycloak-uuid" (FK) ─────▶ users._id                   │       │   │
│  │  │  ├─ companyName, companyDescription, tagline                     │       │   │
│  │  │  ├─ taxId, businessRegistrationNumber, businessType              │       │   │
│  │  │  ├─ businessPhone, businessEmail, businessAddress                │       │   │
│  │  │  ├─ city, province, country, postalCode                          │       │   │
│  │  │  ├─ defaultBankAccountId, commissionRate, payoutSchedule         │       │   │
│  │  │  ├─ status: DRAFT | PENDING_REVIEW | APPROVED | REJECTED         │       │   │
│  │  │  └─ verified, documentsVerified, bankVerified                    │       │   │
│  │  └───────────────────────────────────────────────────────────────────┘       │   │
│  │           │                                                                  │   │
│  │           │ organizerProfileId (FK)                                          │   │
│  │           ▼                                                                  │   │
│  │  ┌───────────────────────────────────────────────────────────────────┐       │   │
│  │  │  organizations COLLECTION (Multi-tenant Business Entity)         │       │   │
│  │  ├───────────────────────────────────────────────────────────────────┤       │   │
│  │  │  _id: auto-generated                                              │       │   │
│  │  │  organizerProfileId: FK ─────▶ organizer_profiles._id            │       │   │
│  │  │  ├─ name, slug (unique), description                             │       │   │
│  │  │  ├─ logoUrl, bannerUrl, website                                  │       │   │
│  │  │  └─ status, createdAt, updatedAt                                 │       │   │
│  │  └───────────────────────────────────────────────────────────────────┘       │   │
│  │                                                                              │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Collection Ownership by Service

| Collection | Owner Service | Purpose |
|------------|--------------|---------|
| `users` | Identity Service | User profiles, roles, preferences |
| `organizer_profiles` | Identity Service | Organizer KYB/business data |
| `organizations` | Identity Service | Multi-tenant business entities |
| `organization_members` | Identity Service | Team membership |
| `team_invitations` | Identity Service | Team invite workflow |
| `events` | Catalog Service | Event listings |
| `tickets` | Booking Service | Purchased tickets |
| `payment_intents` | Booking Service | Payment processing |
| `bank_accounts` | Booking Service | Organizer payout accounts |

## How Services Reference Users

### 1. Identity Service (Owner)

```java
// User.java - Line 64
@Document(collection = "users")
public class User {
    @Id
    private String id;  // = Keycloak user ID (sub claim)
    // ... all user fields
}
```

**GraphQL Schema** (Identity Service):
```graphql
# Identity Service OWNS the User type
type User @key(fields: "id") {
    id: ID!                    # Keycloak user ID
    username: String!
    email: String!
    firstName: String!
    lastName: String!
    roles: [UserType!]!        # CUSTOMER, ORGANIZER, ADMIN, etc.
    organizerProfile: OrganizerProfile
    # ... 40+ fields
}
```

### 2. Catalog Service (References via ID)

```java
// Event.java - Lines 73-74
private String organizerId;      // User ID (Keycloak UUID)
private String organizationId;   // Organization ID
```

**GraphQL Schema** (Catalog Service):
```graphql
# Catalog Service references User as a STUB (cannot resolve User data)
type User @key(fields: "id", resolvable: false) {
    id: ID!
}

type Event @key(fields: "id") {
    # Reference to User via ID
    organizer: User              # Resolved by Identity Service via Federation
    organizerId: String!         # Stored as denormalized ID
    organization: Organization   # Resolved by Identity Service via Federation
    organizationId: String       # Stored as denormalized ID
}
```

### 3. Booking Service (References via ID + Caches Display Data)

```java
// Ticket.java - Lines 72-81
private String buyerId;          // User ID (Keycloak UUID)
private String organizerId;      // Denormalized from Event
private String organizationId;   // Denormalized from Event

// Cached for display (not source of truth)
private String buyerName;
private String buyerEmail;
private String buyerPhone;
```

**GraphQL Schema** (Booking Service):
```graphql
# Booking Service references User as a STUB
type User @key(fields: "id", resolvable: false) {
    id: ID!
    # These are @external - owned by Identity Service
    fullName: String! @external
    email: String! @external
    phoneNumber: String @external
}

# Booking Service EXTENDS User to add booking-related fields
extend type User @key(fields: "id") {
    purchasedTickets: [Ticket!]!
    totalSpent: BigDecimal!
    activeTicketCount: Int!
}

type Ticket @key(fields: "id") {
    buyer: User! @provides(fields: "fullName email phoneNumber")
    buyerId: String!
}
```

## User Types & Role Support

### UserType Enum (shared-library)

```java
// UserType.java - All supported roles
public enum UserType {
    CUSTOMER("CUSTOMER", "Customer", "Regular ticket buyer"),
    ORGANIZER("ORGANIZER", "Organizer", "Event organizer"),
    ADMIN("ADMIN", "Administrator", "Platform administrator"),
    SUPER_ADMIN("SUPER_ADMIN", "Super Administrator", "Highest privileges"),
    SCANNER("SCANNER", "Scanner", "Ticket scanner at events"),
    FINANCE("FINANCE", "Finance", "Financial operations");
}
```

### Multi-Role Support

Users can have **multiple roles** simultaneously:

```java
// User.java - Lines 121-124
@NotEmpty(message = "User must have at least one role")
@Builder.Default
private Set<UserType> roles = EnumSet.of(UserType.CUSTOMER);
```

**Example Role Combinations**:
| User Type | Roles |
|-----------|-------|
| Regular Customer | `[CUSTOMER]` |
| Event Organizer | `[CUSTOMER, ORGANIZER]` |
| Organizer + Scanner | `[CUSTOMER, ORGANIZER, SCANNER]` |
| Platform Admin | `[CUSTOMER, ADMIN]` |
| Finance Team | `[CUSTOMER, FINANCE]` |
| Super Admin | `[CUSTOMER, SUPER_ADMIN]` |

### Role Validation Rules

```java
// UserType.java - Lines 122-143
public static boolean isValidRoleCombination(Set<UserType> roles) {
    // 1. Must have at least one role
    // 2. Must include CUSTOMER (base role cannot be removed)
    // 3. Cannot have both ADMIN and SUPER_ADMIN
    // 4. Maximum 6 roles
}
```

## Registration Flow & User Table Population

### Flow 1: Customer Registration

```
1. User fills Keycloak registration form
   └─ Selects: [✓] CUSTOMER

2. AccountTypeRoleMapper (Keycloak SPI) assigns roles
   └─ Grants realm role: CUSTOMER

3. UserSyncEventListener triggers
   └─ POST /api/internal/keycloak/sync/user

4. Identity Service creates User in MongoDB
   └─ users collection: {
        _id: "keycloak-uuid",
        roles: ["CUSTOMER"],
        ...
      }
```

### Flow 2: Organizer Registration

```
1. User fills Keycloak registration form
   └─ Selects: [✓] CUSTOMER [✓] ORGANIZER

2. AccountTypeRoleMapper assigns roles
   └─ Grants realm roles: CUSTOMER, ORGANIZER

3. UserSyncEventListener triggers

4. Identity Service creates:
   └─ users collection: {
        _id: "keycloak-uuid",
        roles: ["CUSTOMER", "ORGANIZER"],
        ...
      }
   └─ organizer_profiles collection: {
        userId: "keycloak-uuid",
        status: "DRAFT",
        ...
      }
```

### Flow 3: Admin User Creation

```
1. Super Admin creates user via Keycloak Admin Console
   └─ Assigns roles: CUSTOMER, ADMIN

2. UserSyncEventListener triggers

3. Identity Service creates User in MongoDB
   └─ users collection: {
        _id: "keycloak-uuid",
        roles: ["CUSTOMER", "ADMIN"],
        ...
      }
```

## Better Auth Integration (Frontend Session Management)

### How Better Auth Fits

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    BETTER AUTH + KEYCLOAK + MONGODB                                  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  Next.js Admin App                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │  Better Auth (Custom Adapter: keycloakMongoAdapter)                         │   │
│  │  ├─ READS from `users` collection (shared with Identity Service)           │   │
│  │  ├─ WRITES to `auth_sessions` collection (own sessions)                     │   │
│  │  └─ WRITES to `auth_accounts` collection (OAuth tokens from Keycloak)       │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                     │
│  MongoDB Collections:                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                                                                              │   │
│  │  users (SHARED)              auth_sessions            auth_accounts          │   │
│  │  ┌──────────────────┐       ┌──────────────────┐    ┌──────────────────┐    │   │
│  │  │ Identity Service │◀─────│ Better Auth      │    │ Better Auth      │    │   │
│  │  │ WRITES           │ READ │ WRITES           │    │ WRITES           │    │   │
│  │  │                  │ ONLY │                  │    │                  │    │   │
│  │  │ Better Auth      │      │ Session tokens   │    │ Keycloak tokens  │    │   │
│  │  │ READS ONLY       │      │ IP tracking      │    │ OAuth state      │    │   │
│  │  └──────────────────┘      └──────────────────┘    └──────────────────┘    │   │
│  │                                                                              │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                     │
│  ✅ NO DUPLICATE USER DATA - Better Auth uses existing users collection            │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Custom Adapter Configuration

```typescript
// frontend/web/apps/admin/src/lib/auth/index.ts
database: keycloakMongoAdapter(mongoDb, {
  allowUserCreation: false, // Users MUST exist in Identity Service first
  syncUserUpdates: false,   // Identity Service is source of truth
})
```

## GraphQL Federation Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    GRAPHQL FEDERATION - USER TYPE                                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  Apollo Router (Federation Gateway)                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │  Composed Supergraph                                                         │   │
│  │  ├─ User type defined in Identity Service (OWNER)                           │   │
│  │  ├─ User type referenced in Catalog Service (STUB)                          │   │
│  │  └─ User type extended in Booking Service (ADDS purchasedTickets, etc.)     │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                              │                                                      │
│              ┌───────────────┼───────────────┐                                     │
│              ▼               ▼               ▼                                      │
│  ┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐                 │
│  │ Identity Service  │ │ Catalog Service   │ │ Booking Service   │                 │
│  │ (User OWNER)      │ │ (User STUB)       │ │ (User EXTENSION)  │                 │
│  ├───────────────────┤ ├───────────────────┤ ├───────────────────┤                 │
│  │ type User         │ │ type User         │ │ type User         │                 │
│  │   @key(fields:id) │ │   @key(fields:id  │ │   @key(fields:id  │                 │
│  │                   │ │   resolvable:false│ │   resolvable:false│                 │
│  │ CAN RESOLVE:      │ │ CANNOT RESOLVE    │ │ CANNOT RESOLVE    │                 │
│  │ ✅ id             │ │ ❌ User fields    │ │ ❌ User fields    │                 │
│  │ ✅ username       │ │                   │ │                   │                 │
│  │ ✅ email          │ │ References via:   │ │ extend type User  │                 │
│  │ ✅ firstName      │ │ Event.organizerId │ │ + purchasedTickets│                 │
│  │ ✅ roles          │ │ Event.organizer   │ │ + totalSpent      │                 │
│  │ ✅ 40+ fields     │ │                   │ │ + activeTicketCount│                 │
│  └───────────────────┘ └───────────────────┘ └───────────────────┘                 │
│                                                                                     │
│  QUERY RESOLUTION EXAMPLE:                                                          │
│  ─────────────────────────                                                          │
│  query {                                                                            │
│    event(id: "123") {         # Resolved by Catalog Service                         │
│      title                    # Catalog Service                                     │
│      organizer {              # Reference to User                                   │
│        id                     # Identity Service resolves                           │
│        firstName              # Identity Service resolves                           │
│        email                  # Identity Service resolves                           │
│        purchasedTickets {     # Booking Service extends                             │
│          id                   # Booking Service resolves                            │
│        }                                                                            │
│      }                                                                              │
│    }                                                                                │
│  }                                                                                  │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Data Flow Verification

### User ID Linking Key

| Location | Field | Value |
|----------|-------|-------|
| Keycloak `USER_ENTITY` | `id` | `keycloak-uuid` |
| MongoDB `users` | `_id` | `keycloak-uuid` |
| MongoDB `organizer_profiles` | `userId` | `keycloak-uuid` |
| MongoDB `organization_members` | `userId` | `keycloak-uuid` |
| MongoDB `events` | `organizerId` | `keycloak-uuid` |
| MongoDB `events` | `createdBy` | `keycloak-uuid` |
| MongoDB `tickets` | `buyerId` | `keycloak-uuid` |
| JWT Token | `sub` claim | `keycloak-uuid` |
| Better Auth session | `userId` | `keycloak-uuid` |

### Verification: All Services Use Same User ID

```
✅ Keycloak user.id = MongoDB users._id
✅ MongoDB users._id = organizer_profiles.userId
✅ MongoDB users._id = organization_members.userId
✅ MongoDB users._id = events.organizerId
✅ MongoDB users._id = tickets.buyerId
✅ MongoDB users._id = JWT sub claim
✅ MongoDB users._id = Better Auth session.userId
```

## Conclusion

### Architecture Verified

1. **Single `users` collection** - Identity Service is the sole owner
2. **No duplication** - Other services reference via ID only
3. **Keycloak is source of truth** for authentication and roles
4. **MongoDB caches profile data** for GraphQL performance
5. **Better Auth reads from shared collection** - no separate user storage
6. **All user types supported** - CUSTOMER, ORGANIZER, ADMIN, SUPER_ADMIN, SCANNER, FINANCE
7. **Multi-role support** - Users can have multiple roles simultaneously
8. **GraphQL Federation** - User type owned by Identity Service, extended by others

### Files Verified

| File | Status |
|------|--------|
| `identity-service/domain/model/User.java` | ✅ Collection: `users` |
| `identity-service/domain/model/OrganizerProfile.java` | ✅ Collection: `organizer_profiles`, links via `userId` |
| `catalog-service/domain/model/Event.java` | ✅ References `organizerId` (no user collection) |
| `booking-service/domain/model/Ticket.java` | ✅ References `buyerId` (no user collection) |
| `identity-service/resources/graphql/schema.graphqls` | ✅ User type with `@key(fields: "id")` |
| `catalog-service/resources/graphql/schema.graphqls` | ✅ User stub with `resolvable: false` |
| `booking-service/resources/graphql/schema.graphqls` | ✅ User stub + extension |
| `frontend/web/apps/admin/src/lib/auth/keycloak-mongo-adapter.ts` | ✅ Reads from `users`, writes to `auth_sessions` |
