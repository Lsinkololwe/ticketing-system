/**
 * Ticketing App - GraphQL Operations
 *
 * Re-exports consumer-specific GraphQL operations from shared library.
 * This file serves as the entry point for codegen to generate consumer-specific types.
 *
 * Consumer operations use CURSOR PAGINATION for infinite scroll.
 */

// ==================== EVENTS ====================

// Fragments
export {
  TICKET_TIER_FIELDS,
  EVENT_FIELDS,
  EVENT_LIST_FIELDS,
} from '@shared/api/graphql/events/events.consumer.queryDefinitions';

// Queries - Consumer uses cursor pagination for infinite scroll
export {
  GET_EVENT_BY_ID,
  GET_PUBLISHED_EVENTS_CURSOR,
  GET_UPCOMING_EVENTS_CURSOR,
  GET_FEATURED_EVENTS_CURSOR,
  SEARCH_EVENTS_CURSOR,
  GET_EVENTS_BY_CATEGORY_CURSOR,
  GET_EVENTS_BY_CITY_CURSOR,
  GET_EVENTS_BY_DATE_RANGE_CURSOR,
  GET_EVENTS_BY_ORGANIZER_CURSOR,
  DISCOVER_EVENTS,
} from '@shared/api/graphql/events/events.consumer.queryDefinitions';

// ==================== USERS ====================

// Fragments
export {
  USER_FIELDS,
  ORGANIZER_PROFILE_FIELDS,
} from '@shared/api/graphql/users/users.consumer.queryDefinitions';

// Queries - Consumer profile
export {
  ME_QUERY,
  GET_USER,
  GET_MY_ORGANIZER_PROFILE,
  GET_ORGANIZER_PROFILE,
  GET_ORGANIZER_PROFILE_BY_USER_ID,
  GET_ORGANIZATION,
  GET_ORGANIZATION_BY_SLUG,
  GET_MY_ORGANIZATIONS,
  GET_MY_OWNED_ORGANIZATION,
} from '@shared/api/graphql/users/users.consumer.queryDefinitions';

// Mutations - Consumer profile management
export {
  UPDATE_MY_PROFILE,
  APPLY_TO_BE_ORGANIZER,
  UPDATE_ORGANIZER_PROFILE,
} from '@shared/api/graphql/users/users.mutationDefinitions';

// ==================== TICKETS ====================

// Fragments
export {
  TICKET_FIELDS,
} from '@shared/api/graphql/tickets/tickets.consumer.queryDefinitions';

// Queries - Consumer ticket queries (cursor pagination)
export {
  GET_TICKET,
  GET_TICKET_BY_NUMBER,
  GET_TICKETS_BY_EVENT_CURSOR,
  GET_TICKETS_BY_BUYER_CURSOR,
  GET_TICKETS_BY_ORGANIZER_CURSOR,
  SEARCH_TICKETS_CURSOR,
} from '@shared/api/graphql/tickets/tickets.consumer.queryDefinitions';

// Mutations - Consumer ticket operations
export {
  PURCHASE_TICKET,
  RESERVE_TICKETS,
  COMPLETE_RESERVATION,
  CANCEL_RESERVATION,
  EXTEND_RESERVATION,
} from '@shared/api/graphql/tickets/tickets.mutationDefinitions';

// ==================== PAYMENTS ====================

// Fragments
export {
  BANK_ACCOUNT_FIELDS,
} from '@shared/api/graphql/payments/payments.consumer.queryDefinitions';

// Queries - Consumer payment queries
export {
  GET_RESERVATION,
  GET_MY_ACTIVE_RESERVATIONS,
  VALIDATE_PROMO_CODE,
  GET_BANK_ACCOUNTS_BY_ORGANIZER,
  GET_DEFAULT_BANK_ACCOUNT,
  IS_TICKET_ELIGIBLE_FOR_REFUND,
  CALCULATE_REFUND_AMOUNT,
} from '@shared/api/graphql/payments/payments.consumer.queryDefinitions';

// Mutations - Consumer payment operations
export {
  CREATE_USER_REFUND_REQUEST,
  CREATE_BANK_ACCOUNT,
  UPDATE_BANK_ACCOUNT,
  DELETE_BANK_ACCOUNT,
  SET_DEFAULT_BANK_ACCOUNT,
  CREATE_PAYOUT_REQUEST,
  CANCEL_PAYOUT_REQUEST,
} from '@shared/api/graphql/payments/payments.consumer.mutationDefinitions';

// ==================== CATEGORIES ====================

// Fragments
export {
  CATEGORY_FIELDS,
} from '@shared/api/graphql/categories/categories.consumer.queryDefinitions';

// Queries - Consumer category browsing (cursor pagination)
export {
  GET_EVENT_CATEGORY,
  GET_EVENT_CATEGORIES_CURSOR,
  GET_ACTIVE_EVENT_CATEGORIES_CURSOR,
  SEARCH_EVENT_CATEGORIES_CURSOR,
  GET_POPULAR_CATEGORIES,
} from '@shared/api/graphql/categories/categories.consumer.queryDefinitions';

// ==================== LOCATIONS ====================

// Fragments
export {
  LOCATION_FIELDS,
  CITY_FIELDS,
  PROVINCE_FIELDS,
} from '@shared/api/graphql/locations/locations.consumer.queryDefinitions';

// Queries - Consumer location browsing (cursor pagination)
export {
  GET_LOCATION,
  GET_LOCATIONS_CURSOR,
  GET_LOCATIONS_BY_CITY_CURSOR,
  GET_LOCATIONS_BY_COUNTRY_CURSOR,
  SEARCH_LOCATIONS_CURSOR,
  GET_LOCATIONS_NEARBY_CURSOR,
  GET_CITY,
  GET_CITIES_CURSOR,
  GET_CITIES_BY_PROVINCE_CURSOR,
  SEARCH_CITIES_CURSOR,
  GET_PROVINCE,
  GET_PROVINCES_CURSOR,
  GET_PROVINCES_BY_COUNTRY_CURSOR,
  SEARCH_PROVINCES_CURSOR,
} from '@shared/api/graphql/locations/locations.consumer.queryDefinitions';
