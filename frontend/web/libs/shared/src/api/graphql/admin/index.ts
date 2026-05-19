/**
 * Admin API Layer
 *
 * All GraphQL operations and React hooks for the Admin application.
 * Organized by domain (organizers, users, events, analytics).
 *
 * Usage:
 * ```tsx
 * // Import all admin APIs
 * import * as adminApi from '@pml.tickets/shared/api/admin';
 *
 * // Or import specific domain
 * import { useOrganizerApplications } from '@pml.tickets/shared/api/admin/organizers';
 * ```
 */

// Organizer Management
export * from './organizers';

// TODO: Add more admin domains as they are implemented
// export * from './users';
// export * from './events';
// export * from './analytics';
// export * from './payments';
