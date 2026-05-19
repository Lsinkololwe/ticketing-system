# Booking Service - Unmapped Fields Implementation Plan

## Executive Summary

This document outlines the implementation plan for fixing unmapped GraphQL fields in the Booking Service. The plan follows production-grade practices with emphasis on:
- **Data Consistency**: Using optimistic locking (`@Version`) and transactional operations
- **Safety**: Proper validation, error handling, and audit trails
- **Performance**: DGS DataLoaders for N+1 prevention, reactive patterns
- **Maintainability**: Clear separation of concerns, comprehensive documentation

---

## 1. Classification of Unmapped Fields

### 1.1 Federation Entity Stubs (NO ACTION REQUIRED)
These are external fields owned by other services - they are expected to be unmapped:

| Field | Owner Service | Notes |
|-------|---------------|-------|
| `Event.id, Event.totalCapacity, Event.title, Event.eventDateTime, Event.organizerId` | Catalog Service | External entity stub |
| `User.id` | Identity Service | External entity stub |
| `Query._service, Query._entities` | Apollo Router | Federation introspection |

### 1.2 Entity Fields (PRIORITY: HIGH)
Fields missing from domain entities:

| Type | Missing Field | Action | Risk Level |
|------|---------------|--------|------------|
| `Ticket` | `validatedBy` | Add field to entity | LOW |
| `FinancialTransaction` | `maxRetries` | Add constant field resolver | LOW |
| `EventEscrowAccount` | `totalCommissions`, `lockReason`, `closedAt`, `closedReason` | Add fields to entity | MEDIUM |
| `RefundRequest` | `refundPercentage`, `platformRetains`, `daysBeforeEvent`, `policyApplied` | Add computed field resolvers | MEDIUM |
| `PromoCode` | `updatedAt` | Add `@LastModifiedDate` annotation | LOW |

### 1.3 DTO/Statistics Types (PRIORITY: MEDIUM)
Fields missing from DTOs:

| Type | Missing Fields | Action |
|------|----------------|--------|
| `PaginationInfo` | `pageNumber`, `totalElements`, `hasNext`, `hasPrevious` | Update DTO + field resolver |
| `PageInfo` | `totalElements`, `totalPages`, `currentPage`, `pageSize`, `hasNext`, `hasPrevious` | Update DTO + field resolver |
| `TicketStats` | `cancelledTickets`, `pendingPaymentTickets` | Update DTO |
| `PayoutRequestStats` | `totalPayoutAmount`, `pendingPayoutAmount` | Update DTO |
| `AccountSummary` | `totalCommissions` | Update DTO |
| `RefundCalculation` | `policyApplied`, `ineligibleReason` | Update DTO |
| `RefundSummary` | `averageRefundAmount` | Update DTO |

### 1.4 Missing Mutations (PRIORITY: HIGH)
Mutations declared in schema but not implemented:

| Mutation | Purpose | Complexity |
|----------|---------|------------|
| `retryPayoutRequest` | Retry failed payout | HIGH - Financial operation |
| `forceExpireReservation` | Admin force-expire stuck reservations | MEDIUM |
| `activatePromoCode` | Activate a promo code | LOW |

### 1.5 Query Registration Issues (PRIORITY: LOW)
Queries with mismatched method names:

| Query | Issue | Fix |
|-------|-------|-----|
| `Query.bankAccounts` | Parameter mismatch | Check argument mapping |
| `Query.retryablePayoutRequestsOffsetPagination` | Registration issue | Verify @QueryMapping |
| `Query.recentlyResolvedPayoutRequestsOffsetPagination` | Registration issue | Verify @QueryMapping |

---

## 2. Implementation Tasks

### Phase 1: Entity Updates (Days 1-2)

#### Task 1.1: Update Ticket Entity
**File:** `domain/model/Ticket.java`

```java
// Add field after validatedAt
private String validatedBy;  // ID of user/device that validated the ticket
```

**Safety Considerations:**
- Field is nullable (tickets may not be validated yet)
- Populate during ticket validation mutation
- Add audit trail in validation history

#### Task 1.2: Update EventEscrowAccount Entity
**File:** `domain/model/EventEscrowAccount.java`

```java
// Add fields for tracking commissions and closure
@DecimalMin(value = "0.0")
@Builder.Default
private BigDecimal totalCommissions = BigDecimal.ZERO;

private String lockReason;      // Reason for locking (e.g., "HOLD_PERIOD", "FRAUD_REVIEW")
private LocalDateTime closedAt; // When account was closed
private String closedReason;    // Reason for closure (e.g., "FULLY_PAID", "EVENT_CANCELLED")
```

**Data Consistency:**
- `totalCommissions` must be updated atomically with ticket sales
- `closedAt` and `closedReason` set only during state transitions to CLOSED
- Use optimistic locking (`@Version` already exists)

#### Task 1.3: Update PromoCode Entity
**File:** `domain/model/PromoCode.java`

```java
// Add after createdAt
@LastModifiedDate
private LocalDateTime updatedAt;
```

#### Task 1.4: Add maxRetries Constant to FinancialTransaction
**File:** `web/graphql/resolver/FinancialTransactionFieldResolver.java` (NEW)

```java
@DgsComponent
public class FinancialTransactionFieldResolver {

    private static final int MAX_RETRIES = 3;

    @DgsData(parentType = "FinancialTransaction", field = "maxRetries")
    public Integer maxRetries(DgsDataFetchingEnvironment dfe) {
        return MAX_RETRIES;
    }
}
```

### Phase 2: DTO Updates (Days 2-3)

#### Task 2.1: Update PaginationInfo DTO
**File:** `dto/PaginationInfo.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationInfo {
    private Integer pageNumber;     // Current page (0-indexed)
    private Integer pageSize;       // Items per page
    private Long totalElements;     // Total items across all pages
    private Integer totalPages;     // Total number of pages
    private Integer totalCount;     // Alias for totalElements
    private Integer currentPage;    // Alias for pageNumber (1-indexed for UI)
    private Boolean hasNext;        // Has next page
    private Boolean hasPrevious;    // Has previous page
    private Boolean hasNextPage;    // Alias for hasNext
    private Boolean hasPreviousPage; // Alias for hasPrevious
}
```

#### Task 2.2: Create PaginationInfo Field Resolver
**File:** `web/graphql/resolver/PaginationInfoFieldResolver.java` (NEW)

```java
@DgsComponent
public class PaginationInfoFieldResolver {

    @DgsData(parentType = "PaginationInfo", field = "pageNumber")
    public Integer pageNumber(DgsDataFetchingEnvironment dfe) {
        PaginationInfo info = dfe.getSource();
        return info.getPageNumber() != null ? info.getPageNumber() : 0;
    }

    @DgsData(parentType = "PaginationInfo", field = "totalElements")
    public Long totalElements(DgsDataFetchingEnvironment dfe) {
        PaginationInfo info = dfe.getSource();
        return info.getTotalElements() != null ? info.getTotalElements()
            : (info.getTotalCount() != null ? info.getTotalCount().longValue() : null);
    }

    @DgsData(parentType = "PaginationInfo", field = "hasNext")
    public Boolean hasNext(DgsDataFetchingEnvironment dfe) {
        PaginationInfo info = dfe.getSource();
        if (info.getHasNext() != null) return info.getHasNext();
        if (info.getHasNextPage() != null) return info.getHasNextPage();
        // Compute from other fields
        if (info.getPageNumber() != null && info.getTotalPages() != null) {
            return info.getPageNumber() < info.getTotalPages() - 1;
        }
        return null;
    }

    @DgsData(parentType = "PaginationInfo", field = "hasPrevious")
    public Boolean hasPrevious(DgsDataFetchingEnvironment dfe) {
        PaginationInfo info = dfe.getSource();
        if (info.getHasPrevious() != null) return info.getHasPrevious();
        if (info.getHasPreviousPage() != null) return info.getHasPreviousPage();
        if (info.getPageNumber() != null) {
            return info.getPageNumber() > 0;
        }
        return null;
    }
}
```

#### Task 2.3: Update Statistics DTOs
**Files:** Update `TicketStats.java`, `PayoutRequestStats.java`, `RefundSummary.java`, `AccountSummary.java`

Add missing fields as documented in the schema.

### Phase 3: Field Resolvers for Computed Fields (Days 3-4)

#### Task 3.1: RefundRequest Field Resolver
**File:** `web/graphql/resolver/RefundRequestFieldResolver.java` (NEW)

```java
@DgsComponent
@RequiredArgsConstructor
public class RefundRequestFieldResolver {

    private final RefundPolicyService refundPolicyService;

    /**
     * Calculate refund percentage based on policy.
     * This is a computed field based on days before event.
     */
    @DgsData(parentType = "RefundRequest", field = "refundPercentage")
    public Double refundPercentage(DgsDataFetchingEnvironment dfe) {
        RefundRequest request = dfe.getSource();
        if (request.getOriginalTicketPrice() == null ||
            request.getRefundAmount() == null) {
            return null;
        }
        return request.getRefundAmount()
            .divide(request.getOriginalTicketPrice(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }

    /**
     * Amount platform retains from the refund.
     */
    @DgsData(parentType = "RefundRequest", field = "platformRetains")
    public BigDecimal platformRetains(DgsDataFetchingEnvironment dfe) {
        RefundRequest request = dfe.getSource();
        if (request.getOriginalTicketPrice() == null ||
            request.getNetRefundAmount() == null) {
            return BigDecimal.ZERO;
        }
        return request.getOriginalTicketPrice().subtract(request.getNetRefundAmount());
    }

    /**
     * Alias for requestReason field.
     */
    @DgsData(parentType = "RefundRequest", field = "reason")
    public String reason(DgsDataFetchingEnvironment dfe) {
        RefundRequest request = dfe.getSource();
        return request.getRequestReason();
    }

    // Additional computed fields...
}
```

### Phase 4: Mutation Implementations (Days 4-6)

#### Task 4.1: Implement retryPayoutRequest Mutation
**File:** `web/graphql/mutation/PayoutRequestMutationResolver.java`

```java
/**
 * Retry a failed payout request.
 *
 * SAFETY CONSIDERATIONS:
 * - Only FAILED payouts can be retried
 * - Must check retry count limit (max 3)
 * - Must verify escrow has sufficient balance
 * - Must create audit trail
 * - Uses @Transactional for data consistency
 */
@MutationMapping
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public Mono<PayoutMutationResponse> retryPayoutRequest(
        @Argument String payoutRequestId,
        @Argument String adminId,
        @Argument String notes) {

    log.info("Mutation: retryPayoutRequest(id={}, adminId={})", payoutRequestId, adminId);

    return payoutRequestService.findById(payoutRequestId)
        .switchIfEmpty(Mono.error(new PayoutNotFoundException(payoutRequestId)))
        .flatMap(payout -> {
            // Validate state
            if (payout.getStatus() != PayoutRequestStatus.FAILED) {
                return Mono.error(new IllegalStateException(
                    "Only FAILED payouts can be retried. Current status: " + payout.getStatus()));
            }

            // Check retry limit
            if (payout.getRetryCount() >= 3) {
                return Mono.error(new IllegalStateException(
                    "Maximum retry attempts (3) exceeded"));
            }

            // Retry the payout
            return payoutRequestService.retryPayout(payoutRequestId, adminId, notes);
        })
        .map(payout -> PayoutMutationResponse.success(payout, "Payout retry initiated"))
        .onErrorResume(e -> {
            log.error("Error retrying payout {}: {}", payoutRequestId, e.getMessage());
            return Mono.just(PayoutMutationResponse.error(e.getMessage()));
        });
}
```

#### Task 4.2: Implement forceExpireReservation Mutation
**File:** `web/graphql/mutation/ReservationMutationResolver.java`

```java
/**
 * Force-expire a stuck reservation (admin only).
 *
 * SAFETY CONSIDERATIONS:
 * - Must release held inventory back to event
 * - Must be idempotent (no-op if already expired/converted)
 * - Creates audit trail for compliance
 */
@MutationMapping
@PreAuthorize("hasRole('ADMIN')")
public Mono<ReservationMutationResponse> forceExpireReservation(
        @Argument String reservationId,
        @Argument String adminId,
        @Argument String reason) {

    log.info("Mutation: forceExpireReservation(id={}, adminId={}, reason={})",
        reservationId, adminId, reason);

    return reservationService.findById(reservationId)
        .switchIfEmpty(Mono.error(new ReservationNotFoundException(reservationId)))
        .flatMap(reservation -> {
            // Check if already in terminal state
            if (reservation.getStatus() == ReservationStatus.EXPIRED ||
                reservation.getStatus() == ReservationStatus.CONVERTED ||
                reservation.getStatus() == ReservationStatus.CANCELLED) {
                return Mono.just(ReservationMutationResponse.success(
                    reservation, "Reservation already in terminal state: " + reservation.getStatus()));
            }

            // Force expire
            return reservationService.forceExpire(reservationId, adminId, reason);
        })
        .map(res -> ReservationMutationResponse.success(res, "Reservation force-expired successfully"))
        .onErrorResume(e -> {
            log.error("Error force-expiring reservation {}: {}", reservationId, e.getMessage());
            return Mono.just(ReservationMutationResponse.error(e.getMessage()));
        });
}
```

#### Task 4.3: Implement activatePromoCode Mutation
**File:** `web/graphql/mutation/PromoCodeMutationResolver.java`

```java
/**
 * Activate a promo code.
 */
@MutationMapping
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
public Mono<PromoCodeMutationResponse> activatePromoCode(
        @Argument String promoCodeId) {

    log.info("Mutation: activatePromoCode(id={})", promoCodeId);

    return promoCodeService.findById(promoCodeId)
        .switchIfEmpty(Mono.error(new PromoCodeNotFoundException(promoCodeId)))
        .flatMap(code -> {
            code.setActive(true);
            return promoCodeService.save(code);
        })
        .map(code -> PromoCodeMutationResponse.success(code, "Promo code activated"))
        .onErrorResume(e -> {
            log.error("Error activating promo code {}: {}", promoCodeId, e.getMessage());
            return Mono.just(PromoCodeMutationResponse.error(e.getMessage()));
        });
}
```

### Phase 5: Fix Query Registrations (Day 6)

#### Task 5.1: Verify Query Argument Mappings
Check that query method signatures match schema:

```java
// Schema: bankAccounts(organizerId: ID): [BankAccount!]!
@QueryMapping
public Flux<BankAccount> bankAccounts(@Argument String organizerId) { ... }

// Schema: retryablePayoutRequestsOffsetPagination(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
@QueryMapping
public Mono<PayoutRequestOffsetPage> retryablePayoutRequestsOffsetPagination(
        @Argument OffsetPaginationInput pagination) { ... }
```

---

## 3. Data Consistency Patterns

### 3.1 Optimistic Locking
All financial entities use `@Version` for optimistic locking:

```java
@Version
private Long version;
```

Handle `OptimisticLockingFailureException` in exception resolver:
```java
if (ex instanceof OptimisticLockingFailureException) {
    return GraphqlErrorBuilder.newError(env)
        .errorType(ErrorType.BAD_REQUEST)
        .message("Concurrent modification detected. Please refresh and try again.")
        .extensions(Map.of("code", "CONCURRENT_MODIFICATION"))
        .build();
}
```

### 3.2 Transactional Operations
Financial mutations use `@Transactional`:

```java
@Transactional
public Mono<PayoutRequest> retryPayout(String payoutId, String adminId, String notes) {
    return payoutRequestRepository.findById(payoutId)
        .flatMap(payout -> {
            // Update payout
            payout.incrementRetryCount();
            payout.setLastRetryAt(LocalDateTime.now());
            payout.setStatus(PayoutRequestStatus.PROCESSING);

            return payoutRequestRepository.save(payout)
                .flatMap(saved -> {
                    // Create financial transaction
                    return createPayoutTransaction(saved);
                });
        });
}
```

### 3.3 Audit Trail
All mutations create audit entries:

```java
public void addAuditEntry(String action, String performedBy, String notes) {
    AuditEntry entry = AuditEntry.builder()
        .action(action)
        .performedBy(performedBy)
        .performedAt(LocalDateTime.now())
        .notes(notes)
        .build();

    if (this.auditHistory == null) {
        this.auditHistory = new ArrayList<>();
    }
    this.auditHistory.add(entry);
}
```

---

## 4. Testing Strategy

### 4.1 Unit Tests
- Test each field resolver independently
- Mock service dependencies
- Verify null handling and edge cases

### 4.2 Integration Tests
- Use `@DgsQueryExecutor` for GraphQL queries
- Test full request/response cycle
- Verify transactional behavior

### 4.3 Data Consistency Tests
- Test optimistic locking with concurrent modifications
- Test retry logic with simulated failures
- Test inventory release on reservation expiry

---

## 5. Deployment Checklist

- [ ] Database migration scripts for new entity fields
- [ ] Backward compatibility for existing data (nullable new fields)
- [ ] Performance testing for new field resolvers
- [ ] Update API documentation
- [ ] Monitor error rates post-deployment
- [ ] Rollback plan documented

---

## 6. Timeline Summary

| Phase | Tasks | Duration | Dependencies |
|-------|-------|----------|--------------|
| Phase 1 | Entity Updates | 2 days | None |
| Phase 2 | DTO Updates | 1 day | Phase 1 |
| Phase 3 | Field Resolvers | 2 days | Phase 1, 2 |
| Phase 4 | Mutations | 3 days | Phase 1-3 |
| Phase 5 | Query Fixes | 1 day | None |
| Testing | All phases | 2 days | Phase 1-5 |

**Total Estimated Time: 11 days**

---

## 7. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Data inconsistency during payout retry | HIGH | Use transactions, optimistic locking |
| Inventory leak on reservation expiry | MEDIUM | Atomic operations, scheduled cleanup |
| Breaking changes to API | LOW | New fields are additive only |
| Performance degradation | MEDIUM | Use DataLoaders, monitor query times |
