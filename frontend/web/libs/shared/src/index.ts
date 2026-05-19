// Use this file to export React client components or other non-server utilities

// Types are not globally re-exported to avoid name collisions; import from '@pml.tickets/shared/types/*' instead.

// ============== Auth (Keycloak) ==============
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
} from './api/graphql/client';

// ============== GraphQL Feature APIs ==============
// New App-First structure:
// - Admin APIs: '@pml.tickets/shared/api/graphql/admin'
// - Organizer APIs: '@pml.tickets/shared/api/graphql/organizer'
// - Buyer APIs: '@pml.tickets/shared/api/graphql/buyer'
export * from './api/graphql/admin';
export * from './api/graphql/organizer';
export * from './api/graphql/buyer';

// ============== REST API (Admin-only endpoints) ==============
// Used only for specific admin REST endpoints (/api/admin/*, /api/files/*)
// NOT for general data fetching - use GraphQL for that
export {
  type AsyncTokenGetter,
  createApiClient,
  apiClient,
  toApiError,
  handleApiResponse,
  handleApiError,
} from './api/rest/http-client';
export * from './api/rest/admin-api';

// ============== API Configuration ==============
export {
  API_BASE_URL,
  GRAPHQL_ENDPOINT,
  GRAPHQL_WS_ENDPOINT,
  adminServiceBaseUrl,
  filesServiceBaseUrl,
} from './api/service-base-urls';

// ============== REST Utility Hooks ==============
// File upload, health check, bootstrap utilities
export * from './api/rest/use-rest-apis';

// Services
// Server-side auth services are in auth/server/ - import from '@pml.tickets/shared/auth/server'.

// REST Query Client (TanStack Query for REST APIs)
export * from './api/rest/query-client';

// Theme Configuration
export * from './lib/theme';

// GraphQL type exports (namespaced to avoid collisions)
export * as GraphQLTypes from './types/graphql';

// Common type exports for convenience
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
