import { useEffect, useMemo, useState } from 'react';

/**
 * Whole seconds left until the wall-clock moment `endMs` (epoch milliseconds), or 0 when done or when
 * there is no end. The value is always recomputed from the clock, never decremented, so a tab that was
 * in the background (timers throttled or frozen) shows the right time the moment it is visible again.
 */
export function useCountdownUntil(endMs: number | null): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (endMs === null) return;
    const tick = () => setNow(Date.now());
    tick();
    const timer = setInterval(() => {
      tick();
      if (Date.now() >= endMs) clearInterval(timer);
    }, 500);
    const onWake = () => {
      if (document.visibilityState !== 'hidden') tick();
    };
    document.addEventListener('visibilitychange', onWake);
    window.addEventListener('focus', onWake);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onWake);
      window.removeEventListener('focus', onWake);
    };
  }, [endMs]);
  if (endMs === null) return 0;
  return Math.max(0, Math.ceil((endMs - now) / 1000));
}

/** Whole seconds left until `seconds` from the moment `startKey` changes. Returns 0 when done. */
export function useCountdown(seconds: number, startKey: unknown): number {
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const end = useMemo(() => (seconds > 0 ? Date.now() + seconds * 1000 : null), [seconds, startKey]);
  return useCountdownUntil(end);
}

export function formatMMSS(total: number): string {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
