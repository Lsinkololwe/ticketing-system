

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
export { createBffApiClient, toApiError, handleApiResponse, handleApiError, API_BASE_URL } from './rest/http-client';


// =============================================================================
// CONFIGURATION
// =============================================================================

// Service base URLs
export {
  GRAPHQL_ENDPOINT,
  adminServiceBaseUrl,
  filesServiceBaseUrl,
} from './service-base-urls';

// =============================================================================
// FORM DATA HELPERS (mutation hooks shaped for forms/Form, providers, keys)
// =============================================================================
export { useGraphQLMutationForm, useRestMutationForm } from './form-mutations';
export type { FormSubmit, UseGraphQLMutationFormOptions, UseRestMutationFormOptions } from './form-mutations';
export { QueryProvider } from './QueryProvider';
export { defineQueryKeys } from './query-keys';
export { restRequest } from './rest-request';
export type { RestRequestOptions } from './rest-request';
