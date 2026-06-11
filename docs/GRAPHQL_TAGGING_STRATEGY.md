# GraphQL Schema Tagging Strategy for Client-Specific Code Generation

## Overview

This document outlines the tagging strategy for our Apollo Federation GraphQL schema to enable
client-specific code generation. By using `@tag` directives, we can generate optimized TypeScript
types for each frontend client (mobile, organizer dashboard, admin portal) containing only the
types they actually need.

## Why Tagging?

### The Problem
Our full supergraph contains ~500+ types across 3 microservices. Without tagging:
- Mobile app codegen generates 10,000+ lines of types including admin-only types
- Bundle size increases unnecessarily
- Developers see types they can't/shouldn't use
- IDE autocomplete is cluttered with irrelevant options

### The Solution
Operation-based code generation with schema tagging:
1. Each client only writes operations for what it needs
2. `onlyOperationTypes: true` generates types only for those operations
3. Tags document the intended audience for each field/type
4. Future: Apollo Contracts can use tags for schema-level filtering

## Tag Categories

### `@tag(name: "public")`
**Audience**: All clients (mobile, web, admin)
**Use for**: Core types and queries that everyone needs
- Event discovery queries
- Basic event/ticket/user types
- Public category/location lookups

### `@tag(name: "mobile")`
**Audience**: Mobile app (Expo/React Native)
**Use for**: Consumer-facing mobile features
- Simplified event browsing
- Ticket purchase flow
- QR code display
- Push notification management

### `@tag(name: "organizer")`
**Audience**: Organizer dashboard (Next.js admin app)
**Use for**: Event organizer features
- Event creation and management
- Ticket tier configuration
- Sales analytics (own events)
- Team member management
- Payout requests

### `@tag(name: "admin")`
**Audience**: Platform administrators only
**Use for**: Platform management
- Approval workflows (approve/reject events, organizers)
- User management (suspend, lock accounts)
- Platform-wide statistics
- Financial reconciliation
- Bulk operations
- Export/reporting functionality

### `@tag(name: "internal")`
**Audience**: Service-to-service communication only
**Use for**: Backend operations not exposed to clients
- Keycloak sync operations
- Payment processing callbacks
- Audit/logging fields

## Tagging Rules

### Rule 1: Tag at the Most Specific Level
```graphql
# Good: Tag specific fields that are admin-only
type Event @key(fields: "id") {
    id: ID!                           # No tag = public
    title: String!                    # No tag = public
    organizerId: String! @tag(name: "organizer") @tag(name: "admin")
    adminNotes: String @tag(name: "admin")
    internalCode: String @tag(name: "internal")
}
```

### Rule 2: Tag Entire Types When All Fields Are Restricted
```graphql
# Good: Entire type is admin-only
type PlatformStatistics @tag(name: "admin") {
    totalUsers: Int!
    totalRevenue: BigDecimal!
    # All fields inherit the admin tag
}
```

### Rule 3: Queries/Mutations Follow Field Tagging
```graphql
type Query {
    # Public - anyone can discover events
    discoverEvents(filter: EventDiscoveryFilterInput!): EventConnection!

    # Organizer - only event organizers need draft events
    draftEventsOffsetPagination(...): EventOffsetPage! @tag(name: "organizer") @tag(name: "admin")

    # Admin - platform administrators only
    pendingApprovalEventsOffsetPagination(...): EventOffsetPage! @tag(name: "admin")
}
```

### Rule 4: Input Types Mirror Their Usage
```graphql
# If a mutation is admin-only, its input type should be too
input ApproveOrganizerInput @tag(name: "admin") {
    organizationId: ID!
    approvedBy: ID!
    notes: String
}
```

## Service-Specific Tagging

### Catalog Service (Events, Locations, Categories)

| Type/Query | Tags | Reason |
|------------|------|--------|
| `Event` (basic fields) | (none/public) | Everyone browses events |
| `Event.organizerId` | organizer, admin | Only organizers/admins need this |
| `Event.approvedBy`, `Event.rejectedBy` | admin | Approval workflow |
| `discoverEvents` | (none/public) | Mobile event discovery |
| `eventsOffsetPagination` | admin | Admin table pagination |
| `draftEventsOffsetPagination` | organizer, admin | Organizer dashboard |
| `pendingApprovalEventsOffsetPagination` | admin | Admin approval queue |
| `eventStats` | admin | Platform-wide statistics |
| `approveEvent`, `rejectEvent` | admin | Admin-only mutations |
| `createEvent`, `updateEvent` | organizer | Organizer mutations |

### Booking Service (Tickets, Payments, Refunds)

| Type/Query | Tags | Reason |
|------------|------|--------|
| `Ticket` (basic fields) | (none/public) | Ticket holders see their tickets |
| `Ticket.buyerId`, `Ticket.buyerEmail` | organizer, admin | Privacy - organizers/admins only |
| `purchaseTicket` | mobile | Consumer purchase flow |
| `ticketsByBuyerCursorPagination` | mobile | My tickets list |
| `ticketsByEventOffsetPagination` | organizer, admin | Event attendee list |
| `ticketStats`, `transactionStats` | admin | Platform statistics |
| `financialReport` | admin | Financial reporting |
| `adminUpdateTicket` | admin | Admin ticket operations |
| `escrowAccountsOffsetPagination` | admin | Financial admin |
| `createPayoutRequest` | organizer | Organizer payout requests |
| `approvePayoutRequest` | admin | Admin payout approval |

### Identity Service (Users, Organizations, Permissions)

| Type/Query | Tags | Reason |
|------------|------|--------|
| `User` (basic fields) | (none/public) | Profile information |
| `User.keycloakId` | internal | Service sync only |
| `me`, `myProfile` | mobile | Current user info |
| `usersOffsetPagination` | admin | Admin user list |
| `platformStatistics` | admin | Platform-wide stats |
| `myOrganization` | organizer | Organizer dashboard |
| `approveOrganizer`, `rejectOrganizer` | admin | Admin approval |
| `syncUserFromKeycloak` | internal | Service-to-service |
| `inviteTeamMember` | organizer | Team management |

## Implementation Checklist

- [x] Add `@tag` to federation imports in each service
- [x] Tag catalog-service schema
- [x] Tag booking-service schema
- [x] Tag identity-service schema
- [x] Create client-specific codegen configs
- [x] Create example operation files per client (existing operations in libs/shared)
- [ ] Update frontend build pipelines (CI/CD)
- [ ] (Optional) Implement Apollo Contracts for schema-level enforcement

## Codegen Configuration

### Mobile App (`frontend/mobile/codegen.ts`)
```typescript
// Operations configuration - KEY: onlyOperationTypes filters to used types
const operationsConfig = {
  // KEY SETTING: Only generate types used by actual mobile operations
  // This automatically excludes admin/internal types not used by mobile
  onlyOperationTypes: true,
  preResolveTypes: true,
  skipTypename: false,
  nonOptionalTypename: true,
  documentMode: 'documentNode',
};

const config: CodegenConfig = {
  schema: [
    '../../backend/catalog-service/src/main/resources/graphql/**/*.graphqls',
    '../../backend/booking-service/src/main/resources/graphql/**/*.graphqls',
    '../../backend/identity-service/src/main/resources/graphql/**/*.graphqls',
  ],
  documents: [
    'src/api/**/*.documents.ts',
    'src/api/**/*.graphql',
  ],
  generates: {
    'src/types/graphql/generated.ts': {
      plugins: ['typescript', 'typescript-operations', 'typed-document-node'],
      config: operationsConfig,
    },
  },
};
```

### Admin Dashboard (`frontend/web/codegen.ts`)
```typescript
// Operations configuration with onlyOperationTypes
const operationsConfig = {
  onlyOperationTypes: true,
  preResolveTypes: true,
  skipTypename: false,
  nonOptionalTypename: true,
};

const config: CodegenConfig = {
  schema: [
    '../../backend/catalog-service/src/main/resources/graphql/**/*.graphqls',
    '../../backend/booking-service/src/main/resources/graphql/**/*.graphqls',
    '../../backend/identity-service/src/main/resources/graphql/**/*.graphqls',
  ],
  generates: {
    // Admin-specific types
    'apps/admin/src/types/generated/graphql.ts': {
      documents: [
        'libs/shared/src/api/graphql/**/*.admin-hooks.ts',
        'libs/shared/src/api/graphql/**/*Definitions.ts',
      ],
      plugins: ['typescript', 'typescript-operations'],
      config: operationsConfig,
    },
    // Consumer/ticketing types
    'apps/ticketing/src/types/generated/graphql.ts': {
      documents: [
        'libs/shared/src/api/graphql/**/!(*.admin-hooks).ts',
      ],
      plugins: ['typescript', 'typescript-operations'],
      config: operationsConfig,
    },
  },
};
```

## Expected Results

| Client | Operations | Generated Types | File Size |
|--------|------------|-----------------|-----------|
| Mobile App | ~30 | ~150 types | ~500 lines |
| Organizer Dashboard | ~80 | ~300 types | ~1500 lines |
| Admin Portal | ~150 | ~450 types | ~3000 lines |
| Full Schema | All | ~500+ types | ~10000 lines |

## Migration Path

1. **Phase 1**: Add tags to schemas ✅ COMPLETED
2. **Phase 2**: Create client-specific operation files ✅ COMPLETED (existing in libs/shared)
3. **Phase 3**: Configure separate codegen per client ✅ COMPLETED
4. **Phase 4**: Remove monolithic schema-types.ts (pending - keep for backward compatibility)
5. **Phase 5**: (Optional) Implement Apollo Contracts for schema-level enforcement

## Running Codegen

```bash
# Web frontend (admin + ticketing)
cd frontend/web
npm install  # Install new dependencies
npm run codegen

# Mobile frontend
cd frontend/mobile
npm install  # Install new dependencies
npm run codegen
```
