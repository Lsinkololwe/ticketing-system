/**
 * GraphQL API Layer
 *
 * Centralized exports for all GraphQL operations organized by app and domain.
 *
 * ## Structure (App-First)
 *
 * ```
 * api/
 * ├── admin/           # Admin app APIs (organizers, users, events management)
 * ├── organizer/       # Organizer self-service APIs (my events, payouts)
 * ├── buyer/           # Buyer/consumer APIs (browse events, purchase tickets)
 * └── shared/          # Shared utilities (client, types)
 * ```
 *
 * ## Import Pattern
 *
 * ```tsx
 * // For Admin app
 * import { useOrganizerApplications } from '@pml.tickets/shared/api/graphql/admin/organizers';
 *
 * // For Organizer app
 * import { useMyEvents } from '@pml.tickets/shared/api/graphql/organizer';
 *
 * // For Buyer app
 * import { useEventsSearch } from '@pml.tickets/shared/api/graphql/buyer';
 * ```
 *
 * IMPORTANT: Frontend components should ONLY use hooks from these features,
 * never importing Apollo libraries directly.
 */

// ==================== App APIs ====================

// Organization Admin App
export * from './organization-admin';

// ALL OTHER APP APIs HAVE BEEN MIGRATED TO DOMAIN MODULES
//
// Admin operations: '@pml.tickets/shared/api/modules/organization'
// Buyer operations: '@pml.tickets/shared/api/modules/event'
//
// Import directly from domain modules or app-specific GraphQL directories.

// ==================== Shared Utilities ====================

// Apollo Client Factory
export {
  type GraphQLClientConfig,
  type TokenGetter,
  createGraphQLClient,
  type ApolloErrorType,
  categorizeApolloError,
  handleGraphQLError,
  getApolloErrorMessage,
} from './client';
