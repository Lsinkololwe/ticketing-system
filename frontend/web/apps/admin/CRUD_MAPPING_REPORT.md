# Admin Dashboard - CRUD Mapping, Gap Analysis & OWASP Compliance Report

**Generated:** 2026-05-18
**Application:** PML Tickets Admin Dashboard
**Path:** `/frontend/web/apps/admin`

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Overview](#2-architecture-overview)
3. [CRUD Mapping: Frontend to Backend](#3-crud-mapping-frontend-to-backend)
4. [Design Patterns](#4-design-patterns)
5. [OWASP Top 10 Compliance Analysis](#5-owasp-top-10-compliance-analysis)
6. [Gap Analysis: Missing Features](#6-gap-analysis-missing-features)
7. [Redundancies & Technical Debt](#7-redundancies--technical-debt)
8. [Recommendations](#8-recommendations)

---

## 1. Executive Summary

### Overview
The Admin Dashboard is a Next.js 14+ application serving as the platform administration interface for PML Tickets. It provides comprehensive management capabilities for events, users, tickets, payments, refunds, and payouts.

### Key Metrics

| Metric | Count |
|--------|-------|
| Backend GraphQL Queries Available | 190+ |
| Backend GraphQL Mutations Available | 202+ |
| Frontend Implemented Queries | ~80 |
| Frontend Implemented Mutations | ~45 |
| **Coverage Gap** | **~55% unutilized** |

### Critical Findings

| Category | Status | Priority |
|----------|--------|----------|
| OWASP Compliance | ⚠️ Partial | HIGH |
| Feature Coverage | ⚠️ 45% Complete | MEDIUM |
| Form Validation | ❌ Client-side Missing | HIGH |
| Error Handling | ✅ Implemented | LOW |
| Authentication | ✅ Secure (Keycloak + PKCE) | LOW |

---

## 2. Architecture Overview

### Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ADMIN DASHBOARD ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐       │
│  │   Next.js App   │     │  Keycloak IAM   │     │  Apollo Router  │       │
│  │   (Port 3030)   │────▶│   (Port 8084)   │     │   (Port 4001)   │       │
│  └────────┬────────┘     └─────────────────┘     └────────┬────────┘       │
│           │                                               │                 │
│           │ JWT Bearer Token                              │                 │
│           ▼                                               ▼                 │
│  ┌─────────────────────────────────────────────────────────────────┐       │
│  │                      Apollo Client (GraphQL)                     │       │
│  │  - InMemoryCache with typePolicies                              │       │
│  │  - Automatic token injection via httpLink                       │       │
│  │  - Retry logic (3 attempts for network errors)                  │       │
│  └─────────────────────────────────────────────────────────────────┘       │
│                                    │                                        │
│                                    ▼                                        │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐                   │
│  │   Catalog     │  │   Booking     │  │   Identity    │                   │
│  │   Service     │  │   Service     │  │   Service     │                   │
│  │  (Port 8085)  │  │  (Port 8082)  │  │  (Port 8083)  │                   │
│  │               │  │               │  │               │                   │
│  │  • Events     │  │  • Tickets    │  │  • Users      │                   │
│  │  • Categories │  │  • Payments   │  │  • Organizers │                   │
│  │  • Locations  │  │  • Refunds    │  │  • Permissions│                   │
│  │  • Ticket     │  │  • Payouts    │  │  • Teams      │                   │
│  │    Tiers      │  │  • Escrow     │  │  • Orgs       │                   │
│  │               │  │  • Chargebacks│  │               │                   │
│  └───────────────┘  └───────────────┘  └───────────────┘                   │
│           │                 │                 │                             │
│           └─────────────────┼─────────────────┘                             │
│                             ▼                                               │
│                    ┌───────────────┐                                        │
│                    │   MongoDB     │                                        │
│                    │  (Reactive)   │                                        │
│                    └───────────────┘                                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Framework | Next.js (App Router) | 14.x |
| UI Library | Radix UI | 5.x |
| State Management | Apollo Client | 4.x |
| Authentication | Keycloak | 26.x |
| Styling | Tailwind CSS | 3.x |
| Icons | iconoir-react | - |

---

## 3. CRUD Mapping: Frontend to Backend

### 3.1 Events Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **CREATE** | `useCreateEventAdmin()` | `createEvent(input)` | Catalog | ✅ |
| **READ (Single)** | `useEventByIdAdmin()` | `event(id)` | Catalog | ✅ |
| **READ (List)** | `useEventsAdmin()` | `eventsOffsetPagination(filter, pagination)` | Catalog | ✅ |
| **READ (Pending)** | `usePendingApprovalEventsAdmin()` | `pendingApprovalEventsOffsetPagination()` | Catalog | ✅ |
| **READ (Approved)** | `useApprovedNotPublishedEventsAdmin()` | `approvedNotPublishedEventsOffsetPagination()` | Catalog | ✅ |
| **READ (Draft)** | `useDraftEventsAdmin()` | `draftEventsOffsetPagination()` | Catalog | ✅ |
| **READ (Stats)** | `useEventStatsAdmin()` | `eventStats` | Catalog | ✅ |
| **UPDATE** | `useUpdateEventAdmin()` | `updateEvent(id, input)` | Catalog | ✅ |
| **DELETE** | `useDeleteEventAdmin()` | `deleteEvent(id)` | Catalog | ✅ |
| **PUBLISH** | `usePublishEventAdmin()` | `publishEvent(id)` | Catalog | ✅ |
| **CANCEL** | `useCancelEventAdmin()` | `cancelEvent(id, input)` | Catalog | ✅ |
| **APPROVE** | `useApproveEventAdmin()` | `approveEvent(eventId, comments)` | Catalog | ✅ |
| **REJECT** | `useRejectEventAdmin()` | `rejectEvent(eventId, comments)` | Catalog | ✅ |
| **FEATURE** | `useFeatureEventAdmin()` | `featureEvent(eventId, featured)` | Catalog | ✅ |
| **DUPLICATE** | ❌ Not Implemented | `duplicateEvent(eventId, newTitle)` | Catalog | ❌ |
| **CAPACITY UPDATE** | ❌ Not Implemented | `updateEventCapacity(eventId, newCapacity)` | Catalog | ❌ |
| **LIFECYCLE** | `useEventLifecycleAdmin()` | `eventLifecycle(eventId)` | Catalog | ✅ |
| **TRANSITIONS** | `useAllowedStatusTransitionsAdmin()` | `allowedStatusTransitions(eventId)` | Catalog | ✅ |
| **OVERDUE** | ❌ Not Implemented | `overdueApprovalEventsOffsetPagination()` | Catalog | ❌ |
| **EXPORT** | ❌ Not Implemented | `exportEventData(eventId, format)` | Catalog | ❌ |

**Event Coverage: 15/19 operations (79%)**

---

### 3.2 Users Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **CREATE** | `useCreateUserAdmin()` | `createUser(input)` | Identity | ✅ |
| **READ (Me)** | `useMeQuery()` | `me` | Identity | ✅ |
| **READ (Single)** | `useUserAdmin()` | `user(id)` | Identity | ✅ |
| **READ (List)** | `useUsersAdmin()` | `usersOffsetPagination(...)` | Identity | ✅ |
| **READ (Stats)** | ❌ Not Implemented | `userStats` | Identity | ❌ |
| **UPDATE** | `useUpdateUserAdmin()` | `updateUser(id, input)` | Identity | ✅ |
| **SUSPEND** | `useSuspendUserAdmin()` | `suspendUser(id, reason)` | Identity | ✅ |
| **UNSUSPEND** | `useUnsuspendUserAdmin()` | `unsuspendUser(id)` | Identity | ✅ |
| **LOCK** | `useLockUserAdmin()` | `lockUser(id, reason)` | Identity | ✅ |
| **UNLOCK** | `useUnlockUserAdmin()` | `unlockUser(id)` | Identity | ✅ |
| **BY EMAIL** | ❌ Not Implemented | `userByEmail(email)` | Identity | ❌ |
| **BY PHONE** | ❌ Not Implemented | `userByPhone(phoneNumber)` | Identity | ❌ |
| **DEACTIVATE** | ❌ Not Implemented | `deactivateUser(id)` | Identity | ❌ |
| **ACTIVATE** | ❌ Not Implemented | `activateUser(id)` | Identity | ❌ |
| **UPDATE ROLES** | ❌ Not Implemented | `updateUserRoles(userId, roles)` | Identity | ❌ |

**User Coverage: 10/15 operations (67%)**

---

### 3.3 Organizers Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **READ (Profile)** | `useOrganizerProfileAdmin()` | `organizerProfile(id)` | Identity | ✅ |
| **READ (By User)** | `useOrganizerProfileByUserIdAdmin()` | `organizerProfileByUserId(userId)` | Identity | ✅ |
| **READ (Applications)** | `useOrganizerApplicationsAdmin()` | `organizerApplicationsOffsetPagination(...)` | Identity | ✅ |
| **READ (Organizations)** | `useOrganizationsAdmin()` | `organizationsOffsetPagination(...)` | Identity | ✅ |
| **APPROVE** | `useApproveOrganizerAdmin()` | `approveOrganizer(profileId)` | Identity | ✅ |
| **REJECT** | `useRejectOrganizerAdmin()` | `rejectOrganizer(profileId, reason)` | Identity | ✅ |
| **SUSPEND** | `useSuspendOrganizerAdmin()` | `suspendOrganizer(profileId, reason)` | Identity | ✅ |
| **UNSUSPEND** | ❌ Not Implemented | `unsuspendOrganizer(profileId)` | Identity | ❌ |
| **REQUEST CHANGES** | ❌ Not Implemented | `requestOrganizerChanges(profileId, reason)` | Identity | ❌ |
| **VERIFY BUSINESS** | ❌ Not Implemented | `verifyOrganizerBusiness(profileId)` | Identity | ❌ |
| **VERIFY DOCUMENTS** | ❌ Not Implemented | `verifyOrganizerDocuments(profileId)` | Identity | ❌ |
| **VERIFY BANK** | ❌ Not Implemented | `verifyOrganizerBankAccount(profileId)` | Identity | ❌ |
| **REACTIVATE** | ❌ Not Implemented | `reactivateOrganizer(profileId)` | Identity | ❌ |
| **STATISTICS** | ❌ Not Implemented | `organizerStatistics(organizerId)` | Identity | ❌ |

**Organizer Coverage: 7/14 operations (50%)**

---

### 3.4 Tickets Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **READ (Single)** | `useTicket()` | `ticket(id)` | Booking | ✅ |
| **READ (By Number)** | `useTicketByNumber()` | `ticketByNumber(ticketNumber)` | Booking | ✅ |
| **READ (By Event)** | `useTicketsByEventAdmin()` | `ticketsByEventOffsetPagination(...)` | Booking | ✅ |
| **SEARCH** | `useSearchTicketsAdmin()` | `searchTicketsOffsetPagination(...)` | Booking | ✅ |
| **READ (Stats)** | `useTicketStatsAdmin()` | `ticketStats(eventId)` | Booking | ✅ |
| **VALIDATE** | `useValidateTicketAdmin()` | `validateTicket(ticketNumber)` | Booking | ✅ |
| **USE** | `useUseTicketAdmin()` | `useTicket(ticketNumber)` | Booking | ✅ |
| **CANCEL** | `useCancelTicketAdmin()` | `cancelTicket(ticketNumber, reason)` | Booking | ✅ |
| **REFUND** | `useRefundTicketAdmin()` | `refundTicket(ticketNumber, reason)` | Booking | ✅ |
| **ADMIN UPDATE** | `useAdminUpdateTicketAdmin()` | `adminUpdateTicket(ticketId, input)` | Booking | ✅ |
| **REGENERATE QR** | `useRegenerateTicketQrCodeAdmin()` | `regenerateTicketQrCode(ticketId)` | Booking | ✅ |
| **BULK CANCEL** | `useBulkCancelTicketsAdmin()` | `bulkCancelTickets(ticketIds, reason)` | Booking | ✅ |
| **BY BUYER** | ❌ Not Implemented | `ticketsByBuyerOffsetPagination(...)` | Booking | ❌ |
| **BY ORGANIZER** | ❌ Not Implemented | `ticketsByOrganizerOffsetPagination(...)` | Booking | ❌ |
| **COUNTS** | ❌ Not Implemented | `ticketCountByEvent(eventId)` | Booking | ❌ |

**Ticket Coverage: 12/15 operations (80%)**

---

### 3.5 Payments/Transactions Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **READ (Single)** | `useTransaction()` | `paymentAttempt(id)` | Booking | ✅ |
| **READ (List)** | `useTransactionsOffset()` | N/A (custom query) | Booking | ✅ |
| **READ (By Status)** | `useTransactionsByStatus()` | `paymentAttemptsByStatus(status)` | Booking | ✅ |
| **READ (Pending)** | ❌ Not Implemented | `pendingPaymentAttempts` | Booking | ❌ |
| **READ (By Event)** | ❌ Not Implemented | `paymentAttemptsByEvent(eventId)` | Booking | ❌ |
| **READ (By Buyer)** | ❌ Not Implemented | `paymentAttemptsByBuyer(buyerId)` | Booking | ❌ |
| **READ (Stats)** | ❌ Not Implemented | `transactionStats(eventId, organizerId)` | Booking | ❌ |
| **RETRY** | `useRetryFailedTransactionAdmin()` | `retryPaymentAttempt(depositId)` | Booking | ✅ |
| **VERIFY** | ❌ Not Implemented | `verifyPaymentWithGateway(depositId)` | Booking | ❌ |
| **CANCEL** | ❌ Not Implemented | `cancelPaymentAttempt(depositId, reason)` | Booking | ❌ |
| **ADD NOTE** | ❌ Not Implemented | `addPaymentAttemptNote(depositId, note)` | Booking | ❌ |
| **REVIEW STATUS** | ❌ Not Implemented | `setPaymentAttemptReviewStatus(...)` | Booking | ❌ |

**Transaction Coverage: 4/12 operations (33%)**

---

### 3.6 Refunds Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **CREATE (User)** | `useCreateUserRefundRequestAdmin()` | `createUserRefundRequest(input)` | Booking | ✅ |
| **CREATE (Admin)** | `useCreateAdminRefundRequestAdmin()` | `createAdminRefundRequest(...)` | Booking | ✅ |
| **READ (Single)** | `useRefundRequest()` | `refundRequest(id)` | Booking | ✅ |
| **READ (List)** | `useRefundRequestsPaginatedAdmin()` | `refundRequestsOffsetPagination(...)` | Booking | ✅ |
| **READ (Pending)** | `usePendingRefundRequestsAdmin()` | `pendingRefundRequestsOffsetPagination(...)` | Booking | ✅ |
| **READ (By Event)** | ❌ Not Implemented | `refundRequestsByEventOffsetPagination(...)` | Booking | ❌ |
| **READ (By Buyer)** | ❌ Not Implemented | `refundRequestsByBuyerOffsetPagination(...)` | Booking | ❌ |
| **APPROVE** | `useApproveRefundRequestAdmin()` | `approveRefundRequest(...)` | Booking | ✅ |
| **REJECT** | `useRejectRefundRequestAdmin()` | `rejectRefundRequest(...)` | Booking | ✅ |
| **PROCESS** | `useProcessRefundRequestAdmin()` | `processRefundRequest(refundRequestId)` | Booking | ✅ |
| **CANCEL** | `useCancelRefundRequestAdmin()` | `cancelRefundRequest(...)` | Booking | ✅ |
| **BULK APPROVE** | ❌ Not Implemented | `bulkApproveRefunds(refundRequestIds)` | Booking | ❌ |
| **ELIGIBILITY** | ❌ Not Implemented | `isTicketEligibleForRefund(ticketId)` | Booking | ❌ |
| **CALCULATE** | ❌ Not Implemented | `calculateRefundAmount(ticketId)` | Booking | ❌ |
| **SUMMARY** | ❌ Not Implemented | `eventRefundSummary(eventId)` | Booking | ❌ |

**Refund Coverage: 9/15 operations (60%)**

---

### 3.7 Payouts Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **READ (Single)** | `usePayoutRequest()` | `payoutRequest(id)` | Booking | ✅ |
| **READ (List)** | `usePayoutRequestsPaginatedAdmin()` | `payoutRequestsOffsetPagination(...)` | Booking | ✅ |
| **READ (By Organizer)** | `usePayoutRequestsByOrganizerAdmin()` | `payoutRequestsByOrganizerOffsetPagination(...)` | Booking | ✅ |
| **READ (Pending)** | ❌ Not Implemented | `pendingPayoutRequestsOffsetPagination(...)` | Booking | ❌ |
| **READ (Failed)** | ❌ Not Implemented | `failedPayoutRequestsOffsetPagination(...)` | Booking | ❌ |
| **READ (For Review)** | ❌ Not Implemented | `payoutRequestsForReviewOffsetPagination(...)` | Booking | ❌ |
| **READ (Stuck)** | ❌ Not Implemented | `stuckPayoutRequestsOffsetPagination(...)` | Booking | ❌ |
| **READ (Stats)** | ❌ Not Implemented | `payoutRequestStats(organizerId)` | Booking | ❌ |
| **APPROVE** | `useApprovePayoutRequestAdmin()` | `approvePayoutRequest(...)` | Booking | ✅ |
| **REJECT** | `useRejectPayoutRequestAdmin()` | `rejectPayoutRequest(...)` | Booking | ✅ |
| **PROCESS** | `useProcessPayoutRequestAdmin()` | `processPayoutRequest(payoutRequestId)` | Booking | ✅ |
| **COMPLETE** | ❌ Not Implemented | `completePayoutRequest(...)` | Booking | ❌ |
| **CANCEL** | ❌ Not Implemented | `cancelPayoutRequest(...)` | Booking | ❌ |
| **RETRY** | ❌ Not Implemented | `retryPayoutRequest(payoutRequestId)` | Booking | ❌ |
| **RESUME** | ❌ Not Implemented | `resumePayoutRequest(payoutRequestId)` | Booking | ❌ |
| **MARK REVIEW** | ❌ Not Implemented | `markPayoutForReview(...)` | Booking | ❌ |
| **RESOLVE ISSUE** | ❌ Not Implemented | `resolvePayoutIssue(...)` | Booking | ❌ |
| **BULK RETRY** | ❌ Not Implemented | `bulkRetryFailedPayouts(payoutRequestIds)` | Booking | ❌ |
| **ESCALATE** | ❌ Not Implemented | `escalatePayoutRequest(...)` | Booking | ❌ |

**Payout Coverage: 6/19 operations (32%)**

---

### 3.8 Escrow Accounts Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **CREATE** | `useCreateEscrowAccountAdmin()` | `createEscrowAccount(input)` | Booking | ✅ |
| **READ (Single)** | `useEscrowAccount()` | `escrowAccount(id)` | Booking | ✅ |
| **READ (By Event)** | `useEscrowAccountByEventAdmin()` | `escrowAccountByEvent(eventId)` | Booking | ✅ |
| **READ (List)** | `useEscrowAccountsAdmin()` | `escrowAccountsOffsetPagination(...)` | Booking | ✅ |
| **READ (Balance)** | ❌ Not Implemented | `escrowAccountBalance(accountId)` | Booking | ❌ |
| **READ (Summary)** | ❌ Not Implemented | `accountSummary(accountId)` | Booking | ❌ |
| **READ (Platform)** | ❌ Not Implemented | `platformSummary` | Booking | ❌ |
| **UPDATE STATUS** | `useUpdateEscrowAccountStatusAdmin()` | `updateEscrowAccountStatus(...)` | Booking | ✅ |
| **LOCK** | `useLockEscrowAccountAdmin()` | `lockEscrowAccount(...)` | Booking | ✅ |
| **UNLOCK** | `useUnlockEscrowAccountAdmin()` | `unlockEscrowAccount(...)` | Booking | ✅ |
| **PAYOUT ELIGIBLE** | ❌ Not Implemented | `markPayoutEligible(accountId)` | Booking | ❌ |
| **CLOSE** | ❌ Not Implemented | `closeEscrowAccount(...)` | Booking | ❌ |

**Escrow Coverage: 7/12 operations (58%)**

---

### 3.9 Categories Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **CREATE** | `CREATE_EVENT_CATEGORY` (inline) | `createEventCategory(input)` | Catalog | ✅ |
| **READ (Single)** | `GET_EVENT_CATEGORY` | `eventCategory(id)` | Catalog | ✅ |
| **READ (List)** | `GET_EVENT_CATEGORIES` | `eventCategoriesOffsetPagination(...)` | Catalog | ✅ |
| **SEARCH** | `useSearchEventCategoriesAdmin()` | `searchEventCategoriesOffsetPagination(...)` | Catalog | ✅ |
| **UPDATE** | `UPDATE_EVENT_CATEGORY` (inline) | `updateEventCategory(id, input)` | Catalog | ✅ |
| **DELETE** | `DELETE_EVENT_CATEGORY` (inline) | `deleteEventCategory(id)` | Catalog | ✅ |
| **ACTIVATE** | `useActivateEventCategoryAdmin()` | `activateEventCategory(id)` | Catalog | ✅ |
| **DEACTIVATE** | `useDeactivateEventCategoryAdmin()` | `deactivateEventCategory(id)` | Catalog | ✅ |

**Category Coverage: 8/8 operations (100%)**

---

### 3.10 Bank Accounts Entity

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **CREATE** | `useCreateBankAccountAdmin()` | `createBankAccount(input)` | Booking | ✅ |
| **READ (Single)** | `useBankAccount()` | `bankAccount(id)` | Booking | ✅ |
| **READ (By Organizer)** | `useBankAccountsByOrganizerAdmin()` | `bankAccountsByOrganizer(organizerId)` | Booking | ✅ |
| **READ (Default)** | `useDefaultBankAccountAdmin()` | `defaultBankAccount(organizerId)` | Booking | ✅ |
| **UPDATE** | `useUpdateBankAccountAdmin()` | `updateBankAccount(id, input)` | Booking | ✅ |
| **DELETE** | `useDeleteBankAccountAdmin()` | `deleteBankAccount(id)` | Booking | ✅ |
| **SET DEFAULT** | `useSetDefaultBankAccountAdmin()` | `setDefaultBankAccount(id)` | Booking | ✅ |
| **VERIFY** | `useVerifyBankAccountAdmin()` | `verifyBankAccount(id)` | Booking | ✅ |

**Bank Account Coverage: 8/8 operations (100%)**

---

### 3.11 Chargebacks Entity (NOT IMPLEMENTED)

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **READ (Single)** | ❌ Not Implemented | `chargeback(id)` | Booking | ❌ |
| **READ (List)** | ❌ Not Implemented | `chargebacksOffsetPagination(...)` | Booking | ❌ |
| **READ (Pending)** | ❌ Not Implemented | `pendingChargebacks` | Booking | ❌ |
| **READ (Recovery)** | ❌ Not Implemented | `chargebacksPendingRecovery` | Booking | ❌ |
| **READ (Stats)** | ❌ Not Implemented | `chargebackStats(...)` | Booking | ❌ |
| **RECEIVE** | ❌ Not Implemented | `receiveChargeback(input)` | Booking | ❌ |
| **REVIEW** | ❌ Not Implemented | `startChargebackReview(...)` | Booking | ❌ |
| **ACCEPT** | ❌ Not Implemented | `acceptChargeback(...)` | Booking | ❌ |
| **DISPUTE** | ❌ Not Implemented | `disputeChargeback(...)` | Booking | ❌ |
| **OUTCOME** | ❌ Not Implemented | `recordChargebackOutcome(...)` | Booking | ❌ |
| **RECOVER** | ❌ Not Implemented | `recoverChargebackFunds(...)` | Booking | ❌ |

**Chargeback Coverage: 0/11 operations (0%)** ⚠️

---

### 3.12 Reconciliation Entity (NOT IMPLEMENTED)

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **READ (Run)** | ❌ Not Implemented | `reconciliationRun(id)` | Booking | ❌ |
| **READ (List)** | ❌ Not Implemented | `reconciliationRunsOffsetPagination(...)` | Booking | ❌ |
| **READ (Requiring Review)** | ❌ Not Implemented | `reconciliationRunsRequiringReview` | Booking | ❌ |
| **READ (Summary)** | ❌ Not Implemented | `reconciliationSummary(...)` | Booking | ❌ |
| **START** | ❌ Not Implemented | `startReconciliation(input)` | Booking | ❌ |
| **RESOLVE ITEM** | ❌ Not Implemented | `resolveReconciliationItem(...)` | Booking | ❌ |
| **COMPLETE** | ❌ Not Implemented | `completeReconciliation(...)` | Booking | ❌ |
| **FAIL** | ❌ Not Implemented | `failReconciliation(...)` | Booking | ❌ |

**Reconciliation Coverage: 0/8 operations (0%)** ⚠️

---

### 3.13 Journal Entries Entity (NOT IMPLEMENTED)

| Operation | Frontend Implementation | Backend GraphQL | Service | Status |
|-----------|------------------------|-----------------|---------|--------|
| **READ (Single)** | ❌ Not Implemented | `journalEntry(id)` | Booking | ❌ |
| **READ (List)** | ❌ Not Implemented | `journalEntriesOffsetPagination(...)` | Booking | ❌ |
| **READ (Pending)** | ❌ Not Implemented | `pendingJournalEntriesOffsetPagination(...)` | Booking | ❌ |
| **READ (By Account)** | ❌ Not Implemented | `journalEntriesByAccountCode(...)` | Booking | ❌ |
| **CREATE** | ❌ Not Implemented | `createJournalEntry(input)` | Booking | ❌ |
| **POST** | ❌ Not Implemented | `postJournalEntry(id)` | Booking | ❌ |
| **REVERSE** | ❌ Not Implemented | `reverseJournalEntry(...)` | Booking | ❌ |

**Journal Entry Coverage: 0/7 operations (0%)** ⚠️

---

## 4. Design Patterns

### 4.1 Frontend Design Patterns

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DESIGN PATTERNS IMPLEMENTED                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. PROVIDER PATTERN                                                        │
│  ─────────────────                                                          │
│  • KeycloakProvider → Authentication state                                  │
│  • ApolloProvider → GraphQL client and cache                               │
│  • ThemeProvider → Dark/light mode                                         │
│  • QueryClientProvider → React Query (optional)                            │
│                                                                             │
│  2. CUSTOM HOOKS PATTERN                                                    │
│  ──────────────────────                                                     │
│  • useEventsAdmin() → Encapsulates GraphQL query + state                   │
│  • useMyPermissions() → Permission checking                                │
│  • useTokenSync() → JWT synchronization                                    │
│                                                                             │
│  3. COMPOUND COMPONENT PATTERN (Radix UI)                                   │
│  ─────────────────────────────────────────                                  │
│  • <Dialog.Root><Dialog.Trigger><Dialog.Content>                           │
│  • <Tabs.Root><Tabs.List><Tabs.Trigger><Tabs.Content>                      │
│                                                                             │
│  4. RENDER PROPS PATTERN                                                    │
│  ───────────────────────                                                    │
│  • <ProtectedRoute roles={[...]}>{children}</ProtectedRoute>               │
│  • <PermissionGate permission="events.approve">{action}</PermissionGate>   │
│                                                                             │
│  5. CONTAINER/PRESENTATION PATTERN                                          │
│  ─────────────────────────────────                                          │
│  • Page components (containers) → Data fetching                            │
│  • UI components (presentation) → Display only                             │
│                                                                             │
│  6. ERROR BOUNDARY PATTERN                                                  │
│  ─────────────────────────                                                  │
│  • <ErrorBoundary> wraps all routes                                        │
│  • <SectionErrorBoundary sectionName="..."> for granular recovery          │
│                                                                             │
│  7. MEMOIZATION PATTERN                                                     │
│  ───────────────────────                                                    │
│  • React.memo() for StatCard, Badge components                             │
│  • useMemo() for derived data calculations                                 │
│  • useCallback() for stable handler references                             │
│                                                                             │
│  8. SKELETON LOADING PATTERN                                                │
│  ───────────────────────────                                                │
│  • <Skeleton> placeholders during data fetch                               │
│  • Maintains layout stability (prevents CLS)                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Backend Design Patterns

| Pattern | Implementation | Service |
|---------|----------------|---------|
| **Repository Pattern** | Reactive MongoDB repositories | All |
| **Service Layer Pattern** | Business logic encapsulation | All |
| **Event Sourcing** | Spring Modulith events | All |
| **CQRS (Partial)** | Cursor/Offset pagination separation | All |
| **Saga Pattern** | Escrow → Payout workflow | Booking |
| **Domain Events** | `TicketPurchasedEvent`, `EventApprovedEvent` | All |
| **Factory Pattern** | `MobileMoneyGatewayFactory` | Booking |
| **Strategy Pattern** | Payment gateway selection | Booking |
| **Decorator Pattern** | `@auth` directive wrapping | All (GraphQL) |

### 4.3 Data Flow Pattern

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            DATA FLOW PATTERN                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  User Action                                                                │
│       │                                                                     │
│       ▼                                                                     │
│  Component (useState for local)                                             │
│       │                                                                     │
│       ▼                                                                     │
│  Custom Hook (useXxxAdmin)                                                  │
│       │                                                                     │
│       ▼                                                                     │
│  Apollo Client (useMutation/useQuery)                                       │
│       │                                                                     │
│       ├──────────────────────┐                                              │
│       ▼                      ▼                                              │
│  InMemoryCache          HTTP Link                                           │
│  (optimistic UI)        (with JWT header)                                   │
│                              │                                              │
│                              ▼                                              │
│                      Apollo Router (Federation)                             │
│                              │                                              │
│           ┌──────────────────┼──────────────────┐                          │
│           ▼                  ▼                  ▼                          │
│      Catalog Svc        Booking Svc       Identity Svc                     │
│           │                  │                  │                          │
│           ▼                  ▼                  ▼                          │
│      DGS Resolver       DGS Resolver       DGS Resolver                    │
│      (@auth check)      (@auth check)      (@auth check)                   │
│           │                  │                  │                          │
│           ▼                  ▼                  ▼                          │
│      Service Layer      Service Layer      Service Layer                   │
│           │                  │                  │                          │
│           ▼                  ▼                  ▼                          │
│      MongoDB (Reactive) MongoDB (Reactive) MongoDB (Reactive)             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. OWASP Top 10 Compliance Analysis

### 5.1 Compliance Matrix

| OWASP Category | Status | Implementation | Gaps |
|----------------|--------|----------------|------|
| **A01:2021 - Broken Access Control** | ⚠️ Partial | `@auth` directives, `ProtectedRoute`, `PermissionGate` | Missing field-level ABAC on some queries |
| **A02:2021 - Cryptographic Failures** | ✅ Compliant | TLS everywhere, JWT signed, no sensitive data in logs | - |
| **A03:2021 - Injection** | ✅ Compliant | GraphQL parameterized, MongoDB parameterized | - |
| **A04:2021 - Insecure Design** | ⚠️ Partial | Rate limiting, circuit breakers | Missing client-side validation |
| **A05:2021 - Security Misconfiguration** | ✅ Compliant | CORS configured, introspection disabled in prod | - |
| **A06:2021 - Vulnerable Components** | ⚠️ Unknown | Dependencies not audited recently | Run `npm audit` |
| **A07:2021 - Identification Failures** | ✅ Compliant | Keycloak + PKCE, token refresh, backchannel logout | - |
| **A08:2021 - Software/Data Integrity** | ⚠️ Partial | No CSP headers visible | Add Content-Security-Policy |
| **A09:2021 - Security Logging** | ⚠️ Partial | Backend logging exists | Frontend error reporting limited |
| **A10:2021 - SSRF** | ✅ Compliant | No user-controlled URLs in backend calls | - |

### 5.2 Detailed Analysis

#### A01:2021 - Broken Access Control

**✅ Implemented:**
```typescript
// Route-level protection
<ProtectedRoute roles={[KEYCLOAK_ROLES.ADMIN, KEYCLOAK_ROLES.SUPER_ADMIN]}>
  {children}
</ProtectedRoute>

// Action-level protection
<PermissionGate permission="events.approve">
  <ApproveButton />
</PermissionGate>

// Backend @auth directive
approveEvent(eventId: ID!): EventMutationResponse! @auth(requires: ADMIN)
```

**❌ Gaps:**
- Some queries don't have `@auth` directives (e.g., `ticket(id)` is public)
- No ABAC (Attribute-Based Access Control) for data filtering
- Missing row-level security (organizer can't query only their data)

**Recommendation:**
```graphql
# Add data ownership checks
ticketsByBuyerOffsetPagination(buyerId: ID!): TicketConnection!
  @auth(requires: AUTHENTICATED)
  @ownerOnly(field: "buyerId")  # Custom directive needed
```

---

#### A04:2021 - Insecure Design

**❌ Critical Gap: No Client-Side Validation**

Current implementation relies 100% on backend validation. This causes:
1. Poor UX (form errors only shown after submission)
2. Unnecessary API calls for invalid data
3. No instant feedback to users

**Recommendation:**
```typescript
// Add Zod schema validation
import { z } from 'zod';

const CreateEventSchema = z.object({
  title: z.string().min(3).max(200),
  description: z.string().min(10).max(5000),
  startDate: z.date().min(new Date()),
  endDate: z.date(),
  capacity: z.number().int().positive(),
}).refine(data => data.endDate > data.startDate, {
  message: "End date must be after start date"
});

// Use with react-hook-form
const { register, handleSubmit, formState: { errors } } = useForm({
  resolver: zodResolver(CreateEventSchema)
});
```

---

#### A06:2021 - Vulnerable Components

**⚠️ Action Required:**
```bash
# Run security audit
cd frontend/web/apps/admin
npm audit

# Check for known vulnerabilities
npx better-npm-audit audit
```

---

#### A08:2021 - Software/Data Integrity

**❌ Gap: Missing Content Security Policy**

**Recommendation:** Add to `next.config.js`:
```javascript
async headers() {
  return [
    {
      source: '/(.*)',
      headers: [
        {
          key: 'Content-Security-Policy',
          value: "default-src 'self'; script-src 'self' 'unsafe-eval' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'; connect-src 'self' http://localhost:4001 http://localhost:8084;"
        },
        {
          key: 'X-Frame-Options',
          value: 'DENY'
        },
        {
          key: 'X-Content-Type-Options',
          value: 'nosniff'
        }
      ]
    }
  ];
}
```

---

#### A09:2021 - Security Logging and Monitoring

**❌ Gap: Limited Frontend Error Tracking**

**Recommendation:** Add error boundary reporting:
```typescript
// ErrorBoundary.tsx
componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
  // Send to monitoring service (Sentry, LogRocket, etc.)
  reportError({
    error: error.message,
    stack: error.stack,
    componentStack: errorInfo.componentStack,
    userId: keycloak.subject,
    timestamp: new Date().toISOString()
  });
}
```

---

## 6. Gap Analysis: Missing Features

### 6.1 Critical Missing Features (High Priority)

| Feature | Backend Ready | Frontend Status | Impact |
|---------|---------------|-----------------|--------|
| **Chargeback Management** | ✅ Full API | ❌ Not Started | Financial risk |
| **Reconciliation Dashboard** | ✅ Full API | ❌ Not Started | Audit compliance |
| **Journal Entry Viewer** | ✅ Full API | ❌ Not Started | Accounting audit |
| **Overdue Event Approvals** | ✅ Query exists | ❌ Not Implemented | SLA breach |
| **Event Data Export** | ✅ Mutation exists | ❌ Not Implemented | Reporting |
| **User Statistics** | ✅ Query exists | ❌ Not Implemented | Analytics |
| **Transaction Statistics** | ✅ Query exists | ❌ Not Implemented | Finance dashboard |

### 6.2 Medium Priority Missing Features

| Feature | Backend Ready | Frontend Status | Notes |
|---------|---------------|-----------------|-------|
| Platform Summary Dashboard | ✅ | ❌ | `platformSummary` query |
| Escrow Balance View | ✅ | ❌ | `escrowAccountBalance` query |
| Payout Issue Resolution | ✅ | ❌ | `resolvePayoutIssue` mutation |
| Bulk Payout Retry | ✅ | ❌ | `bulkRetryFailedPayouts` mutation |
| Event Duplication | ✅ | ❌ | `duplicateEvent` mutation |
| Organizer Verification Workflow | ✅ | ❌ | Multiple verify mutations |
| Payment Verification | ✅ | ❌ | `verifyPaymentWithGateway` mutation |
| Notification Management | ✅ | ❌ | Full notification API unused |

### 6.3 Low Priority Missing Features

| Feature | Backend Ready | Frontend Status |
|---------|---------------|-----------------|
| User search by email/phone | ✅ | ❌ |
| Promo code management | ✅ | ❌ |
| Location CRUD | ✅ | ❌ |
| Province/City management | ✅ | ❌ |
| Chart of Accounts viewer | ✅ | ❌ |
| Trial Balance report | ✅ | ❌ |

### 6.4 Feature Coverage Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         FEATURE COVERAGE BY ENTITY                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Events          ████████████████░░░░  79%                                 │
│  Categories      ████████████████████  100%                                │
│  Bank Accounts   ████████████████████  100%                                │
│  Tickets         ████████████████░░░░  80%                                 │
│  Users           █████████████░░░░░░░  67%                                 │
│  Refunds         ████████████░░░░░░░░  60%                                 │
│  Escrow          ███████████░░░░░░░░░  58%                                 │
│  Organizers      ██████████░░░░░░░░░░  50%                                 │
│  Transactions    ██████░░░░░░░░░░░░░░  33%                                 │
│  Payouts         ██████░░░░░░░░░░░░░░  32%                                 │
│  Chargebacks     ░░░░░░░░░░░░░░░░░░░░  0%  ⚠️                             │
│  Reconciliation  ░░░░░░░░░░░░░░░░░░░░  0%  ⚠️                             │
│  Journal Entries ░░░░░░░░░░░░░░░░░░░░  0%  ⚠️                             │
│                                                                             │
│  OVERALL: ~45% of backend capabilities utilized                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Redundancies & Technical Debt

### 7.1 Code Redundancies

| Issue | Location | Impact | Recommendation |
|-------|----------|--------|----------------|
| **Inline GraphQL in Categories page** | `categories/page.tsx` | Inconsistent with shared library pattern | Move to shared library |
| **Duplicate stat card rendering** | Multiple pages | Code duplication | Create `<StatsSection>` component |
| **Permission checks duplicated** | Multiple files | Maintenance burden | Centralize in `route-permissions.ts` |
| **Similar table configurations** | All list pages | Repetitive code | Create table config factory |

### 7.2 Unused Code

```typescript
// Found in use-lifecycle-actions.ts
export function useResumeStuckTransaction() {
  // TODO: Implement when backend ready
  return { mutate: async () => {}, loading: false };
}

export function useMarkTransactionForReview() {
  // TODO: Implement when backend ready
  return { mutate: async () => {}, loading: false };
}

// These stubs should be removed or implemented
```

### 7.3 Technical Debt

| Debt Item | Severity | Estimated Effort |
|-----------|----------|------------------|
| No client-side form validation | HIGH | 3-5 days |
| Missing TypeScript strict mode | MEDIUM | 2-3 days |
| No unit tests | HIGH | 5-10 days |
| No E2E tests | MEDIUM | 3-5 days |
| Inconsistent error handling | MEDIUM | 2-3 days |
| Missing loading states on some mutations | LOW | 1-2 days |
| No accessibility audit | MEDIUM | 2-3 days |

### 7.4 Performance Concerns

| Issue | Impact | Solution |
|-------|--------|----------|
| No query debouncing on search | Excessive API calls | Add `useDebouncedValue` hook |
| No query caching strategy documented | Inconsistent cache behavior | Document Apollo cache policies |
| Large bundle size (unverified) | Slow initial load | Run `next build --analyze` |
| No image optimization | Slow page loads | Use Next.js `<Image>` component |

---

## 8. Recommendations

### 8.1 Immediate Actions (1-2 weeks)

1. **Add Form Validation** (OWASP A04)
   - Install Zod + react-hook-form
   - Add schemas for all input types
   - Show inline validation errors

2. **Security Headers** (OWASP A08)
   - Add CSP, X-Frame-Options, X-Content-Type-Options
   - Configure in `next.config.js`

3. **Error Monitoring** (OWASP A09)
   - Integrate Sentry or similar
   - Add error boundary reporting

### 8.2 Short-term (1-2 months)

1. **Implement Critical Missing Features**
   - Chargeback management UI
   - Reconciliation dashboard
   - Journal entry viewer

2. **Add Testing**
   - Unit tests with Jest/Vitest
   - Component tests with Testing Library
   - E2E tests with Playwright

3. **Code Cleanup**
   - Remove stub functions
   - Consolidate inline GraphQL
   - Extract shared components

### 8.3 Long-term (3-6 months)

1. **Full Feature Parity**
   - Implement all remaining backend features
   - Target 90%+ coverage

2. **Performance Optimization**
   - Bundle analysis and splitting
   - Query optimization
   - Implement virtual scrolling for large lists

3. **Accessibility Audit**
   - WCAG 2.1 AA compliance
   - Keyboard navigation
   - Screen reader testing

---

## Appendix A: GraphQL Operation Coverage Matrix

| Entity | Backend Queries | Frontend Queries | Backend Mutations | Frontend Mutations |
|--------|-----------------|------------------|-------------------|-------------------|
| Event | 40 | 15 | 20 | 12 |
| User | 10 | 5 | 10 | 8 |
| Organizer | 15 | 7 | 15 | 6 |
| Ticket | 15 | 8 | 8 | 8 |
| Transaction | 15 | 4 | 10 | 1 |
| Refund | 15 | 5 | 7 | 5 |
| Payout | 20 | 3 | 15 | 3 |
| Escrow | 12 | 4 | 6 | 4 |
| Category | 8 | 4 | 5 | 4 |
| Chargeback | 11 | 0 | 6 | 0 |
| Reconciliation | 8 | 0 | 4 | 0 |
| Journal | 7 | 0 | 3 | 0 |
| **TOTAL** | **176** | **~55** | **109** | **~51** |

---

## Appendix B: File Structure Summary

```
frontend/web/apps/admin/
├── src/
│   ├── app/                          # Next.js App Router
│   │   ├── (dashboard)/              # Protected routes (17 pages)
│   │   ├── api/auth/                 # Auth API routes (3 files)
│   │   └── login/                    # Login page
│   ├── components/                   # React components (40+ files)
│   │   ├── layout/                   # Layout components
│   │   ├── dashboard/                # Dashboard widgets
│   │   ├── events/                   # Event components
│   │   ├── users/                    # User components
│   │   └── ui/                       # Shared UI components
│   ├── graphql/                      # GraphQL re-exports
│   ├── hooks/                        # Custom hooks (5 files)
│   ├── lib/                          # Utilities (10+ files)
│   └── types/                        # TypeScript types
├── public/                           # Static assets
└── package.json                      # Dependencies
```

---

**Report End**

*This report should be reviewed quarterly and updated as features are implemented.*
