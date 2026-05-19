# Naming Conventions

This document defines the naming conventions for the Identity Service to ensure consistency across the codebase.

## Table of Contents

1. [ID Field Naming](#id-field-naming)
2. [Timestamp Naming](#timestamp-naming)
3. [Status Field Naming](#status-field-naming)
4. [Package Naming](#package-naming)
5. [Class Naming](#class-naming)
6. [GraphQL Naming](#graphql-naming)

---

## ID Field Naming

### Primary Key: `id`
Every entity uses `id` as the primary key field name:

```java
@Id
private String id;
```

For MongoDB:
- User: `id` = Keycloak user ID (UUID string)
- Other entities: `id` = MongoDB ObjectId (auto-generated)

### Foreign Keys: `{entity}Id`
Foreign key references follow the pattern `{entityName}Id`:

| Field Name | References | Example Value |
|------------|------------|---------------|
| `userId` | User.id (= Keycloak ID) | "550e8400-e29b-41d4-a716-446655440000" |
| `organizationId` | Organization.id | "507f1f77bcf86cd799439011" |
| `organizerProfileId` | OrganizerProfile.id | "507f1f77bcf86cd799439012" |
| `eventId` | Event.id (Catalog service) | "507f1f77bcf86cd799439013" |

### User References
All user references use `userId` which equals the **Keycloak user ID**:

```java
// In OrganizationMember
private String userId;  // Keycloak user ID

// In Organization
private String ownerId;  // Keycloak user ID of owner

// In TeamInvitation
private String invitedById;  // Keycloak user ID of inviter

// In EventAccessGrant
private String grantedById;  // Keycloak user ID who granted access
private String revokedById;  // Keycloak user ID who revoked access
```

### Avoid Redundant IDs
Do NOT create redundant ID fields:

```java
// BAD - Redundant
@Id
private String id;
private String keycloakUserId;  // Redundant! Same as id

// GOOD - Single ID
@Id
private String id;  // This IS the Keycloak user ID
```

---

## Timestamp Naming

### Standard Timestamp Fields

| Field Name | Purpose | Type |
|------------|---------|------|
| `createdAt` | When entity was created | `Instant` |
| `updatedAt` | When entity was last modified | `Instant` |
| `deletedAt` | When entity was soft-deleted | `Instant` |

### Audit Timestamp Fields

| Field Name | Purpose |
|------------|---------|
| `createdBy` | User ID who created |
| `updatedBy` | User ID who last modified |

### Action Timestamps
Use past tense with `At` suffix:

| Field Name | Purpose |
|------------|---------|
| `joinedAt` | When member joined organization |
| `lastLoginAt` | Last login time |
| `lastActiveAt` | Last activity time |
| `submittedAt` | When application was submitted |
| `approvedAt` | When application was approved |
| `rejectedAt` | When application was rejected |
| `revokedAt` | When access was revoked |
| `verifiedAt` | When verification completed |
| `expiresAt` | When something expires |
| `grantedAt` | When access was granted |
| `acceptedAt` | When invitation was accepted |
| `declinedAt` | When invitation was declined |
| `cancelledAt` | When something was cancelled |
| `completedAt` | When process completed |
| `initiatedAt` | When process started |

### Use `Instant` Type
Always use `java.time.Instant` for timestamps:

```java
import java.time.Instant;

@CreatedDate
private Instant createdAt;

@LastModifiedDate
private Instant updatedAt;

private Instant lastLoginAt;
```

**Do NOT use:**
- `LocalDateTime` (timezone issues)
- `Date` (legacy, mutable)
- `Long` (epoch millis - unclear)

---

## Status Field Naming

### Entity Status: `status`
Main entity status uses the field name `status`:

```java
// User
private AccountStatus accountStatus;

// OrganizerProfile
private OrganizerStatus status;

// Organization
private OrganizationStatus status;

// OrganizationMember
private MemberStatus status;

// TeamInvitation
private InvitationStatus status;
```

### Boolean Flags
Boolean fields start with `is` or represent capability:

```java
// State flags
private boolean active;      // Not isActive (Lombok generates isActive())
private boolean locked;
private boolean verified;

// Verification flags
private boolean emailVerified;
private boolean phoneVerified;
private boolean identityVerified;
private boolean documentsVerified;
private boolean bankVerified;

// Feature flags
private boolean twoFactorEnabled;
```

---

## Package Naming

### Spring Boot Industry Standard Structure

```
com.pml.identity/
├── IdentityServiceApplication.java
│
├── domain/                          # Domain layer
│   ├── model/                       # JPA/MongoDB entities
│   │   ├── User.java
│   │   ├── Organization.java
│   │   └── ...
│   ├── enums/                       # Domain enumerations
│   │   ├── UserType.java
│   │   ├── OrganizationRole.java
│   │   └── ...
│   └── valueobject/                 # Value objects
│       ├── SocialLinks.java
│       └── OrganizationSettings.java
│
├── repository/                      # Data access layer
│   ├── UserRepository.java
│   ├── OrganizationRepository.java
│   └── ...
│
├── service/                         # Business logic interfaces
│   ├── UserService.java
│   ├── OrganizationService.java
│   └── impl/                        # Service implementations
│       ├── UserServiceImpl.java
│       └── OrganizationServiceImpl.java
│
├── web/                             # Presentation layer
│   ├── graphql/                     # GraphQL resolvers
│   │   ├── query/
│   │   │   ├── UserQueryResolver.java
│   │   │   └── OrganizationQueryResolver.java
│   │   ├── mutation/
│   │   │   ├── UserMutationResolver.java
│   │   │   └── OrganizationMutationResolver.java
│   │   └── dto/                     # GraphQL DTOs
│   │       ├── CreateOrganizationInput.java
│   │       └── UpdateUserInput.java
│   └── rest/                        # REST controllers
│       ├── InternalOtpController.java
│       └── KeycloakSyncController.java
│
├── config/                          # Configuration
│   ├── SecurityConfig.java
│   ├── MongoConfig.java
│   └── KeycloakConfig.java
│
├── infrastructure/                  # External integrations
│   ├── keycloak/
│   │   ├── KeycloakService.java
│   │   └── KeycloakGroupManager.java
│   ├── messaging/
│   │   └── EventPublisher.java
│   └── cache/
│       └── RedisOtpStore.java
│
├── scheduler/                       # Scheduled tasks
│   └── ExpirationCleanupScheduler.java
│
└── exception/                       # Custom exceptions
    ├── UserNotFoundException.java
    └── OrganizationNotFoundException.java
```

---

## Class Naming

### Entity Classes
- Singular noun: `User`, `Organization`, `OrganizerProfile`
- No suffix needed for main entities

### Repository Interfaces
- Entity name + `Repository`: `UserRepository`, `OrganizationRepository`
- Extends `ReactiveMongoRepository<Entity, String>`

### Service Interfaces
- Entity name + `Service`: `UserService`, `OrganizationService`
- Define contract only, no implementation details

### Service Implementations
- Service interface name + `Impl`: `UserServiceImpl`, `OrganizationServiceImpl`
- Located in `service/impl/` package

### GraphQL Resolvers
- Query: `{Entity}QueryResolver`: `UserQueryResolver`
- Mutation: `{Entity}MutationResolver`: `UserMutationResolver`

### DTOs/Input Types
- Create: `Create{Entity}Input`: `CreateOrganizationInput`
- Update: `Update{Entity}Input`: `UpdateOrganizationInput`
- Response: `{Entity}Response` or just use entity

### Enums
- Singular noun with purpose: `UserType`, `OrganizationRole`, `MemberStatus`
- Values in UPPER_SNAKE_CASE: `PENDING_REVIEW`, `SUPER_ADMIN`

---

## GraphQL Naming

### Types
- PascalCase: `User`, `Organization`, `OrganizationMember`
- Match Java entity names

### Fields
- camelCase: `firstName`, `organizationId`, `createdAt`
- Match Java field names

### Queries
- camelCase, descriptive: `user`, `userByEmail`, `organizationBySlug`
- Plural for lists: `users`, `organizations`, `myOrganizations`

### Mutations
- camelCase, verb prefix: `createOrganization`, `updateUser`, `inviteTeamMember`
- Action-oriented: `approveOrganizer`, `rejectOrganizer`, `transferOwnership`

### Enums
- UPPER_SNAKE_CASE values: `PENDING_REVIEW`, `OWNER`, `ADMIN`

### Input Types
- Suffix with `Input`: `CreateOrganizationInput`, `UpdateUserInput`
- Group related fields logically

### Example Schema Naming
```graphql
type User @key(fields: "id") {
    id: ID!
    firstName: String!
    lastName: String!
    organizationMemberships: [OrganizationMember!]
    primaryOrganization: Organization
    createdAt: DateTime!
}

input CreateUserInput {
    email: String!
    firstName: String!
    lastName: String!
}

type Query {
    user(id: ID!): User
    userByEmail(email: String!): User
    users(page: PageableInput): PagedUserResult!
}

type Mutation {
    createUser(input: CreateUserInput!): User!
    updateUser(id: ID!, input: UpdateUserInput!): User!
    deleteUser(id: ID!): Boolean!
}
```
