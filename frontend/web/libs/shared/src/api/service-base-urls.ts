/**
 * Service Base URLs - Apollo Federation Architecture
 *
 * Architecture Overview:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                     Frontend (Admin/Web)                     │
 * └─────────────────────┬───────────────────────────────────────┘
 *                       │
 *                       ▼
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    API Gateway (8080)                        │
 * │   Routes: /graphql → Apollo Router                          │
 * │           /api/* → REST endpoints                           │
 * └─────────────────────┬───────────────────────────────────────┘
 *                       │
 *                       ▼
 * ┌─────────────────────────────────────────────────────────────┐
 * │                Apollo Router (4000)                          │
 * │   Federation Gateway - Composes all subgraph schemas        │
 * └──────────┬──────────────────┬───────────────────┬───────────┘
 *            │                  │                   │
 *            ▼                  ▼                   ▼
 * ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
 * │ Catalog Service  │ │ Booking Service  │ │ Identity Service │
 * │     (8085)       │ │     (8082)       │ │     (8083)       │
 * └──────────────────┘ └──────────────────┘ └──────────────────┘
 */

/**
 * API Gateway base URL
 */
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

/**
 * GraphQL endpoint through Apollo Router
 * Single unified endpoint for all GraphQL operations
 */
export const GRAPHQL_ENDPOINT =
  process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT || `${API_BASE_URL}/graphql`;

/**
 * WebSocket endpoint for GraphQL subscriptions
 */
export const GRAPHQL_WS_ENDPOINT =
  process.env.NEXT_PUBLIC_GRAPHQL_WS_ENDPOINT ||
  GRAPHQL_ENDPOINT.replace(/^http/, 'ws').replace('/graphql', '/ws');

// ============== REST Endpoints ==============

/**
 * Admin service REST endpoints
 */
export const adminServiceBaseUrl = `${API_BASE_URL}/api/v1/admin`;

/**
 * Files/uploads service REST endpoint
 */
export const filesServiceBaseUrl = `${API_BASE_URL}/api/files`;

/**
 * Health check endpoint
 */
export const healthCheckUrl = `${API_BASE_URL}/actuator/health`;
