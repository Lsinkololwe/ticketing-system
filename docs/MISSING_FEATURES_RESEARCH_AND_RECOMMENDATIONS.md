# Missing Features: Research, Analysis & Implementation Recommendations

## Document Purpose

This document provides a comprehensive business and technical analysis of missing backend features identified in the ticketing system. It includes:
- Industry research and best practices
- Implementation recommendations with justifications
- Business value assessment
- Architecture alignment with existing patterns

---

## Executive Summary

### Current State Assessment

Your ticketing system follows **industry-leading patterns**:
- **Reactive architecture** (Spring WebFlux + MongoDB Reactive)
- **Apollo Federation 2** for GraphQL composition
- **Event-driven communication** (Azure Service Bus + Spring Modulith)
- **Mobile money integration** (PawaPay) for Zambian market

However, **47+ stub implementations** in the frontend indicate gaps between the admin dashboard's design and backend capabilities, primarily in:

| Gap Category | Business Impact | Priority |
|-------------|-----------------|----------|
| Dashboard Analytics | Blind decision-making, no KPIs | **CRITICAL** |
| Transaction Lifecycle | Manual intervention impossible | **CRITICAL** |
| Bank Account Management | Organizer payout setup blocked | **HIGH** |
| System Health Monitoring | No operational visibility | **MEDIUM** |

### Key Recommendations

1. **Implement MongoDB aggregation-based statistics** (aligns with existing `TicketStatsService` pattern)
2. **Add transaction state machine** with compensating actions (Saga pattern)
3. **Use polling with Apollo Client** for real-time updates (NOT subscriptions - see Part 5)
4. **Leverage Spring Boot Actuator** for system health (not custom implementation)
5. **Implement payment provider abstraction layer** (Strategy + Adapter patterns)
6. **Use Spring Modulith Transactional Outbox** for financial integrity

---

## Part 1: Dashboard Analytics & Statistics

### Industry Research

According to [Ticket Fairy](https://www.ticketfairy.com/event-ticketing/event-data-analytics) and [run.events](https://run.events/knowledge/tickettypes), modern ticketing platforms require real-time visibility into:

| KPI Category | Metrics | Update Frequency |
|--------------|---------|------------------|
| **Sales Velocity** | Tickets sold/hour, conversion rate | Real-time |
| **Revenue** | Gross, net, by category, by event | Real-time |
| **User Growth** | New registrations, active users, churn | Daily |
| **Financial Health** | Pending payouts, escrow balances | Real-time |
| **Geographic** | Sales by city/region | Daily |

[Fintech dashboard best practices](https://www.usedatabrain.com/blog/fintech-dashboards) recommend:
- **Limit to 5-7 key KPIs** per dashboard view
- **Role-based dashboards** (executives vs. operations vs. finance)
- **Real-time for financial data**, daily aggregates for analytics

### Gap Analysis: User Statistics

**Missing Types**: `UserStats`, `UserTypeStats`

**Business Need**: Platform administrators need to monitor user growth for:
- Capacity planning (server scaling)
- Marketing ROI assessment
- Investor reporting
- Fraud pattern detection

**Your Current Pattern** (from `TicketStatsService.java`):
```java
public Mono<TicketStats> getTicketStats() {
    return Mono.zip(
        ticketRepository.count(),
        getStatusCountsAggregation(),
        getCategoryStatsAggregation(),
        getRecentTickets()
    ).map(tuple -> TicketStats.builder()...);
}
```

**Recommended Implementation**:

```graphql
# identity-service/schema.graphqls

type UserStats {
  # Core counts
  totalUsers: Int!
  activeUsers: Int!
  suspendedUsers: Int!
  lockedUsers: Int!
  pendingVerificationUsers: Int!

  # Time-based metrics
  newUsersToday: Int!
  newUsersThisWeek: Int!
  newUsersThisMonth: Int!

  # Growth metrics
  dailyActiveUsers: Int!          # DAU - logged in within 24h
  monthlyActiveUsers: Int!        # MAU - logged in within 30d
  growthRatePercent: Float        # Month-over-month

  # Segmentation
  usersByType: [UserTypeStats!]!
  usersByStatus: [AccountStatusStats!]!

  # Recent activity
  recentRegistrations: [User!]!   # Last 10
}

type UserTypeStats {
  userType: UserType!
  count: Int!
  percentage: Float!
  activeCount: Int!               # Active within this type
}

extend type Query {
  userStats: UserStats! @requiresAuth(roles: ["ADMIN", "SUPER_ADMIN"])
}
```

**Why This Design**:
1. **Single query, multiple aggregations** - Matches your `Mono.zip()` pattern for parallel execution
2. **DAU/MAU metrics** - Industry standard for platform health ([source](https://www.finrofca.com/news/fintech-kpi-guide))
3. **Growth rate** - Essential for investor reporting and capacity planning
4. **Segmentation by type/status** - Enables cohort analysis

**MongoDB Aggregation Pipeline**:
```java
// UserStatsService.java
@Service
@RequiredArgsConstructor
public class UserStatsService {
    private final ReactiveMongoTemplate mongoTemplate;

    public Mono<UserStats> getUserStats() {
        Instant now = Instant.now();
        Instant dayAgo = now.minus(Duration.ofDays(1));
        Instant weekAgo = now.minus(Duration.ofDays(7));
        Instant monthAgo = now.minus(Duration.ofDays(30));

        return Mono.zip(
            // Total counts by status (single aggregation)
            getStatusCountsAggregation(),
            // User type distribution
            getUserTypeDistribution(),
            // Time-based registrations
            getNewUserCounts(dayAgo, weekAgo, monthAgo),
            // Active user counts (DAU/MAU)
            getActiveUserCounts(dayAgo, monthAgo),
            // Recent registrations
            getRecentRegistrations(10)
        ).map(this::buildUserStats);
    }

    private Mono<Map<AccountStatus, Long>> getStatusCountsAggregation() {
        return mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.group("accountStatus").count().as("count"),
                Aggregation.project("count").and("_id").as("status")
            ),
            "users",
            StatusCountResult.class
        ).collectMap(r -> r.getStatus(), r -> r.getCount());
    }
}
```

**Business Value**:
| Metric | Value |
|--------|-------|
| Decision latency | Reduced from days to seconds |
| Investor reporting | Automated vs. manual spreadsheets |
| Fraud detection | Real-time anomaly visibility |
| Marketing ROI | Trackable user acquisition |

---

### Gap Analysis: Payout Statistics

**Missing Types**: `PayoutStats` (comprehensive)

**Business Need**: Finance team requires visibility for:
- Cash flow forecasting
- Organizer relationship management
- Compliance reporting
- Fraud detection (unusual payout patterns)

**Industry Standard** ([Rapyd TLM](https://www.rapyd.net/blog/transaction-lifecycle-management/)):
> "Transaction Lifecycle Management transforms how you handle every payment from checkout through dispute resolution."

**Current State**: Your `PayoutRequestStats` exists but is limited:
```graphql
type PayoutRequestStats {
  pendingPayoutRequests: Int!
  approvedPayoutRequests: Int!
  completedPayoutRequests: Int!
  # Missing: amounts, averages, time-based metrics
}
```

**Recommended Enhancement**:

```graphql
# booking-service/schema.graphqls

type PayoutStats {
  # Request counts by status
  totalRequests: Int!
  pendingRequests: Int!
  approvedRequests: Int!
  processingRequests: Int!
  completedRequests: Int!
  rejectedRequests: Int!
  failedRequests: Int!

  # Monetary amounts (critical for finance)
  totalRequestedAmount: BigDecimal!
  pendingAmount: BigDecimal!           # Cash flow liability
  processingAmount: BigDecimal!        # In-flight funds
  completedAmount: BigDecimal!         # Historical payouts
  failedAmount: BigDecimal!            # Needs attention

  # Performance metrics
  averageRequestAmount: BigDecimal
  averageProcessingTimeHours: Float
  successRate: Float!                  # completed / (completed + failed)

  # Time-based (for trends)
  requestsThisWeek: Int!
  requestsThisMonth: Int!
  amountThisWeek: BigDecimal!
  amountThisMonth: BigDecimal!

  # By payment method (Zambia-specific)
  statsByPayoutMethod: [PayoutMethodStats!]!
}

type PayoutMethodStats {
  method: PayoutMethod!                # MTN_MOBILE_MONEY, AIRTEL_MONEY, BANK_TRANSFER
  requestCount: Int!
  totalAmount: BigDecimal!
  successRate: Float!
  averageProcessingTimeHours: Float
}

extend type Query {
  payoutStats(
    organizerId: String,              # Filter by organizer
    startDate: DateTime,
    endDate: DateTime
  ): PayoutStats! @requiresAuth(roles: ["ADMIN", "FINANCE"])
}
```

**Why This Design**:
1. **Amount tracking** - Finance needs monetary values, not just counts
2. **Success rate** - Key operational health indicator
3. **By payment method** - Zambian market uses MTN/Airtel/Zamtel; track reliability per channel
4. **Time filtering** - Enables trend analysis and reporting periods

**Mobile Money Context** ([PawaPay](https://docs.pawapay.io/implementation)):
> "One integration gives you access to collect payments and send payouts into mobile wallets across Africa."

Your platform should track success rates per mobile money operator because:
- MTN, Airtel, and Zamtel have different reliability profiles
- Network outages affect specific operators
- This data helps optimize payout routing

---

### Gap Analysis: City/Geographic Statistics

**Missing Types**: `CityStats`, `CityEventStats`

**Business Need**:
- Marketing team identifies high-potential markets
- Sales team focuses on underperforming regions
- Expansion planning requires geographic insights

**Recommended Implementation**:

```graphql
# catalog-service/schema.graphqls

type CityEventStats {
  city: String!
  province: String
  country: String!

  # Event metrics
  totalEvents: Int!
  publishedEvents: Int!
  upcomingEvents: Int!

  # Capacity & revenue
  totalCapacity: Int!
  totalTicketsSold: Int!
  totalRevenue: BigDecimal!
  averageTicketPrice: BigDecimal

  # Performance
  averageAttendanceRate: Float        # soldTickets / capacity

  # Top performers
  topCategories: [CategoryStat!]!
}

extend type Query {
  eventStatsByCity(
    country: String = "ZM",
    limit: Int = 20
  ): [CityEventStats!]! @requiresAuth(roles: ["ADMIN"])

  topCitiesByRevenue(limit: Int = 10): [CityEventStats!]!
  topCitiesByEvents(limit: Int = 10): [CityEventStats!]!
}
```

**MongoDB Aggregation**:
```java
// LocationStatsService.java - catalog-service
public Flux<CityEventStats> getEventStatsByCity(String country) {
    return mongoTemplate.aggregate(
        Aggregation.newAggregation(
            Aggregation.match(Criteria.where("location.country").is(country)),
            Aggregation.group("location.city")
                .count().as("totalEvents")
                .sum("totalCapacity").as("totalCapacity")
                .sum("soldTickets").as("totalTicketsSold"),
            Aggregation.project()
                .and("_id").as("city")
                .andInclude("totalEvents", "totalCapacity", "totalTicketsSold"),
            Aggregation.sort(Sort.Direction.DESC, "totalEvents")
        ),
        "events",
        CityEventStats.class
    );
}
```

---

## Part 2: Transaction Lifecycle Management

### Industry Research

[Payment retry strategies](https://primer.io/blog/payment-retry-strategies) and [Airbnb's distributed payments](https://medium.com/airbnb-engineering/avoiding-double-payments-in-a-distributed-payments-system-2981f6b070bb) establish critical patterns:

| Pattern | Purpose | Your Application |
|---------|---------|------------------|
| **Idempotency** | Prevent double charges | Use `correlationId` in transactions |
| **Saga Pattern** | Distributed transactions | Multi-step ticket purchase |
| **Dead Letter Queue** | Handle persistent failures | Azure Service Bus DLQ |
| **Exponential Backoff** | Retry without overload | Mobile money timeouts |

[Flutterwave's fault-tolerant microservice](https://dev.to/flutterwaveeng/how-to-build-a-fault-tolerant-microservice-for-payment-retries-5epg) recommends:
> "Utilize retry queues and dead letter queues. Retryable errors are routed to a retry queue, and if the retry count exceeds the threshold, the event is put in the dead letter queue."

### Gap Analysis: Transaction Recovery Operations

**Missing Hooks**:
- `useResumeStuckTransaction()`
- `useMarkTransactionForReview()`
- `useResolveTransactionIssue()`

**Business Need**:
- Mobile money transactions frequently timeout (network issues in Zambia)
- Manual intervention required for stuck payments
- Audit trail for compliance

**Current State**: You have `retryFailedTransaction` but lack:
- Stuck transaction detection
- Manual review workflow
- Resolution tracking

**Recommended Implementation**:

```graphql
# booking-service/schema.graphqls

# ==================== Transaction State Extensions ====================

enum TransactionIssueType {
  TIMEOUT                    # Common with mobile money
  PAYMENT_GATEWAY_ERROR      # PawaPay/MNO issues
  INSUFFICIENT_FUNDS
  INVALID_RECIPIENT          # Wrong phone number
  NETWORK_ERROR              # Connectivity issues
  SUSPECTED_FRAUD
  DUPLICATE_DETECTED
  COMPLIANCE_HOLD
  OTHER
}

enum TransactionResolution {
  RETRY_SUCCESSFUL
  MANUAL_COMPLETION
  REFUND_ISSUED
  MARKED_AS_FAILED
  FRAUD_CONFIRMED
  FALSE_POSITIVE_CLEARED
  ESCALATED_TO_PROVIDER
}

type TransactionReview {
  id: ID!
  transactionId: String!
  issueType: TransactionIssueType!
  priority: Int!                     # 1=Critical, 2=High, 3=Medium, 4=Low
  notes: String
  createdAt: DateTime!
  createdBy: String!

  # Resolution (nullable until resolved)
  resolution: TransactionResolution
  resolutionNotes: String
  resolvedAt: DateTime
  resolvedBy: String
}

# Extend existing FinancialTransaction
extend type FinancialTransaction {
  # New fields for lifecycle management
  isStuck: Boolean!                  # No status change > threshold
  stuckSince: DateTime
  retryCount: Int!
  lastRetryAt: DateTime
  review: TransactionReview          # If flagged for review

  # Mobile money specific
  mobileMoneyStatus: String          # Raw status from PawaPay
  providerReference: String          # MNO transaction ID
}

# ==================== Queries ====================

extend type Query {
  # Transactions needing attention
  stuckTransactions(
    thresholdMinutes: Int = 30,
    pagination: OffsetPaginationInput
  ): TransactionOffsetPage! @requiresAuth(roles: ["ADMIN", "FINANCE"])

  transactionsForReview(
    issueType: TransactionIssueType,
    unresolved: Boolean = true,
    pagination: OffsetPaginationInput
  ): TransactionOffsetPage! @requiresAuth(roles: ["ADMIN", "FINANCE"])

  transactionReviewHistory(
    transactionId: ID!
  ): [TransactionReview!]! @requiresAuth(roles: ["ADMIN", "FINANCE"])
}

# ==================== Mutations ====================

input MarkForReviewInput {
  transactionId: ID!
  issueType: TransactionIssueType!
  priority: Int = 2
  notes: String
}

input ResolveTransactionInput {
  transactionId: ID!
  resolution: TransactionResolution!
  notes: String!
  refundAmount: BigDecimal           # If resolution involves refund
}

extend type Mutation {
  # Automatic retry with exponential backoff
  retryStuckTransaction(
    transactionId: ID!
  ): TransactionMutationResponse! @requiresAuth(roles: ["ADMIN", "FINANCE"])

  # Manual review workflow
  markTransactionForReview(
    input: MarkForReviewInput!
  ): TransactionMutationResponse! @requiresAuth(roles: ["ADMIN", "FINANCE"])

  resolveTransactionIssue(
    input: ResolveTransactionInput!
  ): TransactionMutationResponse! @requiresAuth(roles: ["ADMIN", "FINANCE"])

  # Bulk operations
  bulkRetryStuckTransactions(
    transactionIds: [ID!]!
  ): BulkOperationResponse! @requiresAuth(roles: ["ADMIN"])

  # Force complete (dangerous - requires SUPER_ADMIN)
  forceCompleteTransaction(
    transactionId: ID!,
    reason: String!
  ): TransactionMutationResponse! @requiresAuth(roles: ["SUPER_ADMIN"])
}
```

**Implementation Pattern** (Saga with compensation):

```java
// TransactionLifecycleService.java
@Service
@RequiredArgsConstructor
public class TransactionLifecycleService {
    private final TransactionRepository transactionRepository;
    private final PawaPayClient pawaPayClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Detect stuck transactions - no status change beyond threshold
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void detectStuckTransactions() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(30));

        transactionRepository.findByStatusInAndUpdatedAtBefore(
            List.of(TransactionStatus.PENDING, TransactionStatus.PROCESSING),
            threshold
        )
        .filter(tx -> !tx.isStuck()) // Not already flagged
        .flatMap(this::markAsStuck)
        .subscribe();
    }

    /**
     * Retry with exponential backoff
     */
    public Mono<TransactionMutationResponse> retryStuckTransaction(String transactionId) {
        return transactionRepository.findById(transactionId)
            .flatMap(tx -> {
                // Check retry limits
                if (tx.getRetryCount() >= MAX_RETRIES) {
                    return Mono.just(TransactionMutationResponse.failed(
                        "Max retry limit reached. Mark for manual review."
                    ));
                }

                // Calculate backoff delay
                Duration backoff = calculateExponentialBackoff(tx.getRetryCount());

                // Retry via PawaPay
                return Mono.delay(backoff)
                    .then(pawaPayClient.checkTransactionStatus(tx.getProviderReference()))
                    .flatMap(status -> updateTransactionFromProvider(tx, status))
                    .map(updated -> TransactionMutationResponse.success(updated));
            });
    }

    private Duration calculateExponentialBackoff(int retryCount) {
        // 1s, 2s, 4s, 8s, 16s, max 30s
        long seconds = Math.min(30, (long) Math.pow(2, retryCount));
        return Duration.ofSeconds(seconds);
    }
}
```

**Why This Design**:

1. **Stuck detection is automated** - Scheduled job, not manual hunting
2. **Exponential backoff** - Industry standard ([source](https://leapcell.medium.com/7-retry-patterns-you-should-know-4b9873f098ef))
3. **Review workflow** - Audit trail for compliance
4. **PawaPay status sync** - Mobile money may complete asynchronously
5. **Bulk operations** - Operational efficiency during outages

**Mobile Money Context**:
> [PawaPay implementation guide](https://docs.pawapay.io/implementation): "Mobile money transactions can take time to process. Always implement status polling and webhook handlers."

Your transactions may be "stuck" because:
- MNO (MTN/Airtel/Zamtel) processing queue
- Network timeout before completion
- Async confirmation pending

The `providerReference` field lets you query PawaPay for the actual status.

---

### Gap Analysis: Payout Lifecycle Operations

**Missing Hooks**:
- `useRetryPayoutRequest()`
- `useMarkPayoutRequestForReview()`
- `useResolvePayoutRequestIssue()`

**Business Need**:
- Organizers depend on payouts for their business
- Failed payouts damage organizer relationships
- Compliance requires audit trails

**Recommended Implementation**:

```graphql
# booking-service/schema.graphqls

enum PayoutIssueType {
  BANK_REJECTED
  MOBILE_WALLET_INVALID
  INSUFFICIENT_ESCROW_BALANCE
  COMPLIANCE_HOLD
  SUSPECTED_FRAUD
  DUPLICATE_REQUEST
  ORGANIZER_ACCOUNT_SUSPENDED
  TECHNICAL_ERROR
}

input MarkPayoutForReviewInput {
  payoutRequestId: ID!
  issueType: PayoutIssueType!
  notes: String
  priority: Int = 2
}

input ResolvePayoutIssueInput {
  payoutRequestId: ID!
  resolution: String!              # Action taken
  notes: String!
  newBankAccountId: ID             # If recipient changed
  adjustedAmount: BigDecimal       # If amount corrected
}

extend type Mutation {
  retryPayoutRequest(
    payoutRequestId: ID!
  ): PayoutRequestMutationResponse! @requiresAuth(roles: ["ADMIN", "FINANCE"])

  markPayoutRequestForReview(
    input: MarkPayoutForReviewInput!
  ): PayoutRequestMutationResponse! @requiresAuth(roles: ["ADMIN", "FINANCE"])

  resolvePayoutRequestIssue(
    input: ResolvePayoutIssueInput!
  ): PayoutRequestMutationResponse! @requiresAuth(roles: ["ADMIN", "FINANCE"])

  # Redirect to different account (e.g., wrong number entered)
  redirectPayout(
    payoutRequestId: ID!,
    newBankAccountId: ID!,
    reason: String!
  ): PayoutRequestMutationResponse! @requiresAuth(roles: ["ADMIN", "FINANCE"])
}

extend type Query {
  payoutRequestsForReview(
    pagination: OffsetPaginationInput
  ): PayoutRequestOffsetPage! @requiresAuth(roles: ["ADMIN", "FINANCE"])

  failedPayoutsRequiringAttention(
    pagination: OffsetPaginationInput
  ): PayoutRequestOffsetPage! @requiresAuth(roles: ["ADMIN", "FINANCE"])
}
```

---

## Part 3: Bank Account Management

### Industry Research

[Fintech API compliance](https://www.cleffex.com/blog/fintech-api-integration-your-complete-guide/) requires:
> "APIs must navigate GDPR for privacy, PCI DSS for payment security... including proper data localization and clear user consent."

For African mobile money ([Klasha](https://www.klasha.com/blog/klasha-launches-momo-payout-api-powering-seamless-mobile-money-disbursements-across-africa)):
> "Businesses can send mobile money payouts directly to customers using MTN, Airtel, Orange, Wave, and more."

### Gap Analysis: Bank Account CRUD

**Missing Hooks**:
- `useCreateBankAccount()`
- `useUpdateBankAccount()`
- `useDeleteBankAccount()`

**Business Need**:
- Organizers must add bank/mobile money accounts for payouts
- Account verification prevents fraud
- Multiple accounts enable flexibility

**Current State**: `BankAccount` type exists but mutations are missing.

**Recommended Implementation**:

```graphql
# booking-service/schema.graphqls

# ==================== Bank Account Types ====================

enum BankAccountType {
  BANK_ACCOUNT           # Traditional bank
  MOBILE_WALLET          # MTN, Airtel, Zamtel
}

enum BankAccountStatus {
  PENDING_VERIFICATION
  VERIFIED
  REJECTED
  SUSPENDED
  CLOSED
}

type BankAccount {
  id: ID!
  organizerId: ID!
  accountType: BankAccountType!

  # Common fields
  accountName: String!            # Name on account
  currency: String!               # ZMW, USD
  isPrimary: Boolean!
  status: BankAccountStatus!

  # Bank-specific (nullable for mobile)
  bankName: String
  bankCode: String
  branchCode: String
  accountNumber: String           # Masked for security
  swiftCode: String

  # Mobile wallet specific (nullable for bank)
  mobileNetwork: MobileNetwork    # MTN, AIRTEL, ZAMTEL
  phoneNumber: String             # Masked: +260 97* *** *89

  # Verification
  verifiedAt: DateTime
  verifiedBy: String
  verificationNotes: String

  # Audit
  createdAt: DateTime!
  updatedAt: DateTime!
}

enum MobileNetwork {
  MTN
  AIRTEL
  ZAMTEL
}

# ==================== Inputs ====================

input CreateBankAccountInput {
  organizerId: ID!
  accountType: BankAccountType!
  accountName: String!
  currency: String! = "ZMW"
  isPrimary: Boolean = false

  # Bank fields (required if BANK_ACCOUNT)
  bankName: String
  bankCode: String
  branchCode: String
  accountNumber: String
  swiftCode: String

  # Mobile fields (required if MOBILE_WALLET)
  mobileNetwork: MobileNetwork
  phoneNumber: String
}

input UpdateBankAccountInput {
  accountName: String
  isPrimary: Boolean
  branchCode: String              # Allow minor updates
  # Cannot change account number - must create new
}

# ==================== Mutations ====================

extend type Mutation {
  # Organizer or admin can create
  createBankAccount(
    input: CreateBankAccountInput!
  ): BankAccountMutationResponse!
    @requiresAuth(roles: ["ORGANIZER", "ADMIN"])

  # Update limited fields
  updateBankAccount(
    id: ID!,
    input: UpdateBankAccountInput!
  ): BankAccountMutationResponse!
    @requiresAuth(roles: ["ORGANIZER", "ADMIN"])

  # Soft delete - prevent accidental loss
  deleteBankAccount(
    id: ID!,
    reason: String
  ): BankAccountMutationResponse!
    @requiresAuth(roles: ["ORGANIZER", "ADMIN"])

  # Admin-only verification
  verifyBankAccount(
    id: ID!,
    notes: String
  ): BankAccountMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])

  rejectBankAccount(
    id: ID!,
    reason: String!
  ): BankAccountMutationResponse!
    @requiresAuth(roles: ["ADMIN", "FINANCE"])

  # Set as primary payout destination
  setPrimaryBankAccount(
    id: ID!
  ): BankAccountMutationResponse!
    @requiresAuth(roles: ["ORGANIZER", "ADMIN"])
}

# ==================== Queries ====================

extend type Query {
  bankAccount(id: ID!): BankAccount

  bankAccountsByOrganizer(
    organizerId: ID!
  ): [BankAccount!]! @requiresAuth(roles: ["ORGANIZER", "ADMIN"])

  # Admin: accounts pending verification
  pendingBankAccountVerifications(
    pagination: OffsetPaginationInput
  ): [BankAccount!]! @requiresAuth(roles: ["ADMIN", "FINANCE"])
}
```

**Implementation Considerations**:

1. **Account Number Security**:
```java
// Mask account numbers in responses
public String getMaskedAccountNumber() {
    if (accountNumber == null || accountNumber.length() < 4) return "****";
    return "*".repeat(accountNumber.length() - 4) +
           accountNumber.substring(accountNumber.length() - 4);
}
```

2. **Mobile Money Validation**:
```java
// Validate Zambian phone numbers
private boolean isValidZambianPhone(String phone) {
    // Zambian numbers: +260 9X XXX XXXX
    return phone.matches("^\\+260(9[567]|77)\\d{7}$");
}
```

3. **Primary Account Logic**:
```java
// Ensure only one primary per organizer
@Transactional
public Mono<BankAccount> setPrimary(String accountId) {
    return bankAccountRepository.findById(accountId)
        .flatMap(account ->
            // Clear other primaries
            bankAccountRepository.updateAllByOrganizerId(
                account.getOrganizerId(),
                Update.update("isPrimary", false)
            )
            .then(
                // Set this one as primary
                bankAccountRepository.save(account.withIsPrimary(true))
            )
        );
}
```

---

## Part 4: System Health Monitoring

### Industry Research

[Spring Boot Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html) and [microservices health patterns](https://microservices.io/patterns/observability/health-check-api.html) recommend:

> "Health checks tell orchestration platforms whether a service can accept traffic. Spring Boot Actuator provides health indicators for common dependencies."

[Prometheus + Grafana stack](https://medium.com/@darrel.danadyaksa19/spring-boot-observability-mastering-metrics-with-prometheus-grafana-d6fb733de138):
> "Has become a de facto standard for observability in the Java ecosystem, particularly for microservices."

### Gap Analysis: System Health

**Missing Hooks**:
- `useSystemHealth()`
- `useTransactionHealth()`
- `useSystemAlerts()`

**Recommendation**: **DO NOT implement custom system health in GraphQL.**

Instead, leverage the existing observability stack:

**Why NOT Custom GraphQL**:
1. **Actuator already provides this** - `/actuator/health`, `/actuator/metrics`
2. **Prometheus integration exists** - Standard metrics collection
3. **Grafana dashboards** - Industry-standard visualization
4. **Real-time concerns** - GraphQL subscriptions add complexity vs. metrics streaming

**Recommended Approach**:

```yaml
# application.yml - each service
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  endpoint:
    health:
      show-details: when_authorized
      show-components: when_authorized
  health:
    mongo:
      enabled: true
    redis:
      enabled: true
```

**For Admin Dashboard**:

Instead of GraphQL health queries, embed Grafana dashboards:

```typescript
// AdminDashboard.tsx
const SystemHealthPanel = () => {
  return (
    <iframe
      src={`${GRAFANA_URL}/d/system-health?orgId=1&kiosk`}
      width="100%"
      height="400"
    />
  );
};
```

**If GraphQL Health is Required** (for mobile app or specific use case):

```graphql
# api-gateway/schema.graphqls (NOT individual services)

type ServiceHealth {
  serviceName: String!
  status: HealthStatus!
  responseTimeMs: Int
  lastCheckedAt: DateTime!
}

enum HealthStatus {
  UP
  DOWN
  DEGRADED
}

type SystemHealth {
  overall: HealthStatus!
  services: [ServiceHealth!]!
  timestamp: DateTime!
}

extend type Query {
  systemHealth: SystemHealth! @requiresAuth(roles: ["ADMIN"])
}

# Use subscription for real-time updates
extend type Subscription {
  serviceStatusChanged: ServiceHealth! @requiresAuth(roles: ["ADMIN"])
}
```

**Implementation** (API Gateway aggregates from Actuator endpoints):

```java
// HealthAggregationService.java - api-gateway
@Service
public class HealthAggregationService {
    private final WebClient webClient;

    private static final Map<String, String> SERVICE_HEALTH_URLS = Map.of(
        "catalog-service", "http://localhost:8085/actuator/health",
        "booking-service", "http://localhost:8082/actuator/health",
        "identity-service", "http://localhost:8083/actuator/health"
    );

    public Mono<SystemHealth> getSystemHealth() {
        return Flux.fromIterable(SERVICE_HEALTH_URLS.entrySet())
            .flatMap(entry -> checkServiceHealth(entry.getKey(), entry.getValue()))
            .collectList()
            .map(services -> SystemHealth.builder()
                .overall(calculateOverallStatus(services))
                .services(services)
                .timestamp(Instant.now())
                .build());
    }
}
```

---

## Part 5: Real-Time Dashboard Updates (Apollo GraphOS Alternatives)

### Why NOT GraphQL Subscriptions with Apollo GraphOS

**Context**: Apollo GraphOS (managed federation) adds complexity for subscriptions:

1. **HTTP Callback Protocol Required**: Apollo Router requires subgraphs to implement the HTTP Callback Protocol for subscriptions, not WebSockets directly
2. **Infrastructure Complexity**: Requires persistent connections from Router to subgraphs
3. **Cost**: GraphOS subscription usage may incur additional costs
4. **Overkill for Dashboard**: Admin dashboards don't need sub-second updates

From Apollo Router documentation:
> "The Apollo Router supports receiving subgraph subscription events via HTTP callbacks, offering an alternative to persistent WebSocket connections."

### Recommended: Polling with Apollo Client

Apollo Client provides excellent polling capabilities that are **simpler, more reliable, and sufficient for admin dashboards**.

**Why Polling is Better for Your Use Case**:
| Factor | Subscriptions | Polling |
|--------|--------------|---------|
| Implementation | Complex (WebSocket + Callback Protocol) | Simple (existing queries) |
| Infrastructure | Router ↔ Subgraph persistent connections | Stateless HTTP |
| Failure handling | Reconnection logic needed | Automatic retry |
| Dashboard needs | Overkill (sub-second updates) | Sufficient (5-30 second intervals) |
| GraphOS cost | Higher | Lower |

### Implementation: Smart Polling Strategy

**Frontend Pattern (Apollo Client 4)**:

```typescript
// hooks/useDashboardStats.ts
import { useQuery } from '@apollo/client';
import { DASHBOARD_STATS_QUERY } from '../queries/dashboard.queries';

export function useDashboardStats() {
  const { data, loading, error, refetch, startPolling, stopPolling } = useQuery(
    DASHBOARD_STATS_QUERY,
    {
      // Poll every 30 seconds for dashboard overview
      pollInterval: 30000,

      // Notify on network status changes for loading states
      notifyOnNetworkStatusChange: true,

      // Use cache-and-network for fresh data with instant display
      fetchPolicy: 'cache-and-network',
    }
  );

  return {
    stats: data?.dashboardStats,
    loading,
    error,
    refetch,
    // Expose controls for user-triggered updates
    startPolling,
    stopPolling,
  };
}

// For critical real-time views (e.g., active event monitoring)
export function useEventSalesLive(eventId: string) {
  return useQuery(EVENT_SALES_QUERY, {
    variables: { eventId },
    // More frequent polling for live event monitoring
    pollInterval: 5000, // 5 seconds
    skip: !eventId,
  });
}
```

**Adaptive Polling (User Activity Based)**:

```typescript
// hooks/useAdaptivePolling.ts
import { useEffect, useRef } from 'react';
import { useQuery } from '@apollo/client';

export function useAdaptivePolling<T>(
  query: DocumentNode,
  options: QueryHookOptions<T>
) {
  const { data, loading, error, startPolling, stopPolling } = useQuery(query, {
    ...options,
    pollInterval: 0, // Start with no polling
  });

  const isActiveRef = useRef(true);
  const fastPollInterval = 5000;  // 5s when active
  const slowPollInterval = 60000; // 60s when idle

  useEffect(() => {
    // Start with fast polling
    startPolling(fastPollInterval);

    // Slow down when user is idle
    const handleVisibilityChange = () => {
      if (document.hidden) {
        stopPolling();
      } else {
        startPolling(isActiveRef.current ? fastPollInterval : slowPollInterval);
      }
    };

    // Track user activity
    const handleActivity = () => {
      isActiveRef.current = true;
      startPolling(fastPollInterval);

      // Reset to slow after 2 minutes of no activity
      setTimeout(() => {
        isActiveRef.current = false;
        startPolling(slowPollInterval);
      }, 120000);
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    document.addEventListener('mousemove', handleActivity);
    document.addEventListener('keypress', handleActivity);

    return () => {
      stopPolling();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      document.removeEventListener('mousemove', handleActivity);
      document.removeEventListener('keypress', handleActivity);
    };
  }, [startPolling, stopPolling]);

  return { data, loading, error };
}
```

**Manual Refetch for User Actions**:

```typescript
// components/TransactionList.tsx
function TransactionList() {
  const { data, loading, refetch } = useTransactions();

  const handleRetryTransaction = async (txId: string) => {
    await retryTransaction({ variables: { txId } });
    // Immediately refetch to show updated status
    await refetch();
  };

  return (
    <div>
      <button onClick={() => refetch()}>
        Refresh
      </button>
      {/* Transaction list */}
    </div>
  );
}
```

### When to Consider Subscriptions

Only implement subscriptions if you have these specific requirements:

1. **Live Auction/Bidding**: Sub-second updates are critical
2. **Live Event Check-in**: Real-time attendee count during event
3. **Chat/Messaging**: True real-time communication

**If You Must Implement Subscriptions** (with Apollo Router HTTP Callback):

```java
// Spring GraphQL 4.3.0+ supports Apollo Router Callback Protocol
// Dependency: com.apollographql.federation:federation-graphql-java-support

@DgsComponent
public class EventSalesSubscription {

    private final Sinks.Many<EventSalesUpdate> salesSink =
        Sinks.many().multicast().onBackpressureBuffer();

    @DgsSubscription
    public Flux<EventSalesUpdate> eventSalesUpdated(@InputArgument String eventId) {
        return salesSink.asFlux()
            .filter(update -> update.getEventId().equals(eventId));
    }

    @ApplicationModuleListener
    public void onTicketPurchased(TicketPurchasedEvent event) {
        salesSink.tryEmitNext(EventSalesUpdate.from(event));
    }
}
```

**Router Configuration for Callbacks**:

```yaml
# router-local.yaml
subscription:
  enabled: true
  mode:
    callback:
      path: /callback
      public_url: http://localhost:4000/callback
      heartbeat_interval: 5s
```

---

## Part 6: Financial Transaction Integrity (Spring Modulith + Azure)

### The Problem: Distributed Transaction Challenges

In your ticketing system, a ticket purchase involves:
1. Create ticket record (Booking Service)
2. Process payment (PawaPay via Booking Service)
3. Update event capacity (Catalog Service)
4. Send confirmation (Identity Service - notifications)

**Without proper patterns**, failures can cause:
- Payment charged but ticket not created
- Ticket created but payment failed (revenue loss)
- Event oversold (capacity not decremented)

### Solution: Transactional Outbox Pattern with Spring Modulith

**Why Spring Modulith for Financial Transactions**:

From Context7 research (Spring Modulith docs):
> "Events are tracked in EventPublicationRegistry. Guaranteed to be called even if the application crashes. Event is republished on restart if incomplete."

**Architecture**:

```
┌─────────────────────────────────────────────────────────────────────┐
│                  TRANSACTIONAL OUTBOX PATTERN                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              BOOKING SERVICE (Single Transaction)            │   │
│  │                                                              │   │
│  │  1. Begin Transaction                                        │   │
│  │     │                                                        │   │
│  │     ▼                                                        │   │
│  │  2. Create Ticket (MongoDB)                                  │   │
│  │     │                                                        │   │
│  │     ▼                                                        │   │
│  │  3. Publish PaymentInitiatedEvent                            │   │
│  │     │                                                        │   │
│  │     ▼                                                        │   │
│  │  4. Event written to PostgreSQL (event_publication table)    │   │
│  │     │                                                        │   │
│  │     ▼                                                        │   │
│  │  5. Commit Transaction                                       │   │
│  │                                                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │           @ApplicationModuleListener (Async)                 │   │
│  │                                                              │   │
│  │  6. PaymentProcessor.onPaymentInitiated()                    │   │
│  │     │                                                        │   │
│  │     ▼                                                        │   │
│  │  7. Call PawaPay API (mobile money)                          │   │
│  │     │                                                        │   │
│  │     ├──► Success: Publish PaymentCompletedEvent              │   │
│  │     │              Mark event as COMPLETED in PostgreSQL     │   │
│  │     │                                                        │   │
│  │     └──► Failure: Event remains INCOMPLETE                   │   │
│  │                   Will be retried on next startup            │   │
│  │                   Or by scheduled republish job              │   │
│  │                                                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Implementation: Payment Processing with Guaranteed Delivery

**1. Application Properties**:

```properties
# application.properties - booking-service

# Enable event publication registry (PostgreSQL-backed)
spring.modulith.events.publication-registry.enabled=true

# Republish incomplete events on startup (crash recovery)
spring.modulith.events.republish-outstanding-events-on-restart=true

# Retention policy for completed events (audit trail)
spring.modulith.events.retention-policy=P30D

# PostgreSQL for event publication (JDBC - blocking, separate from MongoDB)
spring.datasource.url=jdbc:postgresql://localhost:5432/shared_db?currentSchema=modulith_events
spring.datasource.username=${POSTGRES_USER}
spring.datasource.password=${POSTGRES_PASSWORD}
```

**2. Domain Events**:

```java
// events/PaymentEvents.java
package com.pml.booking.events;

import java.math.BigDecimal;
import java.time.Instant;

// Immutable record for event safety
public record PaymentInitiatedEvent(
    String correlationId,       // Idempotency key
    String ticketId,
    String eventId,
    String buyerId,
    String phoneNumber,         // Mobile money number
    BigDecimal amount,
    String currency,
    String paymentMethod,       // MOBILE_MONEY, CARD
    Instant initiatedAt
) {}

public record PaymentCompletedEvent(
    String correlationId,
    String ticketId,
    String transactionId,
    String providerReference,   // PawaPay transaction ID
    BigDecimal amount,
    String currency,
    Instant completedAt
) {}

public record PaymentFailedEvent(
    String correlationId,
    String ticketId,
    String transactionId,
    String failureReason,
    String failureCode,
    int retryCount,
    boolean isRetryable,
    Instant failedAt
) {}
```

**3. Ticket Purchase Service (Transaction Initiator)**:

```java
// service/TicketPurchaseService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketPurchaseService {

    private final TicketRepository ticketRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IdempotencyService idempotencyService;

    /**
     * Atomic ticket creation with payment initiation
     * Uses transactional outbox pattern for guaranteed delivery
     */
    @Transactional
    public Mono<Ticket> purchaseTicket(TicketPurchaseInput input, String buyerId) {
        String correlationId = input.getIdempotencyKey() != null
            ? input.getIdempotencyKey()
            : UUID.randomUUID().toString();

        // Check idempotency - prevent duplicate purchases
        return idempotencyService.checkAndLock(correlationId)
            .flatMap(isNew -> {
                if (!isNew) {
                    // Return existing ticket for duplicate request
                    return ticketRepository.findByCorrelationId(correlationId);
                }

                return createTicketAndInitiatePayment(input, buyerId, correlationId);
            });
    }

    private Mono<Ticket> createTicketAndInitiatePayment(
        TicketPurchaseInput input,
        String buyerId,
        String correlationId
    ) {
        // 1. Create ticket in PENDING_PAYMENT status
        Ticket ticket = Ticket.builder()
            .id(UUID.randomUUID().toString())
            .correlationId(correlationId)
            .eventId(input.getEventId())
            .tierId(input.getTierId())
            .buyerId(buyerId)
            .status(TicketStatus.PENDING_PAYMENT)
            .price(input.getPrice())
            .currency(input.getCurrency())
            .createdAt(Instant.now())
            .build();

        // 2. Create transaction record
        FinancialTransaction transaction = FinancialTransaction.builder()
            .id(UUID.randomUUID().toString())
            .correlationId(correlationId)
            .ticketId(ticket.getId())
            .type(TransactionType.PURCHASE)
            .amount(input.getPrice())
            .currency(input.getCurrency())
            .status(TransactionStatus.PENDING)
            .paymentMethod(input.getPaymentMethod())
            .phoneNumber(input.getPhoneNumber())
            .createdAt(Instant.now())
            .build();

        return ticketRepository.save(ticket)
            .flatMap(savedTicket ->
                transactionRepository.save(transaction)
                    .map(savedTx -> {
                        // 3. Publish event - written to PostgreSQL outbox atomically
                        // This is guaranteed to be processed even if app crashes
                        eventPublisher.publishEvent(new PaymentInitiatedEvent(
                            correlationId,
                            savedTicket.getId(),
                            input.getEventId(),
                            buyerId,
                            input.getPhoneNumber(),
                            input.getPrice(),
                            input.getCurrency(),
                            input.getPaymentMethod(),
                            Instant.now()
                        ));

                        log.info("Payment initiated for ticket {} with correlation {}",
                            savedTicket.getId(), correlationId);

                        return savedTicket;
                    })
            );
    }
}
```

**4. Payment Processor (Event Listener with Guaranteed Delivery)**:

```java
// service/PaymentProcessorService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessorService {

    private final MobileMoneyGateway mobileMoneyGateway;  // Abstraction over PawaPay
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final int MAX_RETRIES = 3;

    /**
     * @ApplicationModuleListener ensures:
     * 1. Event is tracked in PostgreSQL before processing
     * 2. If processing fails, event remains "incomplete"
     * 3. Incomplete events are republished on restart
     * 4. Event marked COMPLETED only after successful processing
     */
    @ApplicationModuleListener
    public void onPaymentInitiated(PaymentInitiatedEvent event) {
        log.info("Processing payment for correlation: {}", event.correlationId());

        // Call mobile money provider (PawaPay abstracted)
        MobileMoneyRequest request = MobileMoneyRequest.builder()
            .correlationId(event.correlationId())
            .phoneNumber(event.phoneNumber())
            .amount(event.amount())
            .currency(event.currency())
            .description("Ticket purchase: " + event.ticketId())
            .build();

        mobileMoneyGateway.initiatePayment(request)
            .flatMap(response -> {
                if (response.isSuccess()) {
                    return handlePaymentSuccess(event, response);
                } else {
                    return handlePaymentFailure(event, response, 0);
                }
            })
            .doOnError(error -> {
                log.error("Payment processing failed for {}: {}",
                    event.correlationId(), error.getMessage());
                // Don't catch - let Modulith handle retry
                throw new PaymentProcessingException(error);
            })
            .subscribe();
    }

    private Mono<Void> handlePaymentSuccess(
        PaymentInitiatedEvent event,
        MobileMoneyResponse response
    ) {
        return transactionRepository.findByCorrelationId(event.correlationId())
            .flatMap(tx -> {
                tx.setStatus(TransactionStatus.COMPLETED);
                tx.setProviderReference(response.getProviderTransactionId());
                tx.setCompletedAt(Instant.now());
                return transactionRepository.save(tx);
            })
            .doOnSuccess(tx -> {
                eventPublisher.publishEvent(new PaymentCompletedEvent(
                    event.correlationId(),
                    event.ticketId(),
                    tx.getId(),
                    tx.getProviderReference(),
                    event.amount(),
                    event.currency(),
                    Instant.now()
                ));
                log.info("Payment completed for correlation: {}", event.correlationId());
            })
            .then();
    }

    private Mono<Void> handlePaymentFailure(
        PaymentInitiatedEvent event,
        MobileMoneyResponse response,
        int retryCount
    ) {
        boolean isRetryable = isRetryableError(response.getErrorCode());

        return transactionRepository.findByCorrelationId(event.correlationId())
            .flatMap(tx -> {
                tx.setStatus(isRetryable ? TransactionStatus.PENDING : TransactionStatus.FAILED);
                tx.setFailureReason(response.getErrorMessage());
                tx.setFailureCode(response.getErrorCode());
                tx.setRetryCount(retryCount);
                return transactionRepository.save(tx);
            })
            .doOnSuccess(tx -> {
                eventPublisher.publishEvent(new PaymentFailedEvent(
                    event.correlationId(),
                    event.ticketId(),
                    tx.getId(),
                    response.getErrorMessage(),
                    response.getErrorCode(),
                    retryCount,
                    isRetryable,
                    Instant.now()
                ));
            })
            .then();
    }

    private boolean isRetryableError(String errorCode) {
        // PawaPay/MNO errors that are safe to retry
        return Set.of(
            "TIMEOUT",
            "NETWORK_ERROR",
            "PROVIDER_UNAVAILABLE",
            "RATE_LIMITED"
        ).contains(errorCode);
    }
}
```

**5. Event Publication Monitoring**:

```java
// service/EventPublicationMonitorService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublicationMonitorService {

    private final EventPublicationRegistry registry;

    /**
     * Scheduled job to detect and alert on stale events
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void monitorStaleEvents() {
        Collection<EventPublication> staleEvents =
            registry.findIncompletePublicationsOlderThan(Duration.ofHours(1));

        if (!staleEvents.isEmpty()) {
            log.warn("Found {} stale event publications", staleEvents.size());

            staleEvents.forEach(publication -> {
                log.warn("Stale event: type={}, published={}, target={}",
                    publication.getEvent().getClass().getSimpleName(),
                    publication.getPublicationDate(),
                    publication.getTargetIdentifier());
            });

            // Alert operations team (integrate with your alerting system)
            // alertService.sendAlert("Stale Events Detected", staleEvents.size());
        }
    }

    /**
     * GraphQL query for admin dashboard
     */
    public List<IncompleteEventDto> getIncompleteEvents() {
        return registry.findIncompletePublications().stream()
            .map(pub -> new IncompleteEventDto(
                pub.getIdentifier().toString(),
                pub.getEvent().getClass().getSimpleName(),
                pub.getPublicationDate(),
                pub.getTargetIdentifier().getValue()
            ))
            .toList();
    }
}
```

### Cross-Service Events with Azure Service Bus

For events that must reach other services (Catalog, Identity), use Azure Service Bus:

```java
// service/CrossServiceEventPublisher.java
@Service
@RequiredArgsConstructor
public class CrossServiceEventPublisher {

    private final StreamBridge streamBridge;

    /**
     * Publish to Azure Service Bus after local transaction completes
     */
    @ApplicationModuleListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        // Publish to Catalog Service (decrement capacity)
        streamBridge.send("booking-events-out-0",
            new TicketSoldEvent(event.ticketId(), event.correlationId()));

        // Publish to Identity Service (send confirmation)
        streamBridge.send("booking-events-out-0",
            new SendConfirmationEvent(event.ticketId(), event.correlationId()));
    }
}
```

**Azure Service Bus Configuration**:

```yaml
# application.yml - booking-service
spring:
  cloud:
    azure:
      servicebus:
        connection-string: ${AZURE_SERVICEBUS_CONNECTION_STRING}
        namespace: ${AZURE_SERVICEBUS_NAMESPACE}

    stream:
      bindings:
        booking-events-out-0:
          destination: booking-events
          producer:
            error-channel-enabled: true

      servicebus:
        bindings:
          booking-events-out-0:
            producer:
              entity-type: topic
```

---

## Part 7: Payment Provider Abstraction Layer

### The Problem: Vendor Lock-in

Your current implementation directly uses PawaPay. If you need to:
- Add Flutterwave as backup provider
- Switch to DPO Group for different markets
- Support card payments via Stripe

...you would need to modify business logic throughout the codebase.

### Solution: Strategy + Adapter Pattern

**Design Principles**:
1. **Provider-agnostic naming** - No "PawaPay" in public interfaces
2. **Swappable implementations** - Change provider without changing business logic
3. **Consistent response format** - Unified result types across providers
4. **Automatic provider selection** - Route based on payment method, amount, or availability

### Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                   PAYMENT PROVIDER ABSTRACTION                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Business Logic Layer                      │   │
│  │                                                              │   │
│  │  TicketPurchaseService                                       │   │
│  │       │                                                      │   │
│  │       └──► MobileMoneyGateway.initiatePayment(request)       │   │
│  │                        │                                     │   │
│  └────────────────────────┼─────────────────────────────────────┘   │
│                           │                                         │
│                           ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │               MobileMoneyGateway (Interface)                 │   │
│  │                                                              │   │
│  │  + initiatePayment(MobileMoneyRequest): Mono<PaymentResult>  │   │
│  │  + checkStatus(String txId): Mono<PaymentStatus>             │   │
│  │  + initiatePayout(PayoutRequest): Mono<PayoutResult>         │   │
│  │  + getSupportedNetworks(): Set<MobileNetwork>                │   │
│  │                                                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                           │                                         │
│                           │ Strategy Pattern                        │
│                           │                                         │
│            ┌──────────────┼──────────────┐                         │
│            ▼              ▼              ▼                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │
│  │  PawaPay    │  │ Flutterwave │  │   DPO       │                 │
│  │  Adapter    │  │   Adapter   │  │  Adapter    │                 │
│  │             │  │             │  │             │                 │
│  │ Implements  │  │ Implements  │  │ Implements  │                 │
│  │ MobileMoney │  │ MobileMoney │  │ MobileMoney │                 │
│  │ Gateway     │  │ Gateway     │  │ Gateway     │                 │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘                 │
│         │                │                │                         │
│         ▼                ▼                ▼                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │
│  │  PawaPay    │  │ Flutterwave │  │    DPO      │                 │
│  │    SDK      │  │     SDK     │  │    SDK      │                 │
│  └─────────────┘  └─────────────┘  └─────────────┘                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Implementation

**1. Provider-Agnostic Domain Types**:

```java
// gateway/domain/MobileMoneyRequest.java
package com.pml.booking.gateway.domain;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * Provider-agnostic payment request.
 * No vendor-specific fields - those are handled by adapters.
 */
@Data
@Builder
public class MobileMoneyRequest {
    private String correlationId;         // Idempotency key
    private String phoneNumber;           // E.164 format: +260...
    private MobileNetwork network;        // MTN, AIRTEL, ZAMTEL
    private BigDecimal amount;
    private String currency;              // ZMW, USD
    private String description;           // Payment description
    private String callbackUrl;           // Webhook for async result

    // Payer metadata (for receipts)
    private String payerName;
    private String payerEmail;
}

// gateway/domain/MobileNetwork.java
public enum MobileNetwork {
    MTN("mtn", "MTN Mobile Money"),
    AIRTEL("airtel", "Airtel Money"),
    ZAMTEL("zamtel", "Zamtel Kwacha");

    private final String code;
    private final String displayName;

    // Used by adapters to map to provider-specific codes
    public String getCode() { return code; }
}

// gateway/domain/PaymentResult.java
@Data
@Builder
public class PaymentResult {
    private String correlationId;
    private String providerTransactionId;  // Provider's reference
    private PaymentResultStatus status;
    private String message;

    // For async payments (mobile money usually async)
    private boolean isPending;
    private String statusCheckUrl;

    // Error details (if failed)
    private String errorCode;
    private String errorMessage;
    private boolean isRetryable;

    public boolean isSuccess() {
        return status == PaymentResultStatus.SUCCESS ||
               status == PaymentResultStatus.PENDING;
    }
}

public enum PaymentResultStatus {
    SUCCESS,           // Payment completed
    PENDING,           // Awaiting user confirmation (USSD prompt)
    FAILED,            // Payment failed
    REJECTED,          // User rejected/cancelled
    EXPIRED            // User didn't respond in time
}
```

**2. Gateway Interface (Strategy Pattern)**:

```java
// gateway/MobileMoneyGateway.java
package com.pml.booking.gateway;

import reactor.core.publisher.Mono;
import java.util.Set;

/**
 * Provider-agnostic mobile money gateway interface.
 *
 * Naming convention: No provider names in method signatures.
 * Use generic terms: "initiate", "check", "payout"
 */
public interface MobileMoneyGateway {

    /**
     * Initiate a collection (charge customer's mobile wallet)
     */
    Mono<PaymentResult> initiatePayment(MobileMoneyRequest request);

    /**
     * Check payment status (for async payments)
     */
    Mono<PaymentStatus> checkPaymentStatus(String providerTransactionId);

    /**
     * Initiate a payout (send money to mobile wallet)
     */
    Mono<PayoutResult> initiatePayout(PayoutRequest request);

    /**
     * Check payout status
     */
    Mono<PayoutStatus> checkPayoutStatus(String providerTransactionId);

    /**
     * Get networks supported by this provider
     */
    Set<MobileNetwork> getSupportedNetworks();

    /**
     * Provider health check
     */
    Mono<Boolean> isAvailable();

    /**
     * Provider identifier (for logging/metrics)
     */
    String getProviderId();
}
```

**3. PawaPay Adapter Implementation**:

```java
// gateway/adapters/PawaPayAdapter.java
package com.pml.booking.gateway.adapters;

import com.pml.booking.gateway.MobileMoneyGateway;
import com.pml.booking.gateway.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class PawaPayAdapter implements MobileMoneyGateway {

    private final WebClient pawaPayClient;
    private final PawaPayConfig config;

    // Map our generic networks to PawaPay correspondent codes
    private static final Map<MobileNetwork, String> NETWORK_MAPPING = Map.of(
        MobileNetwork.MTN, "MWK_MTNMOBILEMONEY",
        MobileNetwork.AIRTEL, "ZMW_AIRTELMONEY",
        MobileNetwork.ZAMTEL, "ZMW_ZAMTELMONEY"
    );

    @Override
    public String getProviderId() {
        return "pawapay";
    }

    @Override
    public Set<MobileNetwork> getSupportedNetworks() {
        return NETWORK_MAPPING.keySet();
    }

    @Override
    public Mono<PaymentResult> initiatePayment(MobileMoneyRequest request) {
        // Transform to PawaPay-specific request format
        PawaPayDepositRequest pawaPayRequest = PawaPayDepositRequest.builder()
            .depositId(request.getCorrelationId())
            .amount(request.getAmount().toString())
            .currency(request.getCurrency())
            .correspondent(NETWORK_MAPPING.get(request.getNetwork()))
            .payer(PawaPayPayer.builder()
                .type("MSISDN")
                .address(PawaPayAddress.builder()
                    .value(normalizePhoneNumber(request.getPhoneNumber()))
                    .build())
                .build())
            .statementDescription(truncate(request.getDescription(), 22))
            .build();

        return pawaPayClient.post()
            .uri("/deposits")
            .bodyValue(pawaPayRequest)
            .retrieve()
            .bodyToMono(PawaPayDepositResponse.class)
            .map(this::mapToPaymentResult)
            .doOnSuccess(result -> log.info(
                "PawaPay payment initiated: correlationId={}, status={}",
                request.getCorrelationId(), result.getStatus()))
            .onErrorResume(this::handlePawaPayError);
    }

    @Override
    public Mono<PaymentStatus> checkPaymentStatus(String providerTransactionId) {
        return pawaPayClient.get()
            .uri("/deposits/{id}", providerTransactionId)
            .retrieve()
            .bodyToMono(PawaPayDepositResponse.class)
            .map(this::mapToPaymentStatus);
    }

    @Override
    public Mono<PayoutResult> initiatePayout(PayoutRequest request) {
        PawaPayPayoutRequest pawaPayRequest = PawaPayPayoutRequest.builder()
            .payoutId(request.getCorrelationId())
            .amount(request.getAmount().toString())
            .currency(request.getCurrency())
            .correspondent(NETWORK_MAPPING.get(request.getNetwork()))
            .recipient(PawaPayRecipient.builder()
                .type("MSISDN")
                .address(PawaPayAddress.builder()
                    .value(normalizePhoneNumber(request.getPhoneNumber()))
                    .build())
                .build())
            .statementDescription(truncate(request.getDescription(), 22))
            .build();

        return pawaPayClient.post()
            .uri("/payouts")
            .bodyValue(pawaPayRequest)
            .retrieve()
            .bodyToMono(PawaPayPayoutResponse.class)
            .map(this::mapToPayoutResult);
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return pawaPayClient.get()
            .uri("/health")
            .retrieve()
            .toBodilessEntity()
            .map(response -> response.getStatusCode().is2xxSuccessful())
            .onErrorReturn(false);
    }

    // ============ Private Mapping Methods ============

    private PaymentResult mapToPaymentResult(PawaPayDepositResponse response) {
        return PaymentResult.builder()
            .correlationId(response.getDepositId())
            .providerTransactionId(response.getDepositId())
            .status(mapPawaPayStatus(response.getStatus()))
            .isPending(response.getStatus().equals("ACCEPTED"))
            .message(response.getStatus())
            .build();
    }

    private PaymentResultStatus mapPawaPayStatus(String pawaPayStatus) {
        return switch (pawaPayStatus) {
            case "COMPLETED" -> PaymentResultStatus.SUCCESS;
            case "ACCEPTED", "SUBMITTED" -> PaymentResultStatus.PENDING;
            case "FAILED" -> PaymentResultStatus.FAILED;
            case "CANCELLED" -> PaymentResultStatus.REJECTED;
            case "EXPIRED" -> PaymentResultStatus.EXPIRED;
            default -> PaymentResultStatus.FAILED;
        };
    }

    private Mono<PaymentResult> handlePawaPayError(Throwable error) {
        log.error("PawaPay API error: {}", error.getMessage());

        return Mono.just(PaymentResult.builder()
            .status(PaymentResultStatus.FAILED)
            .errorCode("PROVIDER_ERROR")
            .errorMessage(error.getMessage())
            .isRetryable(isRetryableError(error))
            .build());
    }

    private boolean isRetryableError(Throwable error) {
        // Network errors, timeouts are retryable
        return error instanceof java.net.ConnectException ||
               error instanceof java.util.concurrent.TimeoutException;
    }

    private String normalizePhoneNumber(String phone) {
        // Ensure E.164 format for PawaPay
        if (phone.startsWith("+")) return phone.substring(1);
        if (phone.startsWith("260")) return phone;
        return "260" + phone;
    }

    private String truncate(String str, int maxLength) {
        return str.length() <= maxLength ? str : str.substring(0, maxLength);
    }
}
```

**4. Gateway Factory (Provider Selection)**:

```java
// gateway/MobileMoneyGatewayFactory.java
package com.pml.booking.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MobileMoneyGatewayFactory {

    private final List<MobileMoneyGateway> gateways;
    private final GatewayConfig config;

    /**
     * Get the preferred gateway for a network.
     * Falls back to available alternatives if primary is unavailable.
     */
    public Mono<MobileMoneyGateway> getGateway(MobileNetwork network) {
        // Get configured primary provider for this network
        String primaryProviderId = config.getPrimaryProvider(network);

        return findGateway(primaryProviderId)
            .flatMap(gateway -> gateway.isAvailable()
                .flatMap(available -> {
                    if (available) {
                        return Mono.just(gateway);
                    }
                    log.warn("Primary gateway {} unavailable, trying fallback",
                        primaryProviderId);
                    return findFallbackGateway(network);
                }))
            .switchIfEmpty(Mono.error(
                new NoGatewayAvailableException("No gateway available for " + network)));
    }

    /**
     * Get gateway by explicit provider ID (for retries on same provider)
     */
    public Mono<MobileMoneyGateway> getGatewayByProvider(String providerId) {
        return findGateway(providerId);
    }

    private Mono<MobileMoneyGateway> findGateway(String providerId) {
        return Mono.justOrEmpty(gateways.stream()
            .filter(g -> g.getProviderId().equals(providerId))
            .findFirst());
    }

    private Mono<MobileMoneyGateway> findFallbackGateway(MobileNetwork network) {
        return Mono.justOrEmpty(gateways.stream()
            .filter(g -> g.getSupportedNetworks().contains(network))
            .filter(g -> !g.getProviderId().equals(config.getPrimaryProvider(network)))
            .findFirst())
            .flatMap(gateway -> gateway.isAvailable()
                .filter(available -> available)
                .map(available -> gateway));
    }
}
```

**5. Gateway Configuration**:

```yaml
# application.yml
payment:
  gateway:
    # Primary provider per network
    primary-providers:
      MTN: pawapay
      AIRTEL: pawapay
      ZAMTEL: pawapay

    # Provider configurations
    providers:
      pawapay:
        enabled: true
        base-url: ${PAWAPAY_API_URL:https://api.sandbox.pawapay.io}
        api-token: ${PAWAPAY_API_TOKEN}
        timeout-seconds: 30

      flutterwave:
        enabled: false  # Enable when ready
        base-url: ${FLUTTERWAVE_API_URL}
        api-token: ${FLUTTERWAVE_API_TOKEN}

    # Fallback behavior
    fallback:
      enabled: true
      max-retries: 2
```

**6. Configuration Class**:

```java
// config/GatewayConfig.java
@Configuration
@ConfigurationProperties(prefix = "payment.gateway")
@Data
public class GatewayConfig {

    private Map<MobileNetwork, String> primaryProviders;
    private Map<String, ProviderConfig> providers;
    private FallbackConfig fallback;

    public String getPrimaryProvider(MobileNetwork network) {
        return primaryProviders.getOrDefault(network, "pawapay");
    }

    @Data
    public static class ProviderConfig {
        private boolean enabled;
        private String baseUrl;
        private String apiToken;
        private int timeoutSeconds = 30;
    }

    @Data
    public static class FallbackConfig {
        private boolean enabled = true;
        private int maxRetries = 2;
    }
}
```

### Benefits of This Abstraction

| Benefit | Description |
|---------|-------------|
| **Vendor Independence** | Switch providers without changing business logic |
| **Testability** | Mock `MobileMoneyGateway` interface for unit tests |
| **Resilience** | Automatic failover to backup providers |
| **Observability** | Centralized logging/metrics via factory |
| **Compliance** | Single point for audit logging |
| **Future-proof** | Add card payments, bank transfers with same pattern |

---

## Implementation Priority Matrix

Based on business impact and technical complexity:

| Feature | Business Impact | Complexity | Priority | Sprint |
|---------|----------------|------------|----------|--------|
| **Payment Provider Abstraction** | CRITICAL - Vendor independence | MEDIUM | P0 | 1 |
| **Transactional Outbox (Modulith)** | CRITICAL - Financial integrity | MEDIUM | P0 | 1 |
| **UserStats** | HIGH - Decision making | LOW | P0 | 1 |
| **PayoutStats** | HIGH - Finance ops | LOW | P0 | 1 |
| **Transaction Recovery** | CRITICAL - Revenue loss | MEDIUM | P0 | 1 |
| **Bank Account CRUD** | HIGH - Organizer onboarding | LOW | P1 | 2 |
| **Payout Lifecycle** | HIGH - Organizer relations | MEDIUM | P1 | 2 |
| **CityEventStats** | MEDIUM - Marketing | LOW | P2 | 3 |
| **Apollo Client Polling** | MEDIUM - UX | LOW | P2 | 3 |
| **System Health (Grafana)** | MEDIUM - Ops | LOW | P3 | 4 |

---

## Architecture Alignment Checklist

Your implementation MUST follow these existing patterns:

### Core Patterns
- [ ] **Reactive types**: All operations return `Mono<T>` or `Flux<T>`
- [ ] **MongoDB aggregation**: Use `ReactiveMongoTemplate` with pipelines
- [ ] **Parallel execution**: Use `Mono.zip()` for independent queries
- [ ] **Authorization**: `@PreAuthorize` on all resolvers
- [ ] **Federation**: Extend types, don't redefine `id`
- [ ] **Response types**: Return `*MutationResponse` with success/errors
- [ ] **Pagination**: Support both Cursor and Offset patterns
- [ ] **Tagging**: Use `@tag(name: "admin")` for admin-only queries

### Event-Driven Architecture
- [ ] **Intra-service events**: Use `@ApplicationModuleListener` for guaranteed delivery
- [ ] **Cross-service events**: Use `StreamBridge` for Azure Service Bus
- [ ] **Event publication tracking**: Enable `EventPublicationRegistry` monitoring
- [ ] **Idempotency**: Include `correlationId` in all payment-related events

### Payment Provider Abstraction
- [ ] **Provider-agnostic interfaces**: No vendor names in public APIs
- [ ] **Strategy pattern**: `MobileMoneyGateway` interface with provider adapters
- [ ] **Automatic failover**: Use `MobileMoneyGatewayFactory` for provider selection
- [ ] **Consistent response types**: Use `PaymentResult`, `PayoutResult` for all providers

### Real-Time Updates (Apollo Client)
- [ ] **Polling for dashboards**: Use `pollInterval` (30s default, 5s for critical views)
- [ ] **Adaptive polling**: Reduce frequency when tab is hidden or user is idle
- [ ] **Manual refetch**: Use `refetch()` after mutations for immediate updates
- [ ] **No subscriptions**: Avoid WebSocket complexity with Apollo GraphOS

---

## Summary: Business Value

| Investment | Return |
|------------|--------|
| **Payment Provider Abstraction** | Vendor independence → Negotiate better rates, add backup providers |
| **Transactional Outbox Pattern** | Zero lost payments → Revenue protection, audit compliance |
| **UserStats + PayoutStats** | Real-time KPIs → Faster decisions, investor-ready metrics |
| **Transaction Recovery** | Reduced failed transactions → Higher revenue, better UX |
| **Bank Account CRUD** | Faster organizer onboarding → More events, more revenue |
| **Payout Lifecycle** | Organizer satisfaction → Retention, word-of-mouth growth |
| **Geographic Stats** | Market insights → Targeted expansion in Zambia |
| **Polling-based Real-time** | Simple, reliable dashboards → Lower operational complexity |

**Total Impact**: Transform admin dashboard from "display-only" to "operational command center" with financially sound, provider-agnostic payment infrastructure.

---

## Sources

### Industry Best Practices
- [Ticket Fairy Analytics](https://www.ticketfairy.com/event-ticketing/event-data-analytics)
- [PawaPay Implementation Guide](https://docs.pawapay.io/implementation)
- [Fintech Dashboard Best Practices](https://www.usedatabrain.com/blog/fintech-dashboards)
- [Payment Retry Strategies - Primer](https://primer.io/blog/payment-retry-strategies)
- [Airbnb Distributed Payments](https://medium.com/airbnb-engineering/avoiding-double-payments-in-a-distributed-payments-system-2981f6b070bb)
- [Flutterwave Fault-Tolerant Retries](https://dev.to/flutterwaveeng/how-to-build-a-fault-tolerant-microservice-for-payment-retries-5epg)
- [Rapyd Transaction Lifecycle Management](https://www.rapyd.net/blog/transaction-lifecycle-management/)
- [Fintech API Compliance](https://www.cleffex.com/blog/fintech-api-integration-your-complete-guide/)

### Apollo Router & GraphQL (Context7 Research)
- [Apollo Router HTTP Callback Protocol](https://github.com/apollographql/router/blob/dev/docs/source/routing/operations/subscriptions/callback-protocol.mdx)
- [Apollo Router Subscription Configuration](https://github.com/apollographql/router/blob/dev/docs/source/routing/operations/subscriptions/configuration.mdx)
- [Apollo Client Polling & Refetching](https://github.com/apollographql/apollo-client/blob/main/docs/source/data/queries.mdx)

### Spring Modulith (Context7 Research)
- [Spring Modulith Transactional Event Handling](https://context7.com/spring-projects/spring-modulith/llms.txt)
- [Event Publication Registry & Guaranteed Delivery](https://context7.com/spring-projects/spring-modulith/llms.txt)
- [Application Module Organization](https://context7.com/spring-projects/spring-modulith/llms.txt)

### Spring Cloud Stream (Context7 Research)
- [Transactional Messaging with Kafka](https://docs.spring.io/spring-cloud-stream/reference/5.0/kafka/kafka_tips)
- [Dead Letter Queue Configuration](https://github.com/spring-cloud/spring-cloud-stream/blob/main/docs/modules/ROOT/pages/spring-cloud-stream/overview-error-handling.adoc)
- [StreamBridge for Dynamic Destinations](https://docs.spring.io/spring-cloud-stream/reference/5.0/spring-cloud-stream/producing-and-consuming-messages)

### Design Patterns (Context7 Research)
- [Strategy Pattern for Payment Processing](https://refactoring.guru/design-patterns/strategy/java/example)
- [Adapter Pattern for Provider Abstraction](https://refactoring.guru/design-patterns/adapter/cpp/example)
- [Factory Pattern for Provider Selection](https://refactoring.guru/design-patterns/strategy/php/example)

### Observability
- [Spring Boot Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Microservices Health Check Pattern](https://microservices.io/patterns/observability/health-check-api.html)

---

*Document Version: 2.0*
*Created: 2026-03-26*
*Updated: 2026-03-26 - Added Apollo GraphOS alternatives, Spring Modulith patterns, and Payment Provider Abstraction*
