/**
 * Admin Modules Index
 *
 * Re-exports all admin-specific modules following module-based architecture.
 *
 * Each module contains:
 * - types.ts - Type definitions and helper functions
 * - schemas.ts - Zod validation schemas (optional)
 * - queries.ts - GraphQL query definitions (optional)
 * - mutations.ts - GraphQL mutation definitions (optional)
 * - hooks.ts - React hooks
 * - rest.ts - REST API operations (optional)
 * - keys.ts - Query key factory (optional)
 * - index.ts - Module exports
 */

export * from './organization';
export * from './document';
export * from './admin';
