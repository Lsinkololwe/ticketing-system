// REST API response types based on backend REST endpoints
// These types represent the structure of responses from REST API endpoints

// Feature flags represented as a boolean map for REST types
type FeatureFlagsMap = Record<string, boolean>;

/**
 * REST API User Type Enum
 * Matches backend UserType enum from com.pml.event_ticketing.constants.UserType
 * This enum is REST-specific and should NOT be confused with GraphQL enums
 */
export enum RestUserType {
  EVENT_ORGANIZER = 'EVENT_ORGANIZER',
  EVENT_ATTENDEE = 'EVENT_ATTENDEE',
  FINANCE_ADMIN = 'FINANCE_ADMIN',
  OPERATIONS_ADMIN = 'OPERATIONS_ADMIN',
  SYSTEM_ADMIN = 'SYSTEM_ADMIN',
  SUPER_ADMIN = 'SUPER_ADMIN'
}

// Common Response Types
// ApiResponse is now imported from common.ts

// PaginatedResponse is now imported from common.ts

// Transaction Monitoring Types
export interface TransactionHealthSummary {
  status: 'HEALTHY' | 'WARNING' | 'CRITICAL';
  totalTransactions: number;
  pendingTransactions: number;
  failedTransactions: number;
  averageProcessingTime: number;
  lastProcessedAt: string;
  alerts: HealthAlert[];
  metrics: HealthMetric[];
}

export interface HealthAlert {
  id: string;
  type: 'PERFORMANCE' | 'ERROR_RATE' | 'QUEUE_SIZE' | 'PROCESSING_TIME';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  message: string;
  timestamp: string;
  resolved: boolean;
}

export interface HealthMetric {
  name: string;
  value: number;
  unit: string;
  threshold: number;
  status: 'OK' | 'WARNING' | 'CRITICAL';
  lastUpdated: string;
}

export interface WebhookDeliveryStats {
  totalWebhooks: number;
  successfulDeliveries: number;
  failedDeliveries: number;
  pendingDeliveries: number;
  averageDeliveryTime: number;
  lastDeliveryAt: string;
  failureReasons: FailureReason[];
}

export interface FailureReason {
  reason: string;
  count: number;
  percentage: number;
}

export interface SystemMetrics {
  cpuUsage: number;
  memoryUsage: number;
  diskUsage: number;
  activeConnections: number;
  queueSize: number;
  throughput: number;
  errorRate: number;
  uptime: string;
  lastUpdated: string;
}

// Payment Webhook Types
export interface WebhookPayload {
  eventType: string;
  eventId: string;
  timestamp: string;
  data: unknown;
  signature?: string;
  source: string;
}

export interface WebhookResponse {
  received: boolean;
  processed: boolean;
  message: string;
  transactionId?: string;
  errors?: string[];
}

// Admin User Types
export interface AdminUserResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  userType: string;
  isActive: boolean;
  permissions: string[];
  lastLoginAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserStats {
  totalAdmins: number;
  activeAdmins: number;
  inactiveAdmins: number;
  superAdmins: number;
  systemAdmins: number;
  lastActivity: string;
}

// Note: LoginRequest and LoginResponse are now in auth.ts
// These were removed to avoid duplication - use types from auth.ts instead

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

// File Upload Types
export interface FileUploadResponse {
  success: boolean;
  message: string;
  fileId?: string;
  fileName?: string;
  fileSize?: number;
  fileUrl?: string;
  errors?: string[];
}

export interface FileMetadata {
  id: string;
  fileName: string;
  originalName: string;
  fileSize: number;
  mimeType: string;
  uploadedBy: string;
  uploadedAt: string;
  fileUrl: string;
}

// Bootstrap Types
export interface BootstrapData {
  systemInfo: SystemInfo;
  configuration: SystemConfiguration;
  features: FeatureFlagsMap;
  permissions: Permission[];
}

export interface SystemInfo {
  version: string;
  buildNumber: string;
  environment: string;
  uptime: string;
  lastRestart: string;
}

export interface SystemConfiguration {
  maxFileSize: number;
  allowedFileTypes: string[];
  sessionTimeout: number;
  passwordPolicy: PasswordPolicy;
  features: FeatureFlagsMap;
}

export interface PasswordPolicy {
  minLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireNumbers: boolean;
  requireSpecialChars: boolean;
  maxAge: number;
}

// GraphQLFeatureFlagsType is now imported from graphqlCommon.ts

export interface Permission {
  id: string;
  name: string;
  description: string;
  category: string;
  granted: boolean;
}

// Health Check Types
export interface HealthCheckResponse {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE';
  components: HealthComponent[];
  timestamp: string;
}

export interface HealthComponent {
  name: string;
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE';
  details?: unknown;
  timestamp: string;
}

// Error Response Types
export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  requestId?: string;
  details?: unknown;
}

export interface ValidationErrorResponse extends ErrorResponse {
  fieldErrors: FieldError[];
}

export interface FieldError {
  field: string;
  rejectedValue: unknown;
  message: string;
}

// Transaction Reconciliation Types
export interface ReconciliationRequest {
  startDate: string;
  endDate: string;
  eventId?: string;
  organizerId?: string;
  includeDetails?: boolean;
}

export interface ReconciliationResponse {
  summary: ReconciliationSummary;
  discrepancies: Discrepancy[];
  recommendations: Recommendation[];
  generatedAt: string;
}

export interface ReconciliationSummary {
  totalTransactions: number;
  matchedTransactions: number;
  unmatchedTransactions: number;
  totalAmount: number;
  reconciledAmount: number;
  discrepancyAmount: number;
  accuracy: number;
}

export interface Discrepancy {
  id: string;
  type: 'AMOUNT_MISMATCH' | 'MISSING_TRANSACTION' | 'DUPLICATE_TRANSACTION' | 'STATUS_MISMATCH';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description: string;
  transactionId?: string;
  expectedValue?: unknown;
  actualValue?: unknown;
  detectedAt: string;
  resolved: boolean;
}

export interface Recommendation {
  id: string;
  type: 'AUTO_RESOLVE' | 'MANUAL_REVIEW' | 'SYSTEM_UPDATE' | 'PROCESS_IMPROVEMENT';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  title: string;
  description: string;
  action: string;
  estimatedEffort: string;
  impact: string;
}
