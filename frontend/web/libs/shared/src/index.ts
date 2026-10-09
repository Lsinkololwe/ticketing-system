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
 * - **auth/bff** - Server-side session BFF (import from the subpath)
 * - **components/** - Shared UI components
 *
 * ## Import Patterns
 *
 * ```typescript
 * // Admin module
 * import {
 *   usePendingOrganizations,
 *   useApproveOrganization,
 * } from '@pml.tickets/shared/api/admin/modules/organization';
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
 * import { createBffApiClient } from '@pml.tickets/shared/api/rest/http-client';
 * import type { FormErrors, PaginationState } from '@pml.tickets/shared/api/types';
 *
 * // Server-only auth (import from subpath, not from main export)
 * import { createBff } from '@pml.tickets/shared/auth/bff';
 * ```
 */

// ============== Auth (Client-Safe Only) ==============
// NOTE: the server-side BFF must be imported from '@pml.tickets/shared/auth/bff'.
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

// ---- GraphQL feature domains (hooks consumed directly by app components) ----
// Analytics domain: action-center / dashboard pending-count badges.
export {
  usePendingCounts,
  usePlatformSummary,
  type PendingCounts,
  type PendingCountKey,
  type UsePendingCountsResult,
  type UsePlatformSummaryResult,
  type PlatformSummaryData,
  PENDING_COUNTS,
} from './api/graphql/analytics';

// Events domain (consumer ticketing app): browsing, detail, filters.
export {
  usePublishedEvents,
  useEvent,
  useActiveEventCategories,
  useCitiesWithEvents,
  type EventListOptions,
  type EventPageInfo,
  type PublishedEventRow,
  type EventDetail,
  type TicketTierRow,
  type EventCategoryOption,
  type CityOption,
  GET_PUBLISHED_EVENTS,
  GET_EVENT_BY_ID,
  GET_ACTIVE_EVENT_CATEGORIES,
  GET_CITIES_WITH_EVENTS,
  EVENT_CARD_FIELDS,
  EVENT_DETAIL_FIELDS,
} from './api/graphql/events';

// Booking domain (consumer ticketing app): reservation, checkout, my tickets.
export {
  useReserveTickets,
  usePayReservation,
  useReservation,
  useMyTickets,
  type MyTicketsOptions,
  type MyTicketRow,
  RESERVE_TICKETS,
  PAY_RESERVATION,
  GET_RESERVATION,
  GET_MY_TICKETS,
  TICKET_FIELDS,
} from './api/graphql/booking';

// ============== API Modules ==============
// Module-based architecture: All app-specific operations in dedicated modules
export * from './api/admin/modules';


// ============== REST API (Shared HTTP utilities) ==============
// Base HTTP client and utilities for REST operations
export {
  toApiError,
  handleApiResponse,
  handleApiError,
  API_BASE_URL,
} from './api/rest/http-client';

// ============== API Configuration ==============
export {
  GRAPHQL_ENDPOINT,
  adminServiceBaseUrl,
  filesServiceBaseUrl,
} from './api/service-base-urls';

// ============== REST Query Client ==============
// TanStack Query client configuration for REST APIs
export * from './api/rest/query-client';

// ============== GraphQL Types ==============
// Namespaced exports to avoid collisions
export * as GraphQLTypes from './types/graphql';

// Enum-shaped GraphQL types for convenience. Deliberately NOT the object
// shapes (`Event`, `Ticket`, `User`, `Location`, …): those are the full
// entity as the schema defines it, not what any one query selects, and
// re-exporting them here is what let consumers reach for "the" `Event` type
// instead of the `*Row`/`*Detail` type codegen actually produced for their
// query — a mismatch of dozens of fields that only fails at the one call
// site that reads a field the query never fetched. A domain module's own
// barrel (e.g. `api/graphql/events`) exports the query-shaped type instead.
export type { EventStatus, TicketStatus, PaymentMethod } from './types/graphql';

// ============== Presentation formatters ==============
// One implementation for all three apps. The design system fixes these
// renderings platform-wide — currency is always "K 125,430", never "ZMW" or
// "$", and an enum never reaches the DOM in SCREAMING_SNAKE — so a per-app copy
// is a per-app opportunity to disagree with the design system.
export {
  formatKwacha,
  formatKwachaCompact,
  formatCount,
  humanizeEnum,
  statusTone,
} from './lib/format';
export type { StatusTone } from './lib/format';

// ============== Error contract ==============
// Clients branch on extensions.errorCode and read extensions.retryable. Message
// text is for humans and is never parsed — copy changes must not change
// behaviour.
export {
  resolveError,
  endsSession,
  fieldErrors,
  sessionAction,
  resetUnrecognisedReporting,
} from './lib/errors';
export type {
  ErrorClassification,
  ErrorAction,
  ErrorExtensions,
  FieldViolation,
  GraphQLLikeError,
  SessionAction,
  ResolvedError,
} from './lib/errors';

// ============== Forms kit ==============
// react-hook-form + zod only. See forms/README.md and forms/API.md.
export * from './forms';

// ============== Form data helpers ==============
export {
  useGraphQLMutationForm,
  useRestMutationForm,
  QueryProvider,
  defineQueryKeys,
  restRequest,
} from './api';
export type { FormSubmit, UseGraphQLMutationFormOptions, UseRestMutationFormOptions, RestRequestOptions } from './api';

// Buyer storefront GraphQL features (discovery, event page, reservations, refunds, reminders, notifications, profile, invitations).
export * from './api/graphql/buyer';
export { sanitizeRich, richView, richToText, isRichHtml, escapeText } from './lib/richtext';
