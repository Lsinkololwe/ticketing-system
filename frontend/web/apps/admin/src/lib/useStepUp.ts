'use client';

import { useCallback } from 'react';
import { checkFreshAuth } from '@/lib/stepUpAction';

/**
 * `guard(fn)` runs `fn` only when the staff member signed in recently. Otherwise the browser leaves for
 * the step-up sign-in (returning to this page) and the promise never settles: the page is going away.
 *
 *   run(guard(() => decisions.approvePayout(id)), 'Payout approved')
 */
export function useStepUp() {
  const guard = useCallback(async <T,>(fn: () => Promise<T>): Promise<T> => {
    const res = await checkFreshAuth(window.location.pathname + window.location.search);
    if (res.fresh) return fn();
    window.location.assign(res.url);
    return new Promise<T>(() => undefined);
  }, []);
  return { guard };
}
