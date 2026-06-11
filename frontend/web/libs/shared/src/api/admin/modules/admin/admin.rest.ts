/**
 * Admin REST API Operations
 *
 * REST API operations for admin-specific functionality including:
 * - User management (create admin, super admin, organizer, attendee)
 * - Organizer approval/rejection
 * - Transaction monitoring and health
 * - Webhook delivery stats
 * - Transaction reconciliation
 * - Bootstrap operations
 */

import { createApiClient, handleApiResponse, handleApiError } from '../../../rest/http-client';
import type { TokenGetter, AsyncTokenGetter } from '../../../rest/http-client';
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

// ==================== User Management ====================

/**
 * Create a new admin user
 */
export async function createAdmin(
  dto: UserRegistrationDto,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<UserResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.post<UserResponse>('/api/admin/users/create-admin', dto);
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Create a new super admin user
 */
export async function createSuperAdmin(
  dto: UserRegistrationDto,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<UserResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.post<UserResponse>('/api/admin/users/create-super-admin', dto);
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Create a new organizer user
 */
export async function createOrganizer(
  dto: UserRegistrationDto,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<UserResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.post<UserResponse>('/api/admin/users/create-organizer', dto);
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Create a new attendee user
 */
export async function createAttendee(
  dto: UserRegistrationDto,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<UserResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.post<UserResponse>('/api/admin/users/create-attendee', dto);
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

// ==================== Organizer Approval ====================

/**
 * Approve an organizer user
 */
export async function approveOrganizer(
  id: string,
  note?: string,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<UserResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const params = note ? { note } : {};
    const response = await client.post<UserResponse>(
      `/api/admin/users/organizers/${id}/approve`,
      null,
      { params }
    );
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Reject an organizer user
 */
export async function rejectOrganizer(
  id: string,
  note?: string,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<UserResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const params = note ? { note } : {};
    const response = await client.post<UserResponse>(
      `/api/admin/users/organizers/${id}/reject`,
      null,
      { params }
    );
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

// ==================== Monitoring ====================

/**
 * Get transaction health summary
 */
export async function getTransactionHealthSummary(
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<TransactionHealthSummary> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.get<TransactionHealthSummary>(
      '/api/admin/monitoring/health/summary'
    );
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Get webhook delivery statistics
 */
export async function getWebhookDeliveryStats(
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<WebhookDeliveryStats> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.get<WebhookDeliveryStats>(
      '/api/admin/monitoring/webhooks/stats'
    );
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Get transaction trends
 */
export async function getTransactionTrends(
  hours: number = 24,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<TransactionTrends> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.get<TransactionTrends>('/api/admin/monitoring/trends', {
      params: { hours },
    });
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Get performance metrics
 */
export async function getPerformanceMetrics(
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<PerformanceMetrics> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.get<PerformanceMetrics>(
      '/api/admin/monitoring/performance/metrics'
    );
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

// ==================== Reconciliation ====================

/**
 * Reconcile a single transaction
 */
export async function reconcileTransaction(
  transactionId: string,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<ReconciliationResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.post<ReconciliationResponse>(
      `/api/admin/reconciliation/transaction/${transactionId}`
    );
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Reconcile all transactions
 */
export async function reconcileAllTransactions(
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<ReconciliationResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.post<ReconciliationResponse>('/api/admin/reconciliation/all');
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Get pending reconciliations
 */
export async function getPendingReconciliations(
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<PendingReconciliationsResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.get<PendingReconciliationsResponse>(
      '/api/admin/reconciliation/pending'
    );
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

// ==================== Bootstrap ====================

/**
 * Get bootstrap status (check if admin users exist)
 */
export async function getBootstrapStatus(
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<BootstrapStatus> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.get<BootstrapStatus>('/api/bootstrap/status');
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Create the first admin user (bootstrap)
 */
export async function createFirstAdmin(
  dto: UserRegistrationDto,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<UserResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.post<UserResponse>('/api/bootstrap/first-admin', dto);
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}

/**
 * Create the first super admin user (bootstrap)
 */
export async function createFirstSuperAdmin(
  dto: UserRegistrationDto,
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): Promise<UserResponse> {
  try {
    const client = createApiClient(tokenGetter, asyncTokenGetter);
    const response = await client.post<UserResponse>('/api/bootstrap/first-super-admin', dto);
    return handleApiResponse(response);
  } catch (error) {
    throw handleApiError(error).error;
  }
}
