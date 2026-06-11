/**
 * Admin Module Types
 *
 * Type definitions for admin operations including:
 * - User management (create admin, organizer approval)
 * - Monitoring (transaction health, webhook stats)
 * - Reconciliation (transaction reconciliation)
 * - Bootstrap (initial system setup)
 *
 * These types align with the backend Java classes for consistency.
 *
 * NOTE: These types use REST API enums (RestUserType) since they're used for REST endpoints,
 * not GraphQL operations. For GraphQL operations, use GraphQL types from graphqlEnums.ts.
 */

import { RestUserType } from '../../../rest/types/user-type';

// ==================== User Management Types ====================

/**
 * User Registration DTO - matches backend UserRegistrationDto.java
 * Used for creating new users (admin, super admin, organizer, attendee)
 *
 * Note: Backend UserRegistrationDto.nextStep is RegistrationStep enum, not string
 * Frontend uses string for flexibility, but should match enum values
 */
export interface UserRegistrationDto {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  userType?: RestUserType;
  password?: string;
  confirmPassword?: string;

  // Business fields for EVENT_ORGANIZER users
  companyName?: string;
  taxId?: string;
  businessPhone?: string;
  businessEmail?: string;

  // Registration progress tracking fields (optional, for internal use)
  userId?: string;
  progressPercentage?: number;
  stepData?: Record<string, unknown>;
  lastUpdated?: string;
  nextStep?: string;
  canProceed?: boolean;
  validationErrors?: string[];
}

/**
 * User Response - matches backend User model
 * Returned from API operations that create or retrieve users
 *
 * Note: Backend User model has both 'enabled' (Spring Security) and 'isActive' (business logic)
 * Both fields are included for completeness
 */
export interface UserResponse {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  userType: RestUserType;
  profilePicture?: string;

  // Spring Security fields
  enabled: boolean;
  accountNonExpired: boolean;
  accountNonLocked: boolean;
  credentialsNonExpired: boolean;

  // Business status field
  isActive: boolean;

  // Verification fields
  emailVerified: boolean;
  phoneVerified: boolean;

  // Timestamps
  createdAt: string;
  updatedAt: string;
  lastLoginAt?: string;

  // Business fields for EVENT_ORGANIZER users
  companyName?: string;
  taxId?: string;
  businessPhone?: string;
  businessEmail?: string;
  organizerApprovalStatus?: string;
  organizerApprovalNote?: string;

  // Audit fields
  createdBy?: string;
  updatedBy?: string;
}

/**
 * Organizer Approval Request
 * Used for approving or rejecting organizer users
 */
export interface OrganizerApprovalRequest {
  note?: string;
}

// ==================== Monitoring Types ====================

/**
 * Monitoring Response
 * Generic response type for monitoring endpoints
 */
export interface MonitoringResponse {
  [key: string]: unknown;
}

/**
 * Transaction Health Summary Response
 */
export interface TransactionHealthSummary extends MonitoringResponse {
  totalTransactions?: number;
  pendingTransactions?: number;
  completedTransactions?: number;
  failedTransactions?: number;
  lastProcessedAt?: string;
  healthStatus?: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY';
  issues?: string[];
}

/**
 * Webhook Delivery Stats Response
 */
export interface WebhookDeliveryStats extends MonitoringResponse {
  totalWebhooks?: number;
  successfulDeliveries?: number;
  failedDeliveries?: number;
  pendingDeliveries?: number;
  successRate?: number;
  averageDeliveryTime?: number;
  lastDeliveryAt?: string;
}

/**
 * Transaction Trends Response
 */
export interface TransactionTrends extends MonitoringResponse {
  trends?: Array<{
    date: string;
    count: number;
    amount?: number;
  }>;
  period?: string;
  totalCount?: number;
  totalAmount?: number;
}

/**
 * Performance Metrics Response
 */
export interface PerformanceMetrics extends MonitoringResponse {
  averageResponseTime?: number;
  p95ResponseTime?: number;
  p99ResponseTime?: number;
  requestsPerSecond?: number;
  errorRate?: number;
  activeConnections?: number;
  memoryUsage?: number;
  cpuUsage?: number;
}

// ==================== Reconciliation Types ====================

/**
 * Reconciliation Response
 * Response from transaction reconciliation operations
 */
export interface ReconciliationResponse {
  transactionId?: string;
  status: 'RECONCILED' | 'PENDING' | 'FAILED' | 'CONFLICT';
  reconciledAt?: string;
  message?: string;
  details?: Record<string, unknown>;
  transactionsProcessed?: number;
  processedAt?: string;
}

/**
 * Reconciliation Request
 * Request for reconciling transactions
 */
export interface ReconciliationRequest {
  transactionId?: string;
  force?: boolean;
}

/**
 * Pending Reconciliations Response
 */
export interface PendingReconciliationsResponse {
  transactions: ReconciliationResponse[];
  count: number;
  retrievedAt: string;
}

// ==================== Bootstrap Types ====================

/**
 * Bootstrap Status Response
 * Response from bootstrap status endpoint
 * Matches backend BootstrapController.BootstrapStatus record
 */
export interface BootstrapStatus {
  adminExists: boolean;
  superAdminExists: boolean;
}

// ==================== Generic API Types ====================

/**
 * Generic API Response wrapper
 * Standardized response format for all API operations
 */
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiError;
  message?: string;
}

/**
 * Standardized API Error type
 */
export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  timestamp?: string;
}

/**
 * Pagination parameters
 */
export interface PaginationParams {
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'ASC' | 'DESC';
}

/**
 * Paginated response
 */
export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
