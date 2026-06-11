/**
 * Admin Module Hooks
 *
 * React hooks for admin operations using TanStack Query.
 * Provides hooks for:
 * - User management
 * - Monitoring
 * - Reconciliation
 * - Bootstrap
 */

'use client';

import { useQuery, useMutation, useQueryClient, type UseQueryResult, type UseMutationResult } from '@tanstack/react-query';
import type { AsyncTokenGetter } from '../../../rest/http-client';
import * as adminRest from './admin.rest';
import type {
  UserRegistrationDto,
  UserResponse,
  TransactionHealthSummary,
  WebhookDeliveryStats,
  TransactionTrends,
  PerformanceMetrics,
  ReconciliationResponse,
  BootstrapStatus,
  PendingReconciliationsResponse,
} from './admin.types';

// ==================== Query Keys ====================

export const adminQueryKeys = {
  all: ['admin'] as const,
  monitoring: {
    all: ['admin', 'monitoring'] as const,
    healthSummary: () => ['admin', 'monitoring', 'healthSummary'] as const,
    webhookStats: () => ['admin', 'monitoring', 'webhookStats'] as const,
    trends: (hours: number) => ['admin', 'monitoring', 'trends', hours] as const,
    performance: () => ['admin', 'monitoring', 'performance'] as const,
  },
  reconciliation: {
    all: ['admin', 'reconciliation'] as const,
    pending: () => ['admin', 'reconciliation', 'pending'] as const,
    transaction: (id: string) => ['admin', 'reconciliation', 'transaction', id] as const,
  },
  bootstrap: {
    all: ['admin', 'bootstrap'] as const,
    status: () => ['admin', 'bootstrap', 'status'] as const,
  },
};

// ==================== User Management Hooks ====================

/**
 * Hook to create a new admin user
 */
export function useCreateAdmin(asyncTokenGetter?: AsyncTokenGetter): UseMutationResult<UserResponse, Error, UserRegistrationDto> {
  return useMutation({
    mutationFn: (dto: UserRegistrationDto) => adminRest.createAdmin(dto, undefined, asyncTokenGetter),
  });
}

/**
 * Hook to create a new super admin user
 */
export function useCreateSuperAdmin(asyncTokenGetter?: AsyncTokenGetter): UseMutationResult<UserResponse, Error, UserRegistrationDto> {
  return useMutation({
    mutationFn: (dto: UserRegistrationDto) => adminRest.createSuperAdmin(dto, undefined, asyncTokenGetter),
  });
}

/**
 * Hook to create a new organizer user
 */
export function useCreateOrganizer(asyncTokenGetter?: AsyncTokenGetter): UseMutationResult<UserResponse, Error, UserRegistrationDto> {
  return useMutation({
    mutationFn: (dto: UserRegistrationDto) => adminRest.createOrganizer(dto, undefined, asyncTokenGetter),
  });
}

/**
 * Hook to create a new attendee user
 */
export function useCreateAttendee(asyncTokenGetter?: AsyncTokenGetter): UseMutationResult<UserResponse, Error, UserRegistrationDto> {
  return useMutation({
    mutationFn: (dto: UserRegistrationDto) => adminRest.createAttendee(dto, undefined, asyncTokenGetter),
  });
}

// ==================== Organizer Approval Hooks ====================

/**
 * Hook to approve an organizer
 */
export function useApproveOrganizerRest(asyncTokenGetter?: AsyncTokenGetter): UseMutationResult<UserResponse, Error, { id: string; note?: string }> {
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) =>
      adminRest.approveOrganizer(id, note, undefined, asyncTokenGetter),
  });
}

/**
 * Hook to reject an organizer
 */
export function useRejectOrganizerRest(asyncTokenGetter?: AsyncTokenGetter): UseMutationResult<UserResponse, Error, { id: string; note?: string }> {
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) =>
      adminRest.rejectOrganizer(id, note, undefined, asyncTokenGetter),
  });
}

// ==================== Monitoring Hooks ====================

/**
 * Hook to get transaction health summary
 */
export function useTransactionHealthSummary(asyncTokenGetter?: AsyncTokenGetter): UseQueryResult<TransactionHealthSummary, Error> {
  return useQuery({
    queryKey: adminQueryKeys.monitoring.healthSummary(),
    queryFn: () => adminRest.getTransactionHealthSummary(undefined, asyncTokenGetter),
    refetchInterval: 30000, // Refetch every 30 seconds
  });
}

/**
 * Hook to get webhook delivery stats
 */
export function useWebhookDeliveryStats(asyncTokenGetter?: AsyncTokenGetter): UseQueryResult<WebhookDeliveryStats, Error> {
  return useQuery({
    queryKey: adminQueryKeys.monitoring.webhookStats(),
    queryFn: () => adminRest.getWebhookDeliveryStats(undefined, asyncTokenGetter),
    refetchInterval: 30000,
  });
}

/**
 * Hook to get transaction trends
 */
export function useTransactionTrends(hours: number = 24, asyncTokenGetter?: AsyncTokenGetter): UseQueryResult<TransactionTrends, Error> {
  return useQuery({
    queryKey: adminQueryKeys.monitoring.trends(hours),
    queryFn: () => adminRest.getTransactionTrends(hours, undefined, asyncTokenGetter),
    refetchInterval: 60000, // Refetch every minute
  });
}

/**
 * Hook to get performance metrics
 */
export function usePerformanceMetrics(asyncTokenGetter?: AsyncTokenGetter): UseQueryResult<PerformanceMetrics, Error> {
  return useQuery({
    queryKey: adminQueryKeys.monitoring.performance(),
    queryFn: () => adminRest.getPerformanceMetrics(undefined, asyncTokenGetter),
    refetchInterval: 30000,
  });
}

// ==================== Reconciliation Hooks ====================

/**
 * Hook to reconcile a single transaction
 */
export function useReconcileTransaction(asyncTokenGetter?: AsyncTokenGetter): UseMutationResult<ReconciliationResponse, Error, string> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (transactionId: string) =>
      adminRest.reconcileTransaction(transactionId, undefined, asyncTokenGetter),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.reconciliation.pending() });
    },
  });
}

/**
 * Hook to reconcile all transactions
 */
export function useReconcileAllTransactions(asyncTokenGetter?: AsyncTokenGetter): UseMutationResult<ReconciliationResponse, Error, void> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => adminRest.reconcileAllTransactions(undefined, asyncTokenGetter),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.reconciliation.all });
    },
  });
}

/**
 * Hook to get pending reconciliations
 */
export function usePendingReconciliations(asyncTokenGetter?: AsyncTokenGetter): UseQueryResult<PendingReconciliationsResponse, Error> {
  return useQuery({
    queryKey: adminQueryKeys.reconciliation.pending(),
    queryFn: () => adminRest.getPendingReconciliations(undefined, asyncTokenGetter),
    refetchInterval: 60000,
  });
}

// ==================== Bootstrap Hooks ====================

/**
 * Hook to get bootstrap status
 */
export function useBootstrapStatus(): UseQueryResult<BootstrapStatus, Error> {
  return useQuery({
    queryKey: adminQueryKeys.bootstrap.status(),
    queryFn: () => adminRest.getBootstrapStatus(),
  });
}

/**
 * Hook to create the first admin user
 */
export function useCreateFirstAdmin(): UseMutationResult<UserResponse, Error, UserRegistrationDto> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (dto: UserRegistrationDto) => adminRest.createFirstAdmin(dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.bootstrap.status() });
    },
  });
}

/**
 * Hook to create the first super admin user
 */
export function useCreateFirstSuperAdmin(): UseMutationResult<UserResponse, Error, UserRegistrationDto> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (dto: UserRegistrationDto) => adminRest.createFirstSuperAdmin(dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.bootstrap.status() });
    },
  });
}
