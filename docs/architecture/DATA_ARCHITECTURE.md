# Data Architecture - Identity Service

This document describes the data architecture for the Identity Service, including entity relationships, data ownership, and Keycloak integration.

## Table of Contents

1. [Entity Hierarchy Overview](#entity-hierarchy-overview)
2. [Entity Relationships](#entity-relationships)
3. [Data Ownership Rules](#data-ownership-rules)
4. [Keycloak Integration](#keycloak-integration)
5. [ID Field Naming Conventions](#id-field-naming-conventions)
6. [Timestamp Standards](#timestamp-standards)

---

## Entity Hierarchy Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           KEYCLOAK (Source of Truth)                            │
│                                                                                 │
│   ┌─────────────────────────────────────────────────────────────────────────┐  │
│   │  KEYCLOAK USER                                                           │  │
│   │  ├── id (UUID) ─────────────────────────────────────────────────────┐   │  │
│   │  ├── username                                                       │   │  │
│   │  ├── email, firstName, lastName                                     │   │  │
│   │  ├── phoneNumber (attribute)                                        │   │  │
│   │  ├── emailVerified, phoneVerified                                   │   │  │
│   │  └── realm_access.roles: [CUSTOMER, ORGANIZER, ADMIN]               │   │  │
│   └─────────────────────────────────────────────────────────────────────│───┘  │
│                                                                         │       │
│   ┌─────────────────────────────────────────────────────────────────────│───┐  │
│   │  KEYCLOAK GROUPS (Organization Multi-Tenancy)                       │   │  │
│   │  └── /organizations                                                 │   │  │
│   │      └── /{org-slug}                                                │   │  │
│   │          ├── /owners    ◄── User added here on approval             │   │  │
│   │          ├── /admins                                                │   │  │
│   │          ├── /managers                                              │   │  │
│   │          ├── /marketers                                             │   │  │
│   │          └── /contributors                                          │   │  │
│   └─────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ User.id = Keycloak user ID
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           MONGODB (Business Data)                               │
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────────┐│
│  │                              USER COLLECTION                                ││
│  │  ┌──────────────────────────────────────────────────────────────────────┐  ││
│  │  │  User                                                                 │  ││
│  │  │  ├── id (= Keycloak user ID, single source)                          │  ││
│  │  │  ├── username, email, firstName, lastName  ◄── CACHED from Keycloak  │  ││
│  │  │  ├── phoneNumber, phoneVerified, emailVerified ◄── CACHED            │  ││
│  │  │  ├── userType: CUSTOMER | ORGANIZER | ADMIN                          │  ││
│  │  │  ├── avatarUrl, bio, dateOfBirth (App-specific profile)              │  ││
│  │  │  ├── locale, timezone (Preferences)                                  │  ││
│  │  │  ├── primaryOrganizationId ─────────────────────────────┐            │  ││
│  │  │  └── timestamps: Instant                                │            │  ││
│  │  └─────────────────────────────────────────────────────────│────────────┘  ││
│  └────────────────────────────────────────────────────────────│────────────────┘│
│                                                               │                 │
│  ┌────────────────────────────────────────────────────────────│────────────────┐│
│  │                        ORGANIZER_PROFILES COLLECTION       │                ││
│  │  ┌─────────────────────────────────────────────────────────│────────────┐  ││
│  │  │  OrganizerProfile                                       │            │  ││
│  │  │  ├── id (MongoDB ObjectId)                              │            │  ││
│  │  │  ├── userId (= Keycloak user ID)                        │            │  ││
│  │  │  ├── companyName, companyDescription                    │            │  ││
│  │  │  ├── taxId, businessRegistrationNumber (KYB data)       │            │  ││
│  │  │  ├── businessPhone, businessEmail, businessAddress      │            │  ││
│  │  │  ├── status: OrganizerStatus (DRAFT→APPROVED)           │            │  ││
│  │  │  ├── verified, documentsVerified, bankVerified          │            │  ││
│  │  │  └── timestamps: Instant                                │            │  ││
│  │  └─────────────────────────────────────────────────────────│────────────┘  ││
│  └────────────────────────────────────────────────────────────│────────────────┘│
│                                                               │                 │
│  ┌────────────────────────────────────────────────────────────│────────────────┐│
│  │                        ORGANIZATIONS COLLECTION            │                ││
│  │  ┌─────────────────────────────────────────────────────────│────────────┐  ││
│  │  │  Organization                                           │            │  ││
│  │  │  ├── id (MongoDB ObjectId) ◄────────────────────────────┘            │  ││
│  │  │  ├── name, slug (unique)                                             │  ││
│  │  │  ├── description, logoUrl, bannerUrl (PUBLIC branding)               │  ││
│  │  │  ├── organizerProfileId (FK to OrganizerProfile)                     │  ││
│  │  │  ├── ownerId (= Keycloak user ID of owner)                           │  ││
│  │  │  ├── keycloakGroupId                                                 │  ││
│  │  │  ├── status: OrganizationStatus                                      │  ││
│  │  │  └── timestamps: Instant                                             │  ││
│  │  └──────────────────────────────────────────────────────────────────────┘  ││
│  └────────────────────────────────────────────────────────────────────────────┘│
│                                                                                │
│  ┌────────────────────────────────────────────────────────────────────────────┐│
│  │                     ORGANIZATION_MEMBERS COLLECTION                        ││
│  │  ┌──────────────────────────────────────────────────────────────────────┐  ││
│  │  │  OrganizationMember                                                   │  ││
│  │  │  ├── id (MongoDB ObjectId)                                           │  ││
│  │  │  ├── userId (= Keycloak user ID)                                     │  ││
│  │  │  ├── organizationId (FK to Organization)                             │  ││
│  │  │  ├── role: OWNER | ADMIN | MANAGER | MARKETER | CONTRIBUTOR          │  ││
│  │  │  ├── customPermissions, deniedPermissions                            │  ││
│  │  │  └── timestamps: Instant                                             │  ││
│  │  └──────────────────────────────────────────────────────────────────────┘  ││
│  └────────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Entity Relationships

### User → OrganizerProfile (1:0..1)
- A User may have zero or one OrganizerProfile
- OrganizerProfile is created when User applies to become an organizer
- Link: `OrganizerProfile.userId` → `User.id`

### OrganizerProfile → Organization (1:0..1)
- An approved OrganizerProfile has exactly one Organization
- Organization is created automatically when OrganizerProfile is approved
- Link: `Organization.organizerProfileId` → `OrganizerProfile.id`
- **Note**: This is a ONE-WAY reference. OrganizerProfile does NOT store organizationId.

### Organization → OrganizationMember (1:N)
- An Organization has many OrganizationMembers
- Link: `OrganizationMember.organizationId` → `Organization.id`

### User → OrganizationMember (1:N)
- A User can be a member of multiple Organizations
- Link: `OrganizationMember.userId` → `User.id` (= Keycloak user ID)

### Organization → TeamInvitation (1:N)
- An Organization has many pending TeamInvitations
- Link: `TeamInvitation.organizationId` → `Organization.id`

### User → EventAccessGrant (1:N)
- A User can have access to multiple Events
- Link: `EventAccessGrant.userId` → `User.id`

---

## Data Ownership Rules

### Keycloak Owns (Source of Truth)
| Data | Location | Notes |
|------|----------|-------|
| Authentication | Keycloak | Passwords, credentials |
| Account Status | Keycloak | enabled, locked |
| Email/Phone Verification | Keycloak | emailVerified, phoneVerified |
| Platform Roles | Keycloak | CUSTOMER, ORGANIZER, ADMIN |
| Group Memberships | Keycloak | /organizations/{slug}/{role} |

### MongoDB Owns (Source of Truth)
| Data | Collection | Notes |
|------|------------|-------|
| User Profile | users | avatarUrl, bio, preferences |
| Business Data | organizer_profiles | companyName, taxId, KYB data |
| Organization | organizations | Public entity, branding |
| Team Membership | organization_members | Roles, permissions |
| Workflow | team_invitations | Invitation status |
| Statistics | users, organizations | Counts, totals |

### Cached Data (Synced from Keycloak)
| Data | MongoDB Field | Keycloak Source |
|------|---------------|-----------------|
| username | User.username | Keycloak username |
| email | User.email | Keycloak email |
| firstName | User.firstName | Keycloak firstName |
| lastName | User.lastName | Keycloak lastName |
| phoneNumber | User.phoneNumber | Keycloak phone_number attribute |

---

## Keycloak Integration

### Group Structure
```
/organizations
├── /acme-events                    ← Organization slug
│   ├── /owners                     ← Owner (exactly 1)
│   ├── /admins                     ← Administrators
│   ├── /managers                   ← Event managers
│   ├── /marketers                  ← Marketing team
│   └── /contributors               ← Contributors
└── /another-org
    └── ...
```

### Sync Strategy

#### Keycloak → MongoDB (User Profile Sync)
- **Trigger**: Keycloak events (REGISTER, UPDATE_PROFILE, LOGIN)
- **Method**: UserSyncEventListener → REST call to Identity Service
- **Data**: username, email, firstName, lastName, phoneNumber, roles

#### MongoDB → Keycloak (Organization Membership)
- **Trigger**: OrganizationMember created/updated/deleted
- **Method**: OrganizationMemberService calls KeycloakService
- **Data**: Add/remove user from /organizations/{slug}/{role} group

---

## ID Field Naming Conventions

### Rule 1: `id` = Primary Key
```java
@Id
private String id;  // MongoDB ObjectId or Keycloak UUID
```

### Rule 2: User References Use `userId`
All references to User use `userId` which equals the Keycloak user ID:
```java
private String userId;        // = Keycloak user ID
private String ownerId;       // = Keycloak user ID (owner)
private String invitedById;   // = Keycloak user ID (inviter)
private String grantedById;   // = Keycloak user ID (granter)
```

### Rule 3: Entity References Use `{entity}Id`
```java
private String organizationId;      // → Organization.id
private String organizerProfileId;  // → OrganizerProfile.id
private String eventId;             // → Event.id (Catalog service)
```

### Rule 4: No Redundant ID Fields
- **REMOVED**: `User.keycloakUserId` (User.id IS the Keycloak ID)
- **REMOVED**: `OrganizerProfile.organizationId` (query via Organization.organizerProfileId)

---

## Timestamp Standards

### Use `Instant` Everywhere
```java
import java.time.Instant;

@CreatedDate
private Instant createdAt;

@LastModifiedDate
private Instant updatedAt;

private Instant lastLoginAt;
private Instant joinedAt;
```

### Why Instant?
1. **Timezone-agnostic**: Stores UTC timestamp
2. **Consistent**: Same type across all entities
3. **Distributed systems friendly**: No timezone conversion issues
4. **Industry standard**: Recommended for microservices

### Conversion in GraphQL
```java
// GraphQL returns DateTime scalar
// Jackson serializes Instant as ISO-8601 string
// Example: "2024-03-15T10:30:00Z"
```

---

## Migration Notes

### Fields Removed from User
| Field | Reason | New Location |
|-------|--------|--------------|
| `keycloakUserId` | Redundant with `id` | N/A |
| `companyName` | Business data | OrganizerProfile |
| `taxId` | Business data | OrganizerProfile |
| `businessPhone` | Business data | OrganizerProfile |
| `businessEmail` | Business data | OrganizerProfile |
| `organizerApprovalStatus` | Business workflow | OrganizerProfile.status |
| `organizerApprovalNote` | Business workflow | OrganizerProfile.rejectionReason |

### Fields Removed from OrganizerProfile
| Field | Reason | New Location |
|-------|--------|--------------|
| `logoUrl` | Public branding | Organization.logoUrl |
| `bannerUrl` | Public branding | Organization.bannerUrl |
| `organizationId` | Bidirectional | Query via Organization.organizerProfileId |

### Timestamp Type Changes
| Entity | Before | After |
|--------|--------|-------|
| User.createdAt | LocalDateTime | Instant |
| User.updatedAt | LocalDateTime | Instant |
| User.lastLoginAt | LocalDateTime | Instant |
