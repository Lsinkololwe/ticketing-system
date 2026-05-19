'use client';

/**
 * System Health Hooks for Admin App
 *
 * NOTE: These are stub implementations. System health monitoring
 * queries are not yet implemented in the backend.
 */

/**
 * Stub hook for system health monitoring
 */
export function useSystemHealth() {
  return {
    health: {
      status: 'healthy',
      uptime: 0,
      services: [],
    },
    loading: false,
    error: undefined,
    refetch: () => Promise.resolve(),
  };
}

/**
 * Stub hook for transaction health monitoring
 */
export function useTransactionHealth() {
  return {
    health: {
      pendingCount: 0,
      failedCount: 0,
      successRate: 100,
    },
    loading: false,
    error: undefined,
    refetch: () => Promise.resolve(),
  };
}

/**
 * Stub hook for system alerts
 */
export function useSystemAlerts() {
  return {
    alerts: [],
    loading: false,
    error: undefined,
    refetch: () => Promise.resolve(),
  };
}

/**
 * Alias for useSystemHealth
 */
export const useHealthCheck = useSystemHealth;
