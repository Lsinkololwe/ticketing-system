/**
 * Admin API Types
 * 
 * Type definitions for admin API operations matching backend DTOs and models.
 * These types align with the backend Java classes for consistency.
 * 
 * NOTE: These types use REST API enums (RestUserType) since they're used for REST endpoints,
 * not GraphQL operations. For GraphQL operations, use GraphQL types from graphqlEnums.ts.
 */

import { RestUserType } from './rest-api-types';

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
  userType?: RestUserType; // Changed from GraphQLUserTypeEnum to RestUserType for REST APIs
  password?: string;
  confirmPassword?: string;
  
  // Business fields for EVENT_ORGANIZER users
  companyName?: string;
  taxId?: string;
  businessPhone?: string;
  businessEmail?: string;
  
  // Registration progress tracking fields (optional, for internal use)
  // Note: These match backend UserRegistrationDto fields
  userId?: string;
  progressPercentage?: number; // Backend uses Float, frontend uses number
  stepData?: Record<string, unknown>; // Backend uses Map<String, Object>
  lastUpdated?: string; // Backend uses LocalDateTime, frontend uses ISO string
  nextStep?: string; // Backend uses RegistrationStep enum, frontend uses string
  canProceed?: boolean; // Backend uses Boolean
  validationErrors?: string[]; // Backend uses List<String>
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
  userType: RestUserType; // Changed from GraphQLUserTypeEnum to RestUserType for REST APIs
  profilePicture?: string; // Not in backend User model, but may be in GraphQL schema
  
  // Spring Security fields (from UserDetails interface)
  enabled: boolean; // Backend User.enabled
  accountNonExpired: boolean; // Backend User.accountNonExpired
  accountNonLocked: boolean; // Backend User.accountNonLocked
  credentialsNonExpired: boolean; // Backend User.credentialsNonExpired
  
  // Business status field
  isActive: boolean; // Backend User.isActive
  
  // Verification fields
  emailVerified: boolean; // Backend User.emailVerified
  phoneVerified: boolean; // Backend User.phoneVerified
  
  // Timestamps
  createdAt: string; // Backend User.createdAt (LocalDateTime -> ISO string)
  updatedAt: string; // Backend User.updatedAt (LocalDateTime -> ISO string)
  lastLoginAt?: string; // Backend User.lastLoginAt (LocalDateTime -> ISO string)
  
  // Business fields for EVENT_ORGANIZER users
  companyName?: string; // Backend User.companyName
  taxId?: string; // Backend User.taxId
  businessPhone?: string; // Backend User.businessPhone
  businessEmail?: string; // Backend User.businessEmail
  organizerApprovalStatus?: string; // Backend User.organizerApprovalStatus (OrganizerApprovalStatus enum -> string)
  organizerApprovalNote?: string; // Backend User.organizerApprovalNote
  
  // Audit fields
  createdBy?: string; // Backend User.createdBy
  updatedBy?: string; // Backend User.updatedBy
  // Note: lastModifiedBy is not in backend User model, but may be in GraphQL schema
  
  // Optional fields that may not be returned in all responses
  password?: never; // Never include password in responses
  otpCode?: never; // Never include OTP code in responses
  otpExpiryTime?: never; // Never include OTP expiry in responses
  failedLoginAttempts?: never; // Internal field, not exposed
  accountLockedUntil?: never; // Internal field, not exposed
}

/**
 * Organizer Approval Request
 * Used for approving or rejecting organizer users
 */
export interface OrganizerApprovalRequest {
  note?: string;
}

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
 * Bootstrap Status Response
 * Response from bootstrap status endpoint
 * Matches backend BootstrapController.BootstrapStatus record
 */
export interface BootstrapStatus {
  adminExists: boolean;
  superAdminExists: boolean;
}

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

/**
 * Pending Reconciliations Response
 * Response from get pending reconciliations endpoint
 * Matches backend TransactionReconciliationController.getPendingTransactions response
 */
export interface PendingReconciliationsResponse {
  transactions: ReconciliationResponse[];
  count: number;
  retrievedAt: string;
}

