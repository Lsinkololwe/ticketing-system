# Stub Types & Missing Backend Features Analysis

## Executive Summary

This document analyzes stub types and placeholder implementations in the frontend that require corresponding backend GraphQL schema definitions. The goal is to establish the **supergraph as the single source of truth** for all types across the ticketing system.

**Current State**: 47+ stub implementations exist in the frontend, representing features that the admin dashboard UI expects but the backend doesn't yet provide.

**Impact**: These stubs prevent full functionality of the admin dashboard, particularly in:
- Dashboard analytics and KPIs
- Financial account management
- Transaction lifecycle management
- System health monitoring

---

## Category 1: Dashboard Analytics & Statistics

### Business Context
The admin dashboard requires aggregated statistics for decision-making, performance monitoring, and operational oversight. Currently, the dashboard displays placeholder data for several key metrics.

### 1.1 User Statistics (`UserStats`, `UserTypeStats`)

**Business Use Case**: Platform administrators need to monitor user growth, identify trends, and track user segmentation for marketing and capacity planning.

**Current Stub** (users.types.ts):
```typescript
export type UserStats = {
  totalUsers: number;
  activeUsers: number;
  suspendedUsers: number;
  newUsersThisMonth: number;
};

export type UserTypeStats = {
  userType: UserType;
  count: number;
  percentage: number;
};
```

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| identity-service | `schema.graphqls` | HIGH |

**Proposed GraphQL Schema**:
```graphql
type UserStats {
  totalUsers: Int!
  activeUsers: Int!
  suspendedUsers: Int!
  lockedUsers: Int!
  pendingVerificationUsers: Int!
  newUsersThisMonth: Int!
  newUsersThisWeek: Int!
  usersByType: [UserTypeStats!]!
  usersByStatus: [UserStatusStats!]!
  growthRate: Float  # Month-over-month percentage
}

type UserTypeStats {
  userType: UserType!
  count: Int!
  percentage: Float!
}

type UserStatusStats {
  status: AccountStatus!
  count: Int!
  percentage: Float!
}

extend type Query {
  userStats: UserStats! @requiresAuth(roles: ["ADMIN", "SUPER_ADMIN"])
}
```

**MongoDB Aggregation** (identity-service):
```java
// UserStatsService.java
public Mono<UserStats> getUserStats() {
    return Mono.zip(
        userRepository.count(),
        userRepository.countByAccountStatus(AccountStatus.ACTIVE),
        userRepository.countByAccountStatus(AccountStatus.SUSPENDED),
        getUsersByTypeAggregation(),
        getNewUsersThisMonth()
    ).map(tuple -> UserStats.builder()
        .totalUsers(tuple.getT1())
        .activeUsers(tuple.getT2())
        .suspendedUsers(tuple.getT3())
        .usersByType(tuple.getT4())
        .newUsersThisMonth(tuple.getT5())
        .build());
}
```

---

### 1.2 Payout Statistics (`PayoutStats`)

**Business Use Case**: Finance team needs to monitor payout volumes, pending amounts, and request status distribution for cash flow management and organizer relations.

**Current Stub** (analytics.admin-hooks.ts):
```typescript
usePayoutStatsAdmin() returns {
  totalPayouts: 0,
  pendingAmount: 0,
  completedAmount: 0,
  pendingCount: 0,
  pendingPayoutRequests: 0,
  processingPayoutRequests: 0,
  completedPayoutRequests: 0,
  totalPayoutRequests: 0,
  approvedPayoutRequests: 0,
  rejectedPayoutRequests: 0,
}
```

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| booking-service | `schema.graphqls` | HIGH |

**Proposed GraphQL Schema**:
```graphql
type PayoutStats {
  # Counts
  totalPayoutRequests: Int!
  pendingPayoutRequests: Int!
  approvedPayoutRequests: Int!
  processingPayoutRequests: Int!
  completedPayoutRequests: Int!
  rejectedPayoutRequests: Int!
  failedPayoutRequests: Int!

  # Amounts
  totalPayoutAmount: BigDecimal!
  pendingPayoutAmount: BigDecimal!
  completedPayoutAmount: BigDecimal!
  failedPayoutAmount: BigDecimal!

  # Averages & Metrics
  averagePayoutAmount: BigDecimal
  averageProcessingTimeHours: Float

  # Time-based
  payoutsThisMonth: Int!
  payoutsThisWeek: Int!
  amountThisMonth: BigDecimal!
}

extend type Query {
  payoutStats(organizerId: String): PayoutStats! @requiresAuth(roles: ["ADMIN", "FINANCE"])
}
```

---

### 1.3 City-based Event Statistics (`CityStats`)

**Business Use Case**: Marketing and expansion teams need geographic insights to identify high-performing cities and potential growth markets.

**Current Stub** (analytics.types.ts):
```typescript
export interface CityStats {
  city: string;
  eventCount: number;
  totalRevenue: number;
}
```

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| catalog-service | `schema.graphqls` | MEDIUM |

**Proposed GraphQL Schema**:
```graphql
type CityEventStats {
  city: String!
  province: String
  country: String!
  eventCount: Int!
  publishedEventCount: Int!
  totalCapacity: Int!
  totalRevenue: BigDecimal!
  averageTicketPrice: BigDecimal
  topCategories: [EventCategoryStats!]!
}

extend type Query {
  eventStatsByCity(country: String): [CityEventStats!]! @requiresAuth(roles: ["ADMIN"])
  topCitiesByEvents(limit: Int = 10): [CityEventStats!]!
  topCitiesByRevenue(limit: Int = 10): [CityEventStats!]!
}
```

---

### 1.4 Ticket Status Statistics (`TicketStatusStats`)

**Business Use Case**: Operations team needs to monitor ticket lifecycle distribution for fraud detection and customer service planning.

**Current Stub** (analytics.types.ts):
```typescript
export interface TicketStatusStats {
  status: string;
  count: number;
  percentage: number;
}
```

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| booking-service | `schema.graphqls` | MEDIUM |

**Note**: This may already exist as part of `TicketStats`. Verify if `ticketsByStatus` field is available.

---

### 1.5 Recent Events for Dashboard

**Business Use Case**: Dashboard quick-view of latest event activity.

**Current Stub** (analytics.admin-hooks.ts):
```typescript
useRecentEventsAdmin() // Returns empty array
```

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| catalog-service | `schema.graphqls` | LOW |

**Proposed GraphQL Schema**:
```graphql
extend type Query {
  # Dashboard-optimized query with limited fields
  dashboardRecentEvents(limit: Int = 5): [EventSummary!]!
}

type EventSummary {
  id: ID!
  title: String!
  status: EventStatus!
  eventDateTime: DateTime!
  organizerName: String!
  category: EventCategory!
  ticketsSold: Int!
  totalCapacity: Int!
  createdAt: DateTime!
}
```

---

## Category 2: Financial Account Management

### Business Context
The platform manages escrow accounts for event proceeds and bank accounts for organizer payouts. Admins need full CRUD capabilities and lifecycle management.

### 2.1 Bank Account Management

**Business Use Case**: Organizers link bank accounts for receiving payouts. Admins may need to verify, update, or remove accounts for compliance.

**Current Stubs** (use-account-management.ts):
- `useCreateBankAccount()`
- `useUpdateBankAccount()`
- `useDeleteBankAccount()`

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| booking-service | `schema.graphqls` | HIGH |

**Proposed GraphQL Schema**:
```graphql
input CreateBankAccountInput {
  organizerId: ID!
  accountName: String!
  accountNumber: String!
  bankName: String!
  bankCode: String!
  branchCode: String
  swiftCode: String
  currency: String! = "ZMW"
  isPrimary: Boolean = false
}

input UpdateBankAccountInput {
  accountName: String
  bankName: String
  branchCode: String
  isPrimary: Boolean
  isActive: Boolean
}

type BankAccountMutationResponse {
  success: Boolean!
  message: String
  data: BankAccount
  errors: [String!]!
}

extend type Mutation {
  createBankAccount(input: CreateBankAccountInput!): BankAccountMutationResponse!
    @requiresAuth(roles: ["ORGANIZER", "ADMIN"])

  updateBankAccount(id: ID!, input: UpdateBankAccountInput!): BankAccountMutationResponse!
    @requiresAuth(roles: ["ORGANIZER", "ADMIN"])

  deleteBankAccount(id: ID!): BankAccountMutationResponse!
    @requiresAuth(roles: ["ORGANIZER", "ADMIN"])

  verifyBankAccount(id: ID!): BankAccountMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])
}
```

---

### 2.2 Platform Revenue Account Management

**Business Use Case**: Platform-level financial account that collects commissions. Requires special admin controls.

**Current Stubs** (use-lifecycle-actions.ts):
- `usePlatformRevenueAccountActions()`
- `useSuspendPlatformRevenueAccount()`
- `useReactivatePlatformRevenueAccount()`
- `useClosePlatformRevenueAccount()`
- `useUpdateAccountLimits()`

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| booking-service | `schema.graphqls` | MEDIUM |

**Proposed GraphQL Schema**:
```graphql
type PlatformRevenueAccount {
  id: ID!
  accountNumber: String!
  status: AccountStatus!
  currentBalance: BigDecimal!
  totalCommissionsCollected: BigDecimal!
  totalWithdrawals: BigDecimal!
  dailyLimit: BigDecimal
  monthlyLimit: BigDecimal
  lastActivityAt: DateTime
  createdAt: DateTime!
  updatedAt: DateTime!
}

input UpdateAccountLimitsInput {
  dailyLimit: BigDecimal
  monthlyLimit: BigDecimal
  transactionLimit: BigDecimal
}

extend type Query {
  platformRevenueAccount: PlatformRevenueAccount @requiresAuth(roles: ["SUPER_ADMIN", "FINANCE"])
  platformRevenueSummary(startDate: DateTime, endDate: DateTime): PlatformRevenueSummary!
}

extend type Mutation {
  suspendPlatformRevenueAccount(reason: String!): MutationResponse!
    @requiresAuth(roles: ["SUPER_ADMIN"])

  reactivatePlatformRevenueAccount: MutationResponse!
    @requiresAuth(roles: ["SUPER_ADMIN"])

  updatePlatformAccountLimits(input: UpdateAccountLimitsInput!): MutationResponse!
    @requiresAuth(roles: ["SUPER_ADMIN", "FINANCE"])
}
```

---

## Category 3: Transaction Lifecycle Management

### Business Context
Transactions can get stuck, fail, or require manual intervention. Admins need tools to manage the full transaction lifecycle.

### 3.1 Transaction Recovery Operations

**Current Stubs** (use-lifecycle-actions.ts):
- `useResumeStuckTransaction()`
- `useMarkTransactionForReview()`
- `useResolveTransactionIssue()`

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| booking-service | `schema.graphqls` | HIGH |

**Proposed GraphQL Schema**:
```graphql
enum TransactionIssueType {
  TIMEOUT
  PAYMENT_GATEWAY_ERROR
  INSUFFICIENT_FUNDS
  INVALID_ACCOUNT
  SUSPECTED_FRAUD
  DUPLICATE_TRANSACTION
  OTHER
}

enum TransactionResolutionType {
  RETRY_SUCCESSFUL
  MANUAL_COMPLETION
  REFUND_ISSUED
  MARKED_AS_FAILED
  FRAUD_CONFIRMED
  FALSE_POSITIVE
}

input MarkForReviewInput {
  transactionId: ID!
  issueType: TransactionIssueType!
  notes: String
  priority: Int = 1
}

input ResolveTransactionInput {
  transactionId: ID!
  resolution: TransactionResolutionType!
  notes: String!
  refundAmount: BigDecimal
}

extend type Mutation {
  resumeStuckTransaction(transactionId: ID!): TransactionMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])

  markTransactionForReview(input: MarkForReviewInput!): TransactionMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])

  resolveTransactionIssue(input: ResolveTransactionInput!): TransactionMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])

  bulkRetryFailedTransactions(transactionIds: [ID!]!): BulkOperationResponse!
    @requiresAuth(roles: ["ADMIN"])
}

extend type Query {
  transactionsForReview(pagination: OffsetPaginationInput): TransactionOffsetPage!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])

  stuckTransactions(pagination: OffsetPaginationInput): TransactionOffsetPage!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])
}
```

---

### 3.2 Payout Request Lifecycle Operations

**Current Stubs** (use-lifecycle-actions.ts):
- `useRetryPayoutRequest()`
- `useResumePayoutRequest()`
- `useMarkPayoutRequestForReview()`
- `useResolvePayoutRequestIssue()`

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| booking-service | `schema.graphqls` | HIGH |

**Proposed GraphQL Schema**:
```graphql
enum PayoutIssueType {
  BANK_REJECTED
  INVALID_ACCOUNT_DETAILS
  INSUFFICIENT_ESCROW_BALANCE
  COMPLIANCE_HOLD
  SUSPECTED_FRAUD
  TECHNICAL_ERROR
  OTHER
}

input MarkPayoutForReviewInput {
  payoutRequestId: ID!
  issueType: PayoutIssueType!
  notes: String
}

input ResolvePayoutIssueInput {
  payoutRequestId: ID!
  resolution: String!
  notes: String!
  newBankAccountId: ID
}

extend type Mutation {
  retryPayoutRequest(payoutRequestId: ID!): PayoutRequestMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])

  resumePayoutRequest(payoutRequestId: ID!): PayoutRequestMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])

  markPayoutRequestForReview(input: MarkPayoutForReviewInput!): PayoutRequestMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])

  resolvePayoutRequestIssue(input: ResolvePayoutIssueInput!): PayoutRequestMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])
}

extend type Query {
  payoutRequestsForReview(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])
}
```

---

### 3.3 Escrow Account Lifecycle Operations

**Current Stubs** (use-lifecycle-actions.ts):
- `useSuspendEscrowAccount()`
- `useReactivateEscrowAccount()`
- `useCloseEscrowAccount()`
- `useToggleEscrowMaintenanceMode()`

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| booking-service | `schema.graphqls` | MEDIUM |

**Note**: Some of these may already exist via `updateEscrowAccountStatus`. Verify current implementation and extend if needed.

**Proposed Additions**:
```graphql
extend type Mutation {
  setEscrowMaintenanceMode(accountId: ID!, enabled: Boolean!, reason: String!): EscrowAccountMutationResponse!
    @requiresAuth(roles: ["ADMIN"])

  closeEscrowAccount(accountId: ID!, reason: String!, transferRemainingTo: ID): EscrowAccountMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])
}
```

---

## Category 4: System Health & Monitoring

### Business Context
Platform administrators need real-time visibility into system health, service status, and operational alerts.

### 4.1 System Health Monitoring

**Current Stubs** (use-system-health.ts):
- `useSystemHealth()`
- `useTransactionHealth()`
- `useSystemAlerts()`

**Implementation Plan**:
| Service | Schema Location | Priority |
|---------|-----------------|----------|
| api-gateway OR dedicated monitoring-service | `schema.graphqls` | LOW |

**Proposed GraphQL Schema**:
```graphql
enum ServiceStatus {
  HEALTHY
  DEGRADED
  UNHEALTHY
  UNKNOWN
}

enum AlertSeverity {
  INFO
  WARNING
  ERROR
  CRITICAL
}

type ServiceHealth {
  serviceName: String!
  status: ServiceStatus!
  responseTimeMs: Int
  lastCheckedAt: DateTime!
  errorMessage: String
}

type SystemHealth {
  overallStatus: ServiceStatus!
  uptime: Int!  # seconds
  services: [ServiceHealth!]!
  databaseStatus: ServiceStatus!
  cacheStatus: ServiceStatus!
  messageQueueStatus: ServiceStatus!
}

type TransactionHealth {
  pendingCount: Int!
  failedCount: Int!
  stuckCount: Int!
  successRate: Float!
  averageProcessingTimeMs: Int!
  lastHourVolume: Int!
}

type SystemAlert {
  id: ID!
  severity: AlertSeverity!
  title: String!
  message: String!
  service: String
  createdAt: DateTime!
  acknowledgedAt: DateTime
  acknowledgedBy: String
  resolved: Boolean!
}

extend type Query {
  systemHealth: SystemHealth! @requiresAuth(roles: ["ADMIN", "SUPER_ADMIN"])
  transactionHealth: TransactionHealth! @requiresAuth(roles: ["ADMIN", "FINANCE"])
  systemAlerts(severity: AlertSeverity, unacknowledgedOnly: Boolean): [SystemAlert!]!
    @requiresAuth(roles: ["ADMIN", "SUPER_ADMIN"])
}

extend type Mutation {
  acknowledgeAlert(alertId: ID!): MutationResponse! @requiresAuth(roles: ["ADMIN"])
}

extend type Subscription {
  systemAlertCreated: SystemAlert! @requiresAuth(roles: ["ADMIN", "SUPER_ADMIN"])
  serviceStatusChanged: ServiceHealth! @requiresAuth(roles: ["ADMIN", "SUPER_ADMIN"])
}
```

---

## Category 5: Type Aliases (Backwards Compatibility)

These are not missing features but naming inconsistencies that should be addressed by standardizing on canonical names.

| Frontend Alias | Actual Type | Recommendation |
|----------------|-------------|----------------|
| `EventLifecycleDto` | `EventLifecycle` | Remove alias, use canonical name |
| `EventTicketStatisticsDto` | `EventTicketStatistics` | Remove alias, use canonical name |
| `TicketValidationMutationResponse` | `TicketMutationResponse` | Remove alias OR add dedicated type if validation returns extra fields |
| `BulkMutationResponse` | `BulkOperationResponse` | Remove alias, use canonical name |
| `OrganizationOffsetPage` | `OrganizerApplicationOffsetPage` | Rename backend type to `OrganizationOffsetPage` for clarity |
| `PaginationInput` | `OffsetPaginationInput` | Remove alias, use canonical name |
| `EscrowAccount` | `EventEscrowAccount` | Consider if a general `EscrowAccount` type is needed |
| `LocationDto` | `Location` | Remove alias, use canonical name |
| `TransactionType` | (missing) | Add enum to backend schema |

---

## Implementation Roadmap

### Phase 1: Critical Dashboard Features (Sprint 1-2)
**Priority: HIGH**

1. **UserStats** - identity-service
2. **PayoutStats** - booking-service
3. **Bank Account CRUD** - booking-service
4. **Transaction Recovery Operations** - booking-service

### Phase 2: Financial Operations (Sprint 3-4)
**Priority: HIGH**

1. **Payout Request Lifecycle** - booking-service
2. **Platform Revenue Account** - booking-service
3. **Enhanced Escrow Operations** - booking-service

### Phase 3: Analytics & Insights (Sprint 5-6)
**Priority: MEDIUM**

1. **CityEventStats** - catalog-service
2. **TicketStatusStats** - booking-service
3. **Dashboard Recent Events** - catalog-service

### Phase 4: Operational Monitoring (Sprint 7-8)
**Priority: LOW**

1. **System Health** - api-gateway or new monitoring-service
2. **Transaction Health** - booking-service
3. **System Alerts** - new monitoring-service

---

## Supergraph Composition Workflow

After implementing backend schemas:

```bash
# 1. Start services with updated schemas
cd backend && mvn spring-boot:run

# 2. Compose supergraph
cd docker-resources/apollo-router/ticketing
./compose-supergraph.sh

# 3. Regenerate frontend types
cd frontend/web
npm run codegen

# 4. Remove corresponding stub types from frontend
# 5. Update hooks to use generated types
```

---

## Success Criteria

1. **Zero stub types** in frontend codebase
2. **All types imported** from `schema-types.ts` (generated)
3. **Supergraph validates** with all subgraphs
4. **Admin dashboard** shows real data for all metrics
5. **Type safety** maintained across frontend-backend boundary

---

## Appendix: Complete Stub Inventory

### Stub Types (8)
| Type | Location | Backend Service |
|------|----------|-----------------|
| `UserStats` | users.types.ts | identity-service |
| `UserTypeStats` | users.types.ts | identity-service |
| `CityStats` | analytics.types.ts | catalog-service |
| `TicketStatusStats` | analytics.types.ts | booking-service |
| `TransactionType` | payments.types.ts | booking-service |
| `PaginationInput` | users.types.ts | shared schema |

### Stub Hooks (35+)
| Category | Count | Backend Service |
|----------|-------|-----------------|
| Analytics/Stats | 4 | Various |
| Bank Account Mgmt | 3 | booking-service |
| Transaction Lifecycle | 3 | booking-service |
| Payout Lifecycle | 4 | booking-service |
| Escrow Lifecycle | 4 | booking-service |
| Platform Account | 5 | booking-service |
| System Health | 4 | api-gateway/monitoring |
| Ticket Queries | 1 | booking-service |

---

*Document Generated: 2026-03-26*
*Last Updated: 2026-03-26*
