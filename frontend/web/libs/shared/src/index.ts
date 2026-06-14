/**
 * Shared Library Index
 *
 * Centralized exports for all shared utilities following module-based architecture.
 *
 * ## Architecture
 *
 * - **api/admin/modules/** - Admin app modules (organization, document, admin)
 * - **api/organization-admin/modules/** - Organization-admin app modules
 * - **api/rest/** - Shared HTTP utilities (http-client, files, health)
 * - **api/graphql/** - Shared GraphQL client utilities
 * - **api/schemas/** - Validation utilities (validateSchema, extractFieldErrors)
 * - **api/types/** - Shared UI types (FormErrors, PaginationState, ApiError)
 * - **auth/** - Authentication (client-safe types only)
 * - **auth/better-auth/server** - Server-only auth configuration
 * - **components/** - Shared UI components
 *
 * ## Import Patterns
 *
 * ```typescript
 * // Admin module
 * import {
 *   useCreateAdmin,
 *   useTransactionHealthSummary,
 *   type UserRegistrationDto,
 * } from '@pml.tickets/shared/api/admin/modules/admin';
 *
 * // Organization-admin module
 * import {
 *   useMyOrganization,
 *   useDocumentUpload,
 *   businessInfoFormSchema,
 * } from '@pml.tickets/shared/api/organization-admin/modules/organization';
 *
 * // Shared utilities
 * import { useFileUpload } from '@pml.tickets/shared/api/rest/files';
 * import { createApiClient } from '@pml.tickets/shared/api/rest/http-client';
 * import type { FormErrors, PaginationState } from '@pml.tickets/shared/api/types';
 *
 * // Server-only auth (import from subpath, not from main export)
 * import { getBetterAuth } from '@pml.tickets/shared/auth/better-auth/server';
 * ```
 */

// ============== Auth (Client-Safe Only) ==============
// NOTE: Server-only auth (getBetterAuth, createBetterAuth) must be imported from:
// import { getBetterAuth } from '@pml.tickets/shared/auth/better-auth/server';
export * from './auth';

// ============== Shared Components ==============
// Auth components (PermissionGate), UI components (SectionError)
export * from './components';

// ============== GraphQL API (Primary Data Layer) ==============
// All data fetching goes through GraphQL via the API Gateway
// Feature-organized: queries, mutations, hooks, types, schemas
// NOTE: Components should ONLY use hooks from api/graphql features, never Apollo directly
export {
  type GraphQLClientConfig,
  type TokenGetter,
  createGraphQLClient,
  type ApolloErrorType,
  type ApolloErrorInterface,
  categorizeApolloError,
  handleGraphQLError,
  getApolloErrorMessage,
  // Error utilities
  isNetworkLoading,
  isRefetching,
  extractErrorMessage,
  filterGraphQLErrors,
  getUserFriendlyErrorMessage,
  isGraphQLAuthError,
  // Network error detection (for graceful degradation)
  isNetworkError,
  isServerUnavailable,
} from './api/graphql/client';

// ============== API Modules ==============
// Module-based architecture: All app-specific operations in dedicated modules
export * from './api/admin/modules';


// ============== REST API (Shared HTTP utilities) ==============
// Base HTTP client and utilities for REST operations
export {
  type AsyncTokenGetter,
  createApiClient,
  apiClient,
  toApiError,
  handleApiResponse,
  handleApiError,
  API_BASE_URL,
} from './api/rest/http-client';

// ============== API Configuration ==============
export {
  GRAPHQL_ENDPOINT,
  GRAPHQL_WS_ENDPOINT,
  adminServiceBaseUrl,
  filesServiceBaseUrl,
} from './api/service-base-urls';

// ============== REST Query Client ==============
// TanStack Query client configuration for REST APIs
export * from './api/rest/query-client';

// ============== Theme Configuration ==============
export * from './lib/theme';

// ============== GraphQL Types ==============
// Namespaced exports to avoid collisions
export * as GraphQLTypes from './types/graphql';

// Common GraphQL types for convenience
export type {
  Event,
  EventCategory,
  EventStatus,
  TicketTier,
  EventFilterInput,
  EventStats,
  Location,
  User,
  Ticket,
  TicketStatus,
  PaymentMethod,
  Query,
} from './types/graphql';
