/**
 * Event Module (Admin App)
 *
 * Events, categories and locations — the three views of
 * `Admin - Events.dc.html`, all owned by catalog-service.
 */

export {
  useAdminEvents,
  useEventStats,
  useAdminEventCategories,
  useAdminLocations,
  useEventDecisions,
  type EventPageInfo,
  type UseAdminEventsOptions,
  type UseAdminEventsResult,
  type UseEventStatsResult,
  type UseEventCategoriesResult,
  type UseAdminLocationsResult,
  type UseEventDecisionsResult,
  type EventDecisionResult,
} from './event.hooks';

export {
  ADMIN_EVENTS,
  ADMIN_EVENT_CATEGORIES,
  ADMIN_LOCATIONS,
  EVENT_STATS,
  EVENT_LIST_FIELDS,
  APPROVE_EVENT,
  REJECT_EVENT,
  REQUEST_EVENT_CHANGES,
} from './event.queries';
