# Hooks Implementation Plan

## Overview

This plan outlines the implementation of React hooks for the remaining services that don't have hooks yet. All hooks will follow the established pattern from `use-users.ts`, `use-events.ts`, and `use-tickets.ts`.

## Architecture Pattern

```
Component → Hook → Service → GraphQL API → Backend
```

Each hook will:
- Use `useState` for data, loading, and error state
- Use `useCallback` for memoized service methods
- Follow consistent error handling patterns
- Return typed data from generated schema types
- Support both query and mutation operations

## Current Status

### ✅ Completed Hooks
- `usePermissions.ts` - Permission checking (updated to use PermissionsService)
- `use-users.ts` - User management (includes `useUsers` and `useCurrentUser`)
- `use-events.ts` - Event management (includes `useEvents` and `useEventStatistics`)
- `use-tickets.ts` - Ticket management

### ⏳ Remaining Hooks to Implement

1. **use-transactions.ts** - Transaction management
2. **use-payouts.ts** - Payout request management
3. **use-refunds.ts** - Refund request management
4. **use-analytics.ts** - Analytics and statistics
5. **use-locations.ts** - Location management
6. **use-event-categories.ts** - Event category management
7. **use-system-health.ts** - System health monitoring
8. **use-account-management.ts** - Account management

## Implementation Details

### 1. use-transactions.ts

**File**: `pml.tickets/apps/admin/src/lib/hooks/use-transactions.ts`

**Hooks to Create**:
- `useTransactions()` - Main hook for transaction operations
- `useTransactionById()` - Hook for fetching single transaction
- `useAccountBalance()` - Hook for account balance queries
- `useTransactionStatistics()` - Hook for transaction statistics

**Key Methods**:
- `fetchTransactionsByDateRange(startDate, endDate)`
- `getTransactionById(transactionId)`
- `getTransactionsByAccountId(accountId)`
- `getTransactionsByEventId(eventId)`
- `getAccountBalance(accountId)`
- `getAccountSummary(accountId, fromDate, toDate)`
- `getPlatformSummary(fromDate, toDate)`
- `getTransactionStatistics(startDate, endDate)`
- `updateTransactionStatus(transactionId, status, reason)`
- `cancelTransaction(transactionId, reason)`
- `processReversal(originalTransactionId, reason, userId, correlationId, metadata)`

**Types to Import**:
```typescript
import type {
  FinancialTransaction,
  AccountSummary,
  PlatformSummary,
  UpdateTransactionStatusMutationResponse,
  CancelTransactionMutationResponse,
  ProcessReversalMutationResponse,
} from '@pml.tickets/shared/types/graphql/schema-types';
```

---

### 2. use-payouts.ts

**File**: `pml.tickets/apps/admin/src/lib/hooks/use-payouts.ts`

**Hooks to Create**:
- `usePayouts()` - Main hook for payout request operations
- `usePayoutRequestById()` - Hook for fetching single payout request
- `usePendingPayouts()` - Hook for pending payout requests

**Key Methods**:
- `fetchPayoutRequests(filter, pagination)`
- `getPayoutRequestById(id)`
- `getPayoutRequestByRequestId(requestId)`
- `getPayoutRequestsByEvent(eventId)`
- `getPayoutRequestsByOrganizer(organizerId)`
- `getPayoutRequestsByStatus(status)`
- `getPendingPayoutRequests()`
- `getPayoutRequestCount()`
- `createPayoutRequest(organizerId, amount, currency, payoutMethod, payoutDetails)`
- `updatePayoutRequest(payoutRequestId, input)`
- `approvePayoutRequest(payoutRequestId, reviewerId, comments)`
- `rejectPayoutRequest(payoutRequestId, reviewerId, reason)`
- `processPayoutRequest(payoutRequestId, processedBy)`
- `cancelPayoutRequest(payoutRequestId, cancelledBy)`
- `bulkApprovePayoutRequests(payoutRequestIds, reviewerId, comments)`
- `bulkProcessPayoutRequests(payoutRequestIds, processedBy)`

**Types to Import**:
```typescript
import type {
  PaginationDto,
  PayoutRequest,
  CreatePayoutRequestMutationResponse,
  UpdatePayoutRequestMutationResponse,
  ApprovePayoutRequestMutationResponse,
  RejectPayoutRequestMutationResponse,
  ProcessPayoutRequestMutationResponse,
  CancelPayoutRequestMutationResponse,
  BulkApprovePayoutRequestsMutationResponse,
  BulkProcessPayoutRequestsMutationResponse,
} from '@pml.tickets/shared/types/graphql/schema-types';
```

---

### 3. use-refunds.ts

**File**: `pml.tickets/apps/admin/src/lib/hooks/use-refunds.ts`

**Hooks to Create**:
- `useRefunds()` - Main hook for refund request operations
- `useRefundRequestById()` - Hook for fetching single refund request

**Key Methods**:
- `createUserRefundRequest(input)`
- `createAdminRefundRequest(ticketId, reason, processedBy, metadata)`
- `approveRefundRequest(refundRequestId, reviewerId, reviewComments)`
- `rejectRefundRequest(refundRequestId, reviewerId, rejectionReason)`
- `processRefundRequest(refundRequestId, processedBy)`
- `calculateRefundAmount(ticketId, refundReason)`
- `updateRefundRequestStatus(refundRequestId, status, updatedBy, comments)`

**Types to Import**:
```typescript
import type {
  CreateUserRefundRequestMutationResponse,
  CreateAdminRefundRequestMutationResponse,
  ApproveRefundRequestMutationResponse,
  RejectRefundRequestMutationResponse,
  ProcessRefundRequestMutationResponse,
  CalculateRefundAmountMutationResponse,
  UpdateRefundRequestStatusMutationResponse,
} from '@pml.tickets/shared/types/graphql/schema-types';
```

---

### 4. use-analytics.ts

**File**: `pml.tickets/apps/admin/src/lib/hooks/use-analytics.ts`

**Hooks to Create**:
- `useDashboardStats()` - Hook for dashboard statistics
- `useUserStats()` - Hook for user statistics
- `useEventStats()` - Hook for event statistics
- `useTicketStats()` - Hook for ticket statistics
- `usePayoutRequestStats()` - Hook for payout request statistics

**Key Methods**:
- `fetchDashboardStats()` - Returns all stats (userStats, eventStats, ticketStats, payoutRequestStats)
- `fetchUserStats()` - Returns user statistics only
- `fetchEventStats()` - Returns event statistics only
- `fetchTicketStats()` - Returns ticket statistics only
- `fetchPayoutRequestStats()` - Returns payout request statistics only

**Types to Import**:
```typescript
import type {
  UserStats,
  EventStats,
  TicketStats,
  PayoutRequestStats,
} from '@pml.tickets/shared/types/graphql/schema-types';
```

**Note**: This hook will likely be used frequently for dashboard views, so consider caching strategies.

---

### 5. use-locations.ts

**File**: `pml.tickets/apps/admin/src/lib/hooks/use-locations.ts`

**Hooks to Create**:
- `useLocations()` - Main hook for location operations

**Key Methods**:
- `createLocation(input)`
- `updateLocation(id, input)`
- `deleteLocation(id)`
- `activateLocation(id)`
- `deactivateLocation(id)`
- `updateLocationCapacity(id, capacity)`
- `addLocationFacility(id, facility)`
- `removeLocationFacility(id, facility)`

**Types to Import**:
```typescript
import type {
  Location,
  CreateLocationMutationResponse,
  UpdateLocationMutationResponse,
  DeleteLocationMutationResponse,
  ActivateLocationMutationResponse,
  DeactivateLocationMutationResponse,
  UpdateLocationCapacityMutationResponse,
  AddLocationFacilityMutationResponse,
  RemoveLocationFacilityMutationResponse,
} from '@pml.tickets/shared/types/graphql/schema-types';
```

---

### 6. use-event-categories.ts

**File**: `pml.tickets/apps/admin/src/lib/hooks/use-event-categories.ts`

**Hooks to Create**:
- `useEventCategories()` - Main hook for event category operations

**Key Methods**:
- `createEventCategory(input)`
- `updateEventCategory(id, input)`
- `deleteEventCategory(id)`

**Types to Import**:
```typescript
import type {
  EventCategory,
  CreateEventCategoryMutationResponse,
  UpdateEventCategoryMutationResponse,
  DeleteEventCategoryMutationResponse,
} from '@pml.tickets/shared/types/graphql/schema-types';
```

---

### 7. use-system-health.ts

**File**: `pml.tickets/apps/admin/src/lib/hooks/use-system-health.ts`

**Hooks to Create**:
- `useSystemHealth()` - Main hook for system health monitoring
- `useTransactionHealth()` - Hook for transaction health summary
- `useSystemAlerts()` - Hook for system alerts

**Key Methods**:
- `fetchSystemHealth()` - Returns all health data
- `fetchTransactionHealthSummary()` - Returns transaction health only
- `fetchSystemAlerts()` - Returns system alerts only
- `performHealthCheck()` - Triggers a health check

**Types to Import**:
```typescript
import type {
  TransactionHealthSummary,
  SystemAlerts,
  HealthCheckResult,
} from '@pml.tickets/shared/types/graphql/schema-types';
```

**Note**: This hook may benefit from polling/refetching strategies for real-time monitoring.

---

### 8. use-account-management.ts

**File**: `pml.tickets/apps/admin/src/lib/hooks/use-account-management.ts`

**Hooks to Create**:
- `useAccountManagement()` - Main hook for account management operations
- `useBankAccounts()` - Hook for bank account operations
- `useEscrowAccounts()` - Hook for escrow account operations
- `usePlatformRevenueAccounts()` - Hook for platform revenue account operations

**Key Methods**:
- `createBankAccount(input)`
- `updateBankAccount(accountNumber, bankName, bankCode)`
- `deleteBankAccount(accountNumber)`
- `createEscrowAccount(input)`
- `createPlatformRevenueAccount(input)`
- `updatePlatformRevenueAccount(accountNumber, accountName, minimumBalance, maximumWithdrawalAmount)`
- `activatePlatformRevenueAccount(accountNumber)`
- `deactivatePlatformRevenueAccount(accountNumber)`
- `linkAccounts(input)`
- `unlinkBankAccount(bankAccountNumber)`
- `unlinkPlatformAccount(platformAccountNumber)`
- `transferBetweenEscrowAccounts(input)`
- `validateEscrowAccountBalance(accountId, amount)`

**Types to Import**:
```typescript
import type {
  BankAccount,
  EscrowAccount,
  PlatformAccount,
  AccountAssociation,
  CreateBankAccountMutationResponse,
  UpdateBankAccountMutationResponse,
  DeleteBankAccountMutationResponse,
  CreateEscrowAccountMutationResponse,
  CreatePlatformRevenueAccountMutationResponse,
  UpdatePlatformRevenueAccountMutationResponse,
  ActivatePlatformRevenueAccountMutationResponse,
  DeactivatePlatformRevenueAccountMutationResponse,
  LinkAccountsMutationResponse,
  UnlinkBankAccountMutationResponse,
  UnlinkPlatformAccountMutationResponse,
  TransferBetweenEscrowAccountsMutationResponse,
  ValidateEscrowAccountBalanceMutationResponse,
} from '@pml.tickets/shared/types/graphql/schema-types';
```

---

## Implementation Pattern Template

Each hook file should follow this structure:

```typescript
'use client';

import { useState, useCallback } from 'react';
import { ServiceName } from '@/lib/services/service-name-service';
import type { /* relevant types */ } from '@pml.tickets/shared/types/graphql/schema-types';

export function useServiceName() {
  const [data, setData] = useState<DataType | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async (params) => {
    setLoading(true);
    setError(null);
    try {
      const result = await ServiceName.methodName(params);
      setData(result);
      return result;
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Failed to fetch data');
      setError(error);
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  // Additional methods following the same pattern...

  return {
    data,
    loading,
    error,
    fetchData,
    // ... other methods
  };
}
```

## Implementation Order

### Phase 1: High Priority (Most Used)
1. **use-analytics.ts** - Dashboard statistics (frequently accessed)
2. **use-payouts.ts** - Payout management (critical business operations)
3. **use-transactions.ts** - Transaction management (financial operations)

### Phase 2: Medium Priority
4. **use-refunds.ts** - Refund management
5. **use-account-management.ts** - Account configuration
6. **use-system-health.ts** - System monitoring

### Phase 3: Lower Priority (Configuration)
7. **use-locations.ts** - Location management
8. **use-event-categories.ts** - Category management

## Testing Strategy

For each hook:
1. Test successful data fetching
2. Test error handling
3. Test loading states
4. Test mutation operations
5. Test state updates after mutations

## Notes

- All hooks should be marked with `'use client'` directive
- Use `useCallback` for all service method wrappers to prevent unnecessary re-renders
- Error handling should be consistent across all hooks
- Consider adding refetch/invalidate capabilities for hooks that need real-time updates
- Some hooks (like `use-analytics`) may benefit from caching strategies
- Hooks that support pagination should handle pagination state appropriately

## File Structure

```
pml.tickets/apps/admin/src/lib/hooks/
├── usePermissions.ts (✅ completed)
├── use-users.ts (✅ completed)
├── use-events.ts (✅ completed)
├── use-tickets.ts (✅ completed)
├── use-transactions.ts (⏳ to implement)
├── use-payouts.ts (⏳ to implement)
├── use-refunds.ts (⏳ to implement)
├── use-analytics.ts (⏳ to implement)
├── use-locations.ts (⏳ to implement)
├── use-event-categories.ts (⏳ to implement)
├── use-system-health.ts (⏳ to implement)
└── use-account-management.ts (⏳ to implement)
```

## Completion Criteria

Each hook is considered complete when:
- ✅ All service methods are wrapped in hook functions
- ✅ Proper TypeScript types are imported and used
- ✅ Error handling is implemented
- ✅ Loading states are managed
- ✅ Hook follows the established pattern
- ✅ No linting errors
- ✅ Hook is exported and ready for use in components

