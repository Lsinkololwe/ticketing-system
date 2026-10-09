'use client';

import { useCallback } from 'react';
import { requireFreshAuth } from './stepup';

/**
 * `ensureFresh()` resolves true when the caller may proceed. When a recent re-authentication is
 * needed it starts the Keycloak step-up (full-page redirect back to the current page) and resolves false.
 */
export function useStepUp() {
  return useCallback(async (): Promise<boolean> => {
    const next = `${window.location.pathname}${window.location.search}`;
    const r = await requireFreshAuth(next);
    if (r.ok) return true;
    if (r.reason === 'STEP_UP_REQUIRED' && r.stepUpUrl) window.location.assign(r.stepUpUrl);
    else window.location.assign('/logout');
    return false;
  }, []);
}
