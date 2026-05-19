# Event Configuration Flow Analysis Report

## Executive Summary

This report analyzes the Event configuration flow in the catalog-service for conformance with multi-tenant infrastructure tracking capabilities, OWASP security standards, and business rule enforcement.

**Overall Assessment**: The Event model has **good foundational architecture** but has **critical gaps** in multi-tenant tracking, audit logging, and security enforcement.

---

## 1. Multi-Tenant Infrastructure Analysis

### 1.1 Current State

| Field | Present | Indexed | Purpose |
|-------|---------|---------|---------|
| `organizerId` | ✅ Yes | ✅ Yes | Primary organizer user ID |
| `organizationId` | ✅ Yes | ✅ Yes | Organization entity for team access |
| `createdBy` | ✅ Yes | ❌ No | Audit: who created |
| `updatedBy` | ✅ Yes | ❌ No | Audit: who modified |

### 1.2 GAPS IDENTIFIED

#### GAP 1: Missing @Version for Optimistic Locking
**Severity**: CRITICAL

The `Event` model has a `version` field but it's **NOT annotated with `@Version`**:

```java
// Current (INCORRECT)
@Builder.Default
private Long version = 0L;

// Required (CORRECT)
@Version
private Long version;
```

**Impact**:
- Race conditions during concurrent event updates
- Data corruption risk when multiple users edit the same event
- No protection against lost updates (OWASP A04:2021 - Insecure Design)

**Contrast with TicketTier**: The `TicketTier` model correctly has `@Version private Long version;`

#### GAP 2: Audit Fields Not Indexed
**Severity**: MEDIUM

`createdBy` and `updatedBy` fields are not indexed, preventing efficient:
- Audit log queries
- Finding all events created/modified by a specific user
- Compliance reporting

#### GAP 3: No Audit Log Entity for Change History
**Severity**: HIGH

No separate audit log captures:
- What was changed (field-level diff)
- Previous values
- IP address / user agent
- Session ID

**OWASP Reference**: A09:2021 - Security Logging and Monitoring Failures

#### GAP 4: Missing Soft Delete Audit Trail
**Severity**: MEDIUM

`deleteEvent()` uses hard delete with no audit trail:

```java
@Override
public Mono<Void> deleteEvent(String id) {
    return eventRepository.deleteById(id);  // NO AUDIT TRAIL!
}
```

---

## 2. Business Rules Analysis

### 2.1 Event Lifecycle State Machine

```
DRAFT → PENDING_APPROVAL → APPROVED → PUBLISHED → COMPLETED
    ↘                    ↘        ↘
  REJECTED ←──────────────←───────←──── CANCELLED
```

### 2.2 State Transition Rules

| Transition | Enforced? | Location | Gap |
|------------|-----------|----------|-----|
| DRAFT → PENDING_APPROVAL | ❌ Partial | MutationResolver | No explicit validation in service |
| PENDING_APPROVAL → APPROVED | ✅ Yes | EventServiceImpl | Admin only via @PreAuthorize |
| APPROVED → PUBLISHED | ✅ Yes | EventServiceImpl | Publishes EventPublishedEvent |
| Any → CANCELLED | ✅ Yes | EventServiceImpl | Publishes EventCancelledEvent |
| Any → COMPLETED | ✅ Yes | EventServiceImpl | Publishes EventCompletedEvent |

### 2.3 GAPS IN BUSINESS RULES

#### GAP 5: No State Transition Validation in Service Layer
**Severity**: HIGH

The service allows invalid state transitions:

```java
// EventServiceImpl.publishEvent() doesn't validate current state
@Override
public Mono<Event> publishEvent(String id) {
    return eventRepository.findById(id)
            .flatMap(event -> {
                // GAP: No validation that event is in APPROVED state!
                event.setStatus(EventStatus.PUBLISHED);
                // ...
            });
}
```

**Required**: Events should only be publishable from APPROVED state.

#### GAP 6: Missing Domain Events for Draft/Submission
**Severity**: MEDIUM

No domain events are published for:
- `EventCreatedEvent` (DRAFT creation)
- `EventSubmittedForApprovalEvent`
- `EventRejectedEvent`

This breaks the event-driven architecture for cross-service notifications.

#### GAP 7: No Validation for Required Fields Before Status Changes
**Severity**: HIGH

No business validation before:
- Submitting for approval (location, pricing, dates required)
- Publishing (capacity > 0, valid dates, etc.)

```java
// Should validate:
// - Event has at least one ticket tier
// - Event has valid start/end dates (future)
// - Event has location information
// - Organizer is verified
```

---

## 3. CRUD Operations Analysis

### 3.1 CREATE Operation

| Aspect | Status | Details |
|--------|--------|---------|
| Multi-tenant context | ⚠️ Partial | `organizationId` set but not validated |
| Audit tracking | ✅ Yes | `@CreatedBy`, `@CreatedDate` via auditing |
| Input validation | ✅ Yes | Jakarta Bean Validation |
| Authorization | ✅ Yes | `@PreAuthorize` in resolver |
| Domain event | ❌ No | No EventCreatedEvent published |

**GAP 8**: No EventCreatedEvent for downstream services (booking-service escrow setup)

### 3.2 READ Operations

| Aspect | Status | Details |
|--------|--------|---------|
| Public queries | ✅ Secure | Filter by `published=true`, `isActive=true` |
| Organizer queries | ⚠️ Fixed | Now uses `OrganizationSecurityService` |
| Admin queries | ✅ Secure | `hasRole('ADMIN')` enforced |
| Pagination | ✅ Yes | Both offset and cursor pagination |

### 3.3 UPDATE Operation

| Aspect | Status | Details |
|--------|--------|---------|
| Optimistic locking | ❌ No | Missing `@Version` annotation |
| Field-level access | ❌ No | All fields updatable regardless of state |
| Audit tracking | ✅ Yes | `@LastModifiedBy`, `@LastModifiedDate` |
| Authorization | ✅ Yes | Identity service check via `checkEventAccess` |

**GAP 9**: No field-level restrictions based on event status:
- Published events shouldn't allow date changes without republishing flow
- Completed/Cancelled events should be immutable

### 3.4 DELETE Operation

| Aspect | Status | Details |
|--------|--------|---------|
| Hard delete | ✅ Implemented | `deleteById()` |
| Soft delete | ❌ Missing | No `deletedAt`, `deletedBy` fields |
| Audit trail | ❌ No | No record of deleted events |
| Cascade handling | ⚠️ Partial | TicketTiers not cascaded |

**GAP 10**: Delete should:
1. Check no tickets sold
2. Use soft delete with `deletedAt`, `deletedBy`
3. Publish `EventDeletedEvent`
4. Cascade to TicketTiers

---

## 4. GraphQL Security Analysis

### 4.1 Query Resolver Security (Updated)

| Query | Authorization | OWASP Compliant |
|-------|--------------|-----------------|
| `event(id)` | Public | ✅ |
| `publishedEvents*` | Public | ✅ |
| `eventsByOrganizer*` | `@organizationSecurityService` | ✅ Fixed |
| `draftEvents*` | `@organizationSecurityService` | ✅ Fixed |
| `eventCountByOrganizer` | `@organizationSecurityService` | ✅ Fixed |
| `eventsOffsetPagination` | `hasRole('ADMIN')` | ✅ |

### 4.2 Mutation Resolver Security

| Mutation | Authorization Check | Gap |
|----------|---------------------|-----|
| `createEvent` | Role + JWT userId | ✅ Secure |
| `updateEvent` | `checkEventAccess("EVENT_EDIT")` | ✅ Secure |
| `deleteEvent` | `checkEventAccess("EVENT_DELETE")` | ✅ Secure |
| `publishEvent` | `checkEventAccess("EVENT_PUBLISH")` | ⚠️ State not validated |
| `approveEvent` | `hasRole('ADMIN')` | ✅ Secure |
| `rejectEvent` | `hasRole('ADMIN')` | ✅ Secure |

### 4.3 GAP 11: Missing Rate Limiting
**Severity**: MEDIUM

No rate limiting on mutations prevents:
- Abuse of create/update operations
- DoS via expensive search queries

**OWASP Reference**: API4:2023 - Unrestricted Resource Consumption

---

## 5. Database Schema Issues

### 5.1 Index Analysis

| Index | Status | Purpose |
|-------|--------|---------|
| `organizerId` | ✅ Indexed | Organizer lookups |
| `organizationId` | ✅ Indexed | Multi-tenant isolation |
| `status` | ✅ Indexed | Status filtering |
| `eventDateTime` | ✅ Indexed | Date range queries |
| `categoryId` | ❌ Not indexed | Category filtering slow |
| `cityName` | ❌ Not indexed | City filtering slow |
| `featured` | ❌ Not indexed | Featured event queries |
| `isFreeEvent` | ✅ Indexed | Free event queries |

**GAP 12**: Missing indexes on frequently queried fields (`categoryId`, `cityName`)

### 5.2 Missing Compound Indexes

```javascript
// Recommended compound indexes
{ "organizerId": 1, "status": 1, "createdAt": -1 }  // Organizer dashboard
{ "status": 1, "approvalDeadline": 1 }  // Admin approval queue
{ "published": 1, "eventDateTime": 1, "isActive": 1 }  // Discovery queries
```

---

## 6. Domain Event Tracking

### 6.1 Events Published

| Event | Published | Consumer | Purpose |
|-------|-----------|----------|---------|
| `EventPublishedEvent` | ✅ Yes | booking-service | Create escrow account |
| `EventCancelledEvent` | ✅ Yes | booking-service | Trigger refunds |
| `EventCompletedEvent` | ✅ Yes | booking-service | Lock escrow, start hold period |
| `EventApprovedEvent` | ✅ Yes | notification-service | Notify organizer |
| `EventRescheduledEvent` | ✅ Yes | booking-service | Open refund window |

### 6.2 Missing Events

| Event | Impact |
|-------|--------|
| `EventCreatedEvent` | booking-service can't pre-create escrow |
| `EventSubmittedEvent` | No admin notification |
| `EventRejectedEvent` | No rejection tracking |
| `EventDeletedEvent` | No cleanup in other services |
| `EventUpdatedEvent` | No change tracking across services |

---

## 7. OWASP Compliance Matrix

| OWASP ID | Vulnerability | Status | Gap |
|----------|--------------|--------|-----|
| A01:2021 | Broken Access Control | ✅ Fixed | OrganizationSecurityService implemented |
| A02:2021 | Cryptographic Failures | N/A | No sensitive data encryption needed |
| A03:2021 | Injection | ✅ Pass | Spring Data MongoDB parameterized queries |
| A04:2021 | Insecure Design | ⚠️ Partial | Missing optimistic locking, state validation |
| A05:2021 | Security Misconfiguration | ✅ Pass | OAuth2 resource server configured |
| A07:2021 | XSS | ✅ Pass | GraphQL type system prevents injection |
| A08:2021 | Data Integrity Failures | ⚠️ Partial | Missing @Version, no transaction isolation |
| A09:2021 | Logging Failures | ⚠️ Partial | Basic logging, no audit log entity |
| A10:2021 | SSRF | ✅ Pass | No external URL fetching |

---

## 8. Recommendations by Priority

### CRITICAL (Must Fix)

1. **Add @Version annotation to Event model**
   ```java
   @Version
   private Long version;
   ```

2. **Implement state transition validation**
   ```java
   public Mono<Event> publishEvent(String id) {
       return eventRepository.findById(id)
           .filter(e -> e.getStatus() == EventStatus.APPROVED)
           .switchIfEmpty(Mono.error(new InvalidStateException("Event must be APPROVED to publish")))
           .flatMap(event -> { ... });
   }
   ```

3. **Add soft delete with audit trail**
   ```java
   // Add to Event model
   private LocalDateTime deletedAt;
   private String deletedBy;
   private boolean isDeleted;
   ```

### HIGH Priority

4. **Create EventAuditLog entity**
   ```java
   @Document(collection = "event_audit_log")
   public class EventAuditLog {
       private String eventId;
       private String action; // CREATE, UPDATE, DELETE, STATUS_CHANGE
       private String userId;
       private String previousValue;
       private String newValue;
       private LocalDateTime timestamp;
       private String ipAddress;
   }
   ```

5. **Publish missing domain events**
   - EventCreatedEvent
   - EventSubmittedEvent
   - EventRejectedEvent
   - EventDeletedEvent

6. **Add missing database indexes**
   ```java
   @Indexed
   private String categoryId;

   @Indexed
   private String cityName;
   ```

### MEDIUM Priority

7. **Index audit fields**
   ```java
   @Indexed
   @CreatedBy
   private String createdBy;
   ```

8. **Add field-level immutability for completed events**

9. **Implement rate limiting on mutations**

---

## 9. Implementation Checklist

- [ ] Add @Version to Event model
- [ ] Create EventAuditLog entity and repository
- [ ] Add EventAuditService for audit logging
- [ ] Implement state transition validation in EventServiceImpl
- [ ] Add soft delete fields and logic
- [ ] Publish missing domain events
- [ ] Add missing indexes
- [ ] Add field-level restrictions based on status
- [ ] Index createdBy/updatedBy fields
- [ ] Add cascade delete for TicketTiers

---

## 10. Testing Strategy

### Unit Tests Required
- State transition validation (valid and invalid transitions)
- Optimistic locking conflict handling
- Soft delete behavior
- Audit log creation

### Integration Tests Required
- Multi-tenant data isolation
- Domain event publishing and consumption
- Cascade delete behavior

---

**Report Generated**: 2026-04-23
**Analyzed By**: Claude Code Security Analysis
**Standards Referenced**: OWASP Top 10 2021, OWASP API Security Top 10 2023, Spring Data MongoDB Best Practices
