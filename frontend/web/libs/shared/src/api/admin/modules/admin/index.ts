/**
 * Admin Module (Admin App)
 *
 * Complete admin module for admin app operations including:
 * - User management (create admin, super admin, organizer, attendee)
 * - Organizer approval/rejection
 * - Transaction monitoring and health
 * - Webhook delivery stats
 * - Transaction reconciliation
 * - Bootstrap operations
 *
 * @example
 * ```tsx
 * import {
 *   useCreateAdmin,
 *   useApproveOrganizerRest,
 *   useTransactionHealthSummary,
 *   useReconcileTransaction,
 *   useBootstrapStatus,
 *   type UserRegistrationDto,
 *   type UserResponse,
 *   type TransactionHealthSummary,
 *   createAdmin,
 *   getBootstrapStatus,
 * } from '@pml.tickets/shared/api/admin/modules/admin';
 * ```
 */

// Types
export * from './admin.types';

// REST API operations
export * from './admin.rest';

// React hooks
export * from './admin.hooks';
