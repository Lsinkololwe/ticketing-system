# User Stories Documentation - Event Ticketing Platform

**Document Version:** 3.0
**Last Updated:** 2026-03-18
**Authors:** Platform Engineering Team
**Target Audience:** Senior Engineers, Solutions Architects, Technical Leads
**Review Status:** Expert Review Ready

---

## Executive Summary

This document provides comprehensive user stories and technical specifications for the event ticketing platform, structured with the **Organizational Hierarchy Model** at its core.

**Key Design Principles:**
1. **Multi-tenant Architecture**: Organizations are isolated tenants with their own team members
2. **Role-Based Access Control (RBAC)**: Three-tier permission hierarchy (Platform → Organization → Event)
3. **Staged Organizer Onboarding**: Progressive access based on verification status
4. **Keycloak Integration**: Centralized identity with synchronized MongoDB profiles

**Architecture Context:** 3-microservice design (Catalog, Booking, Identity) using MongoDB, Spring Modulith Events, and GraphQL Federation 2.

---

## Table of Contents

### Part I: Organizational Hierarchy & Identity
1. [Platform Role Hierarchy](#1-platform-role-hierarchy)
2. [Organization Hierarchy](#2-organization-hierarchy)
3. [Event-Level Roles](#3-event-level-roles)
4. [Organizer Onboarding Flow](#4-organizer-onboarding-flow)
5. [Team Management](#5-team-management)
6. [Keycloak Integration Architecture](#6-keycloak-integration-architecture)

### Part II: CRUD Operations & Business Logic
7. [User CRUD Operations](#7-user-crud-operations)
8. [Organization CRUD Operations](#8-organization-crud-operations)
9. [Team Member CRUD Operations](#9-team-member-crud-operations)
10. [Team Invitation CRUD Operations](#10-team-invitation-crud-operations)
11. [Event Access CRUD Operations](#11-event-access-crud-operations)
12. [Organizer Profile CRUD Operations](#12-organizer-profile-crud-operations)

### Part III: End-User Requirements
13. [Organization Owner Stories](#13-organization-owner-stories)
14. [Organization Admin Stories](#14-organization-admin-stories)
15. [Organization Member Stories](#15-organization-member-stories)
16. [Ticket Buyer Stories](#16-ticket-buyer-stories)
17. [Platform Admin Stories](#17-platform-admin-stories)
18. [Scanner/Validator Stories](#18-scannervalidator-stories)
19. [Finance Team Stories](#19-finance-team-stories)

### Part IV: System Behavior Specifications
20. [Saga Orchestration](#20-saga-orchestration)
21. [State Machines](#21-state-machines)
22. [Permission Resolution Algorithm](#22-permission-resolution-algorithm)

### Part V: Database Schema Design
23. [MongoDB Collections](#23-mongodb-collections)
24. [Document Structures](#24-document-structures)
25. [Indexes & Performance](#25-indexes--performance)

---

# Part I: Organizational Hierarchy & Identity

## 1. Platform Role Hierarchy

Platform roles are **system-wide** roles managed in Keycloak as realm roles. They determine what a user can do across the entire platform.

### Role Definitions

| Role | Description | Capabilities |
|------|-------------|--------------|
| **SUPER_ADMIN** | Full system access | Everything including system configuration |
| **ADMIN** | Platform administration | Manage organizers, approve events, view all data |
| **FINANCE** | Financial operations | Manage payouts, commissions, financial reports |
| **ORGANIZER** | Event organizer | Create organizations, manage events, request payouts |
| **CUSTOMER** | Default user | Browse events, purchase tickets, transfer tickets |

### Role Inheritance

```
SUPER_ADMIN
    └── inherits → ADMIN
                     ├── inherits → FINANCE (financial operations)
                     └── can manage → ORGANIZER (can approve organizers)

ORGANIZER
    └── inherits → CUSTOMER (can also buy tickets)
```

### Keycloak Realm Roles

```
Realm: event-ticketing
├── Roles
│   ├── SUPER_ADMIN
│   ├── ADMIN
│   ├── FINANCE
│   ├── ORGANIZER
│   └── CUSTOMER (default)
```

---

## 2. Organization Hierarchy

Organizations are **tenant-scoped** entities. When a user's organizer application is approved, an Organization is automatically created. Each Organization has exactly ONE owner and can have multiple team members with different roles.

### Organization-Level Roles

| Role | Max Count | Description | Key Capabilities |
|------|-----------|-------------|------------------|
| **OWNER** | 1 | Single account holder | Full access + transfer ownership + delete org |
| **ADMIN** | Unlimited | Full administrative access | Full access except ownership operations |
| **MANAGER** | Unlimited | Event management focus | Create/manage events, limited financial |
| **MARKETER** | Unlimited | Marketing focus | Analytics, promotions, no event creation |
| **CONTRIBUTOR** | Unlimited | Limited access | View-only, assist with check-in |

### Role Permission Matrix

| Permission | OWNER | ADMIN | MANAGER | MARKETER | CONTRIBUTOR |
|------------|:-----:|:-----:|:-------:|:--------:|:-----------:|
| **Organization** |
| Edit organization profile | ✅ | ✅ | ❌ | ❌ | ❌ |
| Manage billing/payments | ✅ | ❌ | ❌ | ❌ | ❌ |
| Transfer ownership | ✅ | ❌ | ❌ | ❌ | ❌ |
| Delete organization | ✅ | ❌ | ❌ | ❌ | ❌ |
| View financial reports | ✅ | ✅ | ✅* | ❌ | ❌ |
| Request payouts | ✅ | ✅* | ❌ | ❌ | ❌ |
| **Team** |
| Invite team members | ✅ | ✅ | ❌ | ❌ | ❌ |
| Remove team members | ✅ | ✅ | ❌ | ❌ | ❌ |
| Change member roles | ✅ | ✅** | ❌ | ❌ | ❌ |
| **Events** |
| Create events | ✅ | ✅ | ✅ | ❌ | ❌ |
| Publish events | ✅ | ✅ | ✅ | ❌ | ❌ |
| Delete events | ✅ | ✅ | ❌ | ❌ | ❌ |
| Edit all events | ✅ | ✅ | ✅ | ❌ | ❌ |
| View event analytics | ✅ | ✅ | ✅ | ✅ | ❌ |
| Manage promotions | ✅ | ✅ | ✅ | ✅ | ❌ |
| View attendee list | ✅ | ✅ | ✅ | ❌ | ✅ |
| Scan tickets | ✅ | ✅ | ✅ | ❌ | ✅ |

*Configurable in organization settings
**Cannot change OWNER role

### Organization Role Inheritance

```
OWNER
    └── inherits all from → ADMIN
                               ├── inherits all from → MANAGER
                               │                          └── inherits → CONTRIBUTOR
                               └── inherits all from → MARKETER
                                                          └── inherits → CONTRIBUTOR (view-only)
```

---

## 3. Event-Level Roles

Event-level roles **override** organization-level permissions for a specific event. This allows granting specific users access to individual events without giving them organization-wide access.

### Event Role Definitions

| Role | Description | Key Capabilities |
|------|-------------|------------------|
| **EVENT_OWNER** | Created the event | Full control, can delete, assign roles |
| **EVENT_ADMIN** | Full event access | Edit everything, issue refunds, assign roles (except EVENT_OWNER) |
| **EDITOR** | Content editor | Edit details, manage tickets, no refunds |
| **CHECK_IN** | Venue staff | Scan tickets only |
| **VIEWER** | Read-only | View details and sales data |

### Event Permission Matrix

| Permission | EVENT_OWNER | EVENT_ADMIN | EDITOR | CHECK_IN | VIEWER |
|------------|:-----------:|:-----------:|:------:|:--------:|:------:|
| Edit event details | ✅ | ✅ | ✅ | ❌ | ❌ |
| Manage ticket types | ✅ | ✅ | ✅ | ❌ | ❌ |
| View sales data | ✅ | ✅ | ✅ | ❌ | ✅ |
| Issue refunds | ✅ | ✅ | ❌ | ❌ | ❌ |
| Scan/validate tickets | ✅ | ✅ | ✅ | ✅ | ❌ |
| View attendee info | ✅ | ✅ | ✅ | ✅ | ✅ |
| Send notifications | ✅ | ✅ | ✅ | ❌ | ❌ |
| Cancel event | ✅ | ❌ | ❌ | ❌ | ❌ |
| Delete event | ✅ | ❌ | ❌ | ❌ | ❌ |
| Assign event roles | ✅ | ✅ | ❌ | ❌ | ❌ |

---

## 4. Organizer Onboarding Flow

The organizer onboarding flow follows a **staged approval process** with progressive access.

### Onboarding Stages

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ORGANIZER ONBOARDING STAGES                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  STAGE 1: REGISTRATION (Immediate)                                          │
│  ─────────────────────────────────                                          │
│  • User signs up (CUSTOMER role)                                            │
│  • Clicks "Become an Organizer"                                             │
│  • Fills basic business info                                                │
│  • Organization created with status: DRAFT                              │
│                                                                             │
│  STAGE 2: PROFILE COMPLETION (User-driven)                                  │
│  ─────────────────────────────────────────                                  │
│  • User completes business profile                                          │
│  • User uploads verification documents                                      │
│  • Status changes to: PENDING_DOCUMENTS (auto)                              │
│                                                                             │
│  STAGE 3: DOCUMENT SUBMISSION (User-driven)                                 │
│  ──────────────────────────────────────────                                 │
│  • User uploads all required documents                                      │
│  • User clicks "Submit for Review"                                          │
│  • Status changes to: PENDING_REVIEW                                        │
│                                                                             │
│  STAGE 4: ADMIN REVIEW (Admin-driven)                                       │
│  ────────────────────────────────────                                       │
│  • Admin reviews application                                                │
│  • Admin verifies documents                                                 │
│  • Decision: APPROVED / REJECTED / CHANGES_REQUESTED                        │
│                                                                             │
│  STAGE 5: ORGANIZATION CREATION (Automatic on APPROVED)                     │
│  ──────────────────────────────────────────────────────                     │
│  • Organization entity created                                              │
│  • User becomes OWNER of Organization                                       │
│  • User's userType changes to ORGANIZER                                     │
│  • ORGANIZER role granted in Keycloak                                       │
│  • Keycloak group created: /organizations/{slug}                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Stage Access Matrix

| Capability | DRAFT | PENDING_DOCUMENTS | PENDING_REVIEW | APPROVED | REJECTED | CHANGES_REQUESTED |
|------------|:-----:|:-----------------:|:--------------:|:--------:|:--------:|:-----------------:|
| Edit profile | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ |
| Upload documents | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ |
| Submit for review | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ |
| Create draft events | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Publish events | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Invite team members | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Request payouts | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Re-apply | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |

### OrganizerStatus State Machine

```
                              ┌──────────────┐
                              │    DRAFT     │
                              └──────┬───────┘
                                     │ profile complete
                                     ▼
                              ┌──────────────────┐
                              │PENDING_DOCUMENTS │
                              └──────┬───────────┘
                                     │ documents uploaded + submit
                                     ▼
                              ┌──────────────────┐
           ┌──────────────────│ PENDING_REVIEW   │──────────────────┐
           │                  └────────┬─────────┘                  │
           │ reject                    │ approve                    │ request changes
           ▼                           ▼                            ▼
    ┌──────────────┐           ┌──────────────┐           ┌────────────────────┐
    │   REJECTED   │           │   APPROVED   │◄──────────│ CHANGES_REQUESTED  │
    └──────┬───────┘           └──────┬───────┘ resubmit  └────────────────────┘
           │ re-apply                 │
           └──────────────────────────┼───────────────────┐
                                      │ suspend           │ On approval:
                                      ▼                   │ - Create Organization
                               ┌──────────────┐          │ - Create OrganizationMember (OWNER)
                               │  SUSPENDED   │          │ - Update User.userType → ORGANIZER
                               └──────────────┘          │ - Grant ORGANIZER role in Keycloak
                                                         │ - Create Keycloak group
```

---

## 5. Team Management

### Team Invitation Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TEAM INVITATION FLOW                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. INITIATE INVITATION                                                     │
│  ──────────────────────                                                     │
│     Owner/Admin calls: inviteTeamMember(organizationId, email, role)        │
│                                                                             │
│  2. CREATE INVITATION                                                       │
│  ────────────────────                                                       │
│     System creates TeamInvitation:                                          │
│     • Generates unique invitationToken                                      │
│     • Sets expiresAt = now() + 7 days                                       │
│     • Status = PENDING                                                      │
│                                                                             │
│  3. SEND INVITATION                                                         │
│  ─────────────────                                                          │
│     System sends notification (email/SMS) with link:                        │
│     https://app.example.com/invitations/accept?token={token}                │
│                                                                             │
│  4. INVITEE RESPONSE                                                        │
│  ─────────────────                                                          │
│     ┌─────────────────────────────────────────────────────────────────┐    │
│     │  IF not registered:                                              │    │
│     │  • Show sign-up form with invitation context                     │    │
│     │  • Create user account                                           │    │
│     │  • Auto-accept invitation after registration                     │    │
│     │                                                                  │    │
│     │  IF registered but not logged in:                                │    │
│     │  • Prompt login                                                  │    │
│     │  • Show invitation details                                       │    │
│     │  • Accept/Decline buttons                                        │    │
│     │                                                                  │    │
│     │  IF registered and logged in:                                    │    │
│     │  • Show invitation details                                       │    │
│     │  • Accept/Decline buttons                                        │    │
│     └─────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  5. ON ACCEPTANCE                                                           │
│  ───────────────                                                            │
│     • Create OrganizationMember with specified role                         │
│     • Add user to Keycloak group: /organizations/{slug}/{role}              │
│     • Update invitation status = ACCEPTED                                   │
│     • Notify organization owner                                             │
│     • Publish TeamMemberJoinedEvent                                         │
│                                                                             │
│  6. ON DECLINE                                                              │
│  ────────────                                                               │
│     • Update invitation status = DECLINED                                   │
│     • Notify inviter                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Invitation Status Transitions

```
    ┌─────────────┐
    │   PENDING   │
    └──────┬──────┘
           │
    ┌──────┼──────────┬──────────┬──────────┐
    │      │          │          │          │
    ▼      ▼          ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│ACCEPTED│ │DECLINED│ │EXPIRED │ │REVOKED │ │RESENT* │
└────────┘ └────────┘ └────────┘ └────────┘ └────────┘
                                              │ *Creates new
                                              │  invitation
                                              ▼
                                        ┌─────────────┐
                                        │   PENDING   │
                                        └─────────────┘
```

---

## 6. Keycloak Integration Architecture

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    KEYCLOAK ↔ MONGODB SYNCHRONIZATION                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                     KEYCLOAK REALM: event-ticketing                   │  │
│  ├──────────────────────────────────────────────────────────────────────┤  │
│  │                                                                       │  │
│  │  REALM ROLES                         GROUPS                           │  │
│  │  ───────────                         ──────                           │  │
│  │  • SUPER_ADMIN                       /organizations                   │  │
│  │  • ADMIN                               ├── /{org-slug}                │  │
│  │  • FINANCE                             │    ├── /owners               │  │
│  │  • ORGANIZER                           │    ├── /admins               │  │
│  │  • CUSTOMER (default)                  │    ├── /managers             │  │
│  │                                        │    ├── /marketers            │  │
│  │                                        │    └── /contributors         │  │
│  │                                        └── /{another-org-slug}        │  │
│  │                                             └── ...                   │  │
│  │                                                                       │  │
│  │  USER ATTRIBUTES                                                      │  │
│  │  ───────────────                                                      │  │
│  │  • mongodb_user_id     → Links to MongoDB User.id                     │  │
│  │  • phone_number        → Primary phone (E.164)                        │  │
│  │  • phone_verified      → Phone verification status                    │  │
│  │  • primary_org_id      → User's primary organization                  │  │
│  │                                                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│                              ▲                                              │
│                              │ Events (REGISTER, UPDATE_PROFILE, etc.)      │
│                              │                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                    UserSyncEventListener                              │  │
│  │                    (keycloak-extensions JAR)                          │  │
│  ├──────────────────────────────────────────────────────────────────────┤  │
│  │  Listens for Keycloak events and syncs to MongoDB:                    │  │
│  │  • REGISTER → Create User in MongoDB                                  │  │
│  │  • UPDATE_PROFILE → Update User in MongoDB                            │  │
│  │  • LOGIN → Update lastLoginAt                                         │  │
│  │  • AdminEvent CREATE → Create User                                    │  │
│  │  • AdminEvent UPDATE → Update User                                    │  │
│  │  • AdminEvent DELETE → Delete User                                    │  │
│  └───────────────────────────────┬──────────────────────────────────────┘  │
│                                  │                                          │
│                                  │ REST API calls                           │
│                                  ▼                                          │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                       IDENTITY SERVICE                                │  │
│  ├──────────────────────────────────────────────────────────────────────┤  │
│  │                                                                       │  │
│  │  KeycloakSyncController                                               │  │
│  │  ───────────────────────                                              │  │
│  │  POST /api/internal/keycloak/sync/user     → Sync single user         │  │
│  │  POST /api/internal/keycloak/sync/event    → Process Keycloak event   │  │
│  │  POST /api/internal/keycloak/sync/all      → Full sync (recovery)     │  │
│  │                                                                       │  │
│  │  UserSyncService                                                      │  │
│  │  ───────────────                                                      │  │
│  │  • fetchFromKeycloak(userId) → Get user from Keycloak Admin API       │  │
│  │  • syncToMongoDB(keycloakUser) → Upsert User document                 │  │
│  │                                                                       │  │
│  └───────────────────────────────┬──────────────────────────────────────┘  │
│                                  │                                          │
│                                  ▼                                          │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                          MONGODB                                      │  │
│  ├──────────────────────────────────────────────────────────────────────┤  │
│  │  Collections:                                                         │  │
│  │  • users                  - User profiles                             │  │
│  │  • organizations          - Organization entities                     │  │
│  │  • organization_members   - User-Organization membership              │  │
│  │  • team_invitations       - Pending team invitations                  │  │
│  │  • event_access_grants    - Event-level access                        │  │
│  │  • organizer_profiles     - Organizer application data                │  │
│  │  • ownership_transfers    - Pending ownership transfers               │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Organization Creation Flow (On Approval)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│               ORGANIZATION CREATION FLOW (On Approval)                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  TRIGGER: Admin calls approveOrganizer(organizationId)                  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ STEP 1: Update Organization                                      │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │ • status = APPROVED                                                  │   │
│  │ • reviewedById = adminId                                             │   │
│  │ • reviewedAt = now()                                                 │   │
│  │ • approvedAt = now()                                                 │   │
│  │ • commissionRate = (from input or default 10%)                       │   │
│  │ • payoutSchedule = (from input or default WEEKLY)                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                  │                                          │
│                                  ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ STEP 2: Create Organization                                          │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │ organization = {                                                     │   │
│  │   id: new ObjectId(),                                                │   │
│  │   name: organization.companyName,                                │   │
│  │   slug: generateSlug(companyName),  // e.g., "acme-events"           │   │
│  │   description: organization.companyDescription,                  │   │
│  │   logoUrl: organization.logoUrl,                                 │   │
│  │   organizationId: organization.id,                           │   │
│  │   ownerId: organization.userId,                                  │   │
│  │   status: ACTIVE,                                                    │   │
│  │   verified: true,                                                    │   │
│  │   settings: { ... default settings ... },                            │   │
│  │   createdAt: now()                                                   │   │
│  │ }                                                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                  │                                          │
│                                  ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ STEP 3: Create OrganizationMember (OWNER)                            │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │ member = {                                                           │   │
│  │   id: new ObjectId(),                                                │   │
│  │   userId: organization.userId,                                   │   │
│  │   organizationId: organization.id,                                   │   │
│  │   role: OWNER,                                                       │   │
│  │   status: ACTIVE,                                                    │   │
│  │   joinedAt: now(),                                                   │   │
│  │   createdAt: now()                                                   │   │
│  │ }                                                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                  │                                          │
│                                  ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ STEP 4: Update User                                                  │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │ user.userType = ORGANIZER                                            │   │
│  │ user.primaryOrganizationId = organization.id                         │   │
│  │ user.updatedAt = now()                                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                  │                                          │
│                                  ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ STEP 5: Update Keycloak                                              │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │ Via Keycloak Admin API:                                              │   │
│  │                                                                      │   │
│  │ 5a. Grant ORGANIZER role to user                                     │   │
│  │     PUT /admin/realms/event-ticketing/users/{userId}/role-mappings   │   │
│  │     /realm  →  [{ name: "ORGANIZER" }]                               │   │
│  │                                                                      │   │
│  │ 5b. Create organization group                                        │   │
│  │     POST /admin/realms/event-ticketing/groups                        │   │
│  │     → { name: "acme-events", path: "/organizations/acme-events" }    │   │
│  │                                                                      │   │
│  │ 5c. Create role subgroups                                            │   │
│  │     POST /admin/realms/event-ticketing/groups/{groupId}/children     │   │
│  │     → { name: "owners" }                                             │   │
│  │     → { name: "admins" }                                             │   │
│  │     → { name: "managers" }                                           │   │
│  │     → { name: "marketers" }                                          │   │
│  │     → { name: "contributors" }                                       │   │
│  │                                                                      │   │
│  │ 5d. Add user to owners group                                         │   │
│  │     PUT /admin/realms/event-ticketing/users/{userId}/groups/{ownersGroupId}│
│  │                                                                      │   │
│  │ 5e. Update user attributes                                           │   │
│  │     PUT /admin/realms/event-ticketing/users/{userId}                 │   │
│  │     → attributes: { primary_org_id: organization.id }                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                  │                                          │
│                                  ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ STEP 6: Link Organization to Organization                        │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │ organization.organizationId = organization.id                    │   │
│  │ organization.updatedAt = now()                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                  │                                          │
│                                  ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ STEP 7: Publish Event                                                │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │ Publish OrganizerApprovedEvent to Azure Service Bus:                 │   │
│  │ {                                                                    │   │
│  │   type: "OrganizerApprovedEvent",                                    │   │
│  │   organizationId: organization.id,                           │   │
│  │   organizationId: organization.id,                                   │   │
│  │   userId: user.id,                                                   │   │
│  │   timestamp: now()                                                   │   │
│  │ }                                                                    │   │
│  │                                                                      │   │
│  │ Other services (Catalog, Booking) can listen and prepare resources.  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                  │                                          │
│                                  ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ STEP 8: Send Notifications                                           │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │ • Send ORGANIZER_APPROVED notification to user                       │   │
│  │ • Send WELCOME email with getting started guide                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

# Part II: CRUD Operations & Business Logic

## 7. User CRUD Operations

### Create User

**GraphQL Mutation:** `createUser`
**Access:** ADMIN, SUPER_ADMIN only

```graphql
mutation CreateUser($input: CreateUserInput!) {
  createUser(input: $input) {
    success
    message
    user { id username email userType }
  }
}
```

**Business Logic:**
```
1. Validate input (email format, unique email, required fields)
2. Create user in Keycloak via Admin API
3. Keycloak triggers REGISTER event
4. UserSyncEventListener syncs to MongoDB
5. Return created user
```

**Service Layer:**
```java
public Mono<User> createUser(CreateUserInput input) {
    return validateUniqueEmail(input.getEmail())
        .then(keycloakAdminClient.createUser(input))
        .flatMap(keycloakUserId -> userRepository.findByKeycloakUserId(keycloakUserId))
        .switchIfEmpty(Mono.defer(() -> createLocalUser(input)));
}
```

### Read User

**GraphQL Queries:**
- `me` - Get current authenticated user
- `user(id: ID!)` - Get user by ID (admin only)
- `userByEmail(email: String!)` - Get user by email
- `users(...)` - Search users with pagination (admin only)

### Update User

**GraphQL Mutations:**
- `updateMyProfile(input: UpdateUserInput!)` - Self-update
- `updateUser(id: ID!, input: UpdateUserInput!)` - Admin update

**Business Logic:**
```
1. Validate current user has permission
2. Validate input fields
3. Update in Keycloak (synced fields: firstName, lastName, email)
4. Update in MongoDB (profile fields: bio, avatarUrl, etc.)
5. Return updated user
```

### Delete User (GDPR)

**GraphQL Mutation:** `requestAccountDeletion`

**Business Logic:**
```
1. Set accountStatus = PENDING_DELETION
2. Schedule deletion after 30-day grace period
3. User can cancel within grace period
4. After grace period:
   - Anonymize personal data in MongoDB
   - Delete from Keycloak
   - Retain transaction records (anonymized) for legal compliance
```

---

## 8. Organization CRUD Operations

### Create Organization

**Note:** Organizations are NOT created directly. They are automatically created when an Organization is approved.

**Internal Method:** `createOrganizationFromApproval`

### Read Organization

**GraphQL Queries:**
- `organization(id: ID!)` - Get by ID
- `organizationBySlug(slug: String!)` - Get by slug
- `myOrganizations` - Get organizations I belong to
- `myOwnedOrganization` - Get organization I own
- `organizations(...)` - Search organizations (admin only)

**Access Control:**
- Public: Basic info (name, logo, verified status)
- Members: Full details
- Admin: All organizations

### Update Organization

**GraphQL Mutation:** `updateOrganization`
**Access:** OWNER, ADMIN of the organization

```graphql
mutation UpdateOrganization($id: ID!, $input: UpdateOrganizationInput!) {
  updateOrganization(id: $id, input: $input) {
    success
    organization { id name description logoUrl }
  }
}
```

**Business Logic:**
```
1. Verify caller has OWNER or ADMIN role in organization
2. Validate input
3. Update organization document
4. If name changed, update slug (check uniqueness)
5. Publish OrganizationUpdatedEvent
```

### Delete Organization

**GraphQL Mutation:** Not directly exposed (admin suspension only)

**Business Logic:**
```
Organizations are never hard-deleted. They are:
1. SUSPENDED by admin (temporarily)
2. INACTIVE by owner (self-deactivation)
3. PENDING_DELETION (scheduled deletion)

On PENDING_DELETION:
- Cancel all pending events
- Refund all unredeemed tickets
- Process pending payouts
- After 90 days, anonymize data
```

---

## 9. Team Member CRUD Operations

### Create Member (via Invitation)

Team members are created through the invitation flow, not directly.

**Flow:**
1. `inviteTeamMember` creates TeamInvitation
2. `acceptInvitation` creates OrganizationMember

### Read Members

**GraphQL Queries:**
- `organizationMembers(organizationId, role?, status?, pageable)` - List members
- `organizationMember(organizationId, userId)` - Get specific member
- `myOrganizationMembership(organizationId)` - Get my membership

**Access Control:**
- OWNER, ADMIN: All members
- MANAGER, MARKETER: Active members only
- CONTRIBUTOR: Own membership only

### Update Member

**GraphQL Mutation:** `updateMemberRole`
**Access:** OWNER, ADMIN

```graphql
mutation UpdateMemberRole($input: UpdateMemberRoleInput!) {
  updateMemberRole(input: $input) {
    success
    member { id role status }
  }
}
```

**Business Logic:**
```
1. Verify caller has permission (OWNER or ADMIN)
2. Verify target is not OWNER (cannot change owner role)
3. Verify ADMIN cannot change another ADMIN (only OWNER can)
4. Update role in MongoDB
5. Update Keycloak groups:
   - Remove from old role group
   - Add to new role group
6. Publish TeamMemberRoleChangedEvent
```

### Delete Member

**GraphQL Mutations:**
- `removeMember(organizationId, memberId)` - Remove by owner/admin
- `leaveOrganization(organizationId)` - Self-removal

**Business Logic:**
```
1. Verify permission
2. OWNER cannot be removed (must transfer ownership first)
3. Set member status = REMOVED
4. Revoke all event access grants
5. Remove from Keycloak organization groups
6. Publish TeamMemberLeftEvent
```

---

## 10. Team Invitation CRUD Operations

### Create Invitation

**GraphQL Mutation:** `inviteTeamMember`
**Access:** OWNER, ADMIN

```graphql
mutation InviteTeamMember($input: InviteTeamMemberInput!) {
  inviteTeamMember(input: $input) {
    success
    invitation { id email proposedRole status expiresAt }
  }
}
```

**Business Logic:**
```
1. Verify caller has permission
2. Check if user already member
3. Check if pending invitation exists (revoke old one)
4. Generate unique invitation token
5. Set expiresAt = now() + 7 days
6. Create TeamInvitation document
7. Send invitation email/SMS
8. Return invitation
```

### Read Invitations

**GraphQL Queries:**
- `pendingInvitations(organizationId)` - List pending (owner/admin)
- `invitationByToken(token)` - Get by token (for acceptance page)
- `myPendingInvitations` - Invitations sent to me

### Update Invitation

**GraphQL Mutations:**
- `resendInvitation(invitationId)` - Resend email
- `revokeInvitation(invitationId)` - Cancel invitation

### Process Invitation

**GraphQL Mutations:**
- `acceptInvitation(input)` - Accept and create member
- `declineInvitation(token)` - Decline invitation

**Accept Business Logic:**
```
1. Validate token exists and not expired
2. Validate invitation status = PENDING
3. Get or create user account
4. Create OrganizationMember with proposedRole
5. If eventAccessGrants specified, create EventAccessGrant records
6. Update invitation status = ACCEPTED
7. Update Keycloak groups
8. Notify organization owner
9. Return created member
```

---

## 11. Event Access CRUD Operations

### Create Event Access

**GraphQL Mutation:** `grantEventAccess`
**Access:** Organization OWNER/ADMIN or EVENT_OWNER/EVENT_ADMIN

```graphql
mutation GrantEventAccess($input: GrantEventAccessInput!) {
  grantEventAccess(input: $input) {
    success
    accessGrant { id userId eventId eventRole status }
  }
}
```

**Business Logic:**
```
1. Verify caller has permission to grant access:
   - Organization OWNER/ADMIN can grant any event role
   - EVENT_OWNER can grant any event role for their event
   - EVENT_ADMIN can grant roles except EVENT_OWNER
2. Verify target user exists
3. Check for existing access (update if exists)
4. Create EventAccessGrant document
5. Publish EventAccessGrantedEvent
```

### Read Event Access

**GraphQL Queries:**
- `eventAccessGrants(eventId, status?, pageable)` - List grants for event
- `userEventAccess(userId, eventId)` - Get specific user's access
- `myEventAccess(eventId)` - Get my access to event
- `myEventAccessGrants` - All events I have access to

### Update Event Access

**GraphQL Mutation:** `updateEventAccess`

### Revoke Event Access

**GraphQL Mutation:** `revokeEventAccess`

**Business Logic:**
```
1. Verify caller has permission
2. EVENT_OWNER cannot be revoked (must delete event)
3. Set status = REVOKED
4. Set revokedAt, revokedById, revocationReason
5. Publish EventAccessRevokedEvent
```

---

## 12. Organizer Profile CRUD Operations

### Create Organizer Profile

**GraphQL Mutation:** `applyToBeOrganizer`
**Access:** Any authenticated CUSTOMER

```graphql
mutation ApplyToBeOrganizer($input: ApplyToBeOrganizerInput!) {
  applyToBeOrganizer(input: $input) {
    success
    organization { id status companyName }
  }
}
```

**Business Logic:**
```
1. Verify user is CUSTOMER (not already ORGANIZER)
2. Verify no existing application
3. Create Organization with status = DRAFT
4. Return profile for completion
```

### Read Organizer Profile

**GraphQL Queries:**
- `myOrganization` - Get my application
- `organization(id)` - Get by ID
- `organizationByUserId(userId)` - Get by user
- `organizerApplications(status?, pageable)` - Admin: list applications

### Update Organizer Profile

**GraphQL Mutations:**
- `updateOrganization` - Update business info
- `uploadVerificationDocument` - Add document
- `submitOrganizerApplication` - Submit for review

**Update Business Logic:**
```
1. Verify profile belongs to caller
2. Verify status allows editing (DRAFT, PENDING_DOCUMENTS, CHANGES_REQUESTED)
3. Update fields
4. Auto-transition to PENDING_DOCUMENTS if profile complete and documents needed
```

**Submit Business Logic:**
```
1. Verify all required fields complete
2. Verify all required documents uploaded
3. Set status = PENDING_REVIEW
4. Set submittedAt = now()
5. Notify admin team
```

### Admin Review Operations

**GraphQL Mutations:**
- `approveOrganizer(input)` - Approve (creates Organization)
- `rejectOrganizer(input)` - Reject with reason
- `requestOrganizerChanges(input)` - Request modifications
- `suspendOrganizer(input)` - Suspend organizer

---

# Part III: End-User Requirements

## 13. Organization Owner Stories

### US-OWNER-001: Approve Organizer Application

**As the** Platform (automatic)
**When** an admin approves my organizer application
**Then** I automatically become the Owner of my Organization

**Acceptance Criteria:**
- Organization is created with my company name
- I am assigned OWNER role
- My userType changes to ORGANIZER
- I can immediately invite team members
- I receive welcome notification with getting started guide

---

### US-OWNER-002: Invite Team Members

**As an** Organization Owner
**I want** to invite team members to my organization
**So that** they can help manage events

**Acceptance Criteria:**
- Given I am an Organization Owner
- When I invite a user by email
- Then they receive an invitation link
- And they can accept to join with the specified role
- And I am notified when they accept/decline

---

### US-OWNER-003: Manage Team Roles

**As an** Organization Owner
**I want** to change team member roles
**So that** I can adjust permissions as needed

**Acceptance Criteria:**
- Given I have team members
- When I change a member's role
- Then their permissions update immediately
- And they are notified of the change

---

### US-OWNER-004: Transfer Organization Ownership

**As an** Organization Owner
**I want** to transfer ownership to another member
**So that** someone else can take over the organization

**Acceptance Criteria:**
- Given I want to transfer ownership
- When I initiate transfer to an existing ADMIN member
- Then they receive a transfer request
- And they must confirm with 2FA
- And my role changes to ADMIN
- And their role changes to OWNER

---

### US-OWNER-005: View All Organization Events

**As an** Organization Owner
**I want** to see all events created by my organization
**So that** I can monitor overall activity

**Acceptance Criteria:**
- Given I am logged in as Owner
- When I view the organization dashboard
- Then I see all events regardless of creator
- And I see sales summaries per event
- And I see team member activity

---

## 14. Organization Admin Stories

### US-ADMIN-001: Manage Events

**As an** Organization Admin
**I want** to create and manage events
**So that** I can support the organization's event portfolio

**Acceptance Criteria:**
- Given I am an Admin
- When I create an event
- Then I am assigned as EVENT_OWNER of that event
- And the event is associated with my organization

---

### US-ADMIN-002: Assign Event Access

**As an** Organization Admin
**I want** to grant team members access to specific events
**So that** they can help with particular events

**Acceptance Criteria:**
- Given I have organization team members
- When I grant event access to a member
- Then they can access that event with the specified role
- And this overrides their organization-level permissions for this event

---

## 15. Organization Member Stories

### US-MEMBER-001: Accept Team Invitation

**As a** User invited to join an organization
**I want** to accept the invitation
**So that** I can join the team

**Acceptance Criteria:**
- Given I received an invitation link
- When I click the link
- Then I see the organization details and my proposed role
- And I can accept or decline
- And upon acceptance, I gain access to the organization

---

### US-MEMBER-002: View Assigned Events

**As an** Organization Member (Manager/Marketer/Contributor)
**I want** to see events I have access to
**So that** I can perform my assigned tasks

**Acceptance Criteria:**
- Given I have limited role in the organization
- When I view my dashboard
- Then I see only events I have explicit access to
- Or events matching my organization role permissions

---

### US-MEMBER-003: Leave Organization

**As an** Organization Member
**I want** to leave an organization
**So that** I can remove my association

**Acceptance Criteria:**
- Given I am a member (not Owner)
- When I leave the organization
- Then my membership is removed
- And I lose all organization and event access
- And the organization owner is notified

---

## 16. Ticket Buyer Stories

### US-BUY-001: Browse Published Events

**As a** Ticket Buyer
**I want** to browse and search for upcoming events
**So that** I can find events I want to attend

**Acceptance Criteria:**
- Given I am on the event discovery page
- When I browse or search events
- Then I only see published events with available tickets
- And I can filter by category, location, date range, and price
- And sold-out events are clearly marked

---

### US-BUY-002: Purchase Tickets

**As a** Ticket Buyer
**I want** to purchase tickets with mobile money
**So that** I can secure my attendance

**Acceptance Criteria:**
- Given I have selected tickets
- When I complete payment via MTN/Airtel/Zamtel
- Then my tickets are confirmed
- And I receive QR codes for entry
- And I receive email/SMS confirmation

---

### US-BUY-003: Transfer Ticket

**As a** Ticket Buyer
**I want** to transfer my ticket to another person
**So that** someone else can attend in my place

**Acceptance Criteria:**
- Given I have a purchased ticket
- When I transfer to another registered user
- Then ownership is updated
- And the new owner receives the QR code
- And I no longer have access to the ticket

---

## 17. Platform Admin Stories

### US-PADM-001: Review Organizer Applications

**As a** Platform Admin
**I want** to review organizer applications
**So that** verified organizers can create events

**Acceptance Criteria:**
- Given there are pending applications
- When I review an application
- Then I can view all submitted documents
- And I can approve, reject, or request changes
- And the applicant is notified of my decision

---

### US-PADM-002: Approve Organizer (Creates Organization)

**As a** Platform Admin
**I want** to approve an organizer application
**So that** they can start creating events

**Acceptance Criteria:**
- Given I approve an application
- Then Organization is automatically created
- And user becomes Owner of the organization
- And user's type changes to ORGANIZER
- And Keycloak groups are created
- And welcome notification is sent

---

### US-PADM-003: Manage Organizations

**As a** Platform Admin
**I want** to view and manage all organizations
**So that** I can ensure platform quality

**Acceptance Criteria:**
- Given I am a platform admin
- When I view organizations
- Then I see all organizations with their status
- And I can suspend/unsuspend organizations
- And I can view team composition

---

## 18. Scanner/Validator Stories

### US-SCN-001: Validate Ticket at Entry

**As a** Scanner (CHECK_IN role)
**I want** to scan and validate tickets
**So that** only valid ticket holders can enter

**Acceptance Criteria:**
- Given I have CHECK_IN access to an event
- When I scan a ticket QR code
- Then the system indicates if valid
- And valid tickets are marked as VALIDATED
- And invalid tickets show rejection reason

---

## 19. Finance Team Stories

### US-FIN-001: Process Payouts

**As a** Finance team member
**I want** to process payout requests
**So that** organizers receive their earnings

**Acceptance Criteria:**
- Given there are pending payout requests
- When I approve a request
- Then funds are transferred via mobile money
- And escrow is debited
- And organizer is notified

---

# Part IV: System Behavior Specifications

## 20. Saga Orchestration

### Ticket Purchase Saga

[Retained from original document - no changes needed]

### Organizer Approval Saga

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ORGANIZER APPROVAL SAGA                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  HAPPY PATH:                                                                │
│  ──────────                                                                 │
│                                                                             │
│  [1] PROFILE_APPROVED                                                       │
│       │                                                                     │
│       ▼                                                                     │
│  [2] ORGANIZATION_CREATED ──► [3] MEMBER_CREATED (OWNER)                   │
│       │                              │                                      │
│       ▼                              ▼                                      │
│  [4] USER_UPGRADED ──► [5] KEYCLOAK_UPDATED ──► [6] NOTIFICATION_SENT      │
│       │                      │                          │                   │
│       ▼                      ▼                          ▼                   │
│  MongoDB User          Keycloak                   Email/Push               │
│  (userType=ORGANIZER)  (role + groups)                                     │
│                                                                             │
│  COMPENSATION (if Keycloak update fails):                                   │
│  ────────────────────────────────────────                                   │
│                                                                             │
│  [5'] KEYCLOAK_FAILED ──► REVERT_USER ──► REVERT_MEMBER ──► REVERT_ORG    │
│       │                       │                │                │           │
│       ▼                       ▼                ▼                ▼           │
│  Retry 3x              userType=CUSTOMER   Delete member   Delete org      │
│  then compensate       status=PENDING_REVIEW                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. State Machines

### Organization Status State Machine

```
    ┌──────────────┐
    │    ACTIVE    │ ◄─── Initial state (on creation)
    └──────┬───────┘
           │
    ┌──────┼──────────────────┐
    │      │ admin suspend    │ owner deactivate
    ▼      │                  ▼
┌──────────────┐       ┌──────────────┐
│  SUSPENDED   │       │   INACTIVE   │
└──────┬───────┘       └──────┬───────┘
       │ unsuspend            │ reactivate
       └──────────────────────┼───────────────────┐
                              │                   │
                              ▼                   │
                       ┌──────────────┐          │
                       │    ACTIVE    │ ◄────────┘
                       └──────┬───────┘
                              │ request deletion
                              ▼
                       ┌──────────────────┐
                       │ PENDING_DELETION │
                       └──────────────────┘
```

### Member Status State Machine

```
    ┌──────────────┐
    │    ACTIVE    │ ◄─── Initial state (on join)
    └──────┬───────┘
           │
    ┌──────┼──────────┐
    │      │          │
    ▼      ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐
│INACTIVE│ │SUSPENDED│ │ REMOVED│
└───┬────┘ └────┬───┘ └────────┘
    │ reactivate│ reactivate     (terminal)
    └───────────┼───────────┐
                │           │
                ▼           │
         ┌──────────────┐   │
         │    ACTIVE    │ ◄─┘
         └──────────────┘
```

---

## 22. Permission Resolution Algorithm

When checking if a user can perform an action:

```
function hasPermission(userId, action, resourceType, resourceId):

    # 1. Get user's platform role
    platformRole = getUserPlatformRole(userId)

    # 2. Check platform-level permissions
    if (platformRole has action for resourceType):
        return true  # Platform admins can do anything

    # 3. If resource is event-scoped, check event access first
    if (resourceType == EVENT):
        eventAccess = getEventAccess(userId, resourceId)
        if (eventAccess exists and eventAccess.status == ACTIVE):
            if (eventAccess.eventRole has action):
                return true
            if (action in eventAccess.customPermissions):
                return true
            if (action in eventAccess.deniedPermissions):
                return false  # Explicit deny

    # 4. Get organization membership
    organizationId = getOrganizationId(resourceId, resourceType)
    membership = getOrganizationMembership(userId, organizationId)

    if (membership == null or membership.status != ACTIVE):
        return false  # Not a member

    # 5. Check organization role permissions
    if (membership.role has action):
        return true

    # 6. Check custom permissions
    if (action in membership.customPermissions):
        return true

    # 7. Check denied permissions
    if (action in membership.deniedPermissions):
        return false

    return false  # Default deny
```

---

# Part V: Database Schema Design

## 23. MongoDB Collections

### Identity Service Collections

| Collection | Description | Key Indexes |
|------------|-------------|-------------|
| `users` | User profiles | `keycloakUserId`, `email`, `phoneNumber` |
| `organizations` | Organization entities | `slug`, `ownerId`, `status` |
| `organization_members` | User-Organization links | `userId+organizationId`, `organizationId+role` |
| `team_invitations` | Pending invitations | `invitationToken`, `email`, `organizationId+status` |
| `event_access_grants` | Event-level access | `userId+eventId`, `eventId+status` |
| `organizer_profiles` | Organizer applications | `userId`, `status`, `organizationId` |
| `ownership_transfers` | Pending transfers | `organizationId+status`, `transferToken` |
| `notifications` | User notifications | `userId+status`, `createdAt` |
| `user_devices` | Push notification devices | `userId`, `deviceToken` |

---

## 24. Document Structures

### Organization Document

```javascript
{
  _id: ObjectId("..."),
  name: "Acme Events",
  slug: "acme-events",  // Unique, URL-friendly
  description: "Premier event organizer in Lusaka",
  logoUrl: "https://...",
  bannerUrl: "https://...",

  // Links
  organizationId: ObjectId("..."),
  keycloakGroupId: "group-uuid",
  ownerId: ObjectId("..."),  // User ID of owner

  // Status
  status: "ACTIVE",  // ACTIVE, SUSPENDED, INACTIVE, PENDING_DELETION
  verified: true,

  // Settings (embedded)
  settings: {
    defaultEventVisibility: "PUBLIC",
    requireEventApproval: false,
    allowMembersToInvite: false,
    inviteRequiresApproval: false,
    maxTeamMembers: null,
    managersCanRequestPayouts: false,
    marketersCanViewFinancials: false,
    notifyOwnerOnMemberJoin: true,
    notifyOwnerOnEventCreated: true,
    notifyOwnerOnPayoutRequest: true
  },

  // Statistics (denormalized for performance)
  stats: {
    memberCount: 5,
    totalEvents: 12,
    totalTicketsSold: 5430,
    totalRevenue: Decimal128("543000.00"),
    averageRating: 4.7
  },

  // Audit
  createdAt: ISODate("..."),
  updatedAt: ISODate("...")
}
```

### OrganizationMember Document

```javascript
{
  _id: ObjectId("..."),

  // Links
  userId: ObjectId("..."),
  organizationId: ObjectId("..."),

  // Role & Permissions
  role: "ADMIN",  // OWNER, ADMIN, MANAGER, MARKETER, CONTRIBUTOR
  customPermissions: ["PAYOUT_REQUEST"],  // Additional permissions
  deniedPermissions: [],  // Explicitly denied

  // Status
  status: "ACTIVE",  // ACTIVE, INACTIVE, SUSPENDED, REMOVED

  // Invitation tracking
  invitedById: ObjectId("..."),

  // Timestamps
  joinedAt: ISODate("..."),
  lastActiveAt: ISODate("..."),
  createdAt: ISODate("..."),
  updatedAt: ISODate("...")
}
```

### TeamInvitation Document

```javascript
{
  _id: ObjectId("..."),

  // Invitee
  email: "john@example.com",
  phoneNumber: "+260971234567",
  inviteeName: "John Doe",

  // Organization
  organizationId: ObjectId("..."),

  // Proposed access
  proposedRole: "MANAGER",
  eventAccessGrants: [
    { eventId: "event-123", role: "EDITOR", expiresAt: null }
  ],

  // Inviter
  invitedById: ObjectId("..."),
  message: "Welcome to our team!",

  // Token & Expiry
  invitationToken: "unique-token-uuid",
  expiresAt: ISODate("2026-03-25T00:00:00Z"),

  // Status
  status: "PENDING",  // PENDING, ACCEPTED, DECLINED, EXPIRED, REVOKED

  // Timestamps
  createdAt: ISODate("..."),
  acceptedAt: null,
  declinedAt: null
}
```

### EventAccessGrant Document

```javascript
{
  _id: ObjectId("..."),

  // Links
  userId: ObjectId("..."),
  eventId: "event-123",  // External ID from Catalog Service
  organizationId: ObjectId("..."),

  // Role
  eventRole: "CHECK_IN",  // EVENT_OWNER, EVENT_ADMIN, EDITOR, CHECK_IN, VIEWER
  customPermissions: [],

  // Assignment
  grantedById: ObjectId("..."),
  reason: "Venue staff for concert",

  // Status & Expiry
  status: "ACTIVE",  // ACTIVE, SUSPENDED, REVOKED, EXPIRED
  expiresAt: ISODate("2026-04-01T00:00:00Z"),

  // Revocation (if revoked)
  revokedById: null,
  revokedAt: null,
  revocationReason: null,

  // Timestamps
  grantedAt: ISODate("..."),
  createdAt: ISODate("..."),
  updatedAt: ISODate("...")
}
```

---

## 25. Indexes & Performance

### Required Indexes

```javascript
// users collection
db.users.createIndex({ "keycloakUserId": 1 }, { unique: true })
db.users.createIndex({ "email": 1 }, { unique: true })
db.users.createIndex({ "phoneNumber": 1 }, { sparse: true })
db.users.createIndex({ "userType": 1, "accountStatus": 1 })

// organizations collection
db.organizations.createIndex({ "slug": 1 }, { unique: true })
db.organizations.createIndex({ "ownerId": 1 })
db.organizations.createIndex({ "status": 1 })
db.organizations.createIndex({ "organizationId": 1 })

// organization_members collection
db.organization_members.createIndex(
  { "userId": 1, "organizationId": 1 },
  { unique: true }
)
db.organization_members.createIndex({ "organizationId": 1, "role": 1 })
db.organization_members.createIndex({ "organizationId": 1, "status": 1 })

// team_invitations collection
db.team_invitations.createIndex({ "invitationToken": 1 }, { unique: true })
db.team_invitations.createIndex({ "email": 1, "organizationId": 1 })
db.team_invitations.createIndex({ "organizationId": 1, "status": 1 })
db.team_invitations.createIndex(
  { "expiresAt": 1 },
  { expireAfterSeconds: 0 }  // TTL index for auto-cleanup
)

// event_access_grants collection
db.event_access_grants.createIndex(
  { "userId": 1, "eventId": 1 },
  { unique: true }
)
db.event_access_grants.createIndex({ "eventId": 1, "status": 1 })
db.event_access_grants.createIndex({ "organizationId": 1 })

// organizer_profiles collection
db.organizer_profiles.createIndex({ "userId": 1 }, { unique: true })
db.organizer_profiles.createIndex({ "status": 1 })
db.organizer_profiles.createIndex({ "organizationId": 1 }, { sparse: true })
```

---

## Appendix: Key Takeaways

### 1. Multi-Tenant Architecture
- Every resource is scoped to an Organization (tenant)
- All queries must include `organizationId` filter
- Cross-tenant access is only for platform admins

### 2. Role Inheritance
- Higher roles inherit all permissions from lower roles
- Custom permissions can be added per member
- Explicit denies override inherited permissions

### 3. Event-Level Override
- Event roles override organization roles for that specific event
- Allows temporary access for external collaborators
- Scanner (CHECK_IN) role for venue staff

### 4. Pre-Approval Capabilities
- PENDING_REVIEW organizers can create draft events
- Cannot publish until approved
- Reduces friction in onboarding

### 5. Keycloak Synchronization
- Users are created in Keycloak first
- MongoDB is kept in sync via events
- Groups represent organization membership
- Realm roles represent platform-level access

### 6. Permission Resolution Order
1. Platform role (super admin overrides all)
2. Event-level access (if exists)
3. Organization role
4. Custom permissions
5. Denied permissions
6. Default: deny
