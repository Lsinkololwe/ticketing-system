

// =============================================================================
// APPLICATION-SPECIFIC MODULES
// =============================================================================

// Admin app modules
// Import from: '@pml.tickets/shared/api/admin/modules/organization'
export * from './admin';



// Legacy graphql exports (being migrated to modules)
export * from './graphql';

// =============================================================================
// SHARED UTILITIES
// =============================================================================

// Shared HTTP client utilities (low-level, not domain-specific)
export { createApiClient, apiClient, toApiError, handleApiResponse, handleApiError, API_BASE_URL } from './rest/http-client';
export type { TokenGetter, AsyncTokenGetter } from './rest/http-client';


// =============================================================================
// CONFIGURATION
// =============================================================================

// Service base URLs
export {
  GRAPHQL_ENDPOINT,
  GRAPHQL_WS_ENDPOINT,
  adminServiceBaseUrl,
  filesServiceBaseUrl,
} from './service-base-urls';
