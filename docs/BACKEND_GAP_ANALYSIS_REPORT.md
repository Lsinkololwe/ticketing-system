# Backend vs Frontend Gap Analysis Report

**Generated**: 2026-03-26
**Purpose**: Compare frontend stub types/hooks against actual backend GraphQL schema implementations

---

## Executive Summary

This analysis compares the 47+ frontend stub implementations documented in `STUB_TYPES_ANALYSIS.md` against the actual GraphQL schemas in the three backend services.

| Category | Expected | Implemented | Missing | % Complete |
|----------|----------|-------------|---------|------------|
| Statistics Types | 10 | 6 | 4 | 60% |
| Bank Account Mgmt | 4 | 5 | 0 | **100%** |
| Transaction Lifecycle | 7 | 1 | 6 | 14% |
| Payout Lifecycle | 4 | 1 | 3 | 25% |
| Escrow Lifecycle | 4 | 1 | 3 | 25% |
| Platform Account | 5 | 0 | 5 | 0% |
| System Health | 8 | 0 | 8 | 0% |
| **TOTAL** | **42** | **14** | **29** | **33%** |

---

## ✅ FULLY IMPLEMENTED (Ready for Frontend Integration)

### 1. Bank Account Management (booking-service)
**Status**: 100% Complete - Frontend can be wired up immediately

| Feature | GraphQL Schema | Status |
|---------|----------------|--------|
| `BankAccount` type | ✅ Line 428 | Complete |
| `createBankAccount` mutation | ✅ Line 1579 | Complete |
| `updateBankAccount` mutation | ✅ Line 1580 | Complete |
| `deleteBankAccount` mutation | ✅ Line 1581 | Complete |
| `setDefaultBankAccount` mutation | ✅ Line 1582 | Complete |
| `verifyBankAccount` mutation | ✅ Line 1584 | Complete |
| `bankAccount(id)` query | ✅ Line 1422 | Complete |
| `bankAccountsByOrganizer` query | ✅ Line 1423 | Complete |
| `defaultBankAccount` query | ✅ Line 1424 | Complete |

**Frontend Action**: Replace stub hooks in `use-account-management.ts` with actual GraphQL calls.

---

### 2. Payout Statistics (booking-service)
**Status**: Complete for basic stats

```graphql
type PayoutRequestStats {
    totalPayoutRequests: Int!
    pendingPayoutRequests: Int!
    approvedPayoutRequests: Int!
    processingPayoutRequests: Int!
    completedPayoutRequests: Int!
    failedPayoutRequests: Int!
    totalPayoutAmount: BigDecimal!
    pendingPayoutAmount: BigDecimal!
}

# Query
payoutRequestStats(organizerId: ID): PayoutRequestStats!
```

**Frontend Action**: Replace `usePayoutStatsAdmin()` stub with actual query.

---

### 3. Event Statistics (catalog-service)
**Status**: Complete

```graphql
type EventStats {
    totalEvents: Int!
    publishedEvents: Int!
    draftEvents: Int!
    pendingApprovalEvents: Int!
    cancelledEvents: Int!
    completedEvents: Int!
    totalCapacity: Int!
    totalSoldTickets: Int!
    totalRevenue: BigDecimal
    eventsByCategory: [EventCategoryStats!]
    eventsByStatus: [EventStatusStats!]
    eventsByOrganizer: [EventOrganizerStats!]
    recentEvents: [Event!]  # ← Covers dashboardRecentEvents need
}
```

**Frontend Action**: Update `useEventStatsAdmin()` to use this query.

---

### 4. Ticket Statistics (booking-service)
**Status**: Complete

```graphql
type TicketStats {
    totalTickets: Int!
    purchasedTickets: Int!
    validatedTickets: Int!
    usedTickets: Int!
    refundedTickets: Int!
    expiredTickets: Int!
    cancelledTickets: Int!
    pendingPaymentTickets: Int!
    ticketsByStatus: [TicketStatusStats!]  # ← Covers TicketStatusStats need
    ticketsByCategory: [TicketCategoryStats!]
    recentTickets: [Ticket!]
}

type TicketStatusStats {
    status: TicketStatus!
    count: Int!
    percentage: Float!
}
```

**Frontend Action**: Update `useTicketStatsAdmin()` to use this query.

---

### 5. Location/City Statistics (catalog-service)
**Status**: Complete (via LocationStats)

```graphql
type LocationStats {
    totalLocations: Int!
    activeLocations: Int!
    citiesWithEvents: Int!
    countriesWithEvents: Int!
    averageEventsPerLocation: Float!
    topCities: [CityStats!]  # ← Contains city-level stats
    recentAdditions: Int!
    locationsByCapacity: [LocationCapacityStats!]
}

type CityStats {
    cityId: String
    cityName: String!
    country: String!
    eventCount: Int!
    totalCapacity: Int!
    averageTicketPrice: BigDecimal
}
```

**Frontend Action**: Use `LocationStats.topCities` for city analytics.

---

### 6. Single Escrow/Payout Operations (booking-service)
**Status**: Partially implemented

| Feature | Status | Notes |
|---------|--------|-------|
| `closeEscrowAccount` | ✅ Line 1556 | Implemented |
| `retryPayoutRequest` | ✅ Line 1571 | Implemented |

---

## ❌ NOT IMPLEMENTED (Backend Development Required)

### Priority 1: HIGH - User Statistics (identity-service)

**Missing Types**:
```graphql
# MUST ADD to identity-service schema.graphqls

type UserStats @tag(name: "admin") {
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

type UserTypeStats @tag(name: "admin") {
    userType: UserType!
    count: Int!
    percentage: Float!
}

type UserStatusStats @tag(name: "admin") {
    status: AccountStatus!
    count: Int!
    percentage: Float!
}

extend type Query {
    userStats: UserStats! @tag(name: "admin")
}
```

**Implementation Files Needed**:
- `identity-service/src/main/java/com/pml/identity/service/UserStatsService.java`
- `identity-service/src/main/java/com/pml/identity/graphql/resolver/UserStatsQueryResolver.java`
- MongoDB aggregation pipelines for user counts

---

### Priority 2: HIGH - Transaction Recovery Operations (booking-service)

**Missing Types & Mutations**:
```graphql
# MUST ADD to booking-service schema.graphqls

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
        @tag(name: "admin")

    markTransactionForReview(input: MarkForReviewInput!): TransactionMutationResponse!
        @tag(name: "admin")

    resolveTransactionIssue(input: ResolveTransactionInput!): TransactionMutationResponse!
        @tag(name: "admin")

    bulkRetryFailedTransactions(transactionIds: [ID!]!): BulkOperationResponse!
        @tag(name: "admin")
}

extend type Query {
    transactionsForReview(pagination: OffsetPaginationInput): TransactionOffsetPage!
        @tag(name: "admin")

    stuckTransactions(pagination: OffsetPaginationInput): TransactionOffsetPage!
        @tag(name: "admin")
}
```

---

### Priority 3: HIGH - Payout Lifecycle Operations (booking-service)

**Missing Mutations** (Only `retryPayoutRequest` exists):
```graphql
# MUST ADD to booking-service schema.graphqls

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
    # retryPayoutRequest already exists ✅

    resumePayoutRequest(payoutRequestId: ID!): PayoutRequestMutationResponse!
        @tag(name: "admin")

    markPayoutRequestForReview(input: MarkPayoutForReviewInput!): PayoutRequestMutationResponse!
        @tag(name: "admin")

    resolvePayoutRequestIssue(input: ResolvePayoutIssueInput!): PayoutRequestMutationResponse!
        @tag(name: "admin")
}

extend type Query {
    payoutRequestsForReview(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
        @tag(name: "admin")
}
```

---

### Priority 4: MEDIUM - Escrow Lifecycle Operations (booking-service)

**Missing Mutations** (Only `closeEscrowAccount` exists):
```graphql
# MUST ADD to booking-service schema.graphqls

extend type Mutation {
    # closeEscrowAccount already exists ✅

    suspendEscrowAccount(accountId: ID!, reason: String!): EscrowAccountMutationResponse!
        @tag(name: "admin")

    reactivateEscrowAccount(accountId: ID!, reason: String): EscrowAccountMutationResponse!
        @tag(name: "admin")

    setEscrowMaintenanceMode(accountId: ID!, enabled: Boolean!, reason: String!): EscrowAccountMutationResponse!
        @tag(name: "admin")
}
```

---

### Priority 5: MEDIUM - Platform Revenue Account (booking-service)

**Missing Types & Operations**:
```graphql
# MUST ADD to booking-service schema.graphqls

type PlatformRevenueAccount @tag(name: "admin") {
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
    platformRevenueAccount: PlatformRevenueAccount @tag(name: "admin")
}

extend type Mutation {
    suspendPlatformRevenueAccount(reason: String!): MutationResponse!
        @tag(name: "admin")

    reactivatePlatformRevenueAccount: MutationResponse!
        @tag(name: "admin")

    updatePlatformAccountLimits(input: UpdateAccountLimitsInput!): MutationResponse!
        @tag(name: "admin")
}
```

---

### Priority 6: LOW - System Health Monitoring

**Service**: Could be API Gateway or new monitoring-service

**Missing Types**:
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
    systemHealth: SystemHealth!
    transactionHealth: TransactionHealth!
    systemAlerts(severity: AlertSeverity, unacknowledgedOnly: Boolean): [SystemAlert!]!
}

extend type Mutation {
    acknowledgeAlert(alertId: ID!): MutationResponse!
}
```

---

## Implementation Roadmap

### Sprint 1-2: Critical Dashboard Stats
| Task | Service | Effort |
|------|---------|--------|
| Implement UserStats type & query | identity-service | 3 days |
| Add UserStatsService with MongoDB aggregation | identity-service | 2 days |
| Wire frontend hooks to existing stats (Event, Ticket, Payout) | frontend | 2 days |

### Sprint 3-4: Transaction & Payout Lifecycle
| Task | Service | Effort |
|------|---------|--------|
| Add TransactionIssueType enum | booking-service | 1 day |
| Implement transaction recovery mutations | booking-service | 3 days |
| Implement payout lifecycle mutations | booking-service | 3 days |
| Add queries for transactions/payouts needing review | booking-service | 2 days |

### Sprint 5-6: Escrow & Platform Account
| Task | Service | Effort |
|------|---------|--------|
| Implement escrow lifecycle mutations | booking-service | 2 days |
| Implement PlatformRevenueAccount | booking-service | 3 days |
| Wire frontend lifecycle hooks | frontend | 2 days |

### Sprint 7-8: System Health (Optional)
| Task | Service | Effort |
|------|---------|--------|
| Design system health architecture | api-gateway | 2 days |
| Implement health aggregation | api-gateway | 3 days |
| Implement alerting | new service | 5 days |

---

## Frontend Actions (Immediate)

The following frontend stubs can be replaced NOW with actual GraphQL calls:

1. **Bank Account Management** (`use-account-management.ts`)
   - `useCreateBankAccount()` → `createBankAccount` mutation
   - `useUpdateBankAccount()` → `updateBankAccount` mutation
   - `useDeleteBankAccount()` → `deleteBankAccount` mutation

2. **Statistics Hooks** (`analytics.admin-hooks.ts`)
   - `useEventStatsAdmin()` → `eventStats` query (catalog-service)
   - `useTicketStatsAdmin()` → Use PlatformSummary or dedicated query
   - `usePayoutStatsAdmin()` → `payoutRequestStats` query
   - `useRecentEventsAdmin()` → `eventStats.recentEvents` field

3. **Payout Operations** (`use-lifecycle-actions.ts`)
   - `useRetryPayoutRequest()` → `retryPayoutRequest` mutation
   - `useCloseEscrowAccount()` → `closeEscrowAccount` mutation

---

## Summary

**Completed (33%)**:
- Bank Account CRUD ✅
- Basic Statistics ✅ (Event, Ticket, Payout counts)
- Location/City Stats ✅
- Basic Escrow/Payout operations ✅

**Missing (67%)**:
- User Statistics (identity-service)
- Transaction Recovery Operations
- Payout Lifecycle (beyond retry)
- Escrow Lifecycle (beyond close)
- Platform Revenue Account
- System Health Monitoring

**Recommended Next Steps**:
1. Implement `UserStats` in identity-service (highest impact for admin dashboard)
2. Wire frontend to existing backend stats queries
3. Implement transaction/payout lifecycle mutations
4. Defer system health monitoring to later phase
